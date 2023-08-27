package com.rpl.mastodon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.rpl.mastodon.data.*;
import com.rpl.mastodon.modules.*;
import com.rpl.mastodon.navs.TField;
import com.rpl.mastodon.serialization.MastodonSerialization;
import com.rpl.rama.*;
import com.rpl.rama.ops.*;
import com.rpl.rama.RamaModule.*;
import com.rpl.rama.helpers.TopologyUtils;
import com.rpl.rama.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static com.rpl.mastodon.TestHelpers.*;

public class RelationshipsTest {
    @Test
    public void relationshipsTest(TestInfo testInfo) throws Exception {
        List<Class> sers = new ArrayList<>();
        sers.add(MastodonSerialization.class);
        try (InProcessCluster ipc = InProcessCluster.create(sers)) {
            Relationships relationshipsModule = new Relationships();
            relationshipsModule.relationshipCountLimit = 4;
            String relationshipsModuleName = relationshipsModule.getClass().getName();
            TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

            Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");
            Depot muteAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*muteAccountDepot");
            Depot featureAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*featureAccountDepot");
            Depot followHashtagDepot = ipc.clusterDepot(relationshipsModuleName, "*followHashtagDepot");

            PState followerToFollowees = ipc.clusterPState(relationshipsModuleName, "$$followerToFollowees");
            PState followerToFolloweesById = ipc.clusterPState(relationshipsModuleName, "$$followerToFolloweesById");
            PState followeeToFollowers = ipc.clusterPState(relationshipsModuleName, "$$followeeToFollowers");

            PState accountIdToSuppressions = ipc.clusterPState(relationshipsModuleName, "$$accountIdToSuppressions");

            PState featurerToFeaturees = ipc.clusterPState(relationshipsModuleName, "$$featurerToFeaturees");

            PState hashtagToFollowers = ipc.clusterPState(relationshipsModuleName, "$$hashtagToFollowers");
            PState accountIdToFollowRequests = ipc.clusterPState(relationshipsModuleName, "$$accountIdToFollowRequests");


            QueryTopologyClient<List> getFamiliarFollowers = ipc.clusterQuery(relationshipsModuleName, "getFamiliarFollowers");
            QueryTopologyClient<List> getAccountRelationship = ipc.clusterQuery(relationshipsModuleName, "getAccountRelationship");

            long aliceId = 1;
            long bobId = 21; // hashes to different task than aliceId for 2 or 4 tasks
            long charlieId = 3;
            long davidId = 4;
            long ericId = 5;
            long fredId = 6;

            // bob follows alice
            followAndBlockAccountDepot.append(new FollowAccount(bobId, aliceId, 0));
            assertNotNull(followerToFollowees.selectOne(Path.key(bobId, aliceId)));
            assertNotNull(followeeToFollowers.selectOne(Path.key(aliceId, bobId)));

            // get relationship
            AccountRelationshipQueryResult bobAliceRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(bobId, aliceId);
            assertTrue(bobAliceRelationship.following);
            AccountRelationshipQueryResult aliceBobRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(aliceId, bobId);
            assertTrue(aliceBobRelationship.followedBy);

            // bob unfollows alice
            followAndBlockAccountDepot.append(new RemoveFollowAccount(bobId, aliceId, 0));
            assertNull(followerToFollowees.selectOne(Path.key(bobId, aliceId)));
            assertNull(followeeToFollowers.selectOne(Path.key(aliceId, bobId)));

            // get relationship
            bobAliceRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(bobId, aliceId);
            assertFalse(bobAliceRelationship.following);
            aliceBobRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(aliceId, bobId);
            assertFalse(aliceBobRelationship.followedBy);

            // get relationship
            bobAliceRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(bobId, aliceId);
            assertFalse(bobAliceRelationship.following);
            aliceBobRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(aliceId, bobId);
            assertFalse(aliceBobRelationship.followedBy);

            // bob follows alice
            followAndBlockAccountDepot.append(new FollowAccount(bobId, aliceId, 0));
            assertNotNull(followerToFollowees.selectOne(Path.key(bobId, aliceId)));
            assertNotNull(followeeToFollowers.selectOne(Path.key(aliceId, bobId)));

            // bob blocks alice
            followAndBlockAccountDepot.append(new BlockAccount(bobId, aliceId, 0));
            assertTrue(!accountIdToSuppressions.select(Path.key(bobId, "blocked").setElem(aliceId)).isEmpty());

            // get relationship
            bobAliceRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(bobId, aliceId);
            assertTrue(bobAliceRelationship.blocking);
            aliceBobRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(aliceId, bobId);
            assertTrue(aliceBobRelationship.blockedBy);

            // bob is no longer following alice
            assertNull(followerToFollowees.selectOne(Path.key(bobId, aliceId)));
            assertNull(followeeToFollowers.selectOne(Path.key(aliceId, bobId)));

            // bob unblocks alice
            followAndBlockAccountDepot.append(new RemoveBlockAccount(bobId, aliceId, 0));
            assertTrue(accountIdToSuppressions.select(Path.key(bobId, "blocked").setElem(aliceId)).isEmpty());

            // bob requests to follow alice
            // n.b. follow requests use `accountId` and
            // `requesterId` due to the partitioning scheme, which is
            // the reverse of other following data structures which use
            // `accountId` and `targetId` -- `accountId` for a follow request
            // corresponds to `targetId` for a follow
            followAndBlockAccountDepot.append(new FollowLockedAccount(aliceId, bobId, 0));
            assertNotNull(accountIdToFollowRequests.select(Path.key(aliceId, bobId)));
            bobAliceRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(bobId, aliceId);
            assertTrue(bobAliceRelationship.requested);
            assertFalse(bobAliceRelationship.following);

            // unfollowing cancels the request
            followAndBlockAccountDepot.append(new RemoveFollowAccount(bobId, aliceId, 0));
            bobAliceRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(bobId, aliceId);
            assertFalse(bobAliceRelationship.requested);

            // request again and alice rejects
            followAndBlockAccountDepot.append(new FollowLockedAccount(aliceId, bobId, 0));
            followAndBlockAccountDepot.append(new RejectFollowRequest(aliceId, bobId));
            bobAliceRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(bobId, aliceId);
            assertFalse(bobAliceRelationship.requested);

            // request again and alice blocks
            followAndBlockAccountDepot.append(new FollowLockedAccount(aliceId, bobId, 0));
            followAndBlockAccountDepot.append(new BlockAccount(aliceId, bobId, 0));
            bobAliceRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(bobId, aliceId);
            assertFalse(bobAliceRelationship.requested);

            // accepting a non-existent request doesn't result in a follow
            followAndBlockAccountDepot.append(new AcceptFollowRequest(aliceId, bobId, 0));
            bobAliceRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(bobId, aliceId);
            assertFalse(bobAliceRelationship.following);
            assertFalse(bobAliceRelationship.requested);

            // bob requests again and alice accepts
            followAndBlockAccountDepot.append(new FollowLockedAccount(aliceId, bobId, 0));

            followAndBlockAccountDepot.append(new AcceptFollowRequest(aliceId, bobId, 0));
            bobAliceRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(bobId, aliceId);
            assertTrue(bobAliceRelationship.following);
            assertFalse(bobAliceRelationship.requested);
            assertFalse(bobAliceRelationship.notifying);
            assertTrue(bobAliceRelationship.showingBoosts);
            assertNotNull(followerToFollowees.selectOne(Path.key(bobId, aliceId)));
            assertNotNull(followeeToFollowers.selectOne(Path.key(aliceId, bobId)));
            long relId = followerToFollowees.selectOne(Path.key(bobId, aliceId));
            assertTrue(((Follower) followerToFolloweesById.selectOne(Path.key(bobId, relId))).showBoosts);

            // verify can update the request
            followAndBlockAccountDepot.append(new FollowLockedAccount(aliceId, bobId, 0).setShowBoosts(false));
            bobAliceRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(bobId, aliceId);
            assertTrue(bobAliceRelationship.following);
            assertFalse(bobAliceRelationship.requested);
            assertFalse(bobAliceRelationship.showingBoosts);
            assertNotNull(followerToFollowees.selectOne(Path.key(bobId, aliceId)));
            assertNotNull(followeeToFollowers.selectOne(Path.key(aliceId, bobId)));
            relId = followerToFollowees.selectOne(Path.key(bobId, aliceId));
            assertFalse(((Follower) followerToFolloweesById.selectOne(Path.key(bobId, relId))).showBoosts);


            // bob mutes alice
            muteAccountDepot.append(new MuteAccount(bobId, aliceId, new MuteAccountOptions(true), 0));
            assertTrue(!accountIdToSuppressions.select(Path.key(bobId, "muted").setElem(aliceId)).isEmpty());

            // bob is still following alice
            assertNotNull(followerToFollowees.selectOne(Path.key(bobId, aliceId)));
            assertNotNull(followeeToFollowers.selectOne(Path.key(aliceId, bobId)));

            // bob unmutes alice
            muteAccountDepot.append(new RemoveMuteAccount(bobId, aliceId, 0));
            assertTrue(accountIdToSuppressions.select(Path.key(bobId, "muted").setElem(aliceId)).isEmpty());

            // bob features alice
            featureAccountDepot.append(new FeatureAccount(bobId, aliceId, 0));
            assertNotNull(featurerToFeaturees.selectOne(Path.key(bobId, aliceId)));

            // bob unfeatures alice
            featureAccountDepot.append(new RemoveFeatureAccount(bobId, aliceId, 0));
            assertNull(featurerToFeaturees.selectOne(Path.key(bobId, aliceId)));

            // bob follows charlie and david, and david follows alice
            // the intersection of bob's followees and alice's followers is david
            followAndBlockAccountDepot.append(new FollowAccount(bobId, charlieId, 0));
            followAndBlockAccountDepot.append(new FollowAccount(bobId, davidId, 0));
            followAndBlockAccountDepot.append(new FollowAccount(davidId, aliceId, 0));
            List results = getFamiliarFollowers.invoke(bobId, aliceId);
            assertEquals(1, results.size());
            assertEquals(davidId, results.get(0));

            // bob follows a hashtag
            followHashtagDepot.append(new FollowHashtag(bobId, "foo", 0));
            assertTrue((boolean) hashtagToFollowers.selectOne(Path.key("foo").view(Ops.CONTAINS, bobId)));

            // bob unfollows a hashtag
            followHashtagDepot.append(new RemoveFollowHashtag(bobId, "foo", 0));
            assertFalse((boolean) hashtagToFollowers.selectOne(Path.key("foo").view(Ops.CONTAINS, bobId)));

            // alice follows david, hiding boosts
            followAndBlockAccountDepot.append((new FollowAccount(aliceId, davidId, 0)).setShowBoosts(false));
            AccountRelationshipQueryResult aliceDavidRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(aliceId, davidId);
            assertTrue(aliceDavidRelationship.following);
            assertFalse(aliceDavidRelationship.showingBoosts);

            // alice can modify notifications setting without changing boosts and vice versa
            followAndBlockAccountDepot.append((new FollowAccount(aliceId, davidId, 0)).setNotify(true));
            aliceDavidRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(aliceId, davidId);
            assertFalse(aliceDavidRelationship.showingBoosts);
            assertTrue(aliceDavidRelationship.notifying);

            followAndBlockAccountDepot.append((new FollowAccount(aliceId, davidId, 0)).setShowBoosts(true));
            aliceDavidRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(aliceId, davidId);
            assertTrue(aliceDavidRelationship.showingBoosts);
            assertTrue(aliceDavidRelationship.notifying);

            // this should work for follow requests as well

            followAndBlockAccountDepot.append((new FollowLockedAccount(charlieId, aliceId, 0)).setShowBoosts(false));
            followAndBlockAccountDepot.append(new AcceptFollowRequest(charlieId, aliceId, 0));
            AccountRelationshipQueryResult aliceCharlieRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(aliceId, charlieId);
            assertFalse(aliceCharlieRelationship.showingBoosts);
            assertFalse(aliceCharlieRelationship.notifying);

            followAndBlockAccountDepot.append((new FollowLockedAccount(charlieId, aliceId, 0)).setNotify(true));
            // shouldn't need to accept this request for it to take effect
            aliceCharlieRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(aliceId, charlieId);
            assertFalse(aliceCharlieRelationship.showingBoosts);
            assertTrue(aliceCharlieRelationship.notifying);

            followAndBlockAccountDepot.append((new FollowAccount(aliceId, charlieId, 0)).setShowBoosts(true));
            aliceCharlieRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(aliceId, charlieId);
            assertTrue(aliceCharlieRelationship.showingBoosts);
            assertTrue(aliceCharlieRelationship.notifying);

            // can also modify languages
            followAndBlockAccountDepot.append((new FollowAccount(aliceId, charlieId, 0)).setLanguages(Arrays.asList("en")));
            aliceCharlieRelationship = (AccountRelationshipQueryResult) getAccountRelationship.invoke(aliceId, charlieId);
            assertEquals(1, aliceCharlieRelationship.getLanguages().size());
            assertEquals("en", aliceCharlieRelationship.getLanguages().get(0));
            assertTrue(aliceCharlieRelationship.showingBoosts);
            assertTrue(aliceCharlieRelationship.notifying);

            // david follows accounts
            followAndBlockAccountDepot.append(new FollowAccount(davidId, aliceId, 0));
            followAndBlockAccountDepot.append(new FollowAccount(davidId, bobId, 0));
            followAndBlockAccountDepot.append(new FollowAccount(davidId, charlieId, 0));
            followAndBlockAccountDepot.append(new FollowAccount(davidId, ericId, 0));
            followAndBlockAccountDepot.append(new FollowAccount(davidId, fredId, 0));

            // david's followee count didn't go beyond the limit
            assertEquals(relationshipsModule.relationshipCountLimit, followerToFollowees.select(Path.key(davidId).mapVals()).size());

            // david blocks accounts
            followAndBlockAccountDepot.append(new BlockAccount(davidId, aliceId, 0));
            followAndBlockAccountDepot.append(new BlockAccount(davidId, bobId, 0));
            followAndBlockAccountDepot.append(new BlockAccount(davidId, charlieId, 0));
            followAndBlockAccountDepot.append(new BlockAccount(davidId, ericId, 0));
            followAndBlockAccountDepot.append(new BlockAccount(davidId, fredId, 0));

            // david's blockee count didn't go beyond the limit
            assertEquals(relationshipsModule.relationshipCountLimit, accountIdToSuppressions.select(Path.key(davidId, "blocked").all()).size());

            // david mutes accounts
            muteAccountDepot.append(new MuteAccount(davidId, aliceId, new MuteAccountOptions(true), 0));
            muteAccountDepot.append(new MuteAccount(davidId, bobId, new MuteAccountOptions(true), 0));
            muteAccountDepot.append(new MuteAccount(davidId, charlieId, new MuteAccountOptions(true), 0));
            muteAccountDepot.append(new MuteAccount(davidId, ericId, new MuteAccountOptions(true), 0));
            muteAccountDepot.append(new MuteAccount(davidId, fredId, new MuteAccountOptions(true), 0));

            // david's mutee count didn't go beyond the limit
            assertEquals(relationshipsModule.relationshipCountLimit, accountIdToSuppressions.select(Path.key(davidId, "muted").all()).size());
        }
    }

