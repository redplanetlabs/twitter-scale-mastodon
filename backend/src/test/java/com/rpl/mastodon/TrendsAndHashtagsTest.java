package com.rpl.mastodon;

import clojure.lang.PersistentHashMap;
import com.rpl.mastodon.data.*;
import com.rpl.mastodon.modules.*;
import com.rpl.mastodon.serialization.MastodonSerialization;
import com.rpl.rama.*;
import com.rpl.rama.helpers.TopologyUtils;
import com.rpl.rama.ops.*;
import com.rpl.rama.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.Closeable;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static com.rpl.mastodon.TestHelpers.*;
import static com.rpl.rama.helpers.TopologyUtils.*;
import static com.rpl.mastodon.MastodonHelpers.*;

public class TrendsAndHashtagsTest {

    @Test
    public void parseTokensTest() {
        List<Token> tokens = Token.parseTokens("hello #world ##foo#bar, @dude! @bob@redplanetlabs.com http://a.com/?a=1&b=2#abc");

        Set<String> hashtags = Token.filterHashtags(tokens);
        assertEquals(3, hashtags.size());
        assertTrue(hashtags.contains("world"));
        assertTrue(hashtags.contains("foo"));
        assertTrue(hashtags.contains("bar"));

        Set<String> mentions = Token.filterMentions(tokens);
        assertEquals(2, mentions.size());
        assertTrue(mentions.contains("dude"));
        assertTrue(mentions.contains("bob@redplanetlabs.com"));

        Set<String> links = Token.filterLinks(tokens);
        assertEquals(1, links.size());
        assertTrue(links.contains("http://a.com/?a=1&b=2#abc"));
    }

    @Test
    public void hashtagFanoutTest(TestInfo testInfo) throws Exception {
        List<Class> sers = new ArrayList<>();
        sers.add(MastodonSerialization.class);
        try(InProcessCluster ipc = InProcessCluster.create(sers)) {
            Relationships relationshipsModule = new Relationships();
            String relationshipsModuleName = relationshipsModule.getClass().getName();
            TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

            Core coreModule = new Core();
            String coreModuleName = coreModule.getClass().getName();
            TestHelpers.launchModule(ipc, coreModule, testInfo);

            TrendsAndHashtags hashtagsModule = new TrendsAndHashtags();
            String hashtagsModuleName = hashtagsModule.getClass().getName();
            TestHelpers.launchModule(ipc, hashtagsModule, testInfo);

            Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
            Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
            PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
            PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");

            QueryTopologyClient<StatusQueryResults> getHashtagTimeline = ipc.clusterQuery(hashtagsModuleName, "getHashtagTimeline");

            int hashtagsCount = 0;

            // wait for tick depots
            ipc.waitForMicrobatchProcessedCount(hashtagsModuleName, "core", hashtagsCount += 2);

            accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));

