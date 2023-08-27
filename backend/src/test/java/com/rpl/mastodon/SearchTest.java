package com.rpl.mastodon;

import com.rpl.mastodon.data.*;
import com.rpl.mastodon.modules.*;
import com.rpl.mastodon.navs.*;
import com.rpl.mastodon.serialization.MastodonSerialization;
import com.rpl.rama.*;
import com.rpl.rama.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static com.rpl.mastodon.TestHelpers.*;

public class SearchTest {
  private Map nextSearchParams(Map res) {
    Map ret = new HashMap();
    ret.put("nextId", res.get("nextId"));
    ret.put("term", res.get("term"));
    return ret;
  }

  @Test
  public void searchStatusesTest(TestInfo testInfo) throws Exception {
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
      TestHelpers.launchModule(ipc, hashtagsModule, testInfo);

      Search searchModule = new Search();
      searchModule.pageAmount = 10;
      String searchModuleName = searchModule.getClass().getName();
      TestHelpers.launchModule(ipc, searchModule, testInfo);

      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
      PState statusTerms = ipc.clusterPState(searchModuleName, "$$statusTerms");
      QueryTopologyClient<Map> statusTermsSearch = ipc.clusterQuery(searchModuleName, "statusTermsSearch");

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", UUID.randomUUID().toString(), "", AccountContent.local(new LocalAccount("")), 1));
      accountDepot.append(new Account("buddy", "buddy@foo.com", "hash2", "en-US", UUID.randomUUID().toString(), "", AccountContent.local(new LocalAccount("")), 1).setDisplayName("Buddy Hackett"));

      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long buddyId = nameToUser.selectOne(Path.key("buddy", "accountId"));

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hello World now", StatusVisibility.Public), 1)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("world goes hello", StatusVisibility.Public), 1)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("world world wow", StatusVisibility.Public), 1)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("hello there planet", StatusVisibility.Public), 1)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hi @buddy #status", StatusVisibility.Public), 1)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, TestHelpers.normalStatusContent("Hi I am Alice", StatusVisibility.Public), 1)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(buddyId, TestHelpers.normalStatusContent("I am Buddy", StatusVisibility.Public), 1)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(buddyId, TestHelpers.normalStatusContent("hi this is   my status", StatusVisibility.Public), 1)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(buddyId, TestHelpers.normalStatusContent("hi hi", StatusVisibility.Public), 1)));

      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 11);

      Map hiRes = statusTermsSearch.invoke(buddyId, Arrays.asList("hi"), null, 2);

      assertTrue(hiRes.containsKey("nextId"));
      assertTrue(hiRes.containsKey("matchList"));
      assertTrue(hiRes.containsKey("term"));
      assertNotNull(hiRes.get("nextId"));
      assertEquals("" + buddyId, hiRes.get("term"));
      List<List> matchList = (List)hiRes.get("matchList");
      assertEquals(2, matchList.size());

      hiRes = statusTermsSearch.invoke(buddyId, Arrays.asList("hi"), nextSearchParams(hiRes), 2);
      assertTrue(hiRes.containsKey("nextId"));
      assertTrue(hiRes.containsKey("matchList"));
      assertTrue(hiRes.containsKey("term"));
      assertNull(hiRes.get("nextId"));
      assertEquals("" + buddyId, hiRes.get("term"));
      List<List> matchList2 = (List)hiRes.get("matchList");
      assertEquals(1, matchList2.size());

      matchList.addAll(matchList2);
      Set<String> res = new HashSet<>();
      for(List pair: matchList) {
        Long accountId = (Long) pair.get(0);
        Long statusId = (Long) pair.get(1);
        res.add(accountIdToStatuses.selectOne(Path.key(accountId, statusId).nth(0).customNav(new TField("content")).customNav(new TField("text"))));
      }
      Set<String> expected = new HashSet() {{
        add("Hi @buddy #status");
        add("hi this is   my status");
        add("hi hi");
      }};
      assertEquals(expected, res);

      Map helloWorldRes = statusTermsSearch.invoke(aliceId, Arrays.asList("hello", "world"), null, 2);
      res = new HashSet<>();
      do {
        assertTrue(helloWorldRes.containsKey("nextId"));
        assertTrue(helloWorldRes.containsKey("matchList"));
        assertTrue(helloWorldRes.containsKey("term"));
        matchList = (List) helloWorldRes.get("matchList");
        for(List pair: matchList) {
          Long accountId = (Long) pair.get(0);
          Long statusId = (Long) pair.get(1);
          res.add(accountIdToStatuses.selectOne(Path.key(accountId, statusId).nth(0).customNav(new TField("content")).customNav(new TField("text"))));
        }
        helloWorldRes =  statusTermsSearch.invoke(aliceId, Arrays.asList("hello", "world"), nextSearchParams(helloWorldRes), 2);
      } while(helloWorldRes.get("nextId")!=null);

      expected = new HashSet() {{
        add("Hello World now");
        add("world goes hello");
      }};
      assertEquals(expected, res);
     }
  }

  @Test
  public void profileSearchTest(TestInfo testInfo) throws Exception {
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
      TestHelpers.launchModule(ipc, hashtagsModule, testInfo);

      Search searchModule = new Search();
      searchModule.pageAmount = 3;
      String searchModuleName = searchModule.getClass().getName();
      TestHelpers.launchModule(ipc, searchModule, testInfo);

      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot accountEditDepot = ipc.clusterDepot(coreModuleName, "*accountEditDepot");
      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      QueryTopologyClient<Map> profileTermsSearch = ipc.clusterQuery(searchModuleName, "profileTermsSearch");

      // allow records to be fully processed to ensure order of indexing for "Alice" and "Ann"
      // to test "best term" computation
      accountDepot.append(new Account("alicex", "alice@foo.com", "hash", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
      accountDepot.append(new Account("alicEann", "alice@foo.com", "hash", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1).setDisplayName("Alice Ann Smith"));
      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 2);

      accountDepot.append(new Account("alice", "alice@foo.com", "hash", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1).setDisplayName("Alice The Happy Ann"));
      accountDepot.append(new Account("alicecarter", "alice@foo.com", "hash", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1).setDisplayName("Alicia Carter"));
      accountDepot.append(new Account("alicex2", "alice@foo.com", "hash", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1).setDisplayName("Alice X2"));
      accountDepot.append(new Account("alicex3", "alice@foo.com", "hash", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1).setDisplayName("Alice X3"));
      accountDepot.append(new Account("alicex4", "alice@foo.com", "hash", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1).setDisplayName("Alice X4"));
      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 7);

      accountDepot.append(new Account("aliceann2", "alice@foo.com", "hash", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1).setDisplayName("Alice Ann Two"));
      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 8);
      accountDepot.append(new Account("alicEann3", "alice@foo.com", "hash", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1).setDisplayName("Alice Ann Three"));
      accountDepot.append(new Account("buddYhackett", "buddy@foo.com", "hash", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1).setDisplayName("Buddy Hackett"));
      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 10);
      accountDepot.append(new Account("buddyboddy", "buddyb@foo.com", "hash", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1).setDisplayName("Buddy Bobby"));
      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 11);

      long aliceId = nameToUser.selectOne(Path.key("alicex", "accountId"));
      accountEditDepot.append(new EditAccount(aliceId, Arrays.asList(EditAccountField.displayName("Alice X Wonder")), 1));
      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 12);


      Map res =  profileTermsSearch.invoke(Arrays.asList("buddy"), null, 2);
      assertEquals("buddy", res.get("term"));
      assertEquals(Arrays.asList("buddyboddy", "buddYhackett"), res.get("matchList"));

      assertNull(res.get("nextId"));

      res = profileTermsSearch.invoke(Arrays.asList("buddyhack"), null, 2);
      assertEquals("buddy", res.get("term"));
      assertEquals(Arrays.asList("buddYhackett"), res.get("matchList"));
      assertNull(res.get("nextId"));

      res = profileTermsSearch.invoke(Arrays.asList("bu", "hackett"), null, 2);
      assertEquals("hackett", res.get("term"));
      assertEquals(Arrays.asList("buddYhackett"), res.get("matchList"));
      assertNull(res.get("nextId"));

      res = profileTermsSearch.invoke(Arrays.asList("alicex"), null, 2);
      assertEquals("alicex", res.get("term"));
      assertEquals(Arrays.asList("alicex"), res.get("matchList"));
      assertNull(res.get("nextId"));

      res = profileTermsSearch.invoke(Arrays.asList("alice", "ann"), null, 2);
      // this verifies it chooses best term
      assertEquals("ann", res.get("term"));
      assertEquals(Arrays.asList("alicEann3", "aliceann2"), res.get("matchList"));
      assertNotNull(res.get("nextId"));

      res = profileTermsSearch.invoke(Arrays.asList("alice", "ann"), nextSearchParams(res), 2);
      assertEquals("ann", res.get("term"));
      assertEquals(Arrays.asList("alice", "alicEann"), res.get("matchList"));
      assertNull(res.get("nextId"));
    }
  }

  @Test
  public void hashtagSearchTest(TestInfo testInfo) throws Exception {
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
      hashtagsModule.reviewTickTimeMillis = 50;
      TestHelpers.launchModule(ipc, hashtagsModule, testInfo);

      Search searchModule = new Search();
      searchModule.pageAmount = 1;
      String searchModuleName = searchModule.getClass().getName();
      TestHelpers.launchModule(ipc, searchModule, testInfo);

      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      Depot reviewHashtagDepot = ipc.clusterDepot(hashtagsModuleName, "*reviewHashtagDepot");
      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      QueryTopologyClient<Map> hashtagSearch = ipc.clusterQuery(searchModuleName, "hashtagSearch");
      QueryTopologyClient<Map> reviewedHashtagSearch = ipc.clusterQuery(searchModuleName, "reviewedHashtagSearch");

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#helloworld #abcde #x", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#helloworld2 #abcde #xyz", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#helloworld3 #cagney", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#helloworld4 #cagneygrapefruit", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#help", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#hercules", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#hellohero", StatusVisibility.Unlisted), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#cagney3", StatusVisibility.Direct), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("#cagney3", StatusVisibility.Private), 0)));

      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 10);

      Map res = hashtagSearch.invoke("hellow", null, 3);
      List matches = (List) res.get("matchList");
      assertNotNull(res.get("nextId"));
      assertEquals("hellow", res.get("term"));
      assertEquals(matches, Arrays.asList("helloworld4", "helloworld3", "helloworld2"));

      res = hashtagSearch.invoke("hellow", nextSearchParams(res), 3);
      matches = (List) res.get("matchList");
      assertNull(res.get("nextId"));
      assertEquals("hellow", res.get("term"));
      assertEquals(matches, Arrays.asList("helloworld"));

      res = hashtagSearch.invoke("helloworld2", null, 3);
      matches = (List) res.get("matchList");
      assertNull(res.get("nextId"));
      assertEquals("helloworld2", res.get("term"));
      assertEquals(matches, Arrays.asList("helloworld2"));

      res = hashtagSearch.invoke("he", null, 3);
      matches = (List) res.get("matchList");
      assertNotNull(res.get("nextId"));
      assertEquals("he", res.get("term"));
      assertEquals(matches, Arrays.asList("hercules", "help", "helloworld4"));

      res = hashtagSearch.invoke("hel", null, 3);
      matches = (List) res.get("matchList");
      assertNotNull(res.get("nextId"));
      assertEquals("hel", res.get("term"));
      assertEquals(matches, Arrays.asList("help", "helloworld4", "helloworld3"));

      res = reviewedHashtagSearch.invoke("hel", null, 3);
      assertNull(res.get("nextId"));
      assertTrue(((List<?>) res.get("matchList")).isEmpty());
      assertEquals("hel", res.get("term"));

      // verify no duplicates
      res = hashtagSearch.invoke("abc", null, 3);
      matches = (List) res.get("matchList");
      assertNull(res.get("nextId"));
      assertEquals("abc", res.get("term"));
      assertEquals(matches, Arrays.asList("abcde"));

      reviewHashtagDepot.append(new ReviewItem("helloworld", 0));
      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 11);
      reviewHashtagDepot.append(new ReviewItem("help", 0));
      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 12);
      reviewHashtagDepot.append(new ReviewItem("hellooooo", 0));
      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 13);
      reviewHashtagDepot.append(new ReviewItem("cagney", 0));
      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 14);
      reviewHashtagDepot.append(new ReviewItem("helloworld2", 0));
      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 15);

      res = reviewedHashtagSearch.invoke("hel", null, 3);
      matches = (List) res.get("matchList");
      assertEquals(matches, Arrays.asList("helloworld2", "hellooooo", "help"));
      assertNotNull(res.get("nextId"));
      assertEquals("hel", res.get("term"));

      res = reviewedHashtagSearch.invoke("hel", nextSearchParams(res), 3);
      matches = (List) res.get("matchList");
      assertNull(res.get("nextId"));
      assertEquals("hel", res.get("term"));
      assertEquals(matches, Arrays.asList("helloworld"));

      res = reviewedHashtagSearch.invoke("hellow", null, 3);
      matches = (List) res.get("matchList");
      assertEquals(matches, Arrays.asList("helloworld2", "helloworld"));
      assertEquals("hellow", res.get("term"));

      reviewHashtagDepot.append(new RemoveReviewItem("helloworld2", 0));
      ipc.waitForMicrobatchProcessedCount(searchModuleName, "search", 16);

      res = reviewedHashtagSearch.invoke("hel", null, 3);
      matches = (List) res.get("matchList");
      assertEquals("hel", res.get("term"));
      assertEquals(matches, Arrays.asList("hellooooo", "help", "helloworld"));

      res = reviewedHashtagSearch.invoke("hellow", null, 3);
      matches = (List) res.get("matchList");
      assertEquals("hellow", res.get("term"));
      assertEquals(matches, Arrays.asList("helloworld"));
    }
  }
}