    @Test
    public void whoToFollowTest(TestInfo testInfo) throws Exception {
        List<Class> sers = new ArrayList<>();
        sers.add(MastodonSerialization.class);
        try (InProcessCluster ipc = InProcessCluster.create(sers)) {
            Relationships relationshipsModule = new Relationships();
            relationshipsModule.whoToFollowTickMillis = 50;
            relationshipsModule.numWhoToFollowSuggestions = 2;
            relationshipsModule.numTopFollowedUsers = 3;
            String moduleName = relationshipsModule.getClass().getName();
            TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

            Depot depot = ipc.clusterDepot(moduleName, "*followAndBlockAccountDepot");
            Depot removeDepot = ipc.clusterDepot(moduleName, "*removeFollowSuggestionDepot");
            PState topFollowedUsers = ipc.clusterPState(moduleName, "$$topFollowedUsers");
            PState whoToFollow = ipc.clusterPState(moduleName, "$$whoToFollow");
            QueryTopologyClient<Set> suggestions = ipc.clusterQuery(moduleName, "getWhoToFollowSuggestions");

            depot.append(new FollowAccount(0, 1, 0));
            depot.append(new FollowAccount(0, 2, 0));
            depot.append(new FollowAccount(0, 3, 0));
            depot.append(new FollowAccount(0, 4, 0));
            depot.append(new FollowAccount(2, 1, 0));
            depot.append(new FollowAccount(3, 1, 0));
            depot.append(new FollowAccount(4, 1, 0));
            depot.append(new FollowAccount(1, 5, 0));
            depot.append(new FollowAccount(1, 6, 0));
            depot.append(new FollowAccount(2, 5, 0));
            depot.append(new FollowAccount(2, 7, 0));
            depot.append(new FollowAccount(3, 5, 0));
            depot.append(new FollowAccount(4, 6, 0));
            depot.append(new FollowAccount(4, 8, 0));
            depot.append(new FollowAccount(10, 100, 0));
            depot.append(new FollowAccount(11, 100, 0));
            depot.append(new FollowAccount(12, 100, 0));
            depot.append(new FollowAccount(13, 100, 0));
            depot.append(new FollowAccount(14, 100, 0));
            depot.append(new FollowAccount(15, 100, 0));
            depot.append(new FollowAccount(20, 21, 0));
            depot.append(new FollowAccount(21, 22, 0));
            depot.append(new FollowAccount(22, 23, 0));
            depot.append(new FollowAccount(22, 24, 0));
            depot.append(new FollowAccount(22, 25, 0));

            List expected = new ArrayList();
            expected.add(100L);
            expected.add(1L);
            expected.add(5L);

            attainCondition(() -> expected.equals(topFollowedUsers.select(Path.all().first())));

            expected.clear();
            expected.add(5L);
            expected.add(6L);
            attainCondition(() -> expected.equals(whoToFollow.selectOne(Path.key(0L))));

            expected.clear();
            expected.add(22L);
            attainCondition(() -> expected.equals(whoToFollow.selectOne(Path.key(20L))));


            Set expectedSet = new HashSet<>();
            expectedSet.add(5L);
            expectedSet.add(6L);
            attainCondition(() -> expectedSet.equals(suggestions.invoke(0L)));

            expectedSet.clear();
            expectedSet.add(5L);
            expectedSet.add(22L);
            expectedSet.add(100L);
            expectedSet.add(1L);
            attainCondition(() -> expectedSet.equals(suggestions.invoke(20L)));

            expectedSet.clear();
            expectedSet.add(1L);
            expectedSet.add(5L);
            expectedSet.add(100L);
            attainCondition(() -> expectedSet.equals(suggestions.invoke(99L)));

            ipc.pauseMicrobatchTopology(moduleName, "whoToFollow");

            removeDepot.append(new RemoveFollowSuggestion(0L, 5L));

            expectedSet.clear();
            expectedSet.add(6L);
            expectedSet.add(100L);
            // doesn't contain 1L since already follows
            assertEquals(expectedSet, suggestions.invoke(0L));

            ipc.resumeMicrobatchTopology(moduleName, "whoToFollow");

            depot.append(new FollowAccount(2, 8, 0));
            expectedSet.clear();
            expectedSet.add(6L);
            expectedSet.add(8L);
            attainCondition(() -> expectedSet.equals(new HashSet(whoToFollow.select(Path.key(0L).all()))));
        }
    }


