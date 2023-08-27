package com.rpl.mastodon.modules;

import clojure.lang.*;

import com.rpl.mastodon.data.*;
import com.rpl.mastodon.navs.TField;
import com.rpl.rama.*;
import com.rpl.rama.helpers.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.rpl.mastodon.MastodonHelpers.*;

/**
 * This module tracks a variety of relationships between entities, most notably follows
 * (which forms the social graph), mutes/blocks, and lists. Multiple views of the social
 * graph are maintained for various purposes:
 *  - Efficiently fetch who a user follows in the order in which they were followed
 *  - Efficiently fetch a user's followers in the order in which they were followed
 *  - Iterate through all of a user's followers in parallel in balanced way, which is important
 *    for maximizing throughput of fanout (see $$partitionedFollowers and similarly named PStates)
 *
 * Another big feature implemented by this module is "who to follow". We talked about this a lot
 * on our blog here: https://blog.redplanetlabs.com/2023/08/15/how-we-reduced-the-cost-of-building-twitter-at-twitter-scale-by-100x/#Personalized_follow_suggestions
 *
 * There are other miscellaneous features supported by this module like auth codes (for login),
 * filters, profile notes, and featured accounts.
 */
public class Relationships implements RamaModule {
    private static final int FAMILIAR_FOLLOWERS_LIMIT = 20;
    /*
     * These are constants that are sometimes adjusted in unit tests to ease testing.
     */
    public static int maxFilterClauses = 500;
    public long whoToFollowTickMillis = 1000 * 30; // 30 seconds
    public long muteExpirationTickMillis = 15 * 1000; // 15 seconds
    public long filterExpirationTickMillis = 60 * 1000; // 60 seconds
    public int numWhoToFollowSuggestions = 80;
    public int numTopFollowedUsers = 30;
    public int whoToFollowThreshold1 = 10;
    public int whoToFollowThreshold2 = 100;
    public int newTaskCutoffLimit = 1000;
    public boolean isCyclicRecompute = true;
    public int relationshipCountLimit = 100000; // total number of follows/blocks/mutes
    public int listCountLimit = 500; // number of lists created
    public int listMemberCountLimit = 5000; // number of members in each list
    public RamaFunction1<Topologies, Object> testTopologiesHook = null;

    public static boolean notEmpty(List l) {
      return !l.isEmpty();
    }

    public static boolean shouldMute(MuteAccountOptions options, boolean notifications) {
      return options != null && (!notifications || options.muteNotifications);
    }

    /*
     * This helper defines a path for a common need throughout the implementation to know if someone is
     * blocked or muted by another user.
     */
    public static Path.Impl isBlockedOrMutedPath(Object accountId, Object targetId, boolean notifications) {
      return Path.key(accountId).subselect(Path.multiPath(Path.key("muted", targetId).view(Relationships::shouldMute, notifications).filterPred(Ops.IDENTITY),
                                                          Path.key("blocked").setElem(targetId))).view(Relationships::notEmpty);
    }

    private static boolean isMuteExpired(MuteAccountOptions options, long currentTimeMillis) {
      return options != null && options.isSetExpirationMillis() && options.expirationMillis <= currentTimeMillis;
    }

    private static boolean isFilterExpired(Filter filter, long currentTimeMillis) {
      return filter != null && filter.isSetExpirationMillis() && filter.expirationMillis <= currentTimeMillis;
    }

    private static Filter updateFilter(Filter filter, EditFilter edit) {
        if (edit.isSetAction()) filter.action = edit.getAction();
        if (edit.isSetContext()) filter.contexts = edit.getContext();
        if (edit.isSetExpirationMillis()) filter.expirationMillis = edit.getExpirationMillis();
        else filter.unsetExpirationMillis();
        if (edit.isSetTitle()) filter.setTitle(edit.getTitle());
        if (edit.isSetKeywords()) {
            for (EditFilterKeyword keywordEdit : edit.getKeywords()) {
                if (keywordEdit.isSetAddKeyword()) {
                    if (filter.getKeywords().size() < maxFilterClauses) filter.getKeywords().add(keywordEdit.getAddKeyword());
                } else if (keywordEdit.isSetDestroyKeyword()) {
                    String destroyWord = keywordEdit.getDestroyKeyword();
                    filter.setKeywords(filter.getKeywords()
                                             .stream()
                                             .filter(m -> !m.getWord().equals(destroyWord))
                                             .collect(Collectors.toList()));
                } else if (keywordEdit.isSetUpdateKeyword()) {
                    UpdateKeyword update = keywordEdit.getUpdateKeyword();
                    Optional<KeywordFilter> maybeKeyword = filter.getKeywords().stream()
                            .filter((KeywordFilter k) -> k.getWord().equals(update.getCurrentWord())).findFirst();
                    if (maybeKeyword.isPresent()) {
                        KeywordFilter keyword = maybeKeyword.get();
                        keyword.setWholeWord(update.wholeWord);
                        keyword.setWord(update.newWord);
                    }
                }
            }
        }

        return filter;
    }

    private static Follower makeFollower(long accountId, Boolean showBoosts, List<String> languages, String sharedInboxUrl, Follower existingFollower) {
      Follower follower = existingFollower == null ? new Follower(accountId, true) : existingFollower;
      if (showBoosts != null) follower.setShowBoosts(showBoosts);
      if (languages != null) follower.setLanguages(languages);
      follower.setSharedInboxUrl(sharedInboxUrl);
      return follower;
    }