            long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
            long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));

            PState hashtagToStatusPointers = ipc.clusterPState(hashtagsModuleName, "$$hashtagToStatusPointers");

            // alice adds a status
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#hello world", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("#hello world2", StatusVisibility.Private), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("#hello world3", StatusVisibility.Unlisted), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("#hello world4", StatusVisibility.Direct), 0)));

            ipc.waitForMicrobatchProcessedCount(hashtagsModuleName, "core", hashtagsCount += 4);

            assertEquals(1, hashtagToStatusPointers.select(Path.key("hello").all()).size());

            // alice's status is in the hashtag timeline
            {
                List<StatusResultWithId> results = getHashtagTimeline.invoke("hello", null, 0L, 5).results;
                assertEquals(1, results.size());
                assertEquals(aliceId, results.get(0).status.author.accountId);
            }

            long statusId1 = accountIdToStatuses.selectOne(Path.key(aliceId).mapKeys());

            // alice edits the status, but it still is in the old hashtag timeline
            statusDepot.append(new EditStatus(statusId1, new Status(aliceId, normalStatusContent("#goodbye world", StatusVisibility.Public), 0)));
            ipc.waitForMicrobatchProcessedCount(hashtagsModuleName, "core", hashtagsCount+=1);
            assertEquals(1, hashtagToStatusPointers.select(Path.key("hello").all()).size());
            assertEquals(1, hashtagToStatusPointers.select(Path.key("goodbye").all()).size());
        }
    }

    private static long DAY_MILLIS = 1000 * 60 * 60 * 24;

    public static List firstElems(List<List> l) {
      List ret = new ArrayList();
      for(List e: l) {
        if(e.size() > 0) ret.add(e.get(0));
      }
      return ret;
    }

    @Test
    public void hashtagTrendsTest(TestInfo testInfo) throws Exception {
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

            TrendsAndHashtags hashtagsModule = new TrendsAndHashtags();
            hashtagsModule.decayTickTimeMillis = 50;
            hashtagsModule.reviewTickTimeMillis = 50;
            hashtagsModule.topAmt = 3;
            String hashtagsModuleName = hashtagsModule.getClass().getName();
            TestHelpers.launchModule(ipc, hashtagsModule, testInfo);

            Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
            Depot reviewHashtagDepot = ipc.clusterDepot(hashtagsModuleName, "*reviewHashtagDepot");
            PState hashtagStats = ipc.clusterPState(hashtagsModuleName, "$$hashtagStats");
            PState trends = ipc.clusterPState(hashtagsModuleName, "$$hashtagTrends");
            PState reviewedTrends = ipc.clusterPState(hashtagsModuleName, "$$reviewedHashtagTrends");

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(1, normalStatusContent("#hello to the #world", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(2, normalStatusContent("#hello there", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(100, normalStatusContent("#hello again", StatusVisibility.Public), 0)));

            attainCondition(() -> trends.select(Path.all().first()).equals(Arrays.asList("hello", "world")));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(3, normalStatusContent("#a #b #c #d #d #d #d #d #d", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(4, normalStatusContent("#world", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(5, normalStatusContent("#a #hello", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(101, normalStatusContent("#hello again", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(102, normalStatusContent("#hello again", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(103, normalStatusContent("#hello again", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(104, normalStatusContent("#world again", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(105, normalStatusContent("#world again", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(106, normalStatusContent("#a", StatusVisibility.Public), 0)));

            attainCondition(() -> trends.select(Path.all().first()).equals(Arrays.asList("hello", "world", "a")));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(6, normalStatusContent("#hello", StatusVisibility.Public), DAY_MILLIS)));

            Map m = new HashMap<>();
            m.put(0L, 7);
            m.put(1L, 1);
            attainCondition(() -> hashtagStats.selectOne(Path.key("hello").nth(TrendsAndHashtags.HISTORY_INDEX).transformed(Path.mapVals().term(Ops.FIRST))).equals(m));

            TopologyUtils.advanceSimTime(3 * DAY_MILLIS);
            List expected = new ArrayList();
            expected.add(new HashMap());
            expected.add(new HashMap());
            expected.add(new HashMap());

            attainCondition(() -> trends.select(Path.all().last()).equals(expected));


            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(7, normalStatusContent("#b", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(8, normalStatusContent("#b", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(9, normalStatusContent("#c", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(10, normalStatusContent("#b", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(11, normalStatusContent("#c", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(100, normalStatusContent("#b", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(101, normalStatusContent("#b", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(102, normalStatusContent("#b", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(103, normalStatusContent("#c", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(104, normalStatusContent("#c", StatusVisibility.Public), 2 * DAY_MILLIS)));

            attainCondition(() -> trends.select(Path.sublist(0, 2).all().first()).equals(Arrays.asList("b", "c")));

            TopologyUtils.advanceSimTime(4 * DAY_MILLIS);

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(12, normalStatusContent("#hello", StatusVisibility.Public), 7 * DAY_MILLIS)));

            m.clear();
            m.put(1L, 1);
            m.put(7L, 1);

            attainCondition(() -> hashtagStats.selectOne(Path.key("hello").nth(TrendsAndHashtags.HISTORY_INDEX).transformed(Path.mapVals().term(Ops.FIRST))).equals(m));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(13, normalStatusContent("#b", StatusVisibility.Public), 7 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(14, normalStatusContent("#b", StatusVisibility.Public), 7 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(15, normalStatusContent("#c", StatusVisibility.Public), 7 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(16, normalStatusContent("#hello", StatusVisibility.Public), 7 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(17, normalStatusContent("#hello", StatusVisibility.Public), 7 * DAY_MILLIS)));

            attainCondition(() -> firstElems(trends.selectOne(Path.stay())).equals(Arrays.asList("hello", "b", "c")));
            assertEquals(new ArrayList(), firstElems(reviewedTrends.select(Path.stay())));

            reviewHashtagDepot.append(new ReviewItem("hello", 0));
            reviewHashtagDepot.append(new ReviewItem("c", 0));

            attainCondition(() -> firstElems(reviewedTrends.selectOne(Path.stay())).equals(Arrays.asList("hello", "c")));

            reviewHashtagDepot.append(new RemoveReviewItem("hello", 0));

            attainCondition(() -> firstElems(reviewedTrends.selectOne(Path.stay())).equals(Arrays.asList("c")));
        }
    }

    @Test
    public void batchHashtagStatsTest(TestInfo testInfo) throws Exception {
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

            TrendsAndHashtags hashtagsModule = new TrendsAndHashtags();
            String hashtagsModuleName = hashtagsModule.getClass().getName();
            TestHelpers.launchModule(ipc, hashtagsModule, testInfo);

            Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
            Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
            Depot reviewHashtagDepot = ipc.clusterDepot(hashtagsModuleName, "*reviewHashtagDepot");
            PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
            QueryTopologyClient<Map> batchHashtagStats = ipc.clusterQuery(hashtagsModuleName, "batchHashtagStats");

            accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));

            long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#hello #world", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#hello", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#hello", StatusVisibility.Public), DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#a", StatusVisibility.Public), DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#a", StatusVisibility.Public), DAY_MILLIS + 1)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#a", StatusVisibility.Public), DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#a", StatusVisibility.Public), DAY_MILLIS)));

            reviewHashtagDepot.append(new ReviewItem("a", 0));

            Map expected = new HashMap();
            TreeMap t = new TreeMap();
            t.put(0L, new DayBucket(2, 1));
            t.put(1L, new DayBucket(1, 1));
            expected.put("hello", new ItemStats(t, 3, DAY_MILLIS, false));
            t = new TreeMap();
            t.put(1L, new DayBucket(4, 1));
            expected.put("a", new ItemStats(t, 4, DAY_MILLIS + 1, true));
            expected.put("b", new ItemStats(PersistentHashMap.EMPTY, 0, -1, false));

            attainCondition(() -> batchHashtagStats.invoke(Arrays.asList("hello", "a", "b")).equals(expected));
        }
    }

    @Test
    public void normalizeURLTest() throws Exception {
        assertEquals("http://foo.com", MastodonHelpers.normalizeURL("http://foo.com//"));
        assertEquals("http://a.b.c.foo.co", MastodonHelpers.normalizeURL("http://a.b.c.foo.co"));
        assertEquals("http://foo.com/bar?a=1&b=2", MastodonHelpers.normalizeURL("http://foo.com/bar?a=1&b=2"));
        assertEquals("http://foo.com/bar/?a=1&b=2", MastodonHelpers.normalizeURL("http://foo.com/bar///?a=1&b=2"));
    }

    @Test
    public void linkTrendsTest(TestInfo testInfo) throws Exception {
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

            TrendsAndHashtags hashtagsModule = new TrendsAndHashtags();
            hashtagsModule.decayTickTimeMillis = 50;
            hashtagsModule.reviewTickTimeMillis = 50;
            hashtagsModule.topAmt = 3;
            String hashtagsModuleName = hashtagsModule.getClass().getName();
            TestHelpers.launchModule(ipc, hashtagsModule, testInfo);

            Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
            Depot reviewLinkDepot = ipc.clusterDepot(hashtagsModuleName, "*reviewLinkDepot");
            PState linkStats = ipc.clusterPState(hashtagsModuleName, "$$linkStats");
            PState trends = ipc.clusterPState(hashtagsModuleName, "$$linkTrends");
            PState reviewedTrends = ipc.clusterPState(hashtagsModuleName, "$$reviewedLinkTrends");

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(1, normalStatusContent("http://hello.com to the http://world.com", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(2, normalStatusContent("http://hello.com there", StatusVisibility.Public), 0)));

            attainCondition(() -> trends.select(Path.all().first()).equals(Arrays.asList("http://hello.com", "http://world.com")));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(3, normalStatusContent("http://a.com http://b.com http://c.com http://d.com http://d.com http://d.com http://d.com http://d.com http://d.com", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(4, normalStatusContent("http://world.com", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(5, normalStatusContent("http://a.com http://hello.com", StatusVisibility.Public), 0)));

            attainCondition(() -> trends.select(Path.all().first()).equals(Arrays.asList("http://hello.com", "http://world.com", "http://a.com")));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(6, normalStatusContent("http://hello.com", StatusVisibility.Public), DAY_MILLIS)));

            Map m = new HashMap<>();
            m.put(0L, 3);
            m.put(1L, 1);
            attainCondition(() -> linkStats.selectOne(Path.key("http://hello.com").nth(TrendsAndHashtags.HISTORY_INDEX).transformed(Path.mapVals().term(Ops.FIRST))).equals(m));

            TopologyUtils.advanceSimTime(3 * DAY_MILLIS);
            List expected = new ArrayList();
            expected.add(new HashMap());
            expected.add(new HashMap());
            expected.add(new HashMap());
            attainCondition(() -> trends.select(Path.all().last()).equals(expected));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(7, normalStatusContent("http://b.com", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(8, normalStatusContent("http://b.com", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(9, normalStatusContent("http://c.com", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(10, normalStatusContent("http://b.com", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(11, normalStatusContent("http://c.com", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(100, normalStatusContent("http://b.com", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(101, normalStatusContent("http://b.com", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(102, normalStatusContent("http://b.com", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(103, normalStatusContent("http://c.com", StatusVisibility.Public), 2 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(104, normalStatusContent("http://c.com", StatusVisibility.Public), 2 * DAY_MILLIS)));

            attainCondition(() -> trends.select(Path.sublist(0, 2).all().first()).equals(Arrays.asList("http://b.com", "http://c.com")));

            TopologyUtils.advanceSimTime(4 * DAY_MILLIS);

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(12, normalStatusContent("http://hello.com", StatusVisibility.Public), 7 * DAY_MILLIS)));

            m.clear();
            m.put(1L, 1);
            m.put(7L, 1);

            attainCondition(() -> linkStats.selectOne(Path.key("http://hello.com").nth(TrendsAndHashtags.HISTORY_INDEX).transformed(Path.mapVals().term(Ops.FIRST))).equals(m));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(13, normalStatusContent("http://b.com", StatusVisibility.Public), 7 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(14, normalStatusContent("http://b.com", StatusVisibility.Public), 7 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(15, normalStatusContent("http://c.com", StatusVisibility.Public), 7 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(16, normalStatusContent("http://hello.com", StatusVisibility.Public), 7 * DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(17, normalStatusContent("http://hello.com", StatusVisibility.Public), 7 * DAY_MILLIS)));

            attainCondition(() -> trends.select(Path.all().first()).equals(Arrays.asList("http://hello.com", "http://b.com", "http://c.com")));
            assertEquals(new ArrayList(), firstElems(reviewedTrends.selectOne(Path.stay())));

            reviewLinkDepot.append(new ReviewItem("http://hello.com", 0));
            reviewLinkDepot.append(new ReviewItem("http://c.com", 0));

            attainCondition(() -> firstElems(reviewedTrends.selectOne(Path.stay())).equals(Arrays.asList("http://hello.com", "http://c.com")));

            reviewLinkDepot.append(new RemoveReviewItem("http://hello.com", 0));

            attainCondition(() -> firstElems(reviewedTrends.selectOne(Path.stay())).equals(Arrays.asList("http://c.com")));
        }
    }

    @Test
    public void batchLinkStatsTest(TestInfo testInfo) throws Exception {
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

            TrendsAndHashtags hashtagsModule = new TrendsAndHashtags();
            String hashtagsModuleName = hashtagsModule.getClass().getName();
            TestHelpers.launchModule(ipc, hashtagsModule, testInfo);

            Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
            Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
            Depot reviewLinkDepot = ipc.clusterDepot(hashtagsModuleName, "*reviewLinkDepot");
            PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
            QueryTopologyClient<Map> batchLinkStats = ipc.clusterQuery(hashtagsModuleName, "batchLinkStats");

            accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "aaa", "", AccountContent.local(new LocalAccount("")), 1));
            long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("http://redplanetlabs.com// https://a.com", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("http://redplanetlabs.com/", StatusVisibility.Public), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("http://redplanetlabs.com/", StatusVisibility.Public), DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("https://abc.com", StatusVisibility.Public), DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("https://abc.com", StatusVisibility.Public), DAY_MILLIS + 1)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("https://abc.com", StatusVisibility.Public), DAY_MILLIS)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("https://abc.com", StatusVisibility.Public), DAY_MILLIS)));

            reviewLinkDepot.append(new ReviewItem("https://abc.com", 0));

            Map expected = new HashMap();
            TreeMap t = new TreeMap();
            t.put(0L, new DayBucket(2, 1));
            t.put(1L, new DayBucket(1, 1));
            expected.put("http://redplanetlabs.com", new ItemStats(t, 3, DAY_MILLIS, false));
            t = new TreeMap();
            t.put(1L, new DayBucket(4, 1));
            expected.put("https://abc.com", new ItemStats(t, 4, DAY_MILLIS + 1, true));
            expected.put("http://aaa.com", new ItemStats(PersistentHashMap.EMPTY, 0, -1, false));

            attainCondition(() -> batchLinkStats.invoke(Arrays.asList("http://redplanetlabs.com", "https://abc.com", "http://aaa.com")).equals(expected));
        }
    }

    private List numUsersStat(QueryTopologyClient<Map> batchStats, String item) {
        Map res = batchStats.invoke(Arrays.asList(item));
        ItemStats s = (ItemStats) res.get(item);
        TreeMap<Long, DayBucket> buckets = new TreeMap(s.getDayBuckets());
        List ret = new ArrayList();
        for(DayBucket b: buckets.values()) ret.add(b.getAccounts());
        return ret;
    }

    @Test
    public void uniqueHashtagUsersTest(TestInfo testInfo) throws Exception {
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

            TrendsAndHashtags hashtagsModule = new TrendsAndHashtags();
            String hashtagsModuleName = hashtagsModule.getClass().getName();
            TestHelpers.launchModule(ipc, hashtagsModule, testInfo);

            Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
            Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
            PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
            QueryTopologyClient<Map> batchHashtagStats = ipc.clusterQuery(hashtagsModuleName, "batchHashtagStats");

            accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
            accountDepot.append(new Account("bob", "bob@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
            accountDepot.append(new Account("charlie", "charlie@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("hello #a #b", StatusVisibility.Public), currentTimeMillis())));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("hello #a #b #c", StatusVisibility.Public), currentTimeMillis())));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("hello #a", StatusVisibility.Public), currentTimeMillis())));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("hello #a", StatusVisibility.Public), currentTimeMillis())));

            attainCondition(() -> numUsersStat(batchHashtagStats, "a").equals(Arrays.asList(2)));
            attainCondition(() -> numUsersStat(batchHashtagStats, "b").equals(Arrays.asList(1)));
            attainCondition(() -> numUsersStat(batchHashtagStats, "c").equals(Arrays.asList(1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);

            assertEquals(Arrays.asList(2), numUsersStat(batchHashtagStats, "a"));
            assertEquals(Arrays.asList(1), numUsersStat(batchHashtagStats, "b"));
            assertEquals(Arrays.asList(1), numUsersStat(batchHashtagStats, "c"));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("#a", StatusVisibility.Public), currentTimeMillis())));

            attainCondition(() -> numUsersStat(batchHashtagStats, "a").equals(Arrays.asList(2, 1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("#a", StatusVisibility.Public), currentTimeMillis())));
            attainCondition(() -> numUsersStat(batchHashtagStats, "a").equals(Arrays.asList(2, 1, 1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("#a", StatusVisibility.Public), currentTimeMillis())));
            attainCondition(() -> numUsersStat(batchHashtagStats, "a").equals(Arrays.asList(2, 1, 1, 1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("#a", StatusVisibility.Public), currentTimeMillis())));
            attainCondition(() -> numUsersStat(batchHashtagStats, "a").equals(Arrays.asList(2, 1, 1, 1, 1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("#a", StatusVisibility.Public), currentTimeMillis())));
            attainCondition(() -> numUsersStat(batchHashtagStats, "a").equals(Arrays.asList(2, 1, 1, 1, 1, 1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("#a", StatusVisibility.Public), currentTimeMillis())));
            attainCondition(() -> numUsersStat(batchHashtagStats, "a").equals(Arrays.asList(2, 1, 1, 1, 1, 1, 1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("#a", StatusVisibility.Public), currentTimeMillis())));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#a", StatusVisibility.Public), currentTimeMillis())));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("#a", StatusVisibility.Public), currentTimeMillis())));
            attainCondition(() -> numUsersStat(batchHashtagStats, "a").equals(Arrays.asList(1, 1, 1, 1, 1, 1, 3)));
        }
    }


    @Test
    public void uniqueLinkUsersTest(TestInfo testInfo) throws Exception {
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

            TrendsAndHashtags hashtagsModule = new TrendsAndHashtags();
            String hashtagsModuleName = hashtagsModule.getClass().getName();
            TestHelpers.launchModule(ipc, hashtagsModule, testInfo);

            Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
            Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
            PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
            QueryTopologyClient<Map> batchLinkStats = ipc.clusterQuery(hashtagsModuleName, "batchLinkStats");

            accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
            accountDepot.append(new Account("bob", "bob@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
            accountDepot.append(new Account("charlie", "charlie@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("hello https://a.com https://b.com", StatusVisibility.Public), currentTimeMillis())));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("hello https://a.com https://b.com https://c.com", StatusVisibility.Public), currentTimeMillis())));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("hello https://a.com", StatusVisibility.Public), currentTimeMillis())));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("hello https://a.com", StatusVisibility.Public), currentTimeMillis())));

            attainCondition(() -> numUsersStat(batchLinkStats, "https://a.com").equals(Arrays.asList(2)));
            attainCondition(() -> numUsersStat(batchLinkStats, "https://b.com").equals(Arrays.asList(1)));
            attainCondition(() -> numUsersStat(batchLinkStats, "https://c.com").equals(Arrays.asList(1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);

            assertEquals(Arrays.asList(2), numUsersStat(batchLinkStats, "https://a.com"));
            assertEquals(Arrays.asList(1), numUsersStat(batchLinkStats, "https://b.com"));
            assertEquals(Arrays.asList(1), numUsersStat(batchLinkStats, "https://c.com"));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("https://a.com", StatusVisibility.Public), currentTimeMillis())));

            attainCondition(() -> numUsersStat(batchLinkStats, "https://a.com").equals(Arrays.asList(2, 1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("https://a.com", StatusVisibility.Public), currentTimeMillis())));
            attainCondition(() -> numUsersStat(batchLinkStats, "https://a.com").equals(Arrays.asList(2, 1, 1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("https://a.com", StatusVisibility.Public), currentTimeMillis())));
            attainCondition(() -> numUsersStat(batchLinkStats, "https://a.com").equals(Arrays.asList(2, 1, 1, 1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("https://a.com", StatusVisibility.Public), currentTimeMillis())));
            attainCondition(() -> numUsersStat(batchLinkStats, "https://a.com").equals(Arrays.asList(2, 1, 1, 1, 1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("https://a.com", StatusVisibility.Public), currentTimeMillis())));
            attainCondition(() -> numUsersStat(batchLinkStats, "https://a.com").equals(Arrays.asList(2, 1, 1, 1, 1, 1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("https://a.com", StatusVisibility.Public), currentTimeMillis())));
            attainCondition(() -> numUsersStat(batchLinkStats, "https://a.com").equals(Arrays.asList(2, 1, 1, 1, 1, 1, 1)));

            TopologyUtils.advanceSimTime(DAY_MILLIS);
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("https://a.com", StatusVisibility.Public), currentTimeMillis())));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("https://a.com", StatusVisibility.Public), currentTimeMillis())));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("https://a.com", StatusVisibility.Public), currentTimeMillis())));
            attainCondition(() -> numUsersStat(batchLinkStats, "https://a.com").equals(Arrays.asList(1, 1, 1, 1, 1, 1, 3)));
        }
    }

    @Test
    public void statusTrendsTest(TestInfo testInfo) throws Exception {
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

            TrendsAndHashtags hashtagsModule = new TrendsAndHashtags();
            hashtagsModule.decayTickTimeMillis = 50;
            hashtagsModule.topAmt = 4;
            String hashtagsModuleName = hashtagsModule.getClass().getName();
            TestHelpers.launchModule(ipc, hashtagsModule, testInfo);

            Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
            PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
            PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
            Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
            Depot favoriteStatusDepot = ipc.clusterDepot(coreModuleName, "*favoriteStatusDepot");
            PState trends = ipc.clusterPState(hashtagsModuleName, "$$statusTrends");

            accountDepot.append(new Account("user1", "user1@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            accountDepot.append(new Account("user2", "user2@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            accountDepot.append(new Account("user3", "user3@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            accountDepot.append(new Account("user4", "user4@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            accountDepot.append(new Account("user5", "user5@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            accountDepot.append(new Account("user6", "user6@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            accountDepot.append(new Account("user7", "user7@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            accountDepot.append(new Account("user8", "user8@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            accountDepot.append(new Account("user9", "user9@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
            long user1Id = nameToUser.selectOne(Path.key("user1", "accountId"));
            long user2Id = nameToUser.selectOne(Path.key("user2", "accountId"));
            long user3Id = nameToUser.selectOne(Path.key("user3", "accountId"));
            long user4Id = nameToUser.selectOne(Path.key("user4", "accountId"));
            long user5Id = nameToUser.selectOne(Path.key("user5", "accountId"));
            long user6Id = nameToUser.selectOne(Path.key("user6", "accountId"));
            long user7Id = nameToUser.selectOne(Path.key("user7", "accountId"));
            long user8Id = nameToUser.selectOne(Path.key("user8", "accountId"));
            long user9Id = nameToUser.selectOne(Path.key("user9", "accountId"));

            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.normalStatusContent("Status 1", StatusVisibility.Public), 0)));
            long status1 = accountIdToStatuses.selectOne(Path.key(user1Id).first().first());
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.normalStatusContent("Status 2", StatusVisibility.Public), 0)));
            long status2 = accountIdToStatuses.selectOne(Path.key(user1Id).first().first());
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user2Id, TestHelpers.normalStatusContent("Status 3", StatusVisibility.Public), 0)));
            long status3 = accountIdToStatuses.selectOne(Path.key(user2Id).first().first());
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user3Id, TestHelpers.normalStatusContent("Status 4", StatusVisibility.Public), 0)));
            long status4 = accountIdToStatuses.selectOne(Path.key(user3Id).first().first());
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user4Id, TestHelpers.normalStatusContent("Status 5", StatusVisibility.Public), 0)));
            long status5 = accountIdToStatuses.selectOne(Path.key(user4Id).first().first());
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user5Id, TestHelpers.normalStatusContent("Status 6", StatusVisibility.Public), 0)));
            long status6 = accountIdToStatuses.selectOne(Path.key(user5Id).first().first());


            // status5 and status6 with one reaction each (all replies are from same person)
            favoriteStatusDepot.append(new FavoriteStatus(user1Id, new StatusPointer(user4Id, status5), 0));
            favoriteStatusDepot.append(new FavoriteStatus(user1Id, new StatusPointer(user5Id, status6), 0));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.replyStatusContent("a", StatusVisibility.Public, user5Id, status6), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.replyStatusContent("b", StatusVisibility.Public, user5Id, status6), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.replyStatusContent("c", StatusVisibility.Public, user5Id, status6), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.replyStatusContent("d", StatusVisibility.Public, user5Id, status6), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.replyStatusContent("e", StatusVisibility.Public, user5Id, status6), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.replyStatusContent("f", StatusVisibility.Public, user5Id, status6), 0)));

            // 3 boosts to status1
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user2Id, TestHelpers.boostStatusContent(user1Id, status1), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user3Id, TestHelpers.boostStatusContent(user1Id, status1), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user4Id, TestHelpers.boostStatusContent(user1Id, status1), 0)));

            // 2 replies to status2
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user6Id, TestHelpers.replyStatusContent("aaa", StatusVisibility.Public, user1Id, status2), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user7Id, TestHelpers.replyStatusContent("aaa", StatusVisibility.Public, user1Id, status2), 0)));

            // 4 likes to status3
            favoriteStatusDepot.append(new FavoriteStatus(user1Id, new StatusPointer(user2Id, status3), 0));
            favoriteStatusDepot.append(new FavoriteStatus(user3Id, new StatusPointer(user2Id, status3), 0));
            favoriteStatusDepot.append(new FavoriteStatus(user4Id, new StatusPointer(user2Id, status3), 0));
            favoriteStatusDepot.append(new FavoriteStatus(user5Id, new StatusPointer(user2Id, status3), 0));

            // mixture of 5 likes, replies, and boosts for status4
            favoriteStatusDepot.append(new FavoriteStatus(user1Id, new StatusPointer(user3Id, status4), 0));
            favoriteStatusDepot.append(new FavoriteStatus(user2Id, new StatusPointer(user3Id, status4), 0));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user4Id, TestHelpers.replyStatusContent("a", StatusVisibility.Public, user3Id, status4), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user5Id, TestHelpers.boostStatusContent(user3Id, status4), 0)));
            statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user8Id, TestHelpers.boostStatusContent(user3Id, status4), 0)));

            List expected = new ArrayList();
            expected.add(new StatusPointer(user3Id, status4));
            expected.add(new StatusPointer(user2Id, status3));
            expected.add(new StatusPointer(user1Id, status1));
            expected.add(new StatusPointer(user1Id, status2));

            attainCondition(() -> expected.equals(trends.select(Path.all().first())));

            TopologyUtils.advanceSimTime(2 * DAY_MILLIS);

            expected.clear();
            expected.add(new HashMap());
            expected.add(new HashMap());
            expected.add(new HashMap());
            expected.add(new HashMap());

            // verify the existing trends decay first so the new trends can supplant them
            attainCondition(() -> expected.equals(trends.select(Path.all().last())));

            favoriteStatusDepot.append(new FavoriteStatus(user1Id, new StatusPointer(user5Id, status6), DAY_MILLIS));
            favoriteStatusDepot.append(new FavoriteStatus(user2Id, new StatusPointer(user5Id, status6), DAY_MILLIS));
            favoriteStatusDepot.append(new FavoriteStatus(user3Id, new StatusPointer(user5Id, status6), DAY_MILLIS));
            favoriteStatusDepot.append(new FavoriteStatus(user4Id, new StatusPointer(user5Id, status6), DAY_MILLIS));

            favoriteStatusDepot.append(new FavoriteStatus(user1Id, new StatusPointer(user2Id, status3), DAY_MILLIS));

            favoriteStatusDepot.append(new FavoriteStatus(user1Id, new StatusPointer(user4Id, status5), DAY_MILLIS));
            favoriteStatusDepot.append(new FavoriteStatus(user2Id, new StatusPointer(user4Id, status5), DAY_MILLIS));
            favoriteStatusDepot.append(new FavoriteStatus(user3Id, new StatusPointer(user4Id, status5), DAY_MILLIS));

            favoriteStatusDepot.append(new FavoriteStatus(user1Id, new StatusPointer(user3Id, status4), DAY_MILLIS));
            favoriteStatusDepot.append(new FavoriteStatus(user2Id, new StatusPointer(user3Id, status4), DAY_MILLIS));


            expected.clear();
            expected.add(new StatusPointer(user5Id, status6));
            expected.add(new StatusPointer(user4Id, status5));
            expected.add(new StatusPointer(user3Id, status4));
            expected.add(new StatusPointer(user2Id, status3));

            attainCondition(() -> expected.equals(trends.select(Path.all().first())));
        }
    }

    @Test
    public void accountHashtagActivityTest(TestInfo testInfo) throws Exception {
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

        TrendsAndHashtags hashtagsModule = new TrendsAndHashtags();
        hashtagsModule.accountRecentHashtagsAmount = 2;
        hashtagsModule.decayTickTimeMillis = 60000000;
        hashtagsModule.reviewTickTimeMillis = 60000000;
        hashtagsModule.featureHashtagLimit = 4;
        String hashtagsModuleName = hashtagsModule.getClass().getName();
        TestHelpers.launchModule(ipc, hashtagsModule, testInfo);

        Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
        PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
        Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
        Depot featureHashtagDepot = ipc.clusterDepot(hashtagsModuleName, "*featureHashtagDepot");
        PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
        PState accountIdToHashtagActivity = ipc.clusterPState(hashtagsModuleName, "$$accountIdToHashtagActivity");
        PState accountIdToRecentHashtags = ipc.clusterPState(hashtagsModuleName, "$$accountIdToRecentHashtags");

        QueryTopologyClient<List> getFeaturedHashtags = ipc.clusterQuery(hashtagsModuleName, "getFeaturedHashtags");

        accountDepot.append(new Account("user1", "user1@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
        accountDepot.append(new Account("user2", "user2@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
        long user1Id = nameToUser.selectOne(Path.key("user1", "accountId"));
        long user2Id = nameToUser.selectOne(Path.key("user2", "accountId"));

        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.normalStatusContent("hello world", StatusVisibility.Public), 0)));
        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.normalStatusContent("#hello #alice", StatusVisibility.Public), 2)));
        long status1 = accountIdToStatuses.selectOne(Path.key(user1Id).first().first());
        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.normalStatusContent("#hello #alice ignore", StatusVisibility.Private), 2)));
        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.normalStatusContent("#hello #alice ignore", StatusVisibility.Unlisted), 2)));
        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.normalStatusContent("#hello #alice ignore", StatusVisibility.Direct), 2)));
        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.normalStatusContent("#hello world", StatusVisibility.Public), 11)));
        long status2 = accountIdToStatuses.selectOne(Path.key(user1Id).first().first());
        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.normalStatusContent("I say #hello", StatusVisibility.Public), 4)));
        long status3 = accountIdToStatuses.selectOne(Path.key(user1Id).first().first());
        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.normalStatusContent("#alice in wonderland", StatusVisibility.Public), 1)));
        long status4 = accountIdToStatuses.selectOne(Path.key(user1Id).first().first());

        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user2Id, TestHelpers.normalStatusContent("#hello #world", StatusVisibility.Public), 2)));
        long status5 = accountIdToStatuses.selectOne(Path.key(user2Id).first().first());

        // 2 extra for the tick depots
        ipc.waitForMicrobatchProcessedCount(hashtagsModuleName, "core", 11);

        assertEquals(Arrays.asList(status3, status2, status1), accountIdToHashtagActivity.select(Path.key(user1Id, "hello", "timeline").all()));
        assertEquals(11L, (Long) accountIdToHashtagActivity.selectOne(Path.key(user1Id, "hello", "lastStatusMillis")));
        assertEquals(Arrays.asList(status4, status1), accountIdToHashtagActivity.select(Path.key(user1Id, "alice", "timeline").all()));
        assertEquals(2L, (Long) accountIdToHashtagActivity.selectOne(Path.key(user1Id, "alice", "lastStatusMillis")));
        assertEquals(2, (Integer) accountIdToHashtagActivity.selectOne(Path.key(user1Id).view(Ops.SIZE)));

        assertEquals(Arrays.asList(status5), accountIdToHashtagActivity.select(Path.key(user2Id, "hello", "timeline").all()));
        assertEquals(2L, (Long) accountIdToHashtagActivity.selectOne(Path.key(user2Id, "hello", "lastStatusMillis")));
        assertEquals(Arrays.asList(status5), accountIdToHashtagActivity.select(Path.key(user2Id, "world", "timeline").all()));
        assertEquals(2L, (Long) accountIdToHashtagActivity.selectOne(Path.key(user2Id, "world", "lastStatusMillis")));
        assertEquals(2, (Integer) accountIdToHashtagActivity.selectOne(Path.key(user2Id).view(Ops.SIZE)));

        List l = (List) accountIdToRecentHashtags.select(Path.key(user1Id).all());
        assertEquals(2, l.size());
        Set expected = new HashSet();
        expected.add("alice");
        expected.add("hello");
        assertEquals(expected, new HashSet(l));

        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.normalStatusContent("#aaa", StatusVisibility.Public), 4)));
        statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(user1Id, TestHelpers.normalStatusContent("#bbb", StatusVisibility.Public), 4)));

        ipc.waitForMicrobatchProcessedCount(hashtagsModuleName, "core", 13);

        l = (List) accountIdToRecentHashtags.select(Path.key(user1Id).all());
        assertEquals(2, l.size());
        expected.clear();
        expected.add("aaa");
        expected.add("bbb");
        assertEquals(expected, new HashSet(l));

        featureHashtagDepot.append(new FeatureHashtag(user1Id, "hello", 0));
        featureHashtagDepot.append(new FeatureHashtag(user1Id, "a", 0));
        featureHashtagDepot.append(new FeatureHashtag(user1Id, "b", 0));

        List<FeaturedHashtagInfo> res = getFeaturedHashtags.invoke(user1Id);
        List expectedRes = new ArrayList();
        expectedRes.add(new FeaturedHashtagInfo("hello", 3, 11));
        expectedRes.add(new FeaturedHashtagInfo("a", 0, -1));
        expectedRes.add(new FeaturedHashtagInfo("b", 0, -1));
        assertEquals(expectedRes, res);

        featureHashtagDepot.append(new FeatureHashtag(user1Id, "alice", 0));
        featureHashtagDepot.append(new FeatureHashtag(user1Id, "c", 0));
        featureHashtagDepot.append(new FeatureHashtag(user1Id, "d", 0));

        res = getFeaturedHashtags.invoke(user1Id);
        expectedRes = new ArrayList();
        expectedRes.add(new FeaturedHashtagInfo("hello", 3, 11));
        expectedRes.add(new FeaturedHashtagInfo("a", 0, -1));
        expectedRes.add(new FeaturedHashtagInfo("b", 0, -1));
        expectedRes.add(new FeaturedHashtagInfo("alice", 2, 2));
        assertEquals(expectedRes, res);

        featureHashtagDepot.append(new RemoveFeatureHashtag(user1Id, "a", 0));
        res = getFeaturedHashtags.invoke(user1Id);
        expectedRes = new ArrayList();
        expectedRes.add(new FeaturedHashtagInfo("hello", 3, 11));
        expectedRes.add(new FeaturedHashtagInfo("b", 0, -1));
        expectedRes.add(new FeaturedHashtagInfo("alice", 2, 2));
        assertEquals(expectedRes, res);

        featureHashtagDepot.append(new FeatureHashtag(user1Id, "c", 0));

        featureHashtagDepot.append(new RemoveFeatureHashtag(user1Id, "a", 0));
        res = getFeaturedHashtags.invoke(user1Id);
        expectedRes = new ArrayList();
        expectedRes.add(new FeaturedHashtagInfo("hello", 3, 11));
        expectedRes.add(new FeaturedHashtagInfo("b", 0, -1));
        expectedRes.add(new FeaturedHashtagInfo("alice", 2, 2));
        expectedRes.add(new FeaturedHashtagInfo("c", 0, -1));
        assertEquals(expectedRes, res);
      }
    }
}