     @Test
    public void whoToFollowFilterFromGlobalTest(TestInfo testInfo) throws Exception {
        List<Class> sers = new ArrayList<>();
        sers.add(MastodonSerialization.class);
        try (InProcessCluster ipc = InProcessCluster.create(sers)) {
            Relationships relationshipsModule = new Relationships();
            relationshipsModule.whoToFollowTickMillis = 50;
            String moduleName = relationshipsModule.getClass().getName();
            TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

            Depot depot = ipc.clusterDepot(moduleName, "*followAndBlockAccountDepot");
            QueryTopologyClient<Set> suggestions = ipc.clusterQuery(moduleName, "getWhoToFollowSuggestions");

            depot.append(new FollowAccount(0, 10, 0));
            depot.append(new FollowAccount(1, 10, 0));
            depot.append(new FollowAccount(2, 10, 0));
            depot.append(new FollowAccount(2, 1, 0));

            Set expected = new HashSet();
            expected.add(1L);

            attainCondition(() -> expected.equals(suggestions.invoke(10L)));

            expected.clear();
            expected.add(10L);
            expected.add(1L);

            attainCondition(() -> expected.equals(suggestions.invoke(100L)));
        }
    }

    public static Object addDebugQueries(Topologies topologies) {
        topologies.query("allForced").out("*res")
                  .allPartition()
                  .localSelect("$$forceRecomputeUsers", Path.all()).out("*accountId")
                  .originPartition()
                  .agg(Agg.set("*accountId")).out("*res");
        return null;
    }

