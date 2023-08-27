package com.rpl.mastodon;

import com.rpl.mastodon.data.*;
import com.rpl.mastodon.modules.*;
import com.rpl.mastodon.modules.Core.Timeline;
import com.rpl.mastodon.navs.TField;
import com.rpl.mastodon.serialization.MastodonSerialization;
import com.rpl.rama.*;
import com.rpl.rama.helpers.TopologyUtils;
import com.rpl.rama.ops.*;
import com.rpl.rama.test.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static com.rpl.mastodon.TestHelpers.*;

public class CoreTest {

  @Test
  public void statusTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      coreModule.maxEditCount = 2;
      coreModule.enableHomeTimelineRefresh = false;
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot statusAttachmentWithIdDepot = ipc.clusterDepot(coreModuleName, "*statusAttachmentWithIdDepot");

      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");
      Depot muteAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*muteAccountDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
      PState uuidToAttachment = ipc.clusterPState(coreModuleName, "$$uuidToAttachment");
      PState postUUIDToStatusId = ipc.clusterPState(coreModuleName, "$$postUUIDToStatusId");

      QueryTopologyClient<StatusQueryResults> getAccountTimeline = ipc.clusterQuery(coreModuleName, "getAccountTimeline");
      QueryTopologyClient<StatusQueryResults> getHomeTimeline = ipc.clusterQuery(coreModuleName, "getHomeTimeline");

      int coreCount = 0;
      int ts = 0;
      int limit = 5;

