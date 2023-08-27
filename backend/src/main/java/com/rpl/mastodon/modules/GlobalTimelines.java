package com.rpl.mastodon.modules;

import clojure.lang.*;
import com.rpl.mastodon.MastodonHelpers;
import com.rpl.mastodon.data.*;
import com.rpl.rama.*;
import com.rpl.rama.helpers.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.rpl.mastodon.MastodonHelpers.*;

/*
 * This module handles Mastodon's local and federated timelines, which show all
 * statuses happening on the platform. The last 800 statuses on each global
 * timeline is kept.
 */
public class GlobalTimelines implements RamaModule {
  public static AtomicInteger TUPLE_FILTERS = null;

  public int timelineAmt = 800;
  public int maxPerMicrobatch = 400;

  // To make the global timelines scale to arbitrary statuses/second, this
  // aggregator leverages Rama's two-phase aggregation to filter the data
  // on each task before aggregating on the global task.
  public static class DataFilter implements RamaCombinerAgg<PersistentVector> {
    int _maxAmt;

    public DataFilter(int maxAmt) {
      _maxAmt = maxAmt;
    }

    @Override
    public PersistentVector combine(PersistentVector curr, PersistentVector arg) {
      PersistentVector ret = curr;
      if(curr.count() == 1 && ((PersistentVector) curr.nth(0)).nth(1) instanceof ModuleInstanceInfo) {
        ret = ((PersistentVector) curr.nth(0)).pop();
      }
      if(arg.count() == 1 && ((PersistentVector) arg.nth(0)).nth(1) instanceof ModuleInstanceInfo) {
        PersistentVector tuple = (PersistentVector) arg.nth(0);
        int numTasks = ((ModuleInstanceInfo) tuple.nth(1)).getNumTasks();
        int maxSize = _maxAmt / numTasks;
        if(ret.count() < maxSize) ret = ret.cons(tuple.nth(0));
        else if(TUPLE_FILTERS != null) TUPLE_FILTERS.incrementAndGet();
      } else {
        Iterator it = arg.iterator();
        while(ret.count() < _maxAmt && it.hasNext()) {
          ret = ret.cons(it.next());
        }
      }
      return ret;
    }

    @Override
    public PersistentVector zeroVal() {
      return PersistentVector.EMPTY;
    }
  }

  private SubBatch filterStatusWithIdSubBatch(String microbatchVar) {
    Block b = Block.explodeMicrobatch(microbatchVar).out("*data")
                   .keepTrue(new Expr(Ops.IS_INSTANCE_OF, StatusWithId.class, "*data"))
                   .macro(extractFields("*data", "*statusId", "*status"))
                   .macro(extractFields("*status", "*authorId", "*content", "*timestamp"))
                   .each(MastodonHelpers::getStatusVisibility, "*status").out("*visibility")
                   // save to global timelines if status is public and it isn't a boost
                   .keepTrue(new Expr(Ops.AND, new Expr(Ops.EQUAL, "*visibility", StatusVisibility.Public),
                                               new Expr(Ops.NOT, new Expr(Ops.IS_INSTANCE_OF, BoostStatusContent.class, "*content"))))
                   .each(Ops.MODULE_INSTANCE_INFO).out("*moduleInfo")
                   .each(Ops.TUPLE, new Expr(Ops.TUPLE, new Expr(Ops.TUPLE, "*data", "*timestamp"), "*moduleInfo")).out("*tupleInit")
                   .globalPartition()
                   .agg(Agg.combiner(new DataFilter(maxPerMicrobatch), "*tupleInit")).out("*allTuples")
                   // sort by timestamp so status IDs are added in correct order
                   .each((PersistentVector tuples, OutputCollector collector) -> {
                     List<PersistentVector> l = new ArrayList(tuples);
                     l.sort((Object o1, Object o2) -> {
                       PersistentVector v1 = (PersistentVector) o1;
                       PersistentVector v2 = (PersistentVector) o2;
                       return ((Long) v1.nth(1)).compareTo((Long) v2.nth(1));
                     });
                     for(PersistentVector tuple: l) collector.emit(tuple.nth(0));
                   }, "*allTuples").out("*statusWithId");
    return new SubBatch(b, "*statusWithId");
  }

  @Override
  public void define(Setup setup, Topologies topologies) {
    setup.clusterDepot("*statusWithIdDepot", Core.class.getName(), "*statusWithIdDepot");

    setup.clusterPState("$$accountIdToAccountTimeline", Core.class.getName(), "$$accountIdToAccountTimeline");

    setup.clusterQuery("*getStatusesFromPointers", Core.class.getName(), "getStatusesFromPointers");

    MicrobatchTopology mb = topologies.microbatch("globalTimelines");

    KeyToUniqueFixedItemsPStateGroup globalTimelines =
      new KeyToUniqueFixedItemsPStateGroup("$$globalTimelines", timelineAmt, Integer.class, StatusPointer.class);
    globalTimelines.declarePStates(mb);

    mb.source("*statusWithIdDepot").out("*microbatch")
      .batchBlock(
        Block.subBatch(filterStatusWithIdSubBatch("*microbatch")).out("*statusWithId")
             .macro(extractFields("*statusWithId", "*statusId", "*status"))
             .macro(extractFields("*status", "*authorId", "*remoteUrl"))
             .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, "*authorId", "*statusId").out("*statusPointer")
             .hashPartition(GlobalTimeline.Public.getValue())
             .macro(globalTimelines.addItem(GlobalTimeline.Public.getValue(), "*statusPointer"))
             .ifTrue(new Expr(Ops.IS_NOT_NULL, "*remoteUrl"),
                     Block.hashPartition(GlobalTimeline.PublicRemote.getValue())
                          .macro(globalTimelines.addItem(GlobalTimeline.PublicRemote.getValue(), "*statusPointer")),
                     Block.hashPartition(GlobalTimeline.PublicLocal.getValue())
                          .macro(globalTimelines.addItem(GlobalTimeline.PublicLocal.getValue(), "*statusPointer"))));
  }
}