    @Test
    public void whoToFollowThresholdTest(TestInfo testInfo) throws Exception {
        List<Class> sers = new ArrayList<>();
        sers.add(MastodonSerialization.class);
        try(InProcessCluster ipc = InProcessCluster.create(sers)) {
            Relationships relationshipsModule = new Relationships();
            relationshipsModule.whoToFollowTickMillis = 50;
            relationshipsModule.numWhoToFollowSuggestions = 2;
            relationshipsModule.whoToFollowThreshold1 = 2;
            relationshipsModule.whoToFollowThreshold2 = 4;
            relationshipsModule.isCyclicRecompute = false;
            relationshipsModule.testTopologiesHook = RelationshipsTest::addDebugQueries;
            String moduleName = relationshipsModule.getClass().getName();
            TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

            Depot depot = ipc.clusterDepot(moduleName, "*followAndBlockAccountDepot");
            Depot removeDepot = ipc.clusterDepot(moduleName, "*removeFollowSuggestionDepot");
            PState whoToFollow = ipc.clusterPState(moduleName, "$$whoToFollow");
            QueryTopologyClient<Set> allForced = ipc.clusterQuery(moduleName, "allForced");

            depot.append(new FollowAccount(0, 1, 0));
            depot.append(new FollowAccount(1, 10, 0));
            depot.append(new FollowAccount(2, 11, 0));
            depot.append(new FollowAccount(0, 2, 0));

            Set expectedSet = new HashSet<>();
            expectedSet.add(10L);
            expectedSet.add(11L);

            attainCondition(() -> expectedSet.equals(new HashSet(whoToFollow.select(Path.key(0L).all()))));

            attainCondition(() -> {
                Set res = allForced.invoke();
                return (res == null || res.isEmpty());
            });

            depot.append(new FollowAccount(1, 12, 0));
            depot.append(new FollowAccount(2, 12, 0));
            depot.append(new FollowAccount(3, 10, 0));
            depot.append(new FollowAccount(0, 3, 0));

            attainStableCondition(() -> expectedSet.equals(new HashSet(whoToFollow.select(Path.key(0L).all()))));

            depot.append(new FollowAccount(0, 1000, 0));

            expectedSet.clear();
            expectedSet.add(10L);
            expectedSet.add(12L);
            attainCondition(() -> expectedSet.equals(new HashSet(whoToFollow.select(Path.key(0L).all()))));
        }
    }