      // add new accounts
      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));

      // get user ids
      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));

      // bob follows alice and charlie, only wanting english posts from alice
      followAndBlockAccountDepot.append((new FollowAccount(bobId, aliceId, ts+=1)).setLanguages(Arrays.asList("en")));
      followAndBlockAccountDepot.append(new FollowAccount(bobId, charlieId, ts+=1));


      StatusPointer start = new StatusPointer(-1, -1);

      // alice posts a status and it appears on bob's and her own home timeline
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), (new Status(aliceId, TestHelpers.normalStatusContent("Hello1", StatusVisibility.Public), ts+=1)).setLanguage("en")));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);
      assertEquals(1, accountIdToStatuses.select(Path.key(aliceId).mapKeys()).size());
      assertEquals(1, getHomeTimeline.invoke(aliceId, start, limit).results.size());
      assertEquals(1, getHomeTimeline.invoke(bobId, start, limit).results.size());

      // alice posts in esperanto and bob doesn't get it
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), (new Status(aliceId, TestHelpers.normalStatusContent("Saluton!", StatusVisibility.Public), ts+=1)).setLanguage("eo")));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);
      assertEquals(2, accountIdToStatuses.select(Path.key(aliceId).mapKeys()).size());
      assertEquals(2, getHomeTimeline.invoke(aliceId, start, limit).results.size());
      assertEquals(1, getHomeTimeline.invoke(bobId, start, limit).results.size());

      // bob mutes alice
      muteAccountDepot.append(new MuteAccount(bobId, aliceId, new MuteAccountOptions(true), ts+=1));

      // alice posts a status but bob's home timeline is empty
      String mutedStatusUUID = UUID.randomUUID().toString();
      statusDepot.append(new AddStatus(mutedStatusUUID, new Status(aliceId, TestHelpers.normalStatusContent("Hello2", StatusVisibility.Public), ts+=1)));
      Long mutedStatusId = postUUIDToStatusId.selectOne(aliceId, Path.key(mutedStatusUUID));
      assertNotNull(mutedStatusId);

      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);
      assertEquals(3, accountIdToStatuses.select(Path.key(aliceId).mapKeys()).size());
      assertEquals(0, getHomeTimeline.invoke(bobId, start, limit).results.size());

      // charlie boosts alice's status but bob's timeline remains empty
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.boostStatusContent(aliceId, mutedStatusId), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);
      assertEquals(1, accountIdToStatuses.select(Path.key(charlieId).mapKeys()).size());
      assertEquals(0, getHomeTimeline.invoke(bobId, start, limit).results.size());

      // bob unmutes alice
      muteAccountDepot.append(new RemoveMuteAccount(bobId, aliceId, ts+=1));

      // bob's home timeline query shows both non-esperanto statuses from alice and the boost from charlie but not the later one
      assertEquals(3, getHomeTimeline.invoke(bobId, start, limit).results.size());

      // bob blocks alice
      followAndBlockAccountDepot.append(new BlockAccount(bobId, aliceId, ts+=1));

      // bob's home timeline query shows none of alice's posts
      assertEquals(0, getHomeTimeline.invoke(bobId, start, limit).results.size());

      // bob unblocks alice and blocks charlie
      followAndBlockAccountDepot.append(new RemoveBlockAccount(bobId, aliceId, ts+=1));
      followAndBlockAccountDepot.append(new BlockAccount(bobId, charlieId, ts+=1));

      // alice makes a new post
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hello3", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // bob's home timeline only shows the Hello1 status.
      // this is because blocking implies unfollowing.
      // however, the two statuses from alice got there before the block and remains there now that alice is unblocked.
      assertEquals(2, getHomeTimeline.invoke(bobId, start, limit).results.size());

      // bob follows alice again
      followAndBlockAccountDepot.append(new FollowAccount(bobId, aliceId, ts+=1));

      // alice makes a new post
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hello4", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // bob's home timeline query includes the new post
      assertEquals(3, getHomeTimeline.invoke(bobId, start, limit).results.size());

      // alice makes more posts
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hello5", StatusVisibility.Public), ts+=1)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hello6", StatusVisibility.Public), ts+=1)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hello7", StatusVisibility.Public), ts+=1)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hello8", StatusVisibility.Public), ts+=1)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hello9", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=5);

      // query the first page of results,
      // and then pass the last result's id to get the next page of results
      {
        List<StatusResultWithId> results = getHomeTimeline.invoke(bobId, start, limit).results;
        assertEquals(limit, results.size());
        StatusResultWithId lastResult = results.get(results.size() - 1);
        assertEquals(3, getHomeTimeline.invoke(bobId, new StatusPointer(lastResult.status.author.accountId, lastResult.statusId), limit).results.size());
      }

      // get alice's last status
      StatusResultWithId status9 = getAccountTimeline.invoke(aliceId, aliceId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(aliceId, status9.statusId)));

      // bob sees alice's last status
      {
        List<StatusResultWithId> results = getHomeTimeline.invoke(bobId, start, limit).results;
        assertEquals(status9.statusId, results.get(0).statusId);
      }

      // alice removes her last status
      statusDepot.append(new RemoveStatus(aliceId, status9.statusId, ts+=1));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1); // for remote fanout
      assertNull(accountIdToStatuses.selectOne(Path.key(aliceId, status9.statusId)));

      // bob no longer sees alice's last status
      {
        List<StatusResultWithId> results = getHomeTimeline.invoke(bobId, start, limit).results;
        assertNotEquals(status9.statusId, results.get(0).statusId);
      }

      // get alice's last status
      StatusResultWithId status8 = getAccountTimeline.invoke(aliceId, aliceId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(aliceId, status8.statusId)));

      // alice edits her last status
      statusDepot.append(new EditStatus(status8.statusId, new Status(aliceId, TestHelpers.normalStatusContent("Hello8 edit", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);
      assertEquals("Hello8 edit", ((Status) accountIdToStatuses.selectOne(Path.key(aliceId, status8.statusId).first())).content.getNormal().text);
      assertEquals(2, ((List) accountIdToStatuses.selectOne(Path.key(aliceId, status8.statusId))).size());

      // alice edits her last status again, but it doesn't go through because it reached maxEditCount
      statusDepot.append(new EditStatus(status8.statusId, new Status(aliceId, TestHelpers.normalStatusContent("Hello8 edit again", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);
      assertEquals("Hello8 edit", ((Status) accountIdToStatuses.selectOne(Path.key(aliceId, status8.statusId).first())).content.getNormal().text);
      assertEquals(2, ((List) accountIdToStatuses.selectOne(Path.key(aliceId, status8.statusId))).size());

      // add attachment
      String attachmentId = UUID.randomUUID().toString();
      statusAttachmentWithIdDepot.append(new AttachmentWithId(attachmentId, new Attachment(AttachmentKind.Image, "png", "my cute dog")));
      Attachment attachment = uuidToAttachment.selectOne(Path.key(attachmentId));
      assertNotNull(attachment);

      // alice makes a new post with an attachment
      Status statusWithAttachment = new Status(aliceId, TestHelpers.normalStatusContent("Hello with attachment", StatusVisibility.Public), ts+=1);
      statusWithAttachment.content.getNormal().setAttachments(Arrays.asList(new AttachmentWithId(attachmentId, attachment)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), statusWithAttachment));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // get alice's last status
      StatusResultWithId statusResultWithAttachment = getAccountTimeline.invoke(aliceId, aliceId, 0L, limit, true).results.get(0);
      assertNotNull(statusResultWithAttachment);
      assertEquals(1, statusResultWithAttachment.status.content.getNormal().getAttachments().size());
    }
  }

  @Test
  public void followerTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try (InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      coreModule.singlePartitionFanoutLimit = 1;
      coreModule.rangeQueryLimit = 1;
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");

      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
      PState statusIdToLocalFollowerFanouts = ipc.clusterPState(coreModuleName, "$$statusIdToLocalFollowerFanouts");
      PState statusIdToRemoteFollowerFanout = ipc.clusterPState(coreModuleName, "$$statusIdToRemoteFollowerFanout");

      PState followerToFolloweesById = ipc.clusterPState(relationshipsModuleName, "$$followerToFolloweesById");
      PState followeeToFollowersById = ipc.clusterPState(relationshipsModuleName, "$$followeeToFollowersById");

      QueryTopologyClient<StatusQueryResults> getAccountTimeline = ipc.clusterQuery(coreModuleName, "getAccountTimeline");
      QueryTopologyClient<StatusQueryResults> getHomeTimeline = ipc.clusterQuery(coreModuleName, "getHomeTimeline");
      QueryTopologyClient<List> getAccountsFromAccountIds = ipc.clusterQuery(coreModuleName, "getAccountsFromAccountIds");

      int coreCount = 0;
      int ts = 0;
      int limit = 5;

      // add new accounts
      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("eric@bar.com", "eric@bar.com", "hash5", "en-US", "", "", AccountContent.remote(new RemoteAccount("", "", "")), ts+=1));
      accountDepot.append(new Account("hannah@bar.com", "hannah@bar.com", "hash6", "en-US", "", "", AccountContent.remote(new RemoteAccount("", "", "")), ts+=1));

      // get user ids
      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));
      long ericId = nameToUser.selectOne(Path.key("eric@bar.com", "accountId"));
      long hannahId = nameToUser.selectOne(Path.key("hannah@bar.com", "accountId"));

      // bob follows alice
      followAndBlockAccountDepot.append(new FollowAccount(bobId, aliceId, ts+=1));

      // charlie follows alice
      followAndBlockAccountDepot.append(new FollowAccount(charlieId, aliceId, ts+=1));

      // eric follows alice
      followAndBlockAccountDepot.append(new FollowAccount(ericId, aliceId, ts+=1));

      // hannah follows alice
      followAndBlockAccountDepot.append(new FollowAccount(hannahId, aliceId, ts+=1));

      // alice makes a status
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hello, world!", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // get alice's last status
      StatusResultWithId aliceStatusResultWithId = getAccountTimeline.invoke(aliceId, aliceId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(aliceId, aliceStatusResultWithId.statusId)));

      // since alice has 4 followers and fanoutLimit is 1,
      // her status should now be in the follower fanout pstate for further processing

      // wait for another microbatch iteration
      ipc.pauseMicrobatchTopology(coreModuleName, "fanout");
      ipc.resumeMicrobatchTopology(coreModuleName, "fanout");

      // wait for another microbatch iteration
      ipc.pauseMicrobatchTopology(coreModuleName, "fanout");
      ipc.resumeMicrobatchTopology(coreModuleName, "fanout");

      // wait for another microbatch iteration
      ipc.pauseMicrobatchTopology(coreModuleName, "fanout");
      ipc.resumeMicrobatchTopology(coreModuleName, "fanout");

      // wait for another microbatch iteration
      ipc.pauseMicrobatchTopology(coreModuleName, "fanout");
      ipc.resumeMicrobatchTopology(coreModuleName, "fanout");

      // now the fanout should be complete
      assertNull(statusIdToLocalFollowerFanouts.selectOne(Path.key(aliceStatusResultWithId.statusId)));
      assertNull(statusIdToRemoteFollowerFanout.selectOne(Path.key(aliceStatusResultWithId.statusId)));

      StatusPointer start = new StatusPointer(-1, -1);

      // bob sees alice's last status
      {
        List<StatusResultWithId> results = getHomeTimeline.invoke(bobId, start, limit).results;
        assertEquals(aliceStatusResultWithId.statusId, results.get(0).statusId);
      }

      // charlie sees alice's last status
      {
        List<StatusResultWithId> results = getHomeTimeline.invoke(charlieId, start, limit).results;
        assertEquals(aliceStatusResultWithId.statusId, results.get(0).statusId);
      }

      // query everyone bob is following
      assertEquals(1, followerToFolloweesById.select(Path.key(bobId).mapVals()).size());

      // query everyone alice is followed by
      assertEquals(4, followeeToFollowersById.select(Path.key(aliceId).mapVals()).size());

      // make sure query works
      {
        List<Follower> followers = followeeToFollowersById.select(Path.key(aliceId).mapVals());
        List<AccountWithId> accounts = getAccountsFromAccountIds.invoke(null, followers.stream().map(ap -> ap.accountId).collect(Collectors.toList()));
        assertEquals(4, followers.size());
        assertEquals(4, accounts.size());
      }

      // alice blocks bob
      followAndBlockAccountDepot.append(new BlockAccount(aliceId, bobId, ts+=1));

      // bob is no longer following alice
      assertEquals(0, followerToFolloweesById.select(Path.key(bobId).mapVals()).size());

      // alice is no longer followed by bob
      assertEquals(3, followeeToFollowersById.select(Path.key(aliceId).mapVals()).size());

      // charlie follows bob, hiding boosts, while david follows bob normally (showing boosts)
      followAndBlockAccountDepot.append((new FollowAccount(charlieId, bobId, ts+=1)).setShowBoosts(false));
      followAndBlockAccountDepot.append(new FollowAccount(davidId, bobId, ts+=1));

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, TestHelpers.boostStatusContent(aliceId, aliceStatusResultWithId.statusId), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      StatusResultWithId bobStatusResultWithId = getAccountTimeline.invoke(bobId, bobId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(bobId, bobStatusResultWithId.statusId)));

      // wait for another microbatch iteration
      ipc.pauseMicrobatchTopology(coreModuleName, "fanout");
      ipc.resumeMicrobatchTopology(coreModuleName, "fanout");

      // wait for another microbatch iteration
      ipc.pauseMicrobatchTopology(coreModuleName, "fanout");
      ipc.resumeMicrobatchTopology(coreModuleName, "fanout");

      // now the fanout should be complete
      assertNull(statusIdToLocalFollowerFanouts.selectOne(Path.key(bobStatusResultWithId.statusId)));

      {
          List<StatusResultWithId> results = getHomeTimeline.invoke(charlieId, start, limit).results;
          assertEquals(0, results.stream()
                                 .filter(statusWithId -> statusWithId.status.author.accountId == bobId)
                                 .collect(Collectors.toList())
                                 .size());
      }

      {
          List<StatusResultWithId> results = getHomeTimeline.invoke(davidId, start, limit).results;
          assertEquals(bobId, results.get(0).status.author.accountId);
      }
    }
  }

  @Test
  public void hashtagTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      coreModule.singlePartitionFanoutLimit = 1;
      coreModule.rangeQueryLimit = 1;
      coreModule.enableHomeTimelineRefresh = false;
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");

      Depot followHashtagDepot = ipc.clusterDepot(relationshipsModuleName, "*followHashtagDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
      PState hashtagFanoutToIndex = ipc.clusterPState(coreModuleName, "$$hashtagFanoutToIndex");

      QueryTopologyClient<StatusQueryResults> getAccountTimeline = ipc.clusterQuery(coreModuleName, "getAccountTimeline");
      QueryTopologyClient<StatusQueryResults> getHomeTimeline = ipc.clusterQuery(coreModuleName, "getHomeTimeline");

      int coreCount = 0;
      int ts = 0;
      int limit = 5;

      // add new accounts
      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));

      // get user ids
      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));

      // bob follows #foo
      followHashtagDepot.append(new FollowHashtag(bobId, "foo", ts+=1));

      // charlie follows #foo
      followHashtagDepot.append(new FollowHashtag(charlieId, "foo", ts+=1));

      // david makes a status with the hashtag
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("#foo", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // get david's last status
      StatusResultWithId statusDavidFoo = getAccountTimeline.invoke(davidId, davidId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(davidId, statusDavidFoo.statusId)));

      // since #foo has 2 followers and fanoutLimit is 1,
      // david's status should now be in the hashtag follower fanout pstate for further processing

      // wait for another microbatch iteration
      ipc.pauseMicrobatchTopology(coreModuleName, "fanout");
      ipc.resumeMicrobatchTopology(coreModuleName, "fanout");

      // wait for another microbatch iteration
      ipc.pauseMicrobatchTopology(coreModuleName, "fanout");
      ipc.resumeMicrobatchTopology(coreModuleName, "fanout");

      // now the fanout should be complete
      assertNull(hashtagFanoutToIndex.selectOne(Path.key(new HashtagFanout(davidId, statusDavidFoo.statusId, "foo"))));

      StatusPointer start = new StatusPointer(-1, -1);

      // bob sees david's last status
      {
        List<StatusResultWithId> results = getHomeTimeline.invoke(bobId, start, limit).results;
        assertEquals(statusDavidFoo.statusId, results.get(0).statusId);
      }

      // charlie sees david's last status
      {
        List<StatusResultWithId> results = getHomeTimeline.invoke(charlieId, start, limit).results;
        assertEquals(statusDavidFoo.statusId, results.get(0).statusId);
      }

      // david edits the status and removes the hashtag
      statusDepot.append(new EditStatus(statusDavidFoo.statusId, new Status(davidId, TestHelpers.normalStatusContent("hello", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // bob still sees david's last status. once a status gets to a timeline, it will remain even if the hashtag is removed.
      {
        List<StatusResultWithId> results = getHomeTimeline.invoke(bobId, start, limit).results;
        assertEquals(statusDavidFoo.statusId, results.get(0).statusId);
      }
    }
  }

  @Test
  public void listTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      relationshipsModule.listCountLimit = 3;
      relationshipsModule.listMemberCountLimit = 3;
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      coreModule.singlePartitionFanoutLimit = 1;
      coreModule.rangeQueryLimit = 1;
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");

      Depot listDepot = ipc.clusterDepot(relationshipsModuleName, "*listDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
      PState statusIdToListFanout = ipc.clusterPState(coreModuleName, "$$statusIdToListFanout");

      PState listIdToList = ipc.clusterPState(relationshipsModuleName, "$$listIdToList");
      PState authorIdToListIds = ipc.clusterPState(relationshipsModuleName, "$$authorIdToListIds");
      PState listIdToMemberIds = ipc.clusterPState(relationshipsModuleName, "$$listIdToMemberIds");

      QueryTopologyClient<StatusQueryResults> getAccountTimeline = ipc.clusterQuery(coreModuleName, "getAccountTimeline");
      QueryTopologyClient<StatusQueryResults> getListTimeline = ipc.clusterQuery(coreModuleName, "getListTimeline");
      QueryTopologyClient<List> getListsFromAuthor = ipc.clusterQuery(relationshipsModuleName, "getListsFromAuthor");

      int coreCount = 0;
      int ts = 0;
      int limit = 5;

      // add new accounts
      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("eric", "eric@foo.com", "hash5", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));

      // get user ids
      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));
      long ericId = nameToUser.selectOne(Path.key("eric", "accountId"));

      // david makes a list
      listDepot.append(new AccountList(davidId, "my friends", "list", ts+=1));
      Long davidsListId = authorIdToListIds.selectOne(Path.key(davidId).first());
      assertNotNull(davidsListId);
      assertEquals(1, getListsFromAuthor.invoke(davidId, null).size());

      // david updates the list
      listDepot.append(new AccountListWithId(davidsListId, new AccountList(davidId, "my cool friends", "list", ts+=1)));
      AccountList davidsList = listIdToList.selectOne(Path.key(davidsListId));
      assertEquals("my cool friends", davidsList.title);

      // david adds accounts to the list
      listDepot.append(new AccountListMember(davidsListId, aliceId, ts+=1));
      listDepot.append(new AccountListMember(davidsListId, bobId, ts+=1));
      listDepot.append(new AccountListMember(davidsListId, charlieId, ts+=1));
      assertEquals(3, listIdToMemberIds.select(Path.key(davidsListId).all()).size());
      assertEquals(1, getListsFromAuthor.invoke(davidId, bobId).size());

      // david removes an account from the list
      listDepot.append(new RemoveAccountListMember(davidsListId, bobId, ts+=1));
      assertEquals(2, listIdToMemberIds.select(Path.key(davidsListId).all()).size());
      assertEquals(0, getListsFromAuthor.invoke(davidId, bobId).size());

      // charlie makes a list
      listDepot.append(new AccountList(charlieId, "my friends", "list", ts+=1));
      Long charliesListId = authorIdToListIds.selectOne(Path.key(charlieId).first());
      assertNotNull(charliesListId);

      // charlie adds accounts to the list
      listDepot.append(new AccountListMember(charliesListId, aliceId, ts+=1));
      listDepot.append(new AccountListMember(charliesListId, bobId, ts+=1));
      listDepot.append(new AccountListMember(charliesListId, davidId, ts+=1));
      assertEquals(3, listIdToMemberIds.select(Path.key(charliesListId).all()).size());

      // alice makes a new status
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hello everyone!", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // get alice's last status
      StatusResultWithId status11 = getAccountTimeline.invoke(aliceId, aliceId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(aliceId, status11.statusId)));

      // since alice is in 2 lists and fanoutLimit is 1,
      // alice's status should now be in the list fanout pstate for further processing

      // wait for another microbatch iteration
      ipc.pauseMicrobatchTopology(coreModuleName, "fanout");
      ipc.resumeMicrobatchTopology(coreModuleName, "fanout");

      // wait for another microbatch iteration
      ipc.pauseMicrobatchTopology(coreModuleName, "fanout");
      ipc.resumeMicrobatchTopology(coreModuleName, "fanout");

      // now the fanout should be complete
      assertNull(statusIdToListFanout.selectOne(Path.key(status11.statusId)));

      // alice's status is in both lists
      assertEquals(status11.statusId, getListTimeline.invoke(davidsListId, 0L, limit).results.get(0).statusId);
      assertEquals(status11.statusId, getListTimeline.invoke(charliesListId, 0L, limit).results.get(0).statusId);

      // david removes the list
      listDepot.append(new RemoveAccountList(davidsListId, ts+=1));
      davidsList = listIdToList.selectOne(Path.key(davidsListId));
      assertNull(davidsList);
      assertEquals(0, listIdToMemberIds.select(Path.key(davidsListId).all()).size());
      assertEquals(0, getListsFromAuthor.invoke(davidId, null).size());

      // alice makes lists
      listDepot.append(new AccountList(aliceId, "list1", "list", ts+=1));
      listDepot.append(new AccountList(aliceId, "list2", "list", ts+=1));
      listDepot.append(new AccountList(aliceId, "list3", "list", ts+=1));
      listDepot.append(new AccountList(aliceId, "list4", "list", ts+=1));

      // alice's list count didn't go beyond the limit
      assertEquals(relationshipsModule.listCountLimit, authorIdToListIds.select(Path.key(aliceId).all()).size());

      // alice adds members to a list
      Long alicesListId = authorIdToListIds.selectOne(Path.key(aliceId).first());
      listDepot.append(new AccountListMember(alicesListId, bobId, ts+=1));
      listDepot.append(new AccountListMember(alicesListId, charlieId, ts+=1));
      listDepot.append(new AccountListMember(alicesListId, davidId, ts+=1));
      listDepot.append(new AccountListMember(alicesListId, ericId, ts+=1));

      // alice's member count didn't go beyond the limit
      assertEquals(relationshipsModule.listMemberCountLimit, listIdToMemberIds.select(Path.key(alicesListId).all()).size());
    }
  }

  @Test
  public void listRepliesTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");

      Depot listDepot = ipc.clusterDepot(relationshipsModuleName, "*listDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");

      PState authorIdToListIds = ipc.clusterPState(relationshipsModuleName, "$$authorIdToListIds");

      QueryTopologyClient<StatusQueryResults> getListTimeline = ipc.clusterQuery(coreModuleName, "getListTimeline");

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));

      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));

      listDepot.append(new AccountList(aliceId, "my friends", "list", 0));
      Long aliceListId = authorIdToListIds.selectOne(Path.key(aliceId).first());
      listDepot.append(new AccountListMember(aliceListId, bobId, 0));
      listDepot.append(new AccountListMember(aliceListId, charlieId, 0));


      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, TestHelpers.normalStatusContent("Hello everyone!", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("Hello everyone!", StatusVisibility.Public), 0)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 2);

      long bob1 = accountIdToStatuses.selectOne(Path.key(bobId).first().first());
      long david1 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.replyStatusContent("R1", StatusVisibility.Public, davidId, david1), 0)));
      long charlie1 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.replyStatusContent("R1", StatusVisibility.Public, bobId, bob1), 0)));
      long charlie2 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());

      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 4);


      List<StatusResultWithId> results = getListTimeline.invoke(aliceListId, 0L, 100).results;
      Set tuples = new HashSet();
      for(StatusResultWithId s: results) tuples.add(Arrays.asList(s.status.author.accountId, s.statusId));

      // verify reply to david doesn't go through
      assertEquals(2, results.size());
      assertEquals(asSet(Arrays.asList(bobId, bob1), Arrays.asList(charlieId, charlie2)), tuples);
    }
  }

  @Test
  public void filterTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");

      Depot filterDepot = ipc.clusterDepot(relationshipsModuleName, "*filterDepot");
      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");

      PState accountIdToFilterIdToFilter = ipc.clusterPState(relationshipsModuleName, "$$accountIdToFilterIdToFilter");

      QueryTopologyClient<StatusQueryResults> getAccountTimeline = ipc.clusterQuery(coreModuleName, "getAccountTimeline");
      QueryTopologyClient<StatusQueryResults> getHomeTimeline = ipc.clusterQuery(coreModuleName, "getHomeTimeline");

      int coreCount = 0;
      int ts = 0;
      int limit = 5;

      // add new accounts
      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));

      // get user ids
      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));

      // david makes a filter
      filterDepot.append(new AddFilter(new Filter(
              davidId,
              "foo",
              new HashSet<>(Arrays.asList(FilterContext.Home)),
              new ArrayList<>(),
              new HashSet<>(),
              FilterAction.Warn,
              ts+=1
      ), UUID.randomUUID().toString()));

      Long davidsFilterId = (Long) accountIdToFilterIdToFilter.select(Path.key(davidId).mapKeys()).get(0);
      assertNotNull(davidsFilterId);

      // david updates the filter
      filterDepot.append(new EditFilter().setFilterId(davidsFilterId)
                                         .setAccountId(davidId)
                                         .setTitle("fairwell")
                                         .setContext(new HashSet<>(Arrays.asList(FilterContext.Home, FilterContext.Account)))
                                         .setKeywords(Arrays.asList(EditFilterKeyword.addKeyword(new KeywordFilter().setWord("goodbye").setWholeWord(true)),
                                                                    EditFilterKeyword.addKeyword(new KeywordFilter().setWord("see ya").setWholeWord(true))))
                                         .setAction(FilterAction.Warn));

      Filter davidsFilter = accountIdToFilterIdToFilter.selectOne(Path.key(davidId, davidsFilterId));
      assertEquals("fairwell", davidsFilter.title);

      // david follows alice
      followAndBlockAccountDepot.append(new FollowAccount(davidId, aliceId, ts+=1));

      // alice makes a new status
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Goodbye everyone!", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // get alice's last status
      StatusResultWithId aliceStatus = getAccountTimeline.invoke(aliceId, aliceId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(aliceId, aliceStatus.statusId)));

      StatusPointer start = new StatusPointer(-1, -1);

      // alice's status is filtered in david's timeline
      StatusResultWithId timelineResult = getHomeTimeline.invoke(davidId, start, limit).results.get(0);
      assertTrue(timelineResult.status.content.isSetNormal());
      assertEquals(davidsFilter.title, timelineResult.status.metadata.filters.get(0).filter.title);

      // alice's status is filtered in her own timeline (as viewed by david)
      StatusResultWithId queryResult = getAccountTimeline.invoke(davidId, aliceId, 0L, limit, true).results.get(0);
      assertTrue(queryResult.status.content.isSetNormal());
      assertEquals(davidsFilter.title, queryResult.status.metadata.filters.get(0).filter.title);

      // charlie makes a new status
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.normalStatusContent("Goodbye everyone!", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // get charlie's last status
      StatusResultWithId charlieStatus = getAccountTimeline.invoke(charlieId, charlieId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(charlieId, charlieStatus.statusId)));

      // alice boosts charlie
      statusDepot.append(new BoostStatus(UUID.randomUUID().toString(), aliceId, new StatusPointer(charlieId, charlieStatus.statusId), ts+=1));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // alice's boost is filtered in david's timeline
      StatusResultWithId boostTimelineResult = getHomeTimeline.invoke(davidId, start, limit).results.get(0);
      assertTrue(boostTimelineResult.status.content.isSetBoost());
      assertEquals(davidsFilter.title, boostTimelineResult.status.content.getBoost().status.metadata.filters.get(0).filter.title);

      // alice's boost is filtered in her own timeline (as viewed by david)
      StatusResultWithId boostQueryResult = getAccountTimeline.invoke(davidId, aliceId, 0L, limit, true).results.get(0);
      assertTrue(boostQueryResult.status.content.isSetBoost());
      assertEquals(davidsFilter.title, boostQueryResult.status.content.getBoost().status.metadata.filters.get(0).filter.title);

      // alice makes a new status which doesn't match the filter
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Fare thee well", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);
      StatusResultWithId secondTimelineResult = getHomeTimeline.invoke(davidId, start, limit).results.get(0);

      // it's not filtered
      assertEquals(0, secondTimelineResult.status.metadata.filters.size());

      // David updates the filter to hide that status in particular
      StatusPointer statusPointer = new StatusPointer(secondTimelineResult.status.author.accountId, secondTimelineResult.statusId);
      filterDepot.append(new AddStatusToFilter(davidsFilterId, davidId, statusPointer));
      Filter updatedFilter = accountIdToFilterIdToFilter.selectOne(Path.key(davidId, davidsFilterId));
      assertTrue(updatedFilter.statuses.contains(statusPointer));

      // status is now filtered
      StatusResultWithId filteredSecondTimelineResult = getHomeTimeline.invoke(davidId, start, limit).results.get(0);
      MatchingFilter matchingFilter = filteredSecondTimelineResult.status.metadata.filters.get(0);
      assertEquals(davidsFilterId, matchingFilter.filterId);
      assertTrue(matchingFilter.statusFilterMatch);
      assertEquals(0, matchingFilter.keywordMatches.size());

      // david removes the filter
      filterDepot.append(new RemoveFilter(davidsFilterId, davidId, ts+=1));
      davidsFilter = accountIdToFilterIdToFilter.selectOne(Path.key(davidId, davidsFilterId));
      assertNull(davidsFilter);
    }
  }

  @Test
  public void replyTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      coreModule.enableHomeTimelineRefresh = false;
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");

      QueryTopologyClient<StatusQueryResults> getAccountTimeline = ipc.clusterQuery(coreModuleName, "getAccountTimeline");
      QueryTopologyClient<StatusQueryResults> getHomeTimeline = ipc.clusterQuery(coreModuleName, "getHomeTimeline");
      QueryTopologyClient<StatusQueryResults> getAncestors = ipc.clusterQuery(coreModuleName, "getAncestors");
      QueryTopologyClient<StatusQueryResults> getDescendants = ipc.clusterQuery(coreModuleName, "getDescendants");

      int coreCount = 0;
      int ts = 0;
      int limit = 5;

      // add new accounts
      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));

      // get user ids
      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));

      // charlie follows alice and bob
      followAndBlockAccountDepot.append(new FollowAccount(charlieId, aliceId, ts+=1));
      followAndBlockAccountDepot.append(new FollowAccount(charlieId, bobId, ts+=1));

      // david follows bob
      followAndBlockAccountDepot.append(new FollowAccount(davidId, bobId, ts+=1));

      ipc.waitForMicrobatchProcessedCount(coreModuleName, "bloom", 3);

      // alice makes a new status
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Anyone there?", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // get alice's last status
      StatusResultWithId aliceStatusResultWithId = getAccountTimeline.invoke(aliceId, aliceId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(aliceId, aliceStatusResultWithId.statusId)));

      // bob responds to alice
      AddStatus bobStatus = new AddStatus(UUID.randomUUID().toString(), new Status(bobId, TestHelpers.replyStatusContent("I am!", StatusVisibility.Public, aliceId, aliceStatusResultWithId.statusId), ts+=1));
      statusDepot.append(bobStatus);
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // make sure the reply count is correct
      aliceStatusResultWithId = getAccountTimeline.invoke(aliceId, aliceId, 0L, limit, true).results.get(0);
      assertEquals(1, aliceStatusResultWithId.status.metadata.replyCount);

      // get bob's last status
      StatusResultWithId bobStatusResultWithId = getAccountTimeline.invoke(bobId, bobId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(bobId, bobStatusResultWithId.statusId)));

      StatusPointer start = new StatusPointer(-1, -1);

      // charlie can see bob's post because he's following both alice and bob
      // (replies are only fanned out to people following both parent and child)
      assertEquals(2, getHomeTimeline.invoke(charlieId, start, limit).results.size());
      assertEquals(bobStatusResultWithId.statusId, getHomeTimeline.invoke(charlieId, start, limit).results.get(0).statusId);

      // david cannot see bob's post because he's not following alice
      assertEquals(0, getHomeTimeline.invoke(davidId, start, limit).results.size());

      // query the descendants and ancestors
      assertEquals(bobStatusResultWithId.statusId, getDescendants.invoke(aliceId, aliceId, aliceStatusResultWithId.statusId, limit).results.get(0).statusId);
      assertEquals(aliceStatusResultWithId.statusId, getAncestors.invoke(bobId, bobId, bobStatusResultWithId.statusId, limit).results.get(0).statusId);

      // charlie responds to alice
      AddStatus charlieStatus = new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.replyStatusContent("I am too!", StatusVisibility.Public, aliceId, aliceStatusResultWithId.statusId), ts+=1));
      statusDepot.append(charlieStatus);
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // get charlie's last status
      StatusResultWithId charlieStatusResultWithId = getAccountTimeline.invoke(charlieId, charlieId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(charlieId, charlieStatusResultWithId.statusId)));

      // david responds to bob
      AddStatus davidStatus = new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.replyStatusContent("Happy to see you", StatusVisibility.Public, bobId, bobStatusResultWithId.statusId), ts+=1));
      statusDepot.append(davidStatus);
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // get david's last status
      StatusResultWithId davidStatusResultWithId = getAccountTimeline.invoke(davidId, davidId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(davidId, davidStatusResultWithId.statusId)));

      // query the descendants and ancestors
      assertEquals(
              Arrays.asList(bobStatusResultWithId.statusId, davidStatusResultWithId.statusId, charlieStatusResultWithId.statusId),
              TestHelpers.getStatusIds(getDescendants.invoke(aliceId, aliceId, aliceStatusResultWithId.statusId, limit).results)
      );
      assertEquals(

              Arrays.asList(aliceStatusResultWithId.statusId, bobStatusResultWithId.statusId),
              TestHelpers.getStatusIds(getAncestors.invoke(davidId, davidId, davidStatusResultWithId.statusId, limit).results)
      );

      // querying descendants and ancestors with an invalid id returns an empty list
      assertEquals(0, getDescendants.invoke(aliceId, aliceId, -1L, limit).results.size());
      assertEquals(0, getAncestors.invoke(davidId, davidId, -1L, limit).results.size());
    }
  }

  @Test
  public void visibilityTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot conversationDepot = ipc.clusterDepot(coreModuleName, "*conversationDepot");

      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
      PState accountIdToConvoIdsById = ipc.clusterPState(coreModuleName, "$$accountIdToConvoIdsById");
      PState accountIdToConvoIdToConvo = ipc.clusterPState(coreModuleName, "$$accountIdToConvoIdToConvo");

      QueryTopologyClient<StatusQueryResults> getAccountTimeline = ipc.clusterQuery(coreModuleName, "getAccountTimeline");
      QueryTopologyClient<List> getConversationTimeline = ipc.clusterQuery(coreModuleName, "getConversationTimeline");
      QueryTopologyClient<List> getConversation = ipc.clusterQuery(coreModuleName, "getConversation");
      QueryTopologyClient<StatusQueryResults> getDirectTimeline = ipc.clusterQuery(coreModuleName, "getDirectTimeline");
      QueryTopologyClient<StatusQueryResults> getStatusesFromPointers = ipc.clusterQuery(coreModuleName, "getStatusesFromPointers");
      QueryTopologyClient<StatusQueryResults> getHomeTimeline = ipc.clusterQuery(coreModuleName, "getHomeTimeline");

      int coreCount = 0;
      int ts = 0;
      int limit = 5;

      // add new accounts
      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));

      // get user ids
      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));

      // bob follows alice
      followAndBlockAccountDepot.append(new FollowAccount(bobId, aliceId, ts+=1));

      StatusPointer start = new StatusPointer(-1, -1);

      long alicePrivateStatusId;

      {
        // alice makes a private status
        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("This is only for my followers!", StatusVisibility.Private), ts+=1)));
        ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

        // get alice's last status
        alicePrivateStatusId = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());

        // alice's status didn't get to her own timeline as viewed by david, because he isn't following her
        assertEquals(0, getAccountTimeline.invoke(davidId, aliceId, 0L, limit, true).results.size());

        // alice's status didn't get to her own timeline as viewed by an anonymous user
        assertEquals(0, getAccountTimeline.invoke(null, aliceId, 0L, limit, true).results.size());

        // alice's status *did* get to her own timeline as viewed by bob, because he is following her
        assertEquals(1, getAccountTimeline.invoke(bobId, aliceId, 0L, limit, true).results.size());
        assertEquals(alicePrivateStatusId, (getAccountTimeline.invoke(bobId, aliceId, 0L, limit, true).results.get(0)).statusId);

        // alice's status got to bob's timeline
        assertEquals(1, getHomeTimeline.invoke(bobId, start, limit).results.size());
      }

      {
        // alice makes an unlisted status
        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("This is unlisted!", StatusVisibility.Unlisted), ts+=1)));
        ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

        // get alice's last status
        long aliceUnlistedStatusId = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());

        // alice's status got to her own timeline as viewed by david
        assertEquals(1, getAccountTimeline.invoke(davidId, aliceId, 0L, limit, true).results.size());

        // alice's status got to her own timeline as viewed by an anonymous user
        assertEquals(1, getAccountTimeline.invoke(null, aliceId, 0L, limit, true).results.size());

        // alice's status got to bob's timeline
        assertEquals(2, getHomeTimeline.invoke(bobId, start, limit).results.size());
      }

      // bob unfollows alice
      followAndBlockAccountDepot.append(new RemoveFollowAccount(bobId, aliceId, ts+=1));

      // alice makes a direct status
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("hey @bob", StatusVisibility.Direct), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // get alice's last status
      long aliceDirectStatusId = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());

      // alice replies to her own direct status
      AddStatus directReply = new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.replyStatusContent("@bob this is a reply", StatusVisibility.Direct, aliceId, aliceDirectStatusId), ts+=1));
      statusDepot.append(directReply);
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // get alice's last status
      long aliceDirectReplyStatusId = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());

      // both direct statuses are in her conversation timeline
      List<Long> conversationIds = accountIdToConvoIdsById.select(Path.key(aliceId).mapVals());
      assertEquals(1, conversationIds.size());
      List<StatusPointer> conversationTimeline = accountIdToConvoIdToConvo.select(Path.key(aliceId, conversationIds.get(0), "timeline").mapVals());
      HashSet<Long> conversationStatusIds = new HashSet<>(conversationTimeline.stream().map(statusPointer -> statusPointer.statusId).collect(Collectors.toList()));
      assertTrue(conversationStatusIds.contains(aliceDirectStatusId));
      assertTrue(conversationStatusIds.contains(aliceDirectReplyStatusId));

      // bob sees both statuses in the direct timeline
      StatusQueryResults bobDirectMessages = getDirectTimeline.invoke(bobId, 0L, limit);
      assertEquals(2, bobDirectMessages.results.size());
      assertEquals(aliceDirectReplyStatusId, bobDirectMessages.results.get(0).statusId);
      assertEquals(aliceDirectStatusId, bobDirectMessages.results.get(1).statusId);

      // bob sees the conversation
      List<Conversation> conversations = getConversationTimeline.invoke(bobId, 0L, limit);
      assertEquals(1, conversations.size());
      assertEquals(aliceDirectReplyStatusId, conversations.get(0).lastStatus.result.statusId);
      assertTrue(conversations.get(0).unread);
      assertEquals(1, conversations.get(0).accounts.size());
      assertEquals(aliceId, conversations.get(0).accounts.get(0).accountId);

      // you can also query by conversation id
      Conversation convo = (Conversation) getConversation.invoke(bobId, conversations.get(0).conversationId);
      assertNotNull(convo);
      assertEquals(aliceDirectReplyStatusId, convo.lastStatus.result.statusId);

      // an invalid conversation id will return null
      assertNull(getConversation.invoke(bobId, -1L));

      // bob edits the conversation to mark it as read
      conversationDepot.append(new EditConversation(bobId, conversations.get(0).conversationId, false));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);
      conversations = getConversationTimeline.invoke(bobId, 0L, limit);
      assertFalse(conversations.get(0).unread);

      // bob removes the conversation
      conversationDepot.append(new RemoveConversation(bobId, conversations.get(0).conversationId));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);
      conversations = getConversationTimeline.invoke(bobId, 0L, limit);
      assertEquals(0, conversations.size());

      // make sure private/direct statuses cannot be seen by invalid requesters
      assertEquals(0, getStatusesFromPointers.invoke(null, Arrays.asList(new StatusPointer(aliceId, alicePrivateStatusId)), new QueryFilterOptions(FilterContext.Public, true)).results.size());
      assertEquals(0, getStatusesFromPointers.invoke(null, Arrays.asList(new StatusPointer(aliceId, aliceDirectStatusId)), new QueryFilterOptions(FilterContext.Public, true)).results.size());
      assertEquals(0, getStatusesFromPointers.invoke(davidId, Arrays.asList(new StatusPointer(aliceId, alicePrivateStatusId)), new QueryFilterOptions(FilterContext.Public, true)).results.size());
      assertEquals(0, getStatusesFromPointers.invoke(davidId, Arrays.asList(new StatusPointer(aliceId, aliceDirectStatusId)), new QueryFilterOptions(FilterContext.Public, true)).results.size());
    }
  }

  @Test
  public void statusModifiersTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try (InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      coreModule.enableHomeTimelineRefresh = false;
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");

      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot favoriteStatusDepot = ipc.clusterDepot(coreModuleName, "*favoriteStatusDepot");
      Depot bookmarkStatusDepot = ipc.clusterDepot(coreModuleName, "*bookmarkStatusDepot");
      Depot muteStatusDepot = ipc.clusterDepot(coreModuleName, "*muteStatusDepot");
      Depot pinStatusDepot = ipc.clusterDepot(coreModuleName, "*pinStatusDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");

      PState favoriterToStatusPointers = ipc.clusterPState(coreModuleName, "$$favoriterToStatusPointers");
      PState bookmarkerToStatusPointers = ipc.clusterPState(coreModuleName, "$$bookmarkerToStatusPointers");
      PState muterToStatusIds = ipc.clusterPState(coreModuleName, "$$muterToStatusIds");
      PState pinnerToStatusIdsReverse = ipc.clusterPState(coreModuleName, "$$pinnerToStatusIdsReverse");
      PState statusIdToBookmarkers = ipc.clusterPState(coreModuleName, "$$statusIdToBookmarkers");
      PState statusIdToMuters = ipc.clusterPState(coreModuleName, "$$statusIdToMuters");

      PState statusIdToBoosters = ipc.clusterPState(coreModuleName, "$$statusIdToBoosters");
      PState statusIdToFavoriters = ipc.clusterPState(coreModuleName, "$$statusIdToFavoriters");

      QueryTopologyClient<StatusQueryResults> getAccountTimeline = ipc.clusterQuery(coreModuleName, "getAccountTimeline");
      QueryTopologyClient<StatusQueryResults> getHomeTimeline = ipc.clusterQuery(coreModuleName, "getHomeTimeline");

      int coreCount = 0;
      int ts = 0;
      int limit = 5;

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));

      // get user ids
      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));

      // alice makes a new status
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Anyone there?", StatusVisibility.Public), ts+=1)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      // get alice's last status
      StatusResultWithId aliceStatusResultWithId = getAccountTimeline.invoke(aliceId, aliceId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(aliceId, aliceStatusResultWithId.statusId)));
      StatusPointer aliceStatusPointer = new StatusPointer(aliceId, aliceStatusResultWithId.statusId);

      // alice follows bob
      followAndBlockAccountDepot.append(new FollowAccount(aliceId, bobId, ts+=1));

      // bob boosts alice
      statusDepot.append(new BoostStatus(UUID.randomUUID().toString(), bobId, aliceStatusPointer, ts+=1));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);
      assertNotNull(statusIdToBoosters.selectOne(aliceId, Path.key(aliceStatusPointer.statusId, bobId)));

      // alice's status, when viewed by bob, indicates it was boosted
      aliceStatusResultWithId = getAccountTimeline.invoke(bobId, aliceId, 0L, limit, true).results.get(0);
      assertTrue(aliceStatusResultWithId.status.metadata.boosted);
      assertEquals(1, aliceStatusResultWithId.status.metadata.boostCount);

      // alice's status has correct account metadata
      assertEquals(1, aliceStatusResultWithId.status.author.metadata.followeeCount);
      assertEquals(0, aliceStatusResultWithId.status.author.metadata.followerCount);

      // get bob's last status (the boost)
      StatusResultWithId bobStatusResultWithId = getAccountTimeline.invoke(bobId, bobId, 0L, limit, true).results.get(0);
      assertNotNull(accountIdToStatuses.selectOne(Path.key(bobId, bobStatusResultWithId.statusId)));
      Status bobBoostStatus = accountIdToStatuses.selectOne(Path.key(bobId, bobStatusResultWithId.statusId).first());
      assertNotNull(bobBoostStatus);
      assertTrue(bobBoostStatus.content.isSetBoost());
      assertEquals(aliceStatusPointer.statusId, bobBoostStatus.content.getBoost().boosted.statusId);

      // bob's status has correct account metadata
      assertEquals(0, bobStatusResultWithId.status.author.metadata.followeeCount);
      assertEquals(1, bobStatusResultWithId.status.author.metadata.followerCount);
      assertEquals(1, bobStatusResultWithId.status.content.getBoost().status.author.metadata.followeeCount);
      assertEquals(0, bobStatusResultWithId.status.content.getBoost().status.author.metadata.followerCount);

      StatusPointer start = new StatusPointer(-1, -1);

      // bob's boost did not get to alice's timeline because it's a boost of her own post
      List<StatusResultWithId> aliceTimeline = getHomeTimeline.invoke(aliceId, start, limit).results;
      assertEquals(1, aliceTimeline.size());
      assertEquals(aliceStatusPointer.statusId, aliceTimeline.get(0).statusId);

      // bob unboosts alice
      statusDepot.append(new RemoveBoostStatus(bobId, aliceStatusPointer, ts+=1));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);
      assertNull(statusIdToBoosters.selectOne(aliceId, Path.key(aliceStatusPointer.statusId, bobId)));

      // alice's status, when viewed by bob, indicates it was not boosted
      aliceStatusResultWithId = getAccountTimeline.invoke(bobId, aliceId, 0L, limit, true).results.get(0);
      assertFalse(aliceStatusResultWithId.status.metadata.boosted);

      // make sure the boost is gone
      assertEquals(0, getAccountTimeline.invoke(bobId, bobId, 0L, limit, true).results.size());
      assertNull(accountIdToStatuses.selectOne(Path.key(bobId, bobStatusResultWithId.statusId)));

      int otherCount = 0;

      // bob favorites alice's status
      favoriteStatusDepot.append(new FavoriteStatus(bobId, aliceStatusPointer, ts+=1));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "core", otherCount+=1);
      assertNotNull(favoriterToStatusPointers.selectOne(Path.key(bobId, aliceStatusPointer)));
      assertNotNull(statusIdToFavoriters.selectOne(aliceId, Path.key(aliceStatusPointer.statusId, bobId)));

      // alice's status, when viewed by bob, indicates it was favorited
      aliceStatusResultWithId = getAccountTimeline.invoke(bobId, aliceId, 0L, limit, true).results.get(0);
      assertTrue(aliceStatusResultWithId.status.metadata.favorited);
      assertEquals(1, aliceStatusResultWithId.status.metadata.favoriteCount);

      // bob unfavorites alice's status
      favoriteStatusDepot.append(new RemoveFavoriteStatus(bobId, aliceStatusPointer, ts+=1));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "core", otherCount+=1);
      assertNull(favoriterToStatusPointers.selectOne(Path.key(bobId, aliceStatusPointer)));
      assertNull(statusIdToFavoriters.selectOne(aliceId, Path.key(aliceStatusPointer.statusId, bobId)));

      // alice's status, when viewed by bob, indicates it was not favorited
      aliceStatusResultWithId = getAccountTimeline.invoke(bobId, aliceId, 0L, limit, true).results.get(0);
      assertFalse(aliceStatusResultWithId.status.metadata.favorited);

      // bob bookmarks alice's status
      bookmarkStatusDepot.append(new BookmarkStatus(bobId, aliceStatusPointer, ts+=1));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "core", otherCount+=1);
      assertNotNull(bookmarkerToStatusPointers.selectOne(Path.key(bobId, aliceStatusPointer)));
      assertTrue((boolean) statusIdToBookmarkers.selectOne(aliceId, Path.key(aliceStatusPointer.statusId).view(Ops.CONTAINS, bobId)));

      // alice's status, when viewed by bob, indicates it was bookmarked
      aliceStatusResultWithId = getAccountTimeline.invoke(bobId, aliceId, 0L, limit, true).results.get(0);
      assertTrue(aliceStatusResultWithId.status.metadata.bookmarked);

      // bob unbookmarks alice's status
      bookmarkStatusDepot.append(new RemoveBookmarkStatus(bobId, aliceStatusPointer, ts+=1));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "core", otherCount+=1);
      assertNull(bookmarkerToStatusPointers.selectOne(Path.key(bobId, aliceStatusPointer)));
      assertFalse((boolean) statusIdToBookmarkers.selectOne(aliceId, Path.key(aliceStatusPointer.statusId).view(Ops.CONTAINS, bobId)));

      // alice's status, when viewed by bob, indicates it was not bookmarked
      aliceStatusResultWithId = getAccountTimeline.invoke(bobId, aliceId, 0L, limit, true).results.get(0);
      assertFalse(aliceStatusResultWithId.status.metadata.bookmarked);

      // bob mutes alice's status
      muteStatusDepot.append(new MuteStatus(bobId, aliceStatusPointer, ts+=1));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "core", otherCount+=1);
      assertNotNull(muterToStatusIds.selectOne(Path.key(bobId, aliceStatusPointer.statusId)));
      assertTrue((boolean) statusIdToMuters.selectOne(aliceId, Path.key(aliceStatusPointer.statusId).view(Ops.CONTAINS, bobId)));


      // alice's status, when viewed by bob, indicates it was muted
      aliceStatusResultWithId = getAccountTimeline.invoke(bobId, aliceId, 0L, limit, true).results.get(0);
      assertTrue(aliceStatusResultWithId.status.metadata.muted);

      // bob unmutes alice's status
      muteStatusDepot.append(new RemoveMuteStatus(bobId, aliceStatusPointer, ts+=1));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "core", otherCount+=1);
      assertNull(muterToStatusIds.selectOne(Path.key(bobId, aliceStatusPointer.statusId)));
      assertFalse((boolean) statusIdToMuters.selectOne(aliceId, Path.key(aliceStatusPointer.statusId).view(Ops.CONTAINS, bobId)));

      // alice's status, when viewed by bob, indicates it was not muted
      aliceStatusResultWithId = getAccountTimeline.invoke(bobId, aliceId, 0L, limit, true).results.get(0);
      assertFalse(aliceStatusResultWithId.status.metadata.muted);

      // alice pins her status
      pinStatusDepot.append(new PinStatus(aliceId, aliceStatusPointer.statusId, ts+=1));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "core", otherCount+=1);
      assertNotNull(pinnerToStatusIdsReverse.selectOne(Path.key(aliceId, aliceStatusPointer.statusId)));

      // alice's status indicates it was pinned
      aliceStatusResultWithId = getAccountTimeline.invoke(aliceId, aliceId, 0L, limit, true).results.get(0);
      assertTrue(aliceStatusResultWithId.status.metadata.pinned);

      // alice unpins her status
      pinStatusDepot.append(new RemovePinStatus(aliceId, aliceStatusPointer.statusId, ts+=1));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "core", otherCount+=1);
      assertNull(pinnerToStatusIdsReverse.selectOne(Path.key(aliceId, aliceStatusPointer.statusId)));

      // alice's status indicates it was not pinned
      aliceStatusResultWithId = getAccountTimeline.invoke(aliceId, aliceId, 0L, limit, true).results.get(0);
      assertFalse(aliceStatusResultWithId.status.metadata.pinned);
    }
  }

  @Test
  public void accountUpdatesTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot accountEditDepot = ipc.clusterDepot(coreModuleName, "*accountEditDepot");
      PState accountIdToAccount = ipc.clusterPState(coreModuleName, "$$accountIdToAccount");
      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");

      int ts = 0;

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), ts+=1));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-UK", UUID.randomUUID().toString(), "", AccountContent.local(new LocalAccount("")), ts+=1));

      Long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      Long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));

      assertEquals("alice@foo.com", accountIdToAccount.selectOne(Path.key(aliceId).customNav(new TField("email"))));
      assertEquals("en-US", accountIdToAccount.selectOne(Path.key(aliceId).customNav(new TField("locale"))));
      assertNull(accountIdToAccount.selectOne(Path.key(aliceId).customNav(new TField("displayName"))));

      assertEquals("bob@foo.com", accountIdToAccount.selectOne(Path.key(bobId).customNav(new TField("email"))));
      assertEquals("en-UK", accountIdToAccount.selectOne(Path.key(bobId).customNav(new TField("locale"))));
      assertNull(accountIdToAccount.selectOne(Path.key(bobId).customNav(new TField("displayName"))));

      accountEditDepot.append(new EditAccount(aliceId, Arrays.asList(EditAccountField.displayName("Alice W."), EditAccountField.locale("SP")), ts+=1));
      accountEditDepot.append(new EditAccount(bobId, Arrays.asList(EditAccountField.displayName("Bob Z."), EditAccountField.locale("FR")), ts+=1));

      assertEquals("Alice W.", accountIdToAccount.selectOne(Path.key(aliceId).customNav(new TField("displayName"))));
      assertEquals("SP", accountIdToAccount.selectOne(Path.key(aliceId).customNav(new TField("locale"))));

      assertEquals("Bob Z.", accountIdToAccount.selectOne(Path.key(bobId).customNav(new TField("displayName"))));
      assertEquals("FR", accountIdToAccount.selectOne(Path.key(bobId).customNav(new TField("locale"))));
    }
  }

  @Test
  public void pollsTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot pollVoteDepot = ipc.clusterDepot(coreModuleName, "*pollVoteDepot");
      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState pollVotes = ipc.clusterPState(coreModuleName, "$$pollVotes");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
      QueryTopologyClient<StatusQueryResults> getStatuses = ipc.clusterQuery(coreModuleName, "getStatusesFromPointers");

      int coreCount = 0;

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("david", "david@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));

      Long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      Long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      Long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      Long davidId = nameToUser.selectOne(Path.key("david", "accountId"));

      NormalStatusContent c = new NormalStatusContent("aaa", StatusVisibility.Public);
      c.setPollContent(new PollContent(Arrays.asList("aaa", "bbb", "ccc"), 1, false));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, StatusContent.normal(c), 0)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      long alice1 = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());
      StatusPointer alicePoll1 = new StatusPointer(aliceId, alice1);

      statusDepot.append(new BoostStatus(UUID.randomUUID().toString(), bobId, alicePoll1, 0));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      long bob1 = accountIdToStatuses.selectOne(Path.key(bobId).first().first());
      StatusPointer bobPoll1 = new StatusPointer(bobId, bob1);

      ReplyStatusContent r = new ReplyStatusContent("rrr", StatusVisibility.Public, alicePoll1);
      r.setPollContent(new PollContent(Arrays.asList("o1", "o2"), 1, false));

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, StatusContent.reply(r), 0)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=1);

      long alice2 = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());
      StatusPointer alicePoll2 = new StatusPointer(aliceId, alice2);

      pollVoteDepot.append(new PollVote(aliceId, alicePoll1, asSet(0, 2), 0));

      assertEquals(asList(asList(aliceId, asSet(0, 2))), pollVotes.select(aliceId, Path.key(alice1, "allVoters").all()));

      assertEquals(asList(asList(0, asList(aliceId)), asList(2, asList(aliceId))), pollVotes.select(aliceId, Path.key(alice1, "choices").all().collectOne(Path.first()).last().subselect(Path.all())));
      StatusResultWithId sqr = getStatuses.invoke(null, Arrays.asList(alicePoll1), new QueryFilterOptions(FilterContext.Public, true)).results.get(0);
      PollInfo pi = sqr.getStatus().getPollInfo();
      assertNotNull(pi);
      assertTrue(pi.getOwnVotes().isEmpty());
      assertEquals(1, pi.getTotalVoters());
      assertEquals(asMap(0, 1, 2, 1), pi.getVoteCounts());

      pollVoteDepot.append(new PollVote(bobId, alicePoll1, asSet(2), 0));
      sqr = getStatuses.invoke(bobId, Arrays.asList(alicePoll1), new QueryFilterOptions(FilterContext.Public, true)).results.get(0);
      pi = sqr.getStatus().getPollInfo();
      assertNotNull(pi);
      assertEquals(asSet(2), pi.getOwnVotes());
      assertEquals(2, pi.getTotalVoters());
      assertEquals(asMap(0, 1, 2, 2), pi.getVoteCounts());

      sqr = getStatuses.invoke(bobId, Arrays.asList(alicePoll2), new QueryFilterOptions(FilterContext.Public, true)).results.get(0);
      pi = sqr.getStatus().getPollInfo();
      assertNotNull(pi);
      assertEquals(asSet(), pi.getOwnVotes());
      assertEquals(0, pi.getTotalVoters());
      assertEquals(asMap(), pi.getVoteCounts());

      pollVoteDepot.append(new PollVote(aliceId, alicePoll2, asSet(0), 0));
      pollVoteDepot.append(new PollVote(bobId, alicePoll2, asSet(0), 0));
      pollVoteDepot.append(new PollVote(charlieId, alicePoll2, asSet(0, 1), 0));
      pollVoteDepot.append(new PollVote(davidId, alicePoll2, asSet(1), 0));

      sqr = getStatuses.invoke(bobId, Arrays.asList(alicePoll2), new QueryFilterOptions(FilterContext.Public, true)).results.get(0);
      pi = sqr.getStatus().getPollInfo();
      assertNotNull(pi);
      assertEquals(asSet(0), pi.getOwnVotes());
      assertEquals(4, pi.getTotalVoters());
      assertEquals(asMap(0, 3, 1, 2), pi.getVoteCounts());

      // verify poll results info retrieved for boosts of statuses with polls
      sqr = getStatuses.invoke(aliceId, Arrays.asList(bobPoll1), new QueryFilterOptions(FilterContext.Public, true)).results.get(0);
      pi = sqr.getStatus().getPollInfo();
      assertNotNull(pi);
      assertEquals(asSet(0, 2), pi.getOwnVotes());
      assertEquals(2, pi.getTotalVoters());
      assertEquals(asMap(0, 1, 2, 2), pi.getVoteCounts());


      c = new NormalStatusContent("a new status", StatusVisibility.Public);
      // same options
      c.setPollContent(new PollContent(Arrays.asList("aaa", "bbb", "ccc"), 1, false));
      statusDepot.append(new EditStatus(alice1, new Status(aliceId, StatusContent.normal(c), 0)));
      // poll votes are the same
      sqr = getStatuses.invoke(bobId, Arrays.asList(alicePoll1), new QueryFilterOptions(FilterContext.Public, true)).results.get(0);
      pi = sqr.getStatus().getPollInfo();
      assertNotNull(pi);
      assertEquals(asSet(2), pi.getOwnVotes());
      assertEquals(2, pi.getTotalVoters());
      assertEquals(asMap(0, 1, 2, 2), pi.getVoteCounts());

      c = new NormalStatusContent("a new status", StatusVisibility.Public);
      // change options and verify this causes votes to be reset
      c.setPollContent(new PollContent(Arrays.asList("aaa", "bbb"), 1, false));
      statusDepot.append(new EditStatus(alice1, new Status(aliceId, StatusContent.normal(c), 0)));
      sqr = getStatuses.invoke(bobId, Arrays.asList(alicePoll1), new QueryFilterOptions(FilterContext.Public, true)).results.get(0);
      pi = sqr.getStatus().getPollInfo();
      assertNotNull(pi);
      assertEquals(asSet(), pi.getOwnVotes());
      assertEquals(0, pi.getTotalVoters());
      assertEquals(asMap(), pi.getVoteCounts());

      // verify new votes go through
      pollVoteDepot.append(new PollVote(bobId, alicePoll1, asSet(0, 1), 0));
      sqr = getStatuses.invoke(bobId, Arrays.asList(alicePoll1), new QueryFilterOptions(FilterContext.Public, true)).results.get(0);
      pi = sqr.getStatus().getPollInfo();
      assertNotNull(pi);
      assertEquals(asSet(0, 1), pi.getOwnVotes());
      assertEquals(1, pi.getTotalVoters());
      assertEquals(asMap(0, 1, 1, 1), pi.getVoteCounts());
    }
  }

  @Test
  public void multipleFanoutIterationsTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      String coreModuleName = coreModule.getClass().getName();
      coreModule.singlePartitionFanoutLimit = 2;
      coreModule.rangeQueryLimit = 1;
      coreModule.enableHomeTimelineRefresh = false;
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");

      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");

      QueryTopologyClient<StatusQueryResults> getHomeTimeline = ipc.clusterQuery(coreModuleName, "getHomeTimeline");

      // add new accounts
      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("emily", "emily@foo.com", "hash5", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("frank", "frank@foo.com", "hash6", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("zzz", "zzz@foo.com", "hash7", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));

      // get user ids
      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));
      long emilyId = nameToUser.selectOne(Path.key("emily", "accountId"));
      long frankId = nameToUser.selectOne(Path.key("frank", "accountId"));
      long zzzId = nameToUser.selectOne(Path.key("zzz", "accountId"));

      followAndBlockAccountDepot.append(new FollowAccount(bobId, aliceId, 0));
      followAndBlockAccountDepot.append(new FollowAccount(charlieId, aliceId, 0));
      followAndBlockAccountDepot.append(new FollowAccount(davidId, aliceId, 0));
      followAndBlockAccountDepot.append(new FollowAccount(emilyId, aliceId, 0));
      followAndBlockAccountDepot.append(new FollowAccount(frankId, aliceId, 0));

      // alice posts a status and it appears on her own home timeline
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 1);

      // execute four more iterations to completion to ensure fanout completed
      //  - fanout uses the range query limit, not the single partition limit
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(zzzId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 2);
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(zzzId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 3);
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(zzzId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 4);
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(zzzId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 5);

      StatusPointer start = new StatusPointer(-1, -1);

      assertEquals(1, accountIdToStatuses.select(Path.key(aliceId).mapKeys()).size());
      assertEquals(1, getHomeTimeline.invoke(aliceId, start, 5).results.size());
      assertEquals(1, getHomeTimeline.invoke(bobId, start, 5).results.size());
      assertEquals(1, getHomeTimeline.invoke(charlieId, start, 5).results.size());
      assertEquals(1, getHomeTimeline.invoke(davidId, start, 5).results.size());
      assertEquals(1, getHomeTimeline.invoke(emilyId, start, 5).results.size());
      assertEquals(1, getHomeTimeline.invoke(frankId, start, 5).results.size());
    }
  }

  @Test
  public void getAccountsWithSuppressionTest(TestInfo testInfo) throws Exception {
  	List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");
      Depot muteAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*muteAccountDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");

      QueryTopologyClient<List> getAccountsFromAccountIds = ipc.clusterQuery(coreModuleName, "getAccountsFromAccountIds");

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("emily", "emily.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));

      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));
      long emilyId = nameToUser.selectOne(Path.key("emily", "accountId"));

      followAndBlockAccountDepot.append(new BlockAccount(aliceId, bobId, 0));
      muteAccountDepot.append(new MuteAccount(aliceId, davidId, new MuteAccountOptions(true), 0));

      List<AccountWithId> res = getAccountsFromAccountIds.invoke(aliceId, Arrays.asList(bobId, charlieId, davidId, emilyId));
      assertEquals(2, res.size());
      Set<Long> accountIds = new HashSet();
      for(AccountWithId a: res) {
        accountIds.add(a.getAccountId());
      }
      Set<Long> expected = new HashSet();
      expected.add(charlieId);
      expected.add(emilyId);
      assertEquals(expected, accountIds);
    }
  }

  @Test
  public void scheduledStatusesTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers);
        Closeable simTime = TopologyUtils.startSimTime()) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot scheduledStatusDepot = ipc.clusterDepot(coreModuleName, "*scheduledStatusDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
      PState accountIdToScheduledStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToScheduledStatuses");

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));

      scheduledStatusDepot.append(new AddScheduledStatus(UUID.randomUUID().toString(),
                                                         new Status(aliceId,
                                                                    normalStatusContent("ABC", StatusVisibility.Public),
                                                                    0),
                                                         10));

      TopologyUtils.advanceSimTime(9);
      Thread.sleep(200);

      attainStableConditionPred(() -> accountIdToStatuses.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 0);

      TopologyUtils.advanceSimTime(1);
      attainStableConditionPred(() -> accountIdToStatuses.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 1);

      Status s = accountIdToStatuses.selectOne(Path.key(aliceId).first().last().first());
      assertEquals(10L, s.getTimestamp());
      assertEquals(aliceId, s.getAuthorId());
      assertTrue(s.getContent().isSetNormal());
      attainConditionPred(() -> accountIdToScheduledStatuses.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 0);

      StatusContent content = normalStatusContent("ABC", StatusVisibility.Public);
      content.getNormal().setPollContent(new PollContent(Arrays.asList("a", "b"), 100, false));

      scheduledStatusDepot.append(new AddScheduledStatus(UUID.randomUUID().toString(),
                                                         new Status(aliceId,
                                                                    content,
                                                                    2),
                                                         16));
      long id = accountIdToScheduledStatuses.selectOne(Path.key(aliceId).first().first());


      scheduledStatusDepot.append(new EditScheduledStatusPublishTime(aliceId, id, 21, 0));

      TopologyUtils.advanceSimTime(10);
      Thread.sleep(200);
      attainStableConditionPred(() -> accountIdToStatuses.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 1);

      TopologyUtils.advanceSimTime(2);
      attainStableConditionPred(() -> accountIdToStatuses.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 2);

      s = accountIdToStatuses.selectOne(Path.key(aliceId).first().last().first());
      assertEquals(22L, s.getTimestamp());
      assertEquals(aliceId, s.getAuthorId());
      assertTrue(s.getContent().isSetNormal());
      PollContent pc = s.getContent().getNormal().getPollContent();
      assertNotNull(pc);
      assertEquals(120L, pc.getExpirationMillis());

      // time is 22L

      scheduledStatusDepot.append(new AddScheduledStatus(UUID.randomUUID().toString(),
                                                         new Status(aliceId,
                                                                    normalStatusContent("AAA", StatusVisibility.Public),
                                                                    0),
                                                         30));
      id = accountIdToScheduledStatuses.selectOne(Path.key(aliceId).first().first());
      scheduledStatusDepot.append(new RemoveStatus(aliceId, id, 0));
      long aliceStatus1 = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());
      StatusContent sc = replyStatusContent("R", StatusVisibility.Public, aliceId, aliceStatus1);
      sc.getReply().setPollContent(new PollContent(Arrays.asList("a", "b"), 200, false));
      scheduledStatusDepot.append(new AddScheduledStatus(UUID.randomUUID().toString(),
                                                         new Status(aliceId,
                                                                    sc,
                                                                    0),
                                                         30));
      sc.getReply().setText("RRR");
      id = accountIdToScheduledStatuses.selectOne(Path.key(aliceId).first().first());
      scheduledStatusDepot.append(new EditStatus(id, new Status(aliceId, sc, 0)));


      TopologyUtils.advanceSimTime(10);
      Thread.sleep(200);
      attainStableConditionPred(() -> accountIdToStatuses.selectOne(Path.key(aliceId).view(Ops.SIZE)), (Integer i) -> i == 3);
      assertEquals(0, (Integer) accountIdToScheduledStatuses.selectOne(Path.key(aliceId).view(Ops.SIZE)));
      s = accountIdToStatuses.selectOne(Path.key(aliceId).first().last().first());
      assertEquals(32L, s.getTimestamp());
      assertEquals(aliceId, s.getAuthorId());
      assertTrue(s.getContent().isSetReply());
      pc = s.getContent().getReply().getPollContent();
      assertNotNull(pc);
      assertEquals(232L, pc.getExpirationMillis());
    }
  }

  @Test
  public void memoryTimelineTest(TestInfo testInfo) {
    Timeline tl = new Timeline(3, false);
    tl.addItem(0, 10);
    tl.addItem(1, 11);

    StatusPointer start = new StatusPointer(-1, -1);

    StatusPointer sp1 = new StatusPointer(0, 10);
    StatusPointer sp2 = new StatusPointer(1, 11);

    List ret = tl.readTimelineFrom(start, null, 1);
    assertEquals(1, ret.size());
    assertEquals(sp2, ret.get(0));

    ret = tl.readTimelineFrom(start, null, 3);
    assertEquals(Arrays.asList(sp2, sp1), ret);

    // verify it's exclusive
    ret = tl.readTimelineFrom(sp2, null, 2);
    assertEquals(Arrays.asList(sp1), ret);


    tl.addItem(2, 12);
    tl.addItem(3, 13);

    StatusPointer sp3 = new StatusPointer(2, 12);
    StatusPointer sp4 = new StatusPointer(3, 13);

    ret = tl.readTimelineFrom(start, null, 2);
    assertEquals(Arrays.asList(sp4, sp3), ret);

    ret = tl.readTimelineFrom(start, null, 3);
    assertEquals(Arrays.asList(sp4, sp3, sp2), ret);

    tl.addItem(4, 14);
    StatusPointer sp5 = new StatusPointer(4, 14);

    ret = tl.readTimelineFrom(start, null, 10);
    assertEquals(Arrays.asList(sp5, sp4, sp3), ret);
    ret = tl.readTimelineFrom(start, null, 1);
    assertEquals(Arrays.asList(sp5), ret);
    ret = tl.readTimelineFrom(sp5, null, 2);
    assertEquals(Arrays.asList(sp4, sp3), ret);
    ret = tl.readTimelineFrom(sp5, null, 10);
    assertEquals(Arrays.asList(sp4, sp3), ret);

    tl.addItem(5, 15);
    StatusPointer sp6 = new StatusPointer(5, 15);

    ret = tl.readTimelineFrom(start, null, 2);
    assertEquals(Arrays.asList(sp6, sp5), ret);
    ret = tl.readTimelineFrom(start, null, 3);
    assertEquals(Arrays.asList(sp6, sp5, sp4), ret);
    ret = tl.readTimelineFrom(start, null, 100);
    assertEquals(Arrays.asList(sp6, sp5, sp4), ret);

    tl.addItem(6, 16);
    StatusPointer sp7 = new StatusPointer(6, 16);

    ret = tl.readTimelineFrom(start, null, 2);
    assertEquals(Arrays.asList(sp7, sp6), ret);
    ret = tl.readTimelineFrom(start, null, 3);
    assertEquals(Arrays.asList(sp7, sp6, sp5), ret);
    ret = tl.readTimelineFrom(start, null, 100);
    assertEquals(Arrays.asList(sp7, sp6, sp5), ret);

    // verify reads from start when status pointer doesn't match
    ret = tl.readTimelineFrom(new StatusPointer(100, 101), null, 2);
    assertEquals(Arrays.asList(sp7, sp6), ret);

    // verify reads from start when status pointer doesn't match
    ret = tl.readTimelineFrom(sp6, null, 2);
    assertEquals(Arrays.asList(sp5), ret);
    ret = tl.readTimelineFrom(sp7, null, 2);
    assertEquals(Arrays.asList(sp6, sp5), ret);
  }

  @Test
  public void memoryTimelineUntilTest(TestInfo testInfo) {
    Timeline tl = new Timeline(10, false);
    tl.addItem(0, 10);
    tl.addItem(1, 11);
    tl.addItem(2, 12);
    tl.addItem(3, 13);
    tl.addItem(4, 14);
    tl.addItem(5, 15);

    StatusPointer sp1 = new StatusPointer(0, 10);
    StatusPointer sp2 = new StatusPointer(1, 11);
    StatusPointer sp3 = new StatusPointer(2, 12);
    StatusPointer sp4 = new StatusPointer(3, 13);
    StatusPointer sp5 = new StatusPointer(4, 14);
    StatusPointer sp6 = new StatusPointer(5, 15);

    StatusPointer start = new StatusPointer(-1, -1);

    List res = tl.readTimelineFrom(start, new StatusPointer(2, 12), 10);
    assertEquals(Arrays.asList(sp6, sp5, sp4), res);

    res = tl.readTimelineFrom(start, new StatusPointer(2, 12), 1);
    assertEquals(Arrays.asList(sp6), res);

    res = tl.readTimelineFrom(new StatusPointer(5, 15), new StatusPointer(1, 11), 10);
    assertEquals(Arrays.asList(sp5, sp4, sp3), res);

    res = tl.readTimelineFrom(new StatusPointer(5, 15), new StatusPointer(1, 11), 2);
    assertEquals(Arrays.asList(sp5, sp4), res);
  }

  private static List<StatusPointer> extractPointers(StatusQueryResults sqr) {
    List pointers = new ArrayList();
    for(StatusResultWithId s: sqr.results) {
      pointers.add(new StatusPointer(s.status.author.accountId, s.statusId));
    }
    return pointers;
  }

  @Test
  public void refreshTimelineTest(TestInfo testInfo) throws Exception {
  List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      coreModule.timelineMaxAmount = 6;
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");

      QueryTopologyClient<StatusQueryResults> getHomeTimeline = ipc.clusterQuery(coreModuleName, "getHomeTimeline");


      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("david", "david@foo.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("emily", "emily.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));

      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));
      long davidId = nameToUser.selectOne(Path.key("david", "accountId"));
      long emilyId = nameToUser.selectOne(Path.key("emily", "accountId"));

      followAndBlockAccountDepot.append(new FollowAccount(aliceId, bobId, 0));
      followAndBlockAccountDepot.append(new FollowAccount(emilyId, davidId, 0));

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      long bob1 = accountIdToStatuses.selectOne(Path.key(bobId).first().first());
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 1);

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 100)));
      long bob2 = accountIdToStatuses.selectOne(Path.key(bobId).first().first());
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 2);

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 10)));
      long charlie1 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 10)));
      long charlie2 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 10)));
      long charlie3 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 10)));
      long charlie4 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 10)));
      long charlie5 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 50)));
      long charlie6 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 80)));
      long charlie7 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 120)));
      long charlie8 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 130)));
      long charlie9 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      long david1 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      long david2 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      long david3 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      long david4 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      long david5 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      long david6 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      long david7 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 20)));
      long david8 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 90)));
      long david9 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 150)));
      long david10 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(emilyId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 200)));
      long emily1 = accountIdToStatuses.selectOne(Path.key(emilyId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(emilyId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 210)));
      long emily2 = accountIdToStatuses.selectOne(Path.key(emilyId).first().first());

      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 23);

      followAndBlockAccountDepot.append(new FollowAccount(aliceId, charlieId, 0));
      followAndBlockAccountDepot.append(new FollowAccount(aliceId, davidId, 0));

      StatusPointer start = new StatusPointer(-1, -1);


      StatusQueryResults sqr = getHomeTimeline.invoke(aliceId, start, 10);
      assertTrue(sqr.refreshed);
      assertEquals(Arrays.asList(new StatusPointer(davidId, david10),
                                 new StatusPointer(charlieId, charlie9),
                                 new StatusPointer(charlieId, charlie8),
                                 new StatusPointer(bobId, bob2),
                                 new StatusPointer(davidId, david9),
                                 new StatusPointer(charlieId, charlie7)),
                   extractPointers(sqr));


      // verify subsequent depot append after refresh goes to start even if timestamp is lower
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(davidId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      long david11 = accountIdToStatuses.selectOne(Path.key(davidId).first().first());

      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 24);

      sqr = getHomeTimeline.invoke(aliceId, start, 10);
      assertFalse(sqr.refreshed);
      assertEquals(Arrays.asList(new StatusPointer(davidId, david11),
                                 new StatusPointer(davidId, david10),
                                 new StatusPointer(charlieId, charlie9),
                                 new StatusPointer(charlieId, charlie8),
                                 new StatusPointer(bobId, bob2),
                                 new StatusPointer(davidId, david9)),
                   extractPointers(sqr));


      accountDepot.append(new Account("frank", "frank.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("george", "george.com", "hash4", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));

      long frankId = nameToUser.selectOne(Path.key("frank", "accountId"));
      long georgeId = nameToUser.selectOne(Path.key("george", "accountId"));

      followAndBlockAccountDepot.append(new FollowAccount(frankId, emilyId, 0));
      followAndBlockAccountDepot.append(new FollowAccount(georgeId, bobId, 0));
      followAndBlockAccountDepot.append(new FollowAccount(georgeId, emilyId, 0));

      sqr = getHomeTimeline.invoke(frankId, start, 10);
      assertTrue(sqr.refreshed);
      assertEquals(Arrays.asList(new StatusPointer(emilyId, emily2),
                                 new StatusPointer(emilyId, emily1)),
                   extractPointers(sqr));
      assertFalse(getHomeTimeline.invoke(frankId, start, 10).refreshed);


      sqr = getHomeTimeline.invoke(georgeId, start, 10);
      assertTrue(sqr.refreshed);
      assertEquals(Arrays.asList(new StatusPointer(emilyId, emily2),
                                 new StatusPointer(emilyId, emily1),
                                 new StatusPointer(bobId, bob2),
                                 new StatusPointer(bobId, bob1)),
                   extractPointers(sqr));
    }
  }

  @Test
  public void refreshTimelineWithReactionsTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      coreModule.timelineMaxAmount = 6;
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");

      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");

      QueryTopologyClient<StatusQueryResults> getHomeTimeline = ipc.clusterQuery(coreModuleName, "getHomeTimeline");

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), 0));

      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));
      long bob1 = accountIdToStatuses.selectOne(Path.key(bobId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, TestHelpers.replyStatusContent("Hello", StatusVisibility.Public, bobId, bob1), 2)));
      long bob2 = accountIdToStatuses.selectOne(Path.key(bobId).first().first());
      statusDepot.append(new BoostStatus(UUID.randomUUID().toString(), bobId, new StatusPointer(bobId, bob1), 4));
      long bob3 = accountIdToStatuses.selectOne(Path.key(bobId).first().first());

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 1)));
      long charlie1 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, TestHelpers.replyStatusContent("Hello", StatusVisibility.Public, bobId, bob1), 3)));
      long charlie2 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());
      statusDepot.append(new BoostStatus(UUID.randomUUID().toString(), charlieId, new StatusPointer(bobId, bob1), 5));
      long charlie3 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());

      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", 6);

      followAndBlockAccountDepot.append(new FollowAccount(aliceId, bobId, 0));
      followAndBlockAccountDepot.append((new FollowAccount(aliceId, charlieId, 0)).setShowBoosts(false));

      StatusPointer start = new StatusPointer(-1, -1);
      StatusQueryResults sqr = getHomeTimeline.invoke(aliceId, start, 10);
      assertTrue(sqr.refreshed);

      assertEquals(Arrays.asList(new StatusPointer(bobId, bob3),
                                 new StatusPointer(charlieId, charlie1),
                                 new StatusPointer(bobId, bob1)),
                   extractPointers(sqr));
    }
  }

  @Test
  public void largeUserTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      relationshipsModule.newTaskCutoffLimit = 2;
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      ipc.launchModule(relationshipsModule, new LaunchConfig(4, 2).numWorkers(2));

      Core coreModule = new Core();
      coreModule.rangeQueryLimit = 1;
      coreModule.enableHomeTimelineRefresh = false;
      String coreModuleName = coreModule.getClass().getName();
      ipc.launchModule(coreModule, new LaunchConfig(2, 2).numWorkers(2));

      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");

      // need to use this one since it doesn't fetch account info
      QueryTopologyClient<Map<Integer, List<StatusPointer>>> getHomeTimelinesUntil = ipc.clusterQuery(coreModuleName, "getHomeTimelinesUntil");

      long aliceId = 0;
      List<Long> followerIds = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L);

      for(Long followerId: followerIds) followAndBlockAccountDepot.append(new FollowAccount(followerId, aliceId, 0));

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hello", StatusVisibility.Public), 0)));

      for(long followerId: followerIds) {
        attainStableConditionPred(() -> getHomeTimelinesUntil.invoke(Arrays.asList(Arrays.asList(followerId, new StatusPointer(-1, -1))), 3).get(0), (List l) -> l.size() == 1);
      }
    }
  }
}
