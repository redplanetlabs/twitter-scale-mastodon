package com.rpl.mastodon;

import com.rpl.mastodon.data.*;
import com.rpl.mastodon.modules.*;
import com.rpl.mastodon.serialization.MastodonSerialization;
import com.rpl.rama.*;
import com.rpl.rama.ops.Ops;
import com.rpl.rama.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static com.rpl.mastodon.TestHelpers.*;

public class GlobalTimelinesTest {

  @Test
  public void globalTimelinesTest(TestInfo testInfo) throws Exception {
    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    GlobalTimelines.TUPLE_FILTERS = new AtomicInteger(0);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      ipc.launchModule(relationshipsModule, new LaunchConfig(2, 1));

      Core coreModule = new Core();
      String coreModuleName = coreModule.getClass().getName();
      ipc.launchModule(coreModule, new LaunchConfig(2, 1));

      GlobalTimelines globalTimelinesModule = new GlobalTimelines();
      globalTimelinesModule.maxPerMicrobatch = 4;
      String globalTimelinesModuleName = globalTimelinesModule.getClass().getName();
      ipc.launchModule(globalTimelinesModule, new LaunchConfig(2, 1));

      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState globalTimelines = ipc.clusterPState(globalTimelinesModuleName, "$$globalTimelines");

      int coreCount = 0;
      int globalTimelinesCount = 0;

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));

      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));

      // through testing verified aliceId and bobId go to different tasks

      ipc.pauseMicrobatchTopology(globalTimelinesModuleName, "globalTimelines");

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("a1", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("a2", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("a3", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("a4", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("a5", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("a6", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("a7", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("a8", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("b1", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("b2", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("b3", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("b4", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("b5", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("b6", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("b7", StatusVisibility.Public), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("b8", StatusVisibility.Public), 0)));

      ipc.resumeMicrobatchTopology(globalTimelinesModuleName, "globalTimelines");

      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=16);
      ipc.waitForMicrobatchProcessedCount(globalTimelinesModuleName, "globalTimelines", globalTimelinesCount+=16);

      assertEquals(12, GlobalTimelines.TUPLE_FILTERS.get());

      assertEquals(4, (int) globalTimelines.selectOne(Path.key(GlobalTimeline.Public.getValue()).view(Ops.SIZE)));
      assertEquals(4, (int) globalTimelines.selectOne(Path.key(GlobalTimeline.PublicLocal.getValue()).view(Ops.SIZE)));

      StatusPointer statusPointer = globalTimelines.selectOne(Path.key(GlobalTimeline.Public.getValue()).first().last());

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("aa", StatusVisibility.Private), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("aa", StatusVisibility.Direct), 0)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=2);
      ipc.waitForMicrobatchProcessedCount(globalTimelinesModuleName, "globalTimelines", globalTimelinesCount+=2);

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("aa", StatusVisibility.Unlisted), 0)));
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, StatusContent.boost(new BoostStatusContent(statusPointer)), 0)));
      ipc.waitForMicrobatchProcessedCount(coreModuleName, "fanout", coreCount+=2);
      ipc.waitForMicrobatchProcessedCount(globalTimelinesModuleName, "globalTimelines", globalTimelinesCount+=2);

      // verify only public, non-boosted statuses make it to global timelines
      assertEquals(4, (int) globalTimelines.selectOne(Path.key(GlobalTimeline.Public.getValue()).view(Ops.SIZE)));
      assertEquals(4, (int) globalTimelines.selectOne(Path.key(GlobalTimeline.PublicLocal.getValue()).view(Ops.SIZE)));
    } finally {
      GlobalTimelines.TUPLE_FILTERS = null;
    }
  }

  @Test
  public void globalTimelinesDeliveryTest(TestInfo testInfo) throws Exception {
    List timelineIds = new ArrayList();
    timelineIds.add(GlobalTimeline.Public.getValue());
    timelineIds.add(GlobalTimeline.PublicLocal.getValue());

    List<Class> sers = new ArrayList<>();
    sers.add(MastodonSerialization.class);
    try(InProcessCluster ipc = InProcessCluster.create(sers)) {
      Relationships relationshipsModule = new Relationships();
      String relationshipsModuleName = relationshipsModule.getClass().getName();
      TestHelpers.launchModule(ipc, relationshipsModule, testInfo);

      Core coreModule = new Core();
      String coreModuleName = coreModule.getClass().getName();
      TestHelpers.launchModule(ipc, coreModule, testInfo);

      GlobalTimelines globalTimelinesModule = new GlobalTimelines();
      String globalTimelinesModuleName = globalTimelinesModule.getClass().getName();
      TestHelpers.launchModule(ipc, globalTimelinesModule, testInfo);

      Depot followAndBlockAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*followAndBlockAccountDepot");
      Depot muteAccountDepot = ipc.clusterDepot(relationshipsModuleName, "*muteAccountDepot");
      Depot accountDepot = ipc.clusterDepot(coreModuleName, "*accountDepot");
      Depot statusDepot = ipc.clusterDepot(coreModuleName, "*statusDepot");
      PState nameToUser = ipc.clusterPState(coreModuleName, "$$nameToUser");
      PState accountIdToStatuses = ipc.clusterPState(coreModuleName, "$$accountIdToStatuses");
      PState globalTimelines = ipc.clusterPState(globalTimelinesModuleName, "$$globalTimelines");
      QueryTopologyClient<StatusQueryResults> getStatusesFromPointers = ipc.clusterQuery(coreModuleName, "getStatusesFromPointers");

      accountDepot.append(new Account("alice", "alice@foo.com", "hash1", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
      accountDepot.append(new Account("bob", "bob@foo.com", "hash2", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));
      accountDepot.append(new Account("charlie", "charlie@foo.com", "hash3", "en-US", "", "", AccountContent.local(new LocalAccount("")), 1));

      long aliceId = nameToUser.selectOne(Path.key("alice", "accountId"));
      long bobId = nameToUser.selectOne(Path.key("bob", "accountId"));
      long charlieId = nameToUser.selectOne(Path.key("charlie", "accountId"));

      int globalTimelinesCount = 0;

      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("a1", StatusVisibility.Public), 0)));
      ipc.waitForMicrobatchProcessedCount(globalTimelinesModuleName, "globalTimelines", globalTimelinesCount+=1);

      long alice1 = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(aliceId, normalStatusContent("a2", StatusVisibility.Public), 0)));
      ipc.waitForMicrobatchProcessedCount(globalTimelinesModuleName, "globalTimelines", globalTimelinesCount+=1);

      long alice2 = accountIdToStatuses.selectOne(Path.key(aliceId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("b1", StatusVisibility.Public), 0)));
      ipc.waitForMicrobatchProcessedCount(globalTimelinesModuleName, "globalTimelines", globalTimelinesCount+=1);

      long bob1 = accountIdToStatuses.selectOne(Path.key(bobId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(bobId, normalStatusContent("b2", StatusVisibility.Public), 0)));
      ipc.waitForMicrobatchProcessedCount(globalTimelinesModuleName, "globalTimelines", globalTimelinesCount+=1);

      long bob2 = accountIdToStatuses.selectOne(Path.key(bobId).first().first());
      statusDepot.append(new AddStatus(UUID.randomUUID().toString(), new Status(charlieId, normalStatusContent("c1", StatusVisibility.Public), 0)));
      ipc.waitForMicrobatchProcessedCount(globalTimelinesModuleName, "globalTimelines", globalTimelinesCount+=1);

      long charlie1 = accountIdToStatuses.selectOne(Path.key(charlieId).first().first());

      List<StatusPointer> global;

      Set<Long> statusIds = new HashSet<>();
      Set<Long> expected = new HashSet<>();

      List testAccountIds = new ArrayList();
      testAccountIds.add(null);
      testAccountIds.add(aliceId);
      testAccountIds.add(bobId);

      for(Object id: timelineIds) {
        Map<Long, String> statuses = new HashMap<>();
        for(Object accountId: testAccountIds) {
          global = globalTimelines.select(Path.key(id).mapVals());
          List<StatusResultWithId> results = getStatusesFromPointers.invoke(accountId, global, new QueryFilterOptions(FilterContext.Public, true)).results;
          assertEquals(globalTimelinesCount, global.size());
          for(StatusResultWithId r : results) {
            statusIds.add(r.getStatusId());
            statuses.put(r.getStatusId(), r.getStatus().getContent().getNormal().getText());
          }
          expected.add(alice1);
          expected.add(alice2);
          expected.add(bob1);
          expected.add(bob2);
          expected.add(charlie1);
          assertEquals(statusIds, expected);
        }
        Map<Long, String> expectedStatuses = new HashMap<>();
        expectedStatuses.put(alice1, "a1");
        expectedStatuses.put(alice2, "a2");
        expectedStatuses.put(bob1, "b1");
        expectedStatuses.put(bob2, "b2");
        expectedStatuses.put(charlie1, "c1");
        assertEquals(expectedStatuses, statuses);
      }

      followAndBlockAccountDepot.append(new BlockAccount(aliceId, bobId, 0));

      for(Object id: timelineIds) {
        global = globalTimelines.select(Path.key(id).mapVals());
        List<StatusResultWithId> results = getStatusesFromPointers.invoke(aliceId, global, new QueryFilterOptions(FilterContext.Public, true)).results;
        statusIds.clear();
        for(StatusResultWithId r : results) statusIds.add(r.getStatusId());
        expected.clear();
        expected.add(alice1);
        expected.add(alice2);
        expected.add(charlie1);
        assertEquals(statusIds, expected);
      }

      muteAccountDepot.append(new MuteAccount(aliceId, charlieId, new MuteAccountOptions(true), 0));

      for(Object id: timelineIds) {
        global = globalTimelines.select(Path.key(id).mapVals());
        List<StatusResultWithId> results = getStatusesFromPointers.invoke(aliceId, global, new QueryFilterOptions(FilterContext.Public, true)).results;
        statusIds.clear();
        for(StatusResultWithId r : results) statusIds.add(r.getStatusId());
        expected.clear();
        expected.add(alice1);
        expected.add(alice2);
        assertEquals(statusIds, expected);
      }
    }
  }
}