    @Test
    public void limitedDurationMuteTest(TestInfo testInfo) throws Exception {
      List<Class> sers = new ArrayList<>();
      sers.add(MastodonSerialization.class);
      try(InProcessCluster ipc = InProcessCluster.create(sers);
          Closeable simTime = TopologyUtils.startSimTime()) {
        Relationships relationshipsModule = new Relationships();
        relationshipsModule.muteExpirationTickMillis = 50;
        String moduleName = relationshipsModule.getClass().getName();
        TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

        Random r = new Random();
        long id1 = r.nextInt();
        long id2 = id1 + 1;
        long id3 = id1 + 2;

        Depot muteAccountDepot = ipc.clusterDepot(moduleName, "*muteAccountDepot");
        PState accountIdToSuppressions = ipc.clusterPState(moduleName, "$$accountIdToSuppressions");

        MuteAccountOptions options = new MuteAccountOptions(true);
        options.setExpirationMillis(TopologyUtils.currentTimeMillis() + 60000);
        muteAccountDepot.append(new MuteAccount(id1, id2, options, 0));

        assertEquals(options, accountIdToSuppressions.selectOne(Path.key(id1, "muted", id2)));

        TopologyUtils.advanceSimTime(59000);
        Thread.sleep(250); // give expiration code a chance to run
        attainStableCondition(() -> accountIdToSuppressions.selectOne(Path.key(id1, "muted").view(Ops.CONTAINS, id2)));

        TopologyUtils.advanceSimTime(2000);
        attainCondition(() -> {
          return !(boolean)accountIdToSuppressions.selectOne(Path.key(id1, "muted").view(Ops.CONTAINS, id2));
        });

        options = new MuteAccountOptions(true);
        options.setExpirationMillis(TopologyUtils.currentTimeMillis() + 30000);
        muteAccountDepot.append(new MuteAccount(id1, id2, options, 0));

        options = new MuteAccountOptions(true);
        options.setExpirationMillis(TopologyUtils.currentTimeMillis() + 45000);
        muteAccountDepot.append(new MuteAccount(id1, id2, options, 0));

        TopologyUtils.advanceSimTime(44000);
        Thread.sleep(250); // give expiration code a chance to run
        attainStableCondition(() -> accountIdToSuppressions.selectOne(Path.key(id1, "muted").view(Ops.CONTAINS, id2)));

        TopologyUtils.advanceSimTime(2000);
        attainCondition(() -> {
          return !(boolean)accountIdToSuppressions.selectOne(Path.key(id1, "muted").view(Ops.CONTAINS, id2));
        });

        options = new MuteAccountOptions(true);
        options.setExpirationMillis(TopologyUtils.currentTimeMillis() + 30000);
        muteAccountDepot.append(new MuteAccount(id1, id2, options, 0));

        muteAccountDepot.append(new MuteAccount(id1, id2, new MuteAccountOptions(true), 0));

        TopologyUtils.advanceSimTime(31000);
        Thread.sleep(250); // give expiration code a chance to run
        attainStableCondition(() -> accountIdToSuppressions.selectOne(Path.key(id1, "muted").view(Ops.CONTAINS, id2)));


        options = new MuteAccountOptions(true);
        options.setExpirationMillis(TopologyUtils.currentTimeMillis() + 30000);
        muteAccountDepot.append(new MuteAccount(id1, id3, options, 0));

        // remove account before expiration
        muteAccountDepot.append(new RemoveMuteAccount(id1, id3, 0));
        TopologyUtils.advanceSimTime(31000);
        Thread.sleep(250); // give expiration code a chance to run
        assertFalse((boolean) accountIdToSuppressions.selectOne(Path.key(id1, "muted").view(Ops.CONTAINS, id3)));

        muteAccountDepot.append(new MuteAccount(id1, id3, new MuteAccountOptions(true), 0));
        assertTrue((boolean) accountIdToSuppressions.selectOne(Path.key(id1, "muted").view(Ops.CONTAINS, id3)));
      }
    }

