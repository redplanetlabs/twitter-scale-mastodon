package com.rpl.mastodon.modules;

import com.rpl.mastodon.*;
import com.rpl.mastodon.data.*;
import com.rpl.rama.*;
import com.rpl.rama.helpers.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;

import static com.rpl.mastodon.MastodonHelpers.*;

import java.util.*;

/*
 * This module implements notifications, which include: boosts of your status, replies to your status,
 * favorites to your status, a poll you created or voted on completing, a status you boosted being edited,
 * mentions, new followers, and statuses from users you requested notifications for.
 */
public class Notifications implements RamaModule {

  public int fanoutLimit = 20000;
  public int rangeQueryLimit = 1000;

  private static void extractUserContentNotifications(long authorId, long statusId, long timestamp, String text, PollContent pc, OutputCollector collector) {
    if(pc!=null) collector.emitStream("polls", pc.getExpirationMillis(), new StatusPointer(authorId, statusId));
    for(String mention: Token.filterMentions(Token.parseTokens(text))) {
      collector.emitStream("username", mention, new Notification(NotificationContent.mention(new StatusPointer(authorId, statusId)), timestamp));
    }
  }

  public Block keepNonSuppressedMacro(Object targetId, Object sourceAccountId, Object authorId, Object statusId) {
    String convoIdVar = Helpers.genVar("convoId");
    String isSuppressedVar = Helpers.genVar("isSuppressed");
    String isStatusSuppressedVar = Helpers.genVar("isStatusSuppressedVar");
    return Block.select("$$accountIdToSuppressions", Relationships.isBlockedOrMutedPath(targetId, sourceAccountId, true)).out(isSuppressedVar)
                .hashPartition("$$statusIdToConvoId", authorId)
                .localSelect("$$statusIdToConvoId", Path.key(statusId).nullToVal(statusId)).out(convoIdVar)
                .select("$$muterToStatusIds", Path.key(targetId).view(Ops.CONTAINS, convoIdVar)).out(isStatusSuppressedVar)
                .keepTrue(new Expr(Ops.AND, new Expr(Ops.NOT, isSuppressedVar), new Expr(Ops.NOT, isStatusSuppressedVar)));
  }