    /*
     * This defines most of the functionality for this module that requires millisecond-level
     * update latencies. It defines them in a single stream topology, but these could also
     * be broken up into multiple stream topologies.
     */
    private void declareStreamTopology(Topologies topologies) {
        StreamTopology stream = topologies.stream("relationshipsStream");

        ModuleUniqueIdPState listId = new ModuleUniqueIdPState("$$listId").descending();
        listId.declarePState(stream);
        ModuleUniqueIdPState filterId = new ModuleUniqueIdPState("$$filterId").descending();
        filterId.declarePState(stream);

        // list pstates
        stream.pstate("$$listIdToList", PState.mapSchema(Long.class, AccountList.class));
        stream.pstate("$$authorIdToListIds", PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));
        stream.pstate("$$memberIdToListIds", PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));
        stream.pstate("$$listIdToMemberIds", PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));

        // This PState is used to ensure duplicate client appends don't result in undesired PState updates. For example,
        // a network partition of the client may cause it to retry and append the same data again. This pattern
        // is also used in other modules.
        stream.pstate("$$postUUIDToGeneratedId", PState.mapSchema(String.class, Long.class));

        stream.source("*listDepot", StreamSourceOptions.retryAllAfter()).out("*data")
              .subSource("*data",
                 SubSource.create(AccountList.class)
                          .macro(listId.genId("*listId"))
                          .macro(extractFields("*data", "*authorId"))

                          .localSelect("$$authorIdToListIds", Path.key("*authorId").view(Ops.SIZE)).out("*listCount")
                          .keepTrue(new Expr(Ops.LESS_THAN, "*listCount", listCountLimit))

                          .hashPartition("*authorId")
                          .localTransform("$$authorIdToListIds", Path.key("*authorId").voidSetElem().termVal("*listId"))

                          .hashPartition("*listId")
                          .localTransform("$$listIdToList", Path.key("*listId").termVal("*data")),
                 SubSource.create(AccountListWithId.class)
                          .macro(extractFields("*data", "*listId", "*accountList"))

                          .hashPartition("*listId")
                          .localTransform("$$listIdToList", Path.must("*listId").termVal("*accountList")),
                 SubSource.create(RemoveAccountList.class)
                          .macro(extractFields("*data", "*listId"))

                          .hashPartition("*listId")
                          .localSelect("$$listIdToList", Path.must("*listId")).out("*list")
                          .macro(extractFields("*list", "*authorId"))

                          .hashPartition("*authorId")
                          .localTransform("$$authorIdToListIds", Path.key("*authorId").setElem("*listId").termVoid())

                          .hashPartition("*listId")
                          .localTransform("$$listIdToList", Path.key("*listId").termVoid())
                          .localSelect("$$listIdToMemberIds", Path.key("*listId").all()).out("*memberId")
                          .localTransform("$$listIdToMemberIds", Path.key("*listId").termVoid())

                          .hashPartition("*memberId")
                          .localTransform("$$memberIdToListIds", Path.key("*memberId").setElem("*listId").termVoid()),
                 SubSource.create(AccountListMember.class)
                          .macro(extractFields("*data", "*listId", "*memberId"))

                          .localSelect("$$listIdToMemberIds", Path.key("*listId").view(Ops.SIZE)).out("*memberCount")
                          .keepTrue(new Expr(Ops.LESS_THAN, "*memberCount", listMemberCountLimit))

                          .hashPartition("*memberId")
                          .localTransform("$$memberIdToListIds", Path.key("*memberId").voidSetElem().termVal("*listId"))

                          .hashPartition("*listId")
                          .localTransform("$$listIdToMemberIds", Path.key("*listId").voidSetElem().termVal("*memberId")),
                 SubSource.create(RemoveAccountListMember.class)
                          .macro(extractFields("*data", "*listId", "*memberId"))

                          .hashPartition("*memberId")
                          .localTransform("$$memberIdToListIds", Path.key("*memberId").setElem("*listId").termVoid())

                          .hashPartition("*listId")
                          .localTransform("$$listIdToMemberIds", Path.key("*listId").setElem("*memberId").termVoid()));

        // filter pstates
        stream.pstate("$$accountIdToFilterIdToFilter", PState.mapSchema(Long.class, PState.mapSchema(Long.class, Filter.class)));

        // This utility from the rama-helpers project makes it extremely easy to schedule future work
        // for a topology in a fault-tolerant way. This instance is used to expire filters.
        TopologyScheduler filterScheduler = new TopologyScheduler("$$filterExpiration");
        filterScheduler.declarePStates(stream);

        stream.source("*filterDepot", StreamSourceOptions.retryAllAfter()).out("*data")
              .subSource("*data",
                 SubSource.create(AddFilter.class)
                          .macro(extractFields("*data", "*filter", "*uuid"))
                          .macro(extractFields("*filter", "*accountId", "*expirationMillis"))
                          // Fetch id if it already exists, assign filter id otherwise
                          .select("$$postUUIDToGeneratedId", Path.key("*uuid")).out("*maybeId")
                          .ifTrue(new Expr(Ops.IS_NULL, "*maybeId"),
                                  Block.macro(filterId.genId("*filterId"))
                                       .localTransform("$$postUUIDToGeneratedId", Path.key("*uuid").termVal("*filterId")),

                                  Block.each(Ops.IDENTITY, "*maybeId").out("*filterId"))
                          .hashPartition("*accountId")
                          // Schedule expiration if needed
                          .ifTrue(new Expr(Ops.IS_NOT_NULL, "*expirationMillis"),
                                  Block.macro(filterScheduler.scheduleItem("*expirationMillis", new Expr(Ops.TUPLE, "*accountId", "*filterId"))))
                          // Finally store the filter
                          .localTransform("$$accountIdToFilterIdToFilter", Path.key("*accountId", "*filterId").termVal("*filter")),
                 SubSource.create(AddStatusToFilter.class)
                          .macro(extractFields("*data", "*filterId", "*accountId", "*target"))
                          .localTransform("$$accountIdToFilterIdToFilter",
                                          Path.must("*accountId", "*filterId")
                                              .customNavBuilder(TField::new, "statuses")
                                              .term((Set<StatusPointer> statuses, StatusPointer target) -> {
                                                  if (statuses.size() >= maxFilterClauses) return statuses;
                                                  statuses.add(target);
                                                  return statuses;
                                              }, "*target")),
                 SubSource.create(RemoveStatusFromFilter.class)
                          .macro(extractFields("*data", "*filterId", "*accountId", "*target"))
                          .localTransform("$$accountIdToFilterIdToFilter",
                                          Path.must("*accountId", "*filterId")
                                              .customNavBuilder(TField::new, "statuses")
                                              .term((Set<StatusPointer> statuses, StatusPointer target) -> {
                                                  statuses.remove(target);
                                                  return statuses;
                                              }, "*target")),
                 SubSource.create(EditFilter.class)
                          .macro(extractFields("*data", "*filterId", "*accountId", "*expirationMillis", "*keywords"))
                          // Schedule expiration if needed
                          .ifTrue(new Expr(Ops.IS_NOT_NULL, "*expirationMillis"),
                                  Block.macro(filterScheduler.scheduleItem("*expirationMillis", new Expr(Ops.TUPLE, "*accountId", "*filterId"))))
                          // Actually do the update
                          .localTransform("$$accountIdToFilterIdToFilter",
                                          Path.must("*accountId", "*filterId").term(Relationships::updateFilter, "*data")),
                 SubSource.create(RemoveFilter.class)
                          .macro(extractFields("*data", "*filterId", "*accountId"))
                          .localTransform("$$accountIdToFilterIdToFilter", Path.key("*accountId", "*filterId").termVoid()));
        stream.source("*filterExpirationTick")
              .macro(filterScheduler.handleExpirations("*tuple", "*currentTimeMillis",
                Block.each(Ops.EXPAND, "*tuple").out("*accountId", "*filterId")
                     .localTransform("$$accountIdToFilterIdToFilter", Path.key("*accountId")
                                                                          .must("*filterId")
                                                                          .filterSelected(Path.view(Relationships::isFilterExpired, "*currentTimeMillis")
                                                                                              .filterPred(Ops.IDENTITY))
                                                                          .termVoid())));
        // account id -> requesting account ids
        KeyToLinkedEntitySetPStateGroup accountIdToFollowRequests = new KeyToLinkedEntitySetPStateGroup("$$accountIdToFollowRequests", Long.class, FollowLockedAccount.class)
            .entityIdFunction(Long.class, req -> ((FollowLockedAccount) req).requesterId)
            .descending();
        accountIdToFollowRequests.declarePStates(stream);

        // In this case the account ID in Follower is the account being followed, and the other fields are about the follower.
        // This PState supports:
        //  - Getting an account's follows in order in which they were followed (paginated)
        //  - Getting number of follows for an account
        //  - Querying whether user A follows user B
        KeyToLinkedEntitySetPStateGroup followerToFollowees = new KeyToLinkedEntitySetPStateGroup("$$followerToFollowees", Long.class, Follower.class)
            .entityIdFunction(Long.class, new ExtractAccountId())
            .descending();

        // This PState supports:
        //  - Getting an account's followers in order in which they followed (paginated)
        //  - Getting number of followers for an account
        //  - Querying whether user A follows user B. Note that this query can be answered
        //    equally well by either this PState or followerToFollowees.
        KeyToLinkedEntitySetPStateGroup followeeToFollowers = new KeyToLinkedEntitySetPStateGroup("$$followeeToFollowers", Long.class, Follower.class)
            .entityIdFunction(Long.class, new ExtractAccountId())
            .descending();
        followerToFollowees.declarePStates(stream);
        followeeToFollowers.declarePStates(stream);

        // The next three PStates define an additional view of the social graph to balance processing as much as possible
        // during fanout even if the social graph is extremely unbalanced (e.g. some users having 20M followers while the average
        // is 400). How this works is explained more below.

        // account ID to list of account IDs on which partitioned followers are kept
        stream.pstate("$$partitionedFollowersControl", PState.mapSchema(Long.class, List.class));
        // account ID -> follower ID -> task
        stream.pstate("$$partitionedFollowerTasks", PState.mapSchema(Long.class, PState.mapSchema(Long.class, Integer.class).subindexed()));

        stream.pstate("$$partitionedFollowers", PState.mapSchema(Long.class, PState.mapSchema(Long.class, Follower.class).subindexed()));

        // We keep an extra PState tracking only followers who've requested notifications for the notifications module
        stream.pstate("$$followeeToNotifiedFollowerIds", PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));

        // This PState is used for federation
        stream.pstate("$$followeeToRemoteServerToFollowers",
                      PState.mapSchema(Long.class, // followee id
                                       PState.mapSchema(String.class, // remote server (shared inbox url)
                                                        PState.setSchema(Long.class).subindexed() // follower ids
                                                        ).subindexed()));

        stream.pstate("$$accountIdToSuppressions",
                      PState.mapSchema(Long.class, PState.fixedKeysSchema("muted", PState.mapSchema(Long.class, MuteAccountOptions.class).subindexed(),
                                                                          "blocked", PState.setSchema(Long.class).subindexed())));

        TopologyScheduler muteScheduler = new TopologyScheduler("$$mutes");
        muteScheduler.declarePStates(stream);

        KeyToLinkedEntitySetPStateGroup featurerToFeaturees = new KeyToLinkedEntitySetPStateGroup("$$featurerToFeaturees", Long.class, Long.class).descending();
        featurerToFeaturees.declarePStates(stream);

        stream.pstate("$$hashtagToFollowers", PState.mapSchema(String.class, PState.setSchema(Long.class).subindexed()));

        stream.source("*followAndBlockAccountDepot", StreamSourceOptions.retryAllAfter()).out("*initialData")
              .anchor("SocialGraphRoot")
              .each(Ops.IDENTITY, "*initialData").out("*data")
              .anchor("Normal")

              .hook("SocialGraphRoot")
              .keepTrue(new Expr(Ops.IS_INSTANCE_OF, BlockAccount.class, "*initialData"))
              .each((BlockAccount data, OutputCollector collector) -> {
                  collector.emit(new RemoveFollowAccount(data.getAccountId(), data.getTargetId(), data.getTimestamp()));
                  collector.emit(new RemoveFollowAccount(data.getTargetId(), data.getAccountId(), data.getTimestamp()));
                  collector.emit(new RejectFollowRequest(data.getAccountId(), data.getTargetId()));
              }, "*initialData").out("*data")
              .anchor("ImplicitUnfollow")

              .hook("SocialGraphRoot")
              .keepTrue(new Expr(Ops.IS_INSTANCE_OF, AcceptFollowRequest.class, "*initialData"))
              .macro(extractFields("*initialData", "*accountId", "*requesterId"))
              .localSelect("$$accountIdToFollowRequests", Path.key("*accountId").must("*requesterId")).out("*followRequestId")
              .localSelect("$$accountIdToFollowRequestsById", Path.key("*accountId", "*followRequestId")).out("*followRequest")
              // FollowAccount.targetId is equivalent to FollowAccountRequest.accountId
              // so we need to partition for the implicit event
              .hashPartition("*requesterId")
              .each((AcceptFollowRequest data, FollowLockedAccount req) -> {
                  FollowAccount follow = new FollowAccount(data.getRequesterId(), data.getAccountId(), data.getTimestamp());
                  if (req.isSetShowBoosts()) follow.setShowBoosts(req.showBoosts);
                  if (req.isSetNotify()) follow.setNotify(req.notify);
                  if (req.isSetLanguages()) follow.setLanguages(req.languages);
                  return follow;
              }
              , "*initialData", "*followRequest").out("*data")
              .anchor("CompleteFollowRequest")

              .hook("SocialGraphRoot")
              .keepTrue(new Expr(Ops.IS_INSTANCE_OF, FollowLockedAccount.class, "*initialData"))
              .macro(extractFields("*initialData", "*accountId", "*requesterId"))
              .localSelect("$$followeeToFollowers", Path.key("*accountId", "*requesterId")).out("*existingFollowerId")
              // Existing relationship means we do NOT need to create a follow request, just update settings.
              .ifTrue(new Expr(Ops.IS_NOT_NULL, "*existingFollowerId"),
                 Block.each((FollowLockedAccount data) -> {
                        FollowAccount follow = new FollowAccount(data.requesterId, data.accountId, data.timestamp);
                        if(data.isSetShowBoosts()) follow.setShowBoosts(data.isShowBoosts());
                        if(data.isSetNotify()) follow.setNotify(data.isNotify());
                        if(data.isSetLanguages()) follow.setLanguages(data.getLanguages());
                        return follow;
                      }, "*initialData").out("*data")
                      .hashPartition("*requesterId")
                      .anchor("UpdatePrivateFollow"),
                 Block.macro(extractFields("*initialData", "*accountId", "*requesterId"))
                      .macro(accountIdToFollowRequests.addToLinkedSet("*accountId", "*initialData")))

              .unify("Normal", "ImplicitUnfollow", "CompleteFollowRequest", "UpdatePrivateFollow")
              .subSource("*data",
                 SubSource.create(FollowAccount.class)
                          .macro(extractFields("*data", "*accountId", "*targetId", "*followerSharedInboxUrl", "*showBoosts", "*notify", "*languages"))
                          .localSelect("$$followerToFollowees", Path.key("*accountId").view(Ops.SIZE)).out("*followeeCount")
                          .keepTrue(new Expr(Ops.LESS_THAN, "*followeeCount", relationshipCountLimit))
                          .localSelect("$$followerToFollowees", Path.key("*accountId", "*targetId")).out("*followeeId")
                          .localSelect("$$followerToFolloweesById", Path.key("*accountId", "*followeeId")).out("*existingFollowee")
                          .each(Relationships::makeFollower, "*targetId", "*showBoosts", "*languages", "*followerSharedInboxUrl", "*existingFollowee").out("*followee")
                          .macro(followerToFollowees.addToLinkedSet("*accountId", "*followee"))
                          .hashPartition("*targetId")
                          .localSelect("$$followeeToFollowers", Path.key("*targetId", "*accountId")).out("*followerId")
                          .localSelect("$$followeeToFollowersById", Path.key("*targetId", "*followerId")).out("*existingFollower")
                          .each(Relationships::makeFollower, "*accountId", "*showBoosts", "*languages", "*followerSharedInboxUrl", "*existingFollower").out("*follower")
                          .macro(followeeToFollowers.addToLinkedSet("*targetId", "*follower"))
                          .macro(accountIdToFollowRequests.removeFromLinkedSetByEntityId("*targetId", "*accountId"))
                          // we only want to update the notify preference if it has been explicitly supplied
                          .ifTrue(new Expr(Ops.IS_NOT_NULL, "*notify"),
                                  Block.ifTrue("*notify",
                                          Block.localTransform("$$followeeToNotifiedFollowerIds", Path.key("*targetId").voidSetElem().termVal("*accountId")),
                                          Block.localTransform("$$followeeToNotifiedFollowerIds", Path.key("*targetId").setElem("*accountId").termVoid())))
                          .ifTrue(new Expr(Ops.IS_NOT_NULL, "*followerSharedInboxUrl"),
                                  Block.localTransform("$$followeeToRemoteServerToFollowers", Path.key("*targetId", "*followerSharedInboxUrl").voidSetElem().termVal("*accountId")))

                          // This block of code creates a view of the social graph that will balance processing for fanout
                          // regardless of how unbalanced the social graph is. It works by spreading an account's followers
                          // across many partitions of the module. Every 1000 followers a new partition is chosen for the
                          // next 1000 followers until all tasks have a partition for this account. After that, new followers
                          // are assigned in round-robin order to all partitions.
                          .localSelect("$$partitionedFollowerTasks", Path.key("*targetId", "*accountId")).out("*currTask")
                          .ifTrue(new Expr(Ops.IS_NOT_NULL, "*currTask"),
                            Block.each(Ops.IDENTITY, "*currTask").out("*targetTask"),
                            Block.localSelect("$$partitionedFollowersControl", Path.key("*targetId")).out("*tasks")
                                 .localSelect("$$partitionedFollowerTasks", Path.key("*targetId").view(Ops.SIZE)).out("*followersCount")
                                 .each((Integer numFollowers, ModuleInstanceInfo info, Integer cutoff) -> {
                                   if(numFollowers >= info.getNumTasks() * cutoff) return "r";
                                   else if(numFollowers % cutoff == 0) return "n";
                                   else return null;
                                  }, "*followersCount", new Expr(Ops.MODULE_INSTANCE_INFO), newTaskCutoffLimit).out("*targetTaskSwitch")
                                 .ifTrue(new Expr(Ops.EQUAL, "n", "*targetTaskSwitch"),
                                   Block.each((ArrayList tasks, Integer currentTaskId, ModuleInstanceInfo info) -> {
                                                if(tasks==null) {
                                                  tasks = new ArrayList();
                                                  tasks.add(currentTaskId);
                                                } else {
                                                  Set currTasks = new HashSet(tasks);
                                                  List<Integer> availableTasks = new ArrayList();
                                                  for(int i=0; i < info.getNumTasks(); i++) {
                                                    if(!currTasks.contains(i)) availableTasks.add(i);
                                                  }
                                                  tasks.add(availableTasks.get(new Random().nextInt(availableTasks.size())));
                                                }
                                                return tasks;
                                              }, "*tasks", new Expr(Ops.CURRENT_TASK_ID), new Expr(Ops.MODULE_INSTANCE_INFO)).out("*newTasks")
                                        .each(Ops.LAST, "*newTasks").out("*targetTask")
                                        .localTransform("$$partitionedFollowersControl", Path.key("*targetId").termVal("*newTasks")),
                                   Block.ifTrue(new Expr(Ops.EQUAL, "r", "*targetTaskSwitch"),
                                       Block.each((List tasks, Integer followersCount) -> tasks.get(followersCount % tasks.size()), "*tasks", "*followersCount").out("*targetTask"),
                                       Block.each(Ops.LAST, "*tasks").out("*targetTask")))
                                 .localTransform("$$partitionedFollowerTasks", Path.key("*targetId", "*accountId").termVal("*targetTask")))
                          .directPartition("*targetTask")
                          .localTransform("$$partitionedFollowers", Path.key("*targetId", "*accountId").termVal("*follower")),
                 SubSource.create(RemoveFollowAccount.class)
                          .macro(extractFields("*data", "*accountId", "*targetId", "*followerSharedInboxUrl"))
                          .hashPartition("*accountId")
                          .macro(followerToFollowees.removeFromLinkedSetByEntityId("*accountId", "*targetId"))
                          .hashPartition("*targetId")
                          .macro(followeeToFollowers.removeFromLinkedSetByEntityId("*targetId", "*accountId"))
                          // Unfollowing also removes any pending follow request that exists.
                          .macro(accountIdToFollowRequests.removeFromLinkedSetByEntityId("*targetId", "*accountId"))
                          .localTransform("$$followeeToNotifiedFollowerIds", Path.key("*targetId").setElem("*accountId").termVoid())
                          .ifTrue(new Expr(Ops.IS_NOT_NULL, "*followerSharedInboxUrl"),
                                  Block.localTransform("$$followeeToRemoteServerToFollowers", Path.key("*targetId", "*followerSharedInboxUrl").setElem("*accountId").termVoid())
                                       // if there are no more followers from this server, remove the server
                                       .localSelect("$$followeeToRemoteServerToFollowers", Path.key("*targetId", "*followerSharedInboxUrl").view(Ops.SIZE)).out("*followerCount")
                                       .ifTrue(new Expr(Ops.EQUAL, "*followerCount", 0),
                                               Block.localTransform("$$followeeToRemoteServerToFollowers", Path.key("*targetId", "*followerSharedInboxUrl").termVoid())))
                          .localSelect("$$partitionedFollowerTasks", Path.key("*targetId").must("*accountId")).out("*task")
                          .directPartition("*task")
                          .localTransform("$$partitionedFollowers", Path.key("*targetId", "*accountId").termVoid()),

                 // handled above
                 SubSource.create(FollowLockedAccount.class),

                 // The actual following is handled by the implicit
                 // event above, so all that's left to do is remove
                 // the request. This also happens in FollowAccount's
                 // handler, because we need to make sure the
                 // FollowAccount event gets generated before we
                 // remove the follow request.
                 SubSource.create(AcceptFollowRequest.class),

                 SubSource.create(RejectFollowRequest.class)
                          .macro(extractFields("*data", "*accountId", "*requesterId"))
                          .macro(accountIdToFollowRequests.removeFromLinkedSetByEntityId("*accountId", "*requesterId")),

                 SubSource.create(BlockAccount.class)
                          .macro(extractFields("*data", "*accountId", "*targetId", "*timestamp"))
                          .localSelect("$$accountIdToSuppressions", Path.key("*accountId", "blocked").view(Ops.SIZE)).out("*blockeeCount")
                          .keepTrue(new Expr(Ops.LESS_THAN, "*blockeeCount", relationshipCountLimit))
                          .localTransform("$$accountIdToSuppressions", Path.key("*accountId", "blocked").voidSetElem().termVal("*targetId")),

                 SubSource.create(RemoveBlockAccount.class)
                          .macro(extractFields("*data", "*accountId", "*targetId"))
                          .localTransform("$$accountIdToSuppressions", Path.key("*accountId", "blocked").setElem("*targetId").termVoid()));

        stream.source("*muteAccountDepot", StreamSourceOptions.retryAllAfter()).out("*data")
              .subSource("*data",
                 SubSource.create(MuteAccount.class)
                          .macro(extractFields("*data", "*accountId", "*targetId", "*options"))
                          .localSelect("$$accountIdToSuppressions", Path.key("*accountId", "muted").view(Ops.SIZE)).out("*muteeCount")
                          .keepTrue(new Expr(Ops.LESS_THAN, "*muteeCount", relationshipCountLimit))
                          .macro(extractFields("*options", "*expirationMillis"))
                          .localTransform("$$accountIdToSuppressions", Path.key("*accountId", "muted", "*targetId").termVal("*options"))
                          .ifTrue(new Expr(MuteAccountOptions::isSetExpirationMillis, "*options"),
                             Block.macro(muteScheduler.scheduleItem("*expirationMillis", new Expr(Ops.TUPLE, "*accountId", "*targetId")))),
                 SubSource.create(RemoveMuteAccount.class)
                          .macro(extractFields("*data", "*accountId", "*targetId"))
                          .localTransform("$$accountIdToSuppressions", Path.key("*accountId", "muted", "*targetId").termVoid()));

        stream.source("*muteExpirationTick")
              .macro(muteScheduler.handleExpirations("*tuple", "*currentTimeMillis",
                Block.each(Ops.EXPAND, "*tuple").out("*accountId", "*targetId")
                     .localTransform("$$accountIdToSuppressions", Path.key("*accountId", "muted")
                                                                      .must("*targetId")
                                                                      .filterSelected(Path.view(Relationships::isMuteExpired, "*currentTimeMillis")
                                                                                          .filterPred(Ops.IDENTITY))
                                                                      .termVoid())));

        stream.source("*featureAccountDepot", StreamSourceOptions.retryAllAfter()).out("*data")
              .subSource("*data",
                 SubSource.create(FeatureAccount.class)
                          .macro(extractFields("*data", "*accountId", "*targetId"))
                          .macro(featurerToFeaturees.addToLinkedSet("*accountId", "*targetId")),
                 SubSource.create(RemoveFeatureAccount.class)
                          .macro(extractFields("*data", "*accountId", "*targetId"))
                          .macro(featurerToFeaturees.removeFromLinkedSet("*accountId", "*targetId")));

        stream.source("*followHashtagDepot", StreamSourceOptions.retryAllAfter()).out("*data")
              .subSource("*data",
                 SubSource.create(FollowHashtag.class)
                          .macro(extractFields("*data", "*accountId", "*token"))
                          .localTransform("$$hashtagToFollowers", Path.key("*token").voidSetElem().termVal("*accountId")),
                 SubSource.create(RemoveFollowHashtag.class)
                          .macro(extractFields("*data", "*accountId", "*token"))
                          .localTransform("$$hashtagToFollowers", Path.key("*token").setElem("*accountId").termVoid()));

      stream.pstate("$$authCodeToAccountId", PState.mapSchema(String.class, Long.class));

      stream.source("*authCodeDepot").out("*data")
            .subSource("*data",
                SubSource.create(AddAuthCode.class)
                         .macro(extractFields("*data", "*code", "*accountId"))
                         .localTransform("$$authCodeToAccountId", Path.key("*code").termVal("*accountId")),
                SubSource.create(RemoveAuthCode.class)
                         .macro(extractFields("*data", "*code"))
                         .localTransform("$$authCodeToAccountId", Path.key("*code").termVoid()));

      stream.pstate("$$accountIdToTargetIdToNote", PState.mapSchema(Long.class, PState.mapSchema(Long.class, String.class).subindexed()));

      stream.source("*noteDepot").out("*data")
            .subSource("*data",
                SubSource.create(Note.class)
                         .macro(extractFields("*data", "*accountId", "*targetId", "*note"))
                         .localTransform("$$accountIdToTargetIdToNote", Path.key("*accountId", "*targetId").termVal("*note")));
    }

    public static AccountRelationshipQueryResult createAccountRelationship(long accountId, List<Boolean> bools, String note) {
        Boolean following = bools.get(0);
        Boolean followedBy = bools.get(1);
        Boolean blocking = bools.get(2);
        Boolean blockedBy = bools.get(3);
        Boolean muting = bools.get(4);
        Boolean endorsed = bools.get(5);
        Boolean requested = bools.get(6);
        return new AccountRelationshipQueryResult().setAccountId(accountId).setFollowing(following).setShowingBoosts(true).setNotifying(false).setLanguages(new ArrayList<>())
                                                   .setFollowedBy(followedBy).setBlocking(blocking).setBlockedBy(blockedBy).setMuting(muting).setMutingNotifications(false)
                                                   .setRequested(requested).setDomainBlocking(false).setEndorsed(endorsed).setNote(note == null ? "" : note);
    }

    private static SubBatch followCounts(String microbatchVar) {
        Block b = Block.explodeMicrobatch(microbatchVar).out("*data")
                       .keepTrue(new Expr(Ops.IS_INSTANCE_OF, FollowAccount.class, "*data"))
                       .macro(extractFields("*data", "*accountId"))
                       .groupBy("*accountId", Block.agg(Agg.count()).out("*count"));
        return new SubBatch(b, "*accountId", "*count");
    }

    public static boolean brokeThreshold(int currCount, int newCount, int threshold1, int threshold2) {
        return currCount < threshold1 && newCount >= threshold1 || currCount < threshold2 && newCount >= threshold2;
    }

    public static final int WHO_TO_FOLLOW_USERS_PER_PARTITION = 15;

    /*
     This produces a subbatch that:
       - Determines for which users to recompute personalized who to follow recommendations
          - Selects next users on each partition, storing that state in the $$nextId PState
            - For 100M users and tick frequency of 30s, on 128 tasks this recomputes who to follow
              for each user once every 46 days.
          - Also selects next users from the $$forceRecomputeUsers PState, which will ensure
            new users get personalized who to follow recommendations faster. Users are added to
            that PState when they cross the 10 and 100 follow thresholds.
       - This then looks at all the follows of the follows for each of those users. These are the
         candidates for personalized who to follow recommendations. For each of those candidates,
         this code aggregates into a map the number of times they're followed by the user's follows.
     */
    private SubBatch candidateCounts() {
        Block b = Block.allPartition()
                       .anchor("Root")
                       .keepTrue(isCyclicRecompute)
                       .localSelect("$$nextId", Path.stay()).out("*startId")
                       .localSelect("$$followerToFolloweesById", Path.sortedMapRangeFrom("*startId", WHO_TO_FOLLOW_USERS_PER_PARTITION)).out("*m")
                       .ifTrue(new Expr(Ops.LESS_THAN, new Expr(Ops.SIZE, "*m"), WHO_TO_FOLLOW_USERS_PER_PARTITION),
                         Block.localTransform("$$nextId", Path.termVal(0L)),
                         Block.each((SortedMap m) -> m.lastKey(), "*m").out("*maxId")
                              .localTransform("$$nextId", Path.termVal(new Expr(Ops.INC_LONG, "*maxId"))))
                       .select("*m", Path.mapKeys()).out("*accountId")
                       .anchor("IterateUsers")

                       .hook("Root")
                       .localSelect("$$forceRecomputeUsers", Path.stay()).out("*allUserSet")
                       .each((PersistentHashSet s, OutputCollector collector) -> {
                           List<Long> ret = new ArrayList();
                           Iterator<Long> it = s.iterator();
                           while(it.hasNext() && ret.size() < 20) ret.add(it.next());
                           for(Long accountId : ret) s = (PersistentHashSet) s.disjoin(accountId);
                           collector.emit(ret, s);
                       }, "*allUserSet").out("*forceUsers", "*laterSet")
                       .localTransform("$$forceRecomputeUsers", Path.termVal("*laterSet"))
                       .each(Ops.EXPLODE, "*forceUsers").out("*accountId")
                       .anchor("ForceUsers")
                       .unify("IterateUsers", "ForceUsers")
                       .localSelect("$$followerToFolloweesById", Path.key("*accountId").sortedMapRangeFrom(0L, 200).mapVals().customNav(new TField("accountId"))).out("*followingId")
                       .select("$$followerToFolloweesById", Path.key("*followingId").sortedMapRangeFrom(0L, 200).mapVals().customNav(new TField("accountId")).filterNotEqual("*accountId")).out("*candidateId")
                       .hashPartition("*accountId")
                       .compoundAgg(CompoundAgg.map("*accountId", CompoundAgg.map("*candidateId", Agg.count()))).out("*m")
                       .each(Ops.EXPLODE_MAP, "*m").out("*accountId", "*candidateCounts");
        return new SubBatch(b, "*accountId", "*candidateCounts");
    }

    /*
     This subbatch gets the unique set of users who received follows in this microbatch. This is used
     for updating the $$topFollowedUsers PState, which is used to augment someone's who to follow
     recommendations when they don't follow many users yet.
     */
    private SubBatch followedUsers(String microbatchVar) {
        Block b = Block.explodeMicrobatch(microbatchVar).out("*data")
                       .keepTrue(new Expr(Ops.IS_INSTANCE_OF, FollowAccount.class, "*data"))
                       .macro(extractFields("*data", "*targetId"))
                       .hashPartition("*targetId")
                       .agg(Agg.set("*targetId")).out("*accounts")
                       .each(Ops.EXPLODE, "*accounts").out("*accountId");
        return new SubBatch(b, "*accountId");
    }

    /*
     * This filters a follow suggestion if they're already followed, have been explicitly removed as a suggestion,
     * or are blocked/muted.
     */
    private Block validSuggestionMacro(String accountIdVar, String targetIdVar, String resVar) {
        String idVar = Helpers.genVar("id");
        String suppressedVar = Helpers.genVar("suppressed");
        String removedSuggestionVar = Helpers.genVar("removedSuggestion");
        return Block.localSelect("$$followerToFollowees", Path.key(accountIdVar, targetIdVar)).out(idVar)
                    .localSelect("$$removedFollowSuggestions", Path.key(accountIdVar)
                                                                   .view(Ops.CONTAINS, targetIdVar)).out(removedSuggestionVar)
                    .localSelect("$$accountIdToSuppressions", Relationships.isBlockedOrMutedPath(accountIdVar, targetIdVar, false)).out(suppressedVar)
                    .each(Ops.AND, new Expr(Ops.IS_NULL, idVar),
                                   new Expr(Ops.NOT, removedSuggestionVar),
                                   new Expr(Ops.NOT, suppressedVar),
                                   new Expr(Ops.NOT_EQUAL, targetIdVar, accountIdVar)).out(resVar);
    }

    @Override
    public void define(Setup setup, Topologies topologies) {
        setup.declareDepot("*followAndBlockAccountDepot", Depot.hashBy(ExtractAccountId.class));
        setup.declareDepot("*muteAccountDepot", Depot.hashBy(ExtractAccountId.class));
        setup.declareDepot("*featureAccountDepot", Depot.hashBy(ExtractAccountId.class));
        setup.declareDepot("*followHashtagDepot", Depot.hashBy(ExtractToken.class));
        setup.declareDepot("*listDepot", Depot.hashBy(ExtractListId.class));
        setup.declareDepot("*filterDepot", Depot.hashBy(ExtractFilterAccountId.class));
        setup.declareDepot("*removeFollowSuggestionDepot", Depot.hashBy(ExtractAccountId.class));
        setup.declareDepot("*authCodeDepot", Depot.hashBy(ExtractCode.class));
        setup.declareDepot("*noteDepot", Depot.hashBy(ExtractAccountId.class));
        setup.declareTickDepot("*whoToFollowTick", whoToFollowTickMillis);
        setup.declareTickDepot("*muteExpirationTick", muteExpirationTickMillis);
        setup.declareTickDepot("*filterExpirationTick", filterExpirationTickMillis);

        declareStreamTopology(topologies);

        StreamTopology suggestionsMeta = topologies.stream("suggestionsMeta");
        suggestionsMeta.pstate("$$removedFollowSuggestions",
                               PState.mapSchema(Long.class, // account id
                                                PState.setSchema(Long.class).subindexed()));

        suggestionsMeta.source("*removeFollowSuggestionDepot").out("*data")
                       .macro(extractFields("*data", "*accountId", "*targetId"))
                       .compoundAgg("$$removedFollowSuggestions", CompoundAgg.map("*accountId", Agg.set("*targetId")));

        MicrobatchTopology whoToFollow = topologies.microbatch("whoToFollow");

        // This PState is a map from user id to a list of personalized recommendations
        whoToFollow.pstate("$$whoToFollow", PState.mapSchema(Long.class, List.class));

        // This PState tracks how many times each user has followed other users (irrespective of unfollows).
        // This is used for detecting follow thresholds for immediate recomputation of personalized
        // recommendations.
        whoToFollow.pstate("$$followCounts", PState.mapSchema(Long.class, Integer.class));

        // This PState tracks the next starting account id for recomputes of personalized recommendations.
        whoToFollow.pstate("$$nextId", Long.class).initialValue(0L).makePrivate();

        // This PState stores account ids that have recently passed the 10 or 100 follow thresholds. It is
        // consumed by the code which recomputes personalized recommendations every 30s.
        whoToFollow.pstate("$$forceRecomputeUsers", Set.class).initialValue(PersistentHashSet.EMPTY).makePrivate();

        // This single-partition PState stores the top 30 most followed users.
        whoToFollow.pstate("$$topFollowedUsers", List.class).global();

        // This portion of the topology consumes all Follow and Unfollow events to produce the
        // $$followCounts and $$topFollowedUsers PStates.
        whoToFollow.source("*followAndBlockAccountDepot").out("*microbatch")
                   // The Follow and Unfollow events in this microbatch are processed in batch to determine
                   // how much someone's follows have changed and whether that breaks the 10 or 100 follow
                   // thresholds.
                   .batchBlock(
                     Block.subBatch(followCounts("*microbatch")).out("*accountId", "*count")
                          .localSelect("$$followCounts", Path.key("*accountId").nullToVal(0)).out("*currCount")
                          .ifTrue(new Expr(Ops.LESS_THAN, "*currCount", whoToFollowThreshold2),
                            Block.each(Ops.PLUS, "*currCount", "*count").out("*newCount")
                                 .ifTrue(new Expr(Relationships::brokeThreshold, "*currCount", "*newCount", whoToFollowThreshold1, whoToFollowThreshold2),
                                   Block.localTransform("$$forceRecomputeUsers", Path.filterPred((Set s) -> s == null || s.size() < 100).voidSetElem().termVal("*accountId")))
                                 .localTransform("$$followCounts", Path.key("*accountId").termVal("*newCount"))))
                   .batchBlock(
                     Block.subBatch(followedUsers("*microbatch")).out("*accountId")
                          // Note that the regular Ops.SIZE function can be called directly on subindexed
                          // maps, just like any other data structure. This is an O(1) operation even
                          // though the entries of that map are indexed individually and there could be
                          // many millions of entries.
                          .localSelect("$$followeeToFollowers", Path.key("*accountId").view(Ops.SIZE)).out("*count")
                          .each(Ops.TUPLE, "*accountId", "*count").out("*tuple")
                          .globalPartition()
                          .agg("$$topFollowedUsers", Agg.topMonotonic(numTopFollowedUsers, "*tuple").idFunction(Ops.FIRST).sortValFunction(Ops.LAST)));

        // This part of the topology does the recomputes of personalized recommendations every 30s
        // for a subset of users each iteration.
        whoToFollow.source("*whoToFollowTick").out("*microbatch")
                   .explodeMicrobatch("*microbatch")
                   .batchBlock(
                     Block.subBatch(candidateCounts()).out("*accountId", "*candidateCounts")
                          .hashPartition("*accountId")
                          // Once potential candidates have been computed, the rest of this part of the
                          // topology determines which of those candidates to recommend. It computes
                          // the candidates most followed by the user's follows who isn't already followed
                          // by that user. To operate most efficiently, this code sorts candidates by
                          // that follow count and stops checking whether candidates are already followed
                          // by the user once enough recommendations have been found.
                          .each((Map cc) -> {
                              ArrayList<Map.Entry<Long, Integer>> l = new ArrayList(cc.entrySet());
                              l.sort(Map.Entry.comparingByValue());
                              Collections.reverse(l);
                              List ret = new LinkedList();
                              for(int i=0; i < Math.min(1000, l.size()); i++) ret.add(l.get(i).getKey());
                              return ret;
                          }, "*candidateCounts").out("*candidateOrder")
                          .each((RamaFunction0) ArrayList::new).out("*whoToFollow")
                          // This loop checks whether a candidate is already followed by the user and
                          // terminates once enough recommendations have been found or there are no more
                          // candidates.
                          .loop(
                            Block.ifTrue(new Expr((List w, List c, Integer targetSuggestions) -> w.size() >= targetSuggestions || c.size() == 0, "*whoToFollow", "*candidateOrder", numWhoToFollowSuggestions),
                              Block.emitLoop(),
                              Block.yieldIfOvertime()
                                   .each((LinkedList l) -> l.pop(), "*candidateOrder").out("*candidateId")
                                   .macro(validSuggestionMacro("*accountId", "*candidateId", "*valid"))
                                   .ifTrue("*valid",
                                     Block.each((List l, Long cid) -> l.add(cid), "*whoToFollow", "*candidateId"))
                                   .continueLoop()))
                          .localTransform("$$whoToFollow", Path.key("*accountId").termVal("*whoToFollow")));

        // This query topology fetches followers of an account that the requesting account already follows.
        topologies.query("getFamiliarFollowers", "*yourAccountId", "*theirAccountId").out("*results")
                  .hashPartition("$$followerToFollowees", "*yourAccountId")
                  .localSelect("$$followerToFolloweesById",
                          Path.key("*yourAccountId")
                              .sortedMapRangeFrom(0, 300) // only test a subset of followees for performance
                              .mapVals()
                              .customNav(new TField("accountId"))).out("*yourFolloweeId")
                  .select("$$followeeToFollowers", Path.key("*theirAccountId").must("*yourFolloweeId"))
                  .originPartition()
                  .agg(Agg.list("*yourFolloweeId")).out("*allResults")
                  .each((List<Long> results) -> results.stream().limit(FAMILIAR_FOLLOWERS_LIMIT).collect(Collectors.toList()), "*allResults").out("*results");

        // This query topology fetches metadata about the relationship between the two accounts.
        topologies.query("getAccountRelationship", "*accountId", "*targetId").out("*relationship")
                  .hashPartition("*accountId")
                  .localSelect("$$followerToFollowees", Path.key("*accountId", "*targetId")).out("*followingIndex")
                  .each(Ops.IS_NOT_NULL, "*followingIndex").out("*following")
                  .localSelect("$$accountIdToSuppressions", Path.key("*accountId", "muted").view(Ops.CONTAINS, "*targetId")).out("*muting")
                  .localSelect("$$accountIdToSuppressions", Path.key("*accountId", "blocked").view(Ops.CONTAINS, "*targetId")).out("*blocking")
                  .localSelect("$$featurerToFeaturees", Path.key("*accountId", "*targetId")).out("*featuringIndex")
                  .each(Ops.IS_NOT_NULL, "*featuringIndex").out("*endorsed")
                  .localSelect("$$accountIdToTargetIdToNote", Path.key("*accountId", "*targetId")).out("*note")
                  .hashPartition("*targetId")
                  .localSelect("$$accountIdToFollowRequests", Path.key("*targetId", "*accountId")).out("*requestedId")
                  .each(Ops.IS_NOT_NULL, "*requestedId").out("*requested")
                  .localSelect("$$followeeToFollowers", Path.key("*targetId", "*accountId")).out("*followerId")
                  .localSelect("$$followeeToFollowersById", Path.key("*targetId", "*followerId")).out("*followerPointer")
                  .localSelect("$$followerToFollowees", Path.key("*targetId", "*accountId")).out("*followedByIndex")
                  .localSelect("$$followeeToNotifiedFollowerIds", Path.key("*targetId", "*accountId").view(Ops.IS_NOT_NULL)).out("*notifying")
                  .each(Ops.IS_NOT_NULL, "*followedByIndex").out("*followedBy")
                  .localSelect("$$accountIdToSuppressions", Path.key("*targetId", "blocked").view(Ops.CONTAINS, "*accountId")).out("*blockedBy")
                  .each((Boolean following, Boolean followedBy, Boolean blocking, Boolean blockedBy, Boolean muting, Boolean endorsed, Boolean requested) -> Arrays.asList(following, followedBy, blocking, blockedBy, muting, endorsed, requested),
                        "*following", "*followedBy", "*blocking", "*blockedBy", "*muting", "*endorsed", "*requested").out("*bools")
                  .each(Relationships::createAccountRelationship, "*targetId", "*bools", "*note").out("*relationship")
                  .each((AccountRelationshipQueryResult result, Follower followerPointer, Boolean notifying) -> {
                      result.setNotifying(notifying);
                      if (followerPointer == null) return result;
                      if (followerPointer.isSetShowBoosts()) result.setShowingBoosts(followerPointer.isShowBoosts());
                      if (followerPointer.isSetLanguages()) result.setLanguages(followerPointer.getLanguages());
                      return result;
                  }, "*relationship", "*followerPointer", "*notifying")
                  .originPartition();

        // This query topology fetches the top followed users that the reuesting account doesn't already follow.
        // It's a fallback set of suggestions when a user doesn't have enough personalized suggestions (e.g.
        // because they don't follow many accounts).
        topologies.query("globalWhoToFollowSuggestions", "*accountId").out("*res")
                  .globalPartition()
                  .localSelect("$$topFollowedUsers", Path.all().first()).out("*targetId")
                  .hashPartition("*accountId")
                  .macro(validSuggestionMacro("*accountId", "*targetId", "*valid"))
                  .keepTrue("*valid")
                  .originPartition()
                  .agg(Agg.set("*targetId")).out("*res");

        // This query topology gets follow suggestions for an account, filling  the suggestions
        // with top followed accounts if there aren't enough personalized suggestions.
        topologies.query("getWhoToFollowSuggestions", "*accountId").out("*res")
                  .hashPartition("*accountId")
                  .localSelect("$$whoToFollow", Path.key("*accountId")).out("*whoToFollow")
                  .each(Ops.EXPLODE, "*whoToFollow").out("*targetId")
                  .macro(validSuggestionMacro("*accountId", "*targetId", "*valid"))
                  .keepTrue("*valid")
                  .originPartition()
                  .agg(Agg.set("*targetId")).out("*personalizedRes")
                  .each(Ops.SIZE, "*personalizedRes").out("*pcount")
                  .ifTrue(new Expr(Ops.LESS_THAN, "*pcount", numWhoToFollowSuggestions),
                    Block.invokeQuery("globalWhoToFollowSuggestions", "*accountId").out("*globalRes")
                         .each((Set s1, Set s2) -> {
                             Set ret = new HashSet();
                             if(s1!=null) ret.addAll(s1);
                             if(s2!=null) ret.addAll(s2);
                             return ret;
                         }, "*personalizedRes", "*globalRes").out("*res"),
                    Block.each(Ops.IDENTITY, "*personalizedRes").out("*res"));

        // This query topology lists for a user, optionally filtered down for lists containing
        // a specific account.
        topologies.query("getListsFromAuthor", "*authorId", "*memberIdMaybe").out("*results")
                  .hashPartition("*authorId")
                  .localSelect("$$authorIdToListIds", Path.must("*authorId").all()).out("*listId")
                  .hashPartition("*listId")
                  .ifTrue(new Expr(Ops.IS_NOT_NULL, "*memberIdMaybe"),
                          Block.localSelect("$$listIdToMemberIds", Path.must("*listId").setElem("*memberIdMaybe")))
                  .localSelect("$$listIdToList", Path.must("*listId")).out("*list")
                  .each((RamaFunction2<Long, AccountList, AccountListWithId>) AccountListWithId::new, "*listId", "*list").out("*listWithId")
                  .originPartition()
                  .agg(Agg.list("*listWithId")).out("*unsortedResults")
                  .each((List<AccountListWithId> unsortedResults) -> {
                    ArrayList<AccountListWithId> results = new ArrayList<>(unsortedResults);
                    results.sort((AccountListWithId a, AccountListWithId b) -> (int) (a.listId - b.listId));
                    return results;
                  }, "*unsortedResults").out("*results");

        // This is used in unit tests to add another topology used for doing assertions.
        if(testTopologiesHook != null) testTopologiesHook.invoke(topologies);
    }
}