    @Test
    public void filtersTest(TestInfo testInfo) throws Exception {
        List<Class> sers = new ArrayList<>();
        sers.add(MastodonSerialization.class);
        try(InProcessCluster ipc = InProcessCluster.create(sers);
            Closeable simTime = TopologyUtils.startSimTime()) {

            Relationships relationshipsModule = new Relationships();
            relationshipsModule.filterExpirationTickMillis = 50;
            String relationshipsModuleName = relationshipsModule.getClass().getName();
            TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

            Depot filterDepot = ipc.clusterDepot(relationshipsModuleName, "*filterDepot");
            PState accountIdToFilterIdToFilter = ipc.clusterPState(relationshipsModuleName, "$$accountIdToFilterIdToFilter");
            PState postUUIDToGeneratedId = ipc.clusterPState(relationshipsModuleName, "$$postUUIDToGeneratedId");

            long bobId = 1;

            String uuid = UUID.randomUUID().toString();
            ArrayList<KeywordFilter> matches = new ArrayList<>();
            HashSet<FilterContext> contexts = new HashSet<>();
            HashSet<StatusPointer> statuses = new HashSet<>();
            matches.add(new KeywordFilter("needle", true));
            contexts.add(FilterContext.Home);
            statuses.add(new StatusPointer(1, 1));
            Filter filter = new Filter(
                bobId,
                "Test filter",
                contexts,
                matches,
                statuses,
                FilterAction.Hide,
                0);
            AddFilter addFilter = new AddFilter(filter, uuid);
            filterDepot.append(addFilter);

            Long filterId = postUUIDToGeneratedId.selectOne(Path.key(uuid));
            Filter appendedFilter = accountIdToFilterIdToFilter.selectOne(Path.key(bobId, filterId));
            assertEquals("Test filter", appendedFilter.title);
            assertEquals("needle", appendedFilter.keywords.get(0).word);

            assertEquals(new StatusPointer(1, 1), appendedFilter.statuses.toArray()[0]);

            filterDepot.append((new RemoveStatusFromFilter()).setAccountId(bobId).setFilterId(filterId).setTarget(new StatusPointer(1, 1)));
            Filter statusRemovedFilter = accountIdToFilterIdToFilter.selectOne(Path.key(bobId, filterId));
            assertEquals(0, statusRemovedFilter.statuses.size());

            filterDepot.append((new AddStatusToFilter()).setAccountId(bobId).setFilterId(filterId).setTarget(new StatusPointer(1, 2)));
            Filter statusAddedFilter = accountIdToFilterIdToFilter.selectOne(Path.key(bobId, filterId));
            assertTrue(statusAddedFilter.statuses.contains(new StatusPointer(1, 2)));

            filterDepot.append(new EditFilter()
                       .setFilterId(filterId)
                       .setAccountId(bobId)
                       .setTimestamp(0)
                       .setTitle("New Title")
                       .setContext(ImmutableSet.of(FilterContext.Notifications, FilterContext.Public))
                       .setKeywords(ImmutableList.of(EditFilterKeyword.updateKeyword(new UpdateKeyword("needle", "new needle", true)))));
            appendedFilter = accountIdToFilterIdToFilter.selectOne(Path.key(bobId, filterId));
            assertEquals("New Title", appendedFilter.title);
            assertEquals("new needle", appendedFilter.keywords.get(0).word);
            assertTrue(appendedFilter.contexts.containsAll(ImmutableList.of(FilterContext.Notifications, FilterContext.Public)));
            assertFalse(appendedFilter.contexts.contains(FilterContext.Home));

            // Can add and remove keywords via a filter edit
            filterDepot.append(new EditFilter()
                       .setAccountId(bobId)
                       .setFilterId(filterId)
                       .setKeywords(ImmutableList.of(EditFilterKeyword.destroyKeyword("new needle"),
                                                     EditFilterKeyword.addKeyword(new KeywordFilter("other needle", false)))));
            appendedFilter = accountIdToFilterIdToFilter.selectOne(Path.key(bobId, filterId));
            assertEquals("other needle", appendedFilter.getKeywords().get(0).word);

            // Filters can expire
            String expiringFilterUUID = UUID.randomUUID().toString();
            Filter expiringFilter = new Filter(bobId, "Expiring filter", contexts, matches, statuses, FilterAction.Hide, 1);
            expiringFilter.setExpirationMillis(TopologyUtils.currentTimeMillis() + 1000);
            filterDepot.append(new AddFilter(expiringFilter, expiringFilterUUID));
            Long expiringFilterId = postUUIDToGeneratedId.selectOne(Path.key(expiringFilterUUID));
            Filter createdExpiringFilter = accountIdToFilterIdToFilter.selectOne(Path.key(bobId, expiringFilterId));
            assertNotNull(createdExpiringFilter);

            TopologyUtils.advanceSimTime(2000);
            Thread.sleep(250);
            attainCondition(() -> null == accountIdToFilterIdToFilter.selectOne(Path.key(bobId, expiringFilterId)));

            // Can't add more than a specified number of filter clauses
            Relationships.maxFilterClauses = 5;
            for (int i = 0; i < 10; i++) {
                filterDepot.append((new EditFilter()).setFilterId(filterId).setAccountId(bobId).setKeywords(Arrays.asList(EditFilterKeyword.addKeyword(new KeywordFilter("" + i, true)))));
                filterDepot.append(new AddStatusToFilter(filterId, bobId, new StatusPointer(1, i)));
            }
            appendedFilter = accountIdToFilterIdToFilter.selectOne(Path.key(bobId, filterId));
            assertEquals(5, appendedFilter.keywords.size());
            assertEquals(5, appendedFilter.statuses.size());

        }
    }

