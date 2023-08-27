package com.rpl.mastodon;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

import com.rpl.mastodon.data.*;
import com.rpl.mastodon.modules.*;
import com.rpl.mastodon.serialization.MastodonSerialization;
import com.rpl.rama.test.*;
import com.rpl.rama.*;
import com.rpl.rama.helpers.TopologyUtils;
import com.rpl.rama.ops.Ops;

import static com.rpl.mastodon.TestHelpers.*;
import static org.junit.Assert.*;

public class NotificationsTest {
  @Test
  public void notificationsETLTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers);
        Closeable simTime = TopologyUtils.startSimTime()) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Notifications notificationsModule = new Notifications();
      notificationsModule.fanoutLimit = 2;
      notificationsModule.rangeQueryLimit = 1;
      String notificationsModuleName = notificationsModule.getClass().getName();
      TestHelpers.launchModule(ipc, notificationsModule, testInfo);

      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot favoriteStatusDepot = ipc.clusterDepot(coreModuleName, "*favoriteStatusDepot");
      Depot pollVoteDepot = ipc.clusterDepot(coreModuleName, "*pollVoteDepot");
      Depot dismissDepot = ipc.clusterDepot(notificationsModuleName, "*dismissDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
      PState pollVotes = ipc.clusterPState(coreModuleName, "$$pollVotes");
      PState statusIdToBoosters = ipc.clusterPState(coreModuleName, "$$statusIdToBoosters");
      PState notifications = ipc.clusterPState(notificationsModuleName, "$$accountIdToNotificationsTimeline");

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));

      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Status 1", StatusVisibility.Public), 0)));
      long aliceStatus1 = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());

      followAndBlockAccountDepot.append(new FollowAccount(bobId, aliceId, 2));
      followAndBlockAccountDepot.append(new RemoveFollowAccount(bobId, charlieId, 2)); // verify it's ignored
      attainConditionPred(() -> notifications.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 1);

      assertEquals(notifications.selectOne(Path.key(aliceId).first().last()), new Notification(NotificationContent.follow(bobId), 2));

      followAndBlockAccountDepot.append(new FollowLockedAccount(aliceId, davidId, 3));
      attainConditionPred(() -> notifications.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 2);
      assertEquals(notifications.selectOne(Path.key(aliceId).first().last()), new Notification(NotificationContent.followRequest(davidId), 3));

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, TestHelpers.replyStatusContent("@alice Hi @david @notAUser", StatusVisibility.Public, aliceId, aliceStatus1), 1)));
      long bobStatus1 = accountIdToStatuses.selectOne(Path.key(bobId).first().first());

      attainConditionPred(() -> notifications.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 3);

      assertEquals(notifications.selectOne(Path.key(aliceId).first().last()), new Notification(NotificationContent.mention(new StatusPointer(bobId, bobStatus1)), 1));

      attainConditionPred(() -> notifications.selectOne(Path.key(davidId).view(Ops.SIZE)), (Integer i) -> i == 1);

      assertEquals(notifications.selectOne(Path.key(davidId).first().last()), new Notification(NotificationContent.mention(new StatusPointer(bobId, bobStatus1)), 1));

      statusDepot.append(new BoostStatus(UUID.randomUUID().toString(), bobId, new StatusPointer(aliceId, aliceStatus1), 3));

      attainConditionPred(() -> notifications.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 4);
      assertEquals(notifications.selectOne(Path.key(aliceId).first().last()), new Notification(NotificationContent.boost(new StatusResponseNotificationContent(bobId, new StatusPointer(aliceId, aliceStatus1))), 3));

      // necessary to guarantee the edit below fans out to bob
      attainConditionPred(() -> statusIdToBoosters.selectOne(aliceId, Path.key(aliceStatus1, bobId)), (Long l) -> l != null);

      statusDepot.append(new EditStatus(aliceStatus1, new Status(aliceId, TestHelpers.normalStatusContent("...", StatusVisibility.Public), 10)));
      attainConditionPred(() -> notifications.selectOne(Path.key(bobId).view(Ops.SIZE)), (Integer i) -> i == 1);
      assertEquals(notifications.selectOne(Path.key(bobId).first().last()), new Notification(NotificationContent.boostedUpdate(new StatusPointer(aliceId, aliceStatus1)), 10));


      NormalStatusContent n = new NormalStatusContent("AAA", StatusVisibility.Public);
      n.setPollContent(new PollContent(Arrays.asList("a", "b"), 10000, false));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, StatusContent.normal(n), 0)));
      long charlieStatus1 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());

      attainStableConditionPred(() -> notifications.selectOne(Path.key(charlieId).view(Ops.SIZE)), (Integer i) -> i == 0);


      Set<Integer> choices = new HashSet();
      choices.add(0);
      pollVoteDepot.append(new PollVote(davidId, new StatusPointer(charlieId, charlieStatus1), choices, 0));


      attainConditionPred(() -> pollVotes.selectOne(charlieId, Path.key(charlieStatus1, "allVoters").view(Ops.SIZE)), (Integer i) -> i == 1);

      ipc.waitForMicrobatchProcessedCount(notificationsModuleName, "notifications", 8);

      TopologyUtils.advanceSimTime(10001);

      attainStableConditionPred(() -> notifications.select(Path.key(charlieId).all()), (List l) -> l.size() == 1);
      assertEquals(notifications.selectOne(Path.key(charlieId).first().last()), new Notification(NotificationContent.pollComplete(new StatusPointer(charlieId, charlieStatus1)), 10001));

      attainConditionPred(() -> notifications.selectOne(Path.key(davidId).view(Ops.SIZE)), (Integer i) -> i == 2);
      assertEquals(notifications.selectOne(Path.key(davidId).first().last()), new Notification(NotificationContent.pollComplete(new StatusPointer(charlieId, charlieStatus1)), 10001));

      favoriteStatusDepot.append(new FavoriteStatus(davidId, new StatusPointer(aliceId, aliceStatus1), 4));
      attainStableConditionPred(() -> notifications.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 5);
      assertEquals(notifications.selectOne(Path.key(aliceId).first().last()), new Notification(NotificationContent.favorite(new StatusResponseNotificationContent(davidId, new StatusPointer(aliceId, aliceStatus1))), 4));
      long dismissId = notifications.selectOne(Path.key(aliceId).first().first());
      dismissDepot.append(new DismissNotification(aliceId).setNotificationId(dismissId));
      attainConditionPred(() -> notifications.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 4);
      assertTrue(notifications.selectOne(Path.key(aliceId, dismissId)) == null);

      dismissDepot.append(new DismissNotification(aliceId));
      attainConditionPred(() -> notifications.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 0);

      dismissDepot.append(new DismissNotification(charlieId));
      attainConditionPred(() -> notifications.selectOne(Path.key(charlieId).view(Ops.SIZE)), (Integer i) -> i == 0);

      dismissDepot.append(new DismissNotification(davidId));
      attainConditionPred(() -> notifications.selectOne(Path.key(davidId).view(Ops.SIZE)), (Integer i) -> i == 0);

      followAndBlockAccountDepot.append((new FollowAccount(aliceId, bobId, 5)).setNotify(true));
      followAndBlockAccountDepot.append((new FollowAccount(davidId, bobId, 5)).setNotify(true));
      followAndBlockAccountDepot.append((new FollowAccount(charlieId, bobId, 5)).setNotify(true));

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, StatusContent.normal(n), 0)));
      attainConditionPred(() -> notifications.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 1);
      attainConditionPred(() -> notifications.selectOne(Path.key(davidId).view(Ops.SIZE)), (Integer i) -> i == 1);
      attainConditionPred(() -> notifications.selectOne(Path.key(charlieId).view(Ops.SIZE)), (Integer i) -> i == 1);

      // Direct messages don't result in notifications
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, StatusContent.normal(n.setVisibility(StatusVisibility.Direct)), 0)));
      attainStableConditionPred(() -> notifications.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 1);
    }
  }

  @Test
  public void suppressedNotificationWritesTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Notifications notificationsModule = new Notifications();
      String notificationsModuleName = notificationsModule.getClass().getName();
      TestHelpers.launchModule(ipc, notificationsModule, testInfo);

      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");
      Depot muteAccountDepot = ipc.clusterDepot(Relationships.class.getName(), "*muteAccountDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot favoriteStatusDepot = ipc.clusterDepot(coreModuleName, "*favoriteStatusDepot");
      Depot muteStatusDepot = ipc.clusterDepot(coreModuleName, "*muteStatusDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
      PState notifications = ipc.clusterPState(notificationsModuleName, "$$accountIdToNotificationsTimeline");

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("emily", "emily.com", "hash5", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));

      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));
      long emilyId = nameToUser.selectOne(Path.key("emily", "accountId"));

      muteAccountDepot.append(new MuteAccount(aliceId, bobId, new MuteAccountOptions(true), 0));
      muteAccountDepot.append(new MuteAccount(aliceId, emilyId, new MuteAccountOptions(false), 0));
      followAndBlockAccountDepot.append(new BlockAccount(aliceId, charlieId, 0));

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Status 1", StatusVisibility.Public), 0)));
      long aliceStatus1 = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Status 2", StatusVisibility.Public), 0)));
      long aliceStatus2 = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 2);

      muteStatusDepot.append(new MuteStatus(aliceId, new StatusPointer(aliceId, aliceStatus1), 0));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "core", 1);

      // suppressed
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.normalStatusContent("Hi @alice", StatusVisibility.Public), 0)));
      // suppressed
      favoriteStatusDepot.append(new FavoriteStatus(davidId, new StatusPointer(aliceId, aliceStatus1), 0));
      // goes through
      favoriteStatusDepot.append(new FavoriteStatus(davidId, new StatusPointer(aliceId, aliceStatus2), 0));
      // no notifications to self so doesn't go through
      favoriteStatusDepot.append(new FavoriteStatus(aliceId, new StatusPointer(aliceId, aliceStatus2), 0));

      attainStableCondition(() -> notifications.selectOne(Path.key(aliceId).view(Ops.SIZE)).equals(1));
      assertEquals(notifications.selectOne(Path.key(aliceId).first().last()), new Notification(NotificationContent.favorite(new StatusResponseNotificationContent(davidId, new StatusPointer(aliceId, aliceStatus2))), 0));

      // suppressed
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, TestHelpers.replyStatusContent("Reply", StatusVisibility.Public, aliceId, aliceStatus2), 0)));
      // goes through
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.replyStatusContent("Reply!", StatusVisibility.Public, aliceId, aliceStatus2), 0)));
      long davidStatus1 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());
      // suppressed
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.replyStatusContent("Reply?", StatusVisibility.Public, aliceId, aliceStatus1), 0)));

      attainStableCondition(() -> notifications.selectOne(Path.key(aliceId).view(Ops.SIZE)).equals(2));
      assertEquals(notifications.selectOne(Path.key(aliceId).first().last()), new Notification(NotificationContent.mention(new StatusPointer(davidId, davidStatus1)), 0));

      // goes through
      favoriteStatusDepot.append(new FavoriteStatus(emilyId, new StatusPointer(aliceId, aliceStatus2), 0));
      attainStableCondition(() -> notifications.selectOne(Path.key(aliceId).view(Ops.SIZE)).equals(3));
      assertEquals(notifications.selectOne(Path.key(aliceId).first().last()), new Notification(NotificationContent.favorite(new StatusResponseNotificationContent(emilyId, new StatusPointer(aliceId, aliceStatus2))), 0));
    }
  }
}