  @Override
  public void define(Setup setup, Topologies topologies) {
      setup.declareDepot("*dismissDepot", Depot.hashBy(MastodonHelpers.ExtractAccountId.class));

      setup.clusterDepot("*statusWithIdDepot", Core.class.getName(), "*statusWithIdDepot");
      setup.clusterDepot("*favoriteStatusDepot", Core.class.getName(), "*favoriteStatusDepot");
      setup.clusterDepot("*followAndBlockAccountDepot", Relationships.class.getName(), "*followAndBlockAccountDepot");

      setup.clusterPState("$$accountIdToSuppressions", Relationships.class.getName(), "$$accountIdToSuppressions");
      setup.clusterPState("$$statusIdToBoostersById", Core.class.getName(), "$$statusIdToBoostersById");
      setup.clusterPState("$$nameToUser", Core.class.getName(), "$$nameToUser");
      setup.clusterPState("$$accountIdToStatuses", Core.class.getName(), "$$accountIdToStatuses");
      setup.clusterPState("$$pollVotes", Core.class.getName(), "$$pollVotes");
      setup.clusterPState("$$muterToStatusIds", Core.class.getName(), "$$muterToStatusIds");
      setup.clusterPState("$$statusIdToConvoId", Core.class.getName(), "$$statusIdToConvoId");
      setup.clusterPState("$$followeeToNotifiedFollowerIds", Relationships.class.getName(), "$$followeeToNotifiedFollowerIds");

      MicrobatchTopology notifications = topologies.microbatch("notifications");
      KeyToFixedItemsPStateGroup accountIdToNotificationsTimeline =
              new KeyToFixedItemsPStateGroup("$$accountIdToNotificationsTimeline", 800, Long.class, Notification.class);
      accountIdToNotificationsTimeline.declarePStates(notifications);

      // This handles expiring polls in a fault-tolerant way.
      TopologyScheduler pollScheduler = new TopologyScheduler("$$poll");
      pollScheduler.declarePStates(notifications);

      notifications.pstate("$$statusWithIdToFollowerFanout", PState.mapSchema(StatusWithId.class, FollowerFanout.class));

      notifications.source("*statusWithIdDepot").out("*microbatch")
                   .anchor("Root")
                   // A convenient place to handle TopologyScheduler expirations is at the root
                   // of a microbatch, disconnected from the microbatch data itself.
                   .macro(pollScheduler.handleExpirations("*pointer", "*currentTimeMillis",
                     Block.macro(extractFields("*pointer", "*authorId", "*statusId"))
                          .select("$$accountIdToStatuses", Path.key("*authorId", "*statusId").first()).out("*status")
                          .keepTrue(new Expr((Status s, Long currentTimeMillis) -> {
                            // this checks the poll is actually expired, handling the case
                            // where a poll's expiration time was edited
                            PollContent pc = MastodonHelpers.extractPollContent(s);
                            return pc != null && pc.getExpirationMillis() <= currentTimeMillis;
                          }, "*status", "*currentTimeMillis"))
                          .each((Long authorId, Long statusId, Long currentTimeMillis) ->
                            new Notification(NotificationContent.pollComplete(new StatusPointer(authorId, statusId)), currentTimeMillis),
                            "*authorId", "*statusId", "*currentTimeMillis").out("*notification")
                          .anchor("PollFanoutRoot")))

                   .hook("PollFanoutRoot")
                   // This fans out poll expiration to the creator of the poll even if they didn't vote in it.
                   .each(Ops.IDENTITY, "*authorId").out("*targetId")
                   .anchor("PollSelfFanout")

                   .hook("PollFanoutRoot")
                   .hashPartition("$$pollVotes", "*authorId")
                   .loopWithVars(LoopVars.var("*i", -1),
                     Block.yieldIfOvertime()
                          .localSelect("$$pollVotes",
                              Path.key("*statusId", "allVoters")
                                  .sortedMapRangeFrom("*i", SortedRangeFromOptions.excludeStart().maxAmt(BULK_FETCH_SIZE))
                                  .subselect(Path.mapKeys())).out("*targetIds")
                          .atomicBlock(
                              Block.each(Ops.EXPLODE, "*targetIds").out("*targetId")
                                   .emitLoop("*targetId"))
                          .ifTrue(new Expr(Ops.EQUAL, new Expr(Ops.SIZE, "*targetIds"), BULK_FETCH_SIZE),
                              Block.continueLoop(new Expr(Ops.LAST, "*targetIds")))).out("*targetId")
                   // If author of poll voted on it, don't fan it out here since it's already fanned out
                   // in the PollSelfFanout branch.
                   .keepTrue(new Expr(Ops.NOT_EQUAL, "*targetId", "*authorId"))
                   .anchor("PollVoterFanout")

                   .unify("PollSelfFanout", "PollVoterFanout")
                   .hashPartition("*targetId")
                   .macro(accountIdToNotificationsTimeline.addItem("*targetId", "*notification"))

                   .hook("Root")
                   .explodeMicrobatch("*microbatch").out("*data")
                   .keepTrue(new Expr(Ops.OR, new Expr(Ops.IS_INSTANCE_OF, EditStatus.class, "*data"),
                                              new Expr(Ops.IS_INSTANCE_OF, StatusWithId.class, "*data")))
                   .macro(extractFields("*data", "*statusId", "*status"))
                   .macro(extractFields("*status", "*authorId", "*content", "*timestamp"))
                   .anchor("DataRoot")

                   // This extracts the different kidns of notifications that can come from statuses.
                   .each((Long statusId, Long authorId, Long timestamp, Object content, OutputCollector collector) -> {
                     if(content instanceof ReplyStatusContent) {
                       long parentAuthorId = ((ReplyStatusContent) content).getParent().authorId;
                       collector.emit(parentAuthorId, new Notification(NotificationContent.mention(new StatusPointer(authorId, statusId)), timestamp));
                       extractUserContentNotifications(authorId, statusId, timestamp, ((ReplyStatusContent) content).getText(), ((ReplyStatusContent) content).getPollContent(), collector);
                     } else if(content instanceof BoostStatusContent) {
                       long parentAuthorId = ((BoostStatusContent) content).boosted.authorId;
                       long parentStatusId = ((BoostStatusContent) content).boosted.statusId;
                       collector.emit(parentAuthorId, new Notification(NotificationContent.boost(new StatusResponseNotificationContent(authorId, new StatusPointer(parentAuthorId, parentStatusId))), timestamp));
                     } else {
                       extractUserContentNotifications(authorId, statusId, timestamp, ((NormalStatusContent) content).getText(), ((NormalStatusContent) content).getPollContent(), collector);
                     }
                   }, "*statusId", "*authorId", "*timestamp", "*content").outStream("username", "Username", "*targetName", "*notification")
                                                                         .outStream("polls", "Polls", "*expirationMillis", "*pointer")
                                                                         .out("*targetId", "*notification")
                   .anchor("MainNotifications")

                   .hook("Username")
                   .select("$$nameToUser", Path.key("*targetName").must("accountId")).out("*targetId")
                   .keepTrue(new Expr((Object content, Long targetId) -> {
                     if(content instanceof ReplyStatusContent) return ((ReplyStatusContent) content).getParent().authorId != targetId;
                     else return true;
                   }, "*content", "*targetId"))
                   .anchor("NameMention")

                   .hook("Polls")
                   .macro(pollScheduler.scheduleItem("*expirationMillis", "*pointer"))

                   .hook("DataRoot")
                   .keepTrue(new Expr(Ops.OR, new Expr(Ops.IS_INSTANCE_OF, NormalStatusContent.class, "*content"),
                                              new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content")))
                   .hashPartition("$$statusIdToBoostersById", "*authorId")
                   .each((Long authorId, Long statusId, Long timestamp) -> new Notification(NotificationContent.boostedUpdate(new StatusPointer(authorId, statusId)), timestamp),
                         "*authorId", "*statusId", "*timestamp").out("*notification")
                   .loopWithVars(LoopVars.var("*i", -1),
                       Block.yieldIfOvertime()
                            .localSelect("$$statusIdToBoostersById",
                                         Path.key("*statusId")
                                             .sortedMapRangeFrom("*i", SortedRangeFromOptions.excludeStart().maxAmt(BULK_FETCH_SIZE))).out("*targetMap")
                            .atomicBlock(
                               Block.each(Ops.EXPLODE_MAP, "*targetMap").out("*j", "*targetPointer")
                                    .each((StatusPointer sp) -> sp.authorId, "*targetPointer").out("*targetId")
                                    .emitLoop("*targetId"))
                            .ifTrue(new Expr(Ops.EQUAL, new Expr(Ops.SIZE, "*targetMap"), BULK_FETCH_SIZE),
                               Block.each((SortedMap m) -> m.lastKey(), "*targetMap").out("*newI")
                                    .continueLoop("*newI"))).out("*targetId")
                   .anchor("BoosterNotifications")

                   .unify("BoosterNotifications", "MainNotifications", "NameMention")
                   .keepTrue(new Expr(Ops.NOT_EQUAL, "*authorId", "*targetId"))
                   .macro(keepNonSuppressedMacro("*targetId", "*authorId", "*authorId", "*statusId"))
                   .hashPartition("*targetId")
                   .macro(accountIdToNotificationsTimeline.addItem("*targetId", "*notification"))

                   // start fanout for new statuses
                   .hook("Root")
                   .explodeMicrobatch("*microbatch").out("*statusWithId")
                   .keepTrue(new Expr(Ops.IS_INSTANCE_OF, StatusWithId.class, "*statusWithId"))
                   .each((StatusWithId statusWithId) -> {
                       StatusVisibility vis = MastodonHelpers.getStatusVisibility(statusWithId.status);
                       return vis == StatusVisibility.Public || vis == StatusVisibility.Private || vis == StatusVisibility.Unlisted;
                   }, "*statusWithId").out("*visibleToFollowers")
                   .keepTrue("*visibleToFollowers")
                   .each(Ops.IDENTITY, -1L).out("*nextIndex")
                   .anchor("FollowerFanout")

                   // Continue fanout of statuses to followers
                   .hook("Root")
                   .allPartition()
                   .localSelect("$$statusWithIdToFollowerFanout", Path.all()).out("*keyAndVal")
                   .each(Ops.EXPAND, "*keyAndVal").out("*statusWithId", "*followerFanout")
                   .localTransform("$$statusWithIdToFollowerFanout", Path.key("*statusWithId").termVoid())
                   .macro(extractFields("*followerFanout", "*nextIndex"))
                   .anchor("FollowerFanoutContinue")

                   .unify("FollowerFanout", "FollowerFanoutContinue")
                   .macro(extractFields("*statusWithId", "*status"))
                   .macro(extractFields("*status", "*authorId"))
                   .hashPartition("$$followeeToNotifiedFollowerIds", "*authorId")
                   .each((RamaFunction0) ArrayList::new).out("*followerIds")
                   .loopWithVars(LoopVars.var("*loopNextIndex", "*nextIndex"),
                       Block.localSelect("$$followeeToNotifiedFollowerIds", Path.key("*authorId").sortedSetRangeFrom("*loopNextIndex", SortedRangeFromOptions.excludeStart().maxAmt(rangeQueryLimit))).out("*idsBatch")
                            .each((List<Long> acc, SortedSet<Long> resultBatch, Integer rangeQueryLimit) -> {
                                resultBatch.forEach(followerId -> acc.add(followerId));
                                return resultBatch.size() < rangeQueryLimit ? null : resultBatch.last();
                            }, "*followerIds", "*idsBatch", rangeQueryLimit).out("*nextUnhandledId")
                            .ifTrue(new Expr(Ops.OR, new Expr(Ops.IS_NULL, "*nextUnhandledId"), new Expr(Ops.GREATER_THAN_OR_EQUAL, new Expr(Ops.SIZE, "*followerIds"), fanoutLimit)),
                                    Block.emitLoop("*nextUnhandledId"),
                                    Block.continueLoop("*nextUnhandledId"))).out("*nextFanoutStart")
                   .ifTrue(new Expr(Ops.IS_NOT_NULL, "*nextFanoutStart"),
                           Block.each((Long authorId, Long nextFollowerId, Status status) -> new FollowerFanout(authorId, nextFollowerId, FanoutAction.Add, status, -1), "*authorId", "*nextFanoutStart", "*status").out("*followerFanout")
                                .localTransform("$$statusWithIdToFollowerFanout", Path.key("*statusWithId").termVal("*followerFanout")))
                   .macro(extractFields("*status", "*timestamp"))
                   .each((StatusWithId statusWithId, Long timestamp) -> {
                       return new Notification(NotificationContent.followeeStatus(new StatusPointer(statusWithId.status.authorId, statusWithId.statusId)), timestamp);
                   }, "*statusWithId", "*timestamp").out("*notification")
                   .each(Ops.EXPLODE, "*followerIds").out("*followerId")
                   .hashPartition("*followerId")
                   .macro(accountIdToNotificationsTimeline.addItem("*followerId", "*notification"));

      notifications.source("*favoriteStatusDepot").out("*microbatch")
                   .explodeMicrobatch("*microbatch").out("*data")
                   .keepTrue(new Expr(Ops.IS_INSTANCE_OF, FavoriteStatus.class, "*data"))
                   .macro(extractFields("*data", "*accountId", "*target", "*timestamp"))
                   .macro(extractFields("*target", "*authorId", "*statusId"))
                   .keepTrue(new Expr(Ops.NOT_EQUAL, "*authorId", "*accountId"))
                   .macro(keepNonSuppressedMacro("*authorId", "*accountId","*authorId", "*statusId"))
                   .hashPartition("*authorId")
                   .each((Long accountId, Long authorId, Long statusId, Long timestamp) ->
                     new Notification(NotificationContent.favorite(new StatusResponseNotificationContent(accountId, new StatusPointer(authorId, statusId))), timestamp),
                     "*accountId", "*authorId", "*statusId", "*timestamp").out("*notification")
                   .macro(accountIdToNotificationsTimeline.addItem("*authorId", "*notification"));

      notifications.source("*followAndBlockAccountDepot").out("*microbatch")
                   .explodeMicrobatch("*microbatch").out("*data")
                   .anchor("FollowRoot")
                   .keepTrue(new Expr(Ops.IS_INSTANCE_OF, FollowLockedAccount.class, "*data"))
                   .macro(extractFields("*data", "*accountId", "*requesterId", "*timestamp"))
                   .hashPartition("*accountId")
                   .each((Long requesterId, Long timestamp) -> new Notification(NotificationContent.followRequest(requesterId), timestamp),
                         "*requesterId", "*timestamp").out("*notification")
                   .macro(accountIdToNotificationsTimeline.addItem("*accountId", "*notification"))

                   .hook("FollowRoot")
                   .keepTrue(new Expr(Ops.IS_INSTANCE_OF, FollowAccount.class, "*data"))
                   .macro(extractFields("*data", "*accountId", "*targetId", "*timestamp"))
                   .hashPartition("*targetId")
                   .each((Long accountId, Long timestamp) -> new Notification(NotificationContent.follow(accountId), timestamp),
                         "*accountId", "*timestamp").out("*notification")
                   .macro(accountIdToNotificationsTimeline.addItem("*targetId", "*notification"));

      notifications.source("*dismissDepot").out("*microbatch")
                   .explodeMicrobatch("*microbatch").out("*data")
                   .macro(extractFields("*data", "*accountId", "*notificationId"))
                   .ifTrue(new Expr(Ops.IS_NOT_NULL, "*notificationId"),
                      Block.macro(accountIdToNotificationsTimeline.removeItemById("*accountId", "*notificationId")),
                      Block.macro(accountIdToNotificationsTimeline.clearItems("*accountId")));
  }
}