    @Test
    public void largeUserFollowersTest(TestInfo testInfo) throws Exception {
      List<Class> sers = new ArrayList<>();
      sers.add(MastodonSerialization.class);
      try(InProcessCluster ipc = InProcessCluster.create(sers)) {
        Relationships relationshipsModule = new Relationships();
        relationshipsModule.newTaskCutoffLimit = 2;

        String relationshipsModuleName = relationshipsModule.getClass().getName();
        ipc.launchModule(relationshipsModule, new LaunchConfig(4, 2).numWorkers(2));

        Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");
        PState partitionedFollowersControl = ipc.clusterPState(relationshipsModuleName, "$$partitionedFollowersControl");
        PState partitionedFollowers = ipc.clusterPState(relationshipsModuleName, "$$partitionedFollowers");

        List taskKeys = Helpers.genHashingIndexKeys(4);

        long aliceId = 0;
        long bobId = 1;
        long charlieId = 2;
        long davidId = 3;
        long emilyId = 4;
        long frankId = 5;
        long georgeId = 6;
        long harryId = 7;
        long ireneId = 8;
        long julieId = 9;
        long karlId = 10;
        long larryId = 11;
        long maryId = 12;


        followAndBlockAccountDepot.append(new FollowAccount(bobId, aliceId, 0));
        assertEquals(1, (int) partitionedFollowersControl.selectOne(Path.key(aliceId).view(Ops.SIZE)));
        followAndBlockAccountDepot.append(new FollowAccount(charlieId, aliceId, 0));
        assertEquals(1, (int) partitionedFollowersControl.selectOne(Path.key(aliceId).view(Ops.SIZE)));
        followAndBlockAccountDepot.append(new FollowAccount(charlieId, aliceId, 0));
        assertEquals(1, (int) partitionedFollowersControl.selectOne(Path.key(aliceId).view(Ops.SIZE)));

        assertEquals(Arrays.asList(bobId, charlieId), partitionedFollowers.select(aliceId, Path.key(aliceId).mapKeys()));

        followAndBlockAccountDepot.append(new FollowAccount(davidId, aliceId, 0));
        assertEquals(2, (int) partitionedFollowersControl.selectOne(Path.key(aliceId).view(Ops.SIZE)));
        followAndBlockAccountDepot.append(new FollowAccount(emilyId, aliceId, 0));
        assertEquals(2, (int) partitionedFollowersControl.selectOne(Path.key(aliceId).view(Ops.SIZE)));
        int newTask1 = (int) partitionedFollowersControl.selectOne(Path.key(aliceId).last());
        assertEquals(Arrays.asList(davidId, emilyId), partitionedFollowers.select(taskKeys.get(newTask1), Path.key(aliceId).mapKeys()));

        followAndBlockAccountDepot.append(new FollowAccount(frankId, aliceId, 0));
        assertEquals(3, (int) partitionedFollowersControl.selectOne(Path.key(aliceId).view(Ops.SIZE)));
        followAndBlockAccountDepot.append(new FollowAccount(georgeId, aliceId, 0));
        assertEquals(3, (int) partitionedFollowersControl.selectOne(Path.key(aliceId).view(Ops.SIZE)));
        int newTask2 = (int) partitionedFollowersControl.selectOne(Path.key(aliceId).last());
        assertEquals(Arrays.asList(frankId, georgeId), partitionedFollowers.select(taskKeys.get(newTask2), Path.key(aliceId).mapKeys()));

        followAndBlockAccountDepot.append(new FollowAccount(harryId, aliceId, 0));
        assertEquals(4, (int) partitionedFollowersControl.selectOne(Path.key(aliceId).view(Ops.SIZE)));
        followAndBlockAccountDepot.append(new FollowAccount(ireneId, aliceId, 0));
        assertEquals(4, (int) partitionedFollowersControl.selectOne(Path.key(aliceId).view(Ops.SIZE)));
        int newTask3 = (int) partitionedFollowersControl.selectOne(Path.key(aliceId).last());
        assertEquals(Arrays.asList(harryId, ireneId), partitionedFollowers.select(taskKeys.get(newTask3), Path.key(aliceId).mapKeys()));

        // verify original hasn't changed
        assertEquals(Arrays.asList(bobId, charlieId), partitionedFollowers.select(aliceId, Path.key(aliceId).mapKeys()));

        // now verify round robin once fill up all initial partitions
        followAndBlockAccountDepot.append(new FollowAccount(julieId, aliceId, 0));
        followAndBlockAccountDepot.append(new FollowAccount(karlId, aliceId, 0));
        followAndBlockAccountDepot.append(new FollowAccount(larryId, aliceId, 0));
        followAndBlockAccountDepot.append(new FollowAccount(maryId, aliceId, 0));

        assertEquals(4, (int) partitionedFollowersControl.selectOne(Path.key(aliceId).view(Ops.SIZE)));

        Set received = new HashSet();
        for(Object k: taskKeys) {
          List users = partitionedFollowers.select(k, Path.key(aliceId).mapKeys());
          received.addAll(users);
          assertEquals(3, users.size());
        }
        assertEquals(received, asSet(bobId, charlieId, davidId, emilyId, frankId, georgeId, harryId, ireneId, julieId, karlId, larryId, maryId));


        followAndBlockAccountDepot.append(new RemoveFollowAccount(charlieId, aliceId, 0));
        assertEquals(2, partitionedFollowers.select(aliceId, Path.key(aliceId).mapKeys()).size());
        assertNull(partitionedFollowers.selectOne(aliceId, Path.key(aliceId, charlieId)));

        followAndBlockAccountDepot.append(new FollowAccount(charlieId, aliceId, 0));
        assertEquals(3, partitionedFollowers.select(aliceId, Path.key(aliceId).mapKeys()).size());
        assertNotNull(partitionedFollowers.selectOne(aliceId, Path.key(aliceId, charlieId)));


        followAndBlockAccountDepot.append(new RemoveFollowAccount(harryId, aliceId, 0));
        assertEquals(2, partitionedFollowers.select(taskKeys.get(newTask3), Path.key(aliceId).mapKeys()).size());
        assertNull(partitionedFollowers.selectOne(taskKeys.get(newTask3), Path.key(aliceId, harryId)));

        followAndBlockAccountDepot.append(new FollowAccount(harryId, aliceId, 0));
        assertEquals(3, partitionedFollowers.select(taskKeys.get(newTask3), Path.key(aliceId).mapKeys()).size());
        assertNotNull(partitionedFollowers.selectOne(taskKeys.get(newTask3), Path.key(aliceId, harryId)));
      }
    }
}
