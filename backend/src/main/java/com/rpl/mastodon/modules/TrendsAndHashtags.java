package com.rpl.mastodon.modules;

import clojure.lang.*;

import com.clearspring.analytics.stream.cardinality.HyperLogLog;
import com.rpl.mastodon.*;
import com.rpl.mastodon.data.*;
import com.rpl.rama.*;
import com.rpl.rama.helpers.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;

import java.io.*;
import java.net.URISyntaxException;
import java.util.*;

import static com.rpl.mastodon.MastodonHelpers.*;

/*
 * This module implements trending links, trending hashtags, trending statuses,
 * featured hashtags on an account's profile, and hashtag timelines.
 *
 * For trending links and hashtags, the general approach is to have one
 * PState storing stats for all links/hashtags. When any link/hashtag is updated,
 * it's compared against the list of trending links/hashtags to see if it
 * should replace anytning on that list. Since the topology uses two-phase aggregation
 * this is completely scalable.
 */
public class TrendsAndHashtags implements RamaModule {
  public static final int HISTORY_INDEX = 0;
  public static final int STATUS_COUNT_INDEX = 1;
  public static final int LATEST_STATUS_TIMESTAMP_INDEX = 2;

  public long decayTickTimeMillis = 1000 * 60 * 60;
  public long reviewTickTimeMillis = 1000 * 60;
  public int topAmt = 100;
  public int accountRecentHashtagsAmount = 10;
  public int featureHashtagLimit = 10;

  // HyperLogLog is used to efficiently compute the approximate number of unique users
  // posting a hashtag or link.
  public static class RHyperLogLog implements RamaSerializable {
    public HyperLogLog hll;

    public RHyperLogLog(int log2m) {
      this.hll = new HyperLogLog(log2m);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      byte[] ser = hll.getBytes();
      out.writeInt(ser.length);
      out.write(ser);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      byte[] ser = new byte[in.readInt()];
      in.read(ser);
      hll = HyperLogLog.Builder.build(ser);
    }
  }

  public static long toDayBucket(long timestampMillis) {
    return timestampMillis / (1000 * 60 * 60 * 24);
  }

  private static void dropOldBuckets(TreeMap m, Long currDay) {
    Long cutoff = currDay - 7;
    Iterator<Long> it = m.keySet().iterator();
    while(it.hasNext()) {
      Long dayBucket = it.next();
      if(dayBucket <= cutoff) it.remove();
      else break;
    }
  }

  public static class TrendsAgg implements RamaAccumulatorAgg2<TreeMap, Long, Long> {
    @Override
    public TreeMap accumulate(TreeMap m, Long authorId, Long timestampMillis) {
      long dayBucket = toDayBucket(timestampMillis);
      List tuple;
      if(m.containsKey(dayBucket)) tuple = (List) m.get(dayBucket);
      else tuple = Arrays.asList(0, new RHyperLogLog(9));
      tuple.set(0, ((Integer) tuple.get(0)) + 1);
      ((RHyperLogLog) tuple.get(1)).hll.offer(authorId);
      m.put(dayBucket, tuple);
      dropOldBuckets(m, dayBucket);
      return m;
    }

    @Override
    public TreeMap initVal() {
      return new TreeMap();
    }
  }

  public static long getTrendScore(Map m) {
    Iterator it = m.values().iterator();
    long score = 0;
    while(it.hasNext()) score += (long) it.next();
    return score;
  }

  public static long getTrendTupleScore(List l) {
    return getTrendScore((Map) l.get(1));
  }

  public static List decayTrends(List<List> trends) {
    long currDayBucket = toDayBucket(TopologyUtils.currentTimeMillis());
    List<List> ret = new ArrayList();
    for(List tuple: trends == null ? new ArrayList<List>() : trends) {
      Object item = tuple.get(0);
      Map<Long, Long> scores = (Map) tuple.get(1);
      Map newScores = new HashMap();
      for(Map.Entry<Long, Long> e: scores.entrySet()) {
        if(currDayBucket <= e.getKey() + 1) newScores.put(e.getKey(), e.getValue());
      }
      List newTuple = new ArrayList();
      newTuple.add(item);
      newTuple.add(newScores);
      ret.add(newTuple);
    }
    ret.sort((List l1, List l2) -> (int) (getTrendScore((Map) l2.get(1)) - getTrendScore((Map) l1.get(1))));
    return PersistentVector.create(ret);
  }

  private static Map historyStats(TreeMap history) {
    Map ret = new HashMap();
    long bucket = (Long) history.lastKey();
    ret.put(bucket, ((RHyperLogLog) ((List)history.get(bucket)).get(1)).hll.cardinality());
    if(history.containsKey(bucket-1)) ret.put(bucket-1, ((RHyperLogLog) ((List)history.get(bucket-1)).get(1)).hll.cardinality());
    return ret;
  }

  private static SubBatch itemHistorySubBatch(String sourcePStateVar, String targetPStateVar) {
    Block b = Block.explodeMaterialized(sourcePStateVar).out("*authorId", "*statusId", "*timestamp", "*items")
                   .each(Ops.EXPLODE, "*items").out("*item")
                   .hashPartition("*item")
                   .compoundAgg(targetPStateVar,
                                CompoundAgg.map("*item",
                                                CompoundAgg.list(Agg.accumulator(new TrendsAgg(), "*authorId", "*timestamp")
                                                                    .captureNewValInto("*history"),
                                                                 Agg.count(),
                                                                 Agg.max("*timestamp"))))
                   .each(TrendsAndHashtags::historyStats, "*history").out("*stats");
    return new SubBatch(b, "*item", "*stats");
  }

  private static SubBatch statusHistorySubBatch(String sourcePStateVar, String targetPStateVar) {
    Block b = Block.explodeMaterialized(sourcePStateVar).out("*pointAuthorId", "*pointStatusId", "*authorId", "*timestamp")
                   // partition the status by its author for consistency in how other status PStates are partitioned
                   .hashPartition("*pointAuthorId")
                   .compoundAgg(targetPStateVar,
                                CompoundAgg.map("*pointStatusId",
                                                CompoundAgg.list(Agg.last("*pointAuthorId")
                                                                    .captureNewValInto("*pointAuthorId"),
                                                                Agg.accumulator(new TrendsAgg(), "*authorId", "*timestamp")
                                                                   .captureNewValInto("*history"))))
                   .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, "*pointAuthorId", "*pointStatusId").out("*pointer")
                   .each(TrendsAndHashtags::historyStats, "*history").out("*stats");
    return new SubBatch(b, "*pointer", "*stats");
  }

  private Block trendsRankingMacro(RamaFunction2<String, String, SubBatch> subbatchFn, String sourcePState, String statsPState, String trendsPState) {
    // safe to hardcode intermediate vars because they are scoped by the batch block
    return Block.batchBlock(
             Block.subBatch(subbatchFn.invoke(sourcePState, statsPState)).out("*item", "*stats")
                  .each(Ops.TUPLE, "*item", "*stats").out("*tuple")
                  .globalPartition()
                  .agg(trendsPState, Agg.topMonotonic(this.topAmt, "*tuple")
                                        .idFunction(Ops.FIRST)
                                        .sortValFunction(TrendsAndHashtags::getTrendTupleScore)));
  }

  private void reviewImpl(StreamTopology review, String depotVar, String pstateVar) {
    review.pstate(pstateVar, PState.mapSchema(String.class, Boolean.class));
    review.source(depotVar, StreamSourceOptions.retryAllAfter()).out("*data")
          .macro(extractFields("*data", "*item"))
          .subSource("*data",
            SubSource.create(ReviewItem.class)
                     .localTransform(pstateVar, Path.key("*item").termVal(true)),
            SubSource.create(RemoveReviewItem.class)
                     .localTransform(pstateVar, Path.key("*item").termVoid()));
  }

  private Block computeReviewedTrendsBlock(String trendsPState, String reviewedPState, String reviewedTrendsPState) {
    return Block.batchBlock(
             Block.localSelect(trendsPState, Path.stay()).out("*trends")
                  .each(Ops.EXPLODE_INDEXED, "*trends").out("*i", "*tuple")
                  .each(Ops.EXPAND, "*tuple").out("*item", "*stats")
                  .hashPartition("*item")
                  .localSelect(reviewedPState, Path.must("*item"))
                  .globalPartition()
                  .agg(Agg.map("*i", "*tuple")).out("*res")
                  .each((Map res) -> res==null ? new ArrayList() : new ArrayList(new TreeMap(res).values()), "*res").out("*reviewedTrends")
                  .localTransform(reviewedTrendsPState, Path.termVal("*reviewedTrends")));
  }

  private void batchItemQuery(Topologies topologies, String name, String statsPState, String reviewedPState) {
    topologies.query(name, "*items").out("*ret")
              .each(TopologyUtils::currentTimeMillis).out("*timestamp")
              .each(Ops.EXPLODE, "*items").out("*item")
              .select(statsPState, Path.key("*item")).out("*info")
              .localSelect(reviewedPState, Path.key("*item").nullToVal(false)).out("*reviewed")
              .each((List info, Boolean reviewed, Long timestampMillis) -> {
                if(info==null) return new ItemStats(new HashMap(), 0, -1, reviewed);
                long dayBucket = toDayBucket(timestampMillis);
                Map<Long, List> history = (Map) info.get(HISTORY_INDEX);
                TreeMap dayBuckets = new TreeMap();
                for(Map.Entry<Long, List> entry: history.entrySet()) {
                  List tuple = entry.getValue();
                  dayBuckets.put(entry.getKey(), new DayBucket((int) tuple.get(0), (int) ((RHyperLogLog) tuple.get(1)).hll.cardinality()));
                }
                return new ItemStats(dayBuckets, (long) info.get(STATUS_COUNT_INDEX), (long) info.get(LATEST_STATUS_TIMESTAMP_INDEX), reviewed);
              }, "*info", "*reviewed", "*timestamp").out("*itemStats")
              .originPartition()
              .agg(Agg.map("*item", "*itemStats")).out("*ret");
  }

  private SubBatch accountHashtagTimelineSubbatch(String hashtagPState, String accountIdToHashtagActivityPState) {
    Block b = Block.explodeMaterialized("$$hashtags").out("*authorId", "*statusId", "*timestamp", "*hashtags")
                   .each(Ops.EXPLODE, "*hashtags").out("*hashtag")
                   .hashPartition("*authorId")
                   .compoundAgg(accountIdToHashtagActivityPState,
                                CompoundAgg.map("*authorId",
                                                CompoundAgg.map("*hashtag",
                                                                CompoundAgg.map("timeline",  Agg.set("*statusId"),
                                                                                "lastStatusMillis", Agg.max("*timestamp")
                                                                                                       .captureNewValInto("*ts")))));
    return new SubBatch(b, "*authorId", "*hashtag");
  }

  private static PersistentVector consolidateRecentHashtags(PersistentVector v, int topAmt) {
    List keep = new ArrayList();
    Set seen = new HashSet();
    ISeq seq = v.rseq();
    while(seq != null && keep.size() < topAmt) {
      Object val = seq.first();
      if(!seen.contains(val)) {
        keep.add(val);
        seen.add(val);
      }
      seq = seq.next();
    }
    PersistentVector ret = PersistentVector.EMPTY;
    Collections.reverse(keep);
    for(Object o: keep) ret = ret.cons(o);
    return ret;
  }

  @Override
  public void define(Setup setup, Topologies topologies) {
      setup.declareDepot("*reviewHashtagDepot", Depot.hashBy(ExtractItem.class));
      setup.declareDepot("*reviewLinkDepot", Depot.hashBy(ExtractItem.class));
      setup.declareDepot("*featureHashtagDepot", Depot.hashBy(ExtractAccountId.class));
      setup.declareTickDepot("*decayTick", this.decayTickTimeMillis);
      setup.declareTickDepot("*reviewTick", this.reviewTickTimeMillis);

      setup.clusterDepot("*statusWithIdDepot", Core.class.getName(), "*statusWithIdDepot");
      setup.clusterDepot("*favoriteStatusDepot", Core.class.getName(), "*favoriteStatusDepot");

      setup.clusterPState("$$accountIdToAccountTimeline", Core.class.getName(), "$$accountIdToAccountTimeline");

      setup.clusterQuery("*getStatusesFromPointers", Core.class.getName(), "getStatusesFromPointers");

      MicrobatchTopology core = topologies.microbatch("core");

      KeyToUniqueFixedItemsPStateGroup hashtagToStatusPointers =
        new KeyToUniqueFixedItemsPStateGroup("$$hashtagToStatusPointers", Core.DEFAULT_TIMELINE_MAX_AMOUNT, String.class, StatusPointer.class);
      hashtagToStatusPointers.declarePStates(core);

      // index 0 is history, index 1 is statusCount, and index 2 is latest status timestamp
      core.pstate("$$hashtagStats", PState.mapSchema(String.class, PState.listSchema(Object.class)));
      core.pstate("$$linkStats", PState.mapSchema(String.class, PState.listSchema(Object.class)));
      core.pstate("$$statusStats", PState.mapSchema(Long.class, PState.listSchema(Object.class)));

      core.pstate("$$hashtagTrends", List.class).global();
      core.pstate("$$linkTrends", List.class).global();
      core.pstate("$$statusTrends", List.class).global();
      core.pstate("$$reviewedHashtagTrends", List.class).global();
      core.pstate("$$reviewedLinkTrends", List.class).global();

      core.pstate("$$accountIdToHashtagActivity",
                  PState.mapSchema(Long.class,
                                   PState.mapSchema(String.class,
                                                    PState.fixedKeysSchema("timeline", PState.setSchema(Long.class).subindexed(),
                                                                           "lastStatusMillis", Long.class))
                                                    .subindexed()));
      core.pstate("$$accountIdToRecentHashtags", PState.mapSchema(Long.class, List.class));

      core.source("*statusWithIdDepot").out("*microbatch")
          .batchBlock(
            Block.explodeMicrobatch("*microbatch").out("*data")
                 .keepTrue(new Expr(Ops.OR, new Expr(Ops.IS_INSTANCE_OF, EditStatus.class, "*data"),
                                            new Expr(Ops.IS_INSTANCE_OF, StatusWithId.class, "*data")))
                 .macro(extractFields("*data", "*statusId", "*status"))
                 .macro(extractFields("*status", "*authorId", "*content", "*timestamp"))
                 .ifTrue(new Expr(Ops.IS_INSTANCE_OF, BoostStatusContent.class, "*content"),
                   Block.each(Ops.IDENTITY, null).out("*hashtags")
                        .each(Ops.IDENTITY, null).out("*links")
                        .each((BoostStatusContent c) -> Arrays.asList(c.boosted.authorId, c.boosted.statusId), "*content").out("*tuplePoint"),
                   Block.macro(MastodonHelpers.extractFields("*content", "*visibility", "*text"))
                        .keepTrue(new Expr(Ops.EQUAL, "*visibility", StatusVisibility.Public))
                        .ifTrue(new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content"),
                          Block.each((ReplyStatusContent c) -> Arrays.asList(c.parent.authorId, c.parent.statusId), "*content").out("*tuplePoint"),
                          Block.each(Ops.IDENTITY, null).out("*tuplePoint"))
                        .each(Token::parseTokens, "*text").out("*tokens")
                        .each(Token::filterHashtags, "*tokens").out("*hashtags")
                        .each(Token::filterLinks, "*tokens").out("*rawLinks")
                        .each((Set<String> links) -> {
                            Set ret = new HashSet();
                            for(String url: links) {
                              try {
                                ret.add(MastodonHelpers.normalizeURL(url));
                              } catch(URISyntaxException e) { }
                            }
                            return ret;
                        }, "*rawLinks").out("*links"))
                 .ifTrue(new Expr(Ops.GREATER_THAN, new Expr(Ops.SIZE, "*hashtags"), 0),
                    Block.materialize("*authorId", "*statusId", "*timestamp", "*hashtags").out("$$hashtags"))
                 .ifTrue(new Expr(Ops.GREATER_THAN, new Expr(Ops.SIZE, "*links"), 0),
                    Block.materialize("*authorId", "*statusId", "*timestamp", "*links").out("$$links"))
                 .ifTrue(new Expr(Ops.IS_NOT_NULL, "*tuplePoint"),
                    Block.each(Ops.EXPAND, "*tuplePoint").out("*pointAuthorId", "*pointStatusId")
                         .materialize("*pointAuthorId", "*pointStatusId", "*authorId", "*timestamp").out("$$statusPoints")))
          .anchor("dataRoot")
          .batchBlock(
            Block.explodeMaterialized("$$hashtags").out("*authorId", "*statusId", "*timestamp", "*hashtags")
                 .each(Ops.EXPLODE, "*hashtags").out("*hashtag")
                 .hashPartition("*hashtag")
                 .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, "*authorId", "*statusId").out("*statusPointer")
                 .macro(hashtagToStatusPointers.addItem("*hashtag", "*statusPointer")))

          .hook("dataRoot")
          .batchBlock(
              Block.subBatch(accountHashtagTimelineSubbatch("$$hashtags", "$$accountIdToHashtagActivity")).out("*authorId", "*hashtag")
                   .hashPartition("*authorId")
                   .compoundAgg("$$accountIdToRecentHashtags", CompoundAgg.map("*authorId", Agg.list("*hashtag")))
                   .agg(Agg.list("*authorId")).out("*authors")
                   .each(Ops.EXPLODE, "*authors").out("*authorId")
                   .localSelect("$$accountIdToRecentHashtags", Path.key("*authorId")).out("*before")
                   .localTransform("$$accountIdToRecentHashtags", Path.key("*authorId").term(TrendsAndHashtags::consolidateRecentHashtags, accountRecentHashtagsAmount))
                   .localSelect("$$accountIdToRecentHashtags", Path.key("*authorId")).out("*after"))


          .hook("dataRoot")
          .macro(trendsRankingMacro(TrendsAndHashtags::itemHistorySubBatch, "$$hashtags", "$$hashtagStats", "$$hashtagTrends"))

          .hook("dataRoot")
          .macro(trendsRankingMacro(TrendsAndHashtags::itemHistorySubBatch, "$$links", "$$linkStats", "$$linkTrends"))

          .hook("dataRoot")
          .macro(trendsRankingMacro(TrendsAndHashtags::statusHistorySubBatch, "$$statusPoints", "$$statusStats", "$$statusTrends"));

      core.source("*favoriteStatusDepot").out("*microbatch")
          .batchBlock(
              Block.explodeMicrobatch("*microbatch").out("*data")
              .keepTrue(new Expr(Ops.IS_INSTANCE_OF, FavoriteStatus.class, "*data"))
              .macro(extractFields("*data", "*accountId", "*target", "*timestamp"))
              .each(StatusPointer::getAuthorId, "*target").out("*pointAuthorId")
              .each(StatusPointer::getStatusId, "*target").out("*pointStatusId")
              .materialize("*pointAuthorId", "*pointStatusId", "*accountId", "*timestamp").out("$$statusFavoritePoints"))
          .macro(trendsRankingMacro(TrendsAndHashtags::statusHistorySubBatch, "$$statusFavoritePoints", "$$statusStats", "$$statusTrends"));

      core.source("*decayTick").out("*microbatch")
          .explodeMicrobatch("*microbatch")
          .globalPartition("$$hashtagTrends")
          .localTransform("$$hashtagTrends", Path.term(TrendsAndHashtags::decayTrends))
          .localTransform("$$linkTrends", Path.term(TrendsAndHashtags::decayTrends))
          .localTransform("$$statusTrends", Path.term(TrendsAndHashtags::decayTrends));

      core.source("*reviewTick").out("*microbatch")
          .explodeMicrobatch("*microbatch")
          .macro(computeReviewedTrendsBlock("$$hashtagTrends", "$$reviewedHashtags", "$$reviewedHashtagTrends"))
          .macro(computeReviewedTrendsBlock("$$linkTrends", "$$reviewedLinks", "$$reviewedLinkTrends"));

      StreamTopology review = topologies.stream("review");
      reviewImpl(review, "*reviewHashtagDepot", "$$reviewedHashtags");
      reviewImpl(review, "*reviewLinkDepot", "$$reviewedLinks");

      StreamTopology feature = topologies.stream("feature");
      KeyToLinkedEntitySetPStateGroup featurerToHashtags = new KeyToLinkedEntitySetPStateGroup("$$featurerToHashtags", Long.class, String.class);
      featurerToHashtags.declarePStates(feature);
      feature.pstate("$$accountIdToFeaturedHashtags", PState.mapSchema(Long.class, List.class));
      feature.source("*featureHashtagDepot", StreamSourceOptions.retryAllAfter()).out("*data")
             .subSource("*data",
                        SubSource.create(FeatureHashtag.class)
                                 .macro(extractFields("*data", "*accountId", "*hashtag"))
                                 .localSelect("$$featurerToHashtags", Path.key("*accountId").view(Ops.SIZE)).out("*currSize")
                                 .ifTrue(new Expr(Ops.LESS_THAN, "*currSize", featureHashtagLimit),
                                   Block.macro(featurerToHashtags.addToLinkedSet("*accountId", "*hashtag"))),
                        SubSource.create(RemoveFeatureHashtag.class)
                                 .macro(extractFields("*data", "*accountId", "*hashtag"))
                                 .macro(featurerToHashtags.removeFromLinkedSet("*accountId", "*hashtag")));

      batchItemQuery(topologies, "batchHashtagStats", "$$hashtagStats", "$$reviewedHashtags");
      batchItemQuery(topologies, "batchLinkStats", "$$linkStats", "$$reviewedLinks");

      topologies.query("getHashtagTimeline", "*hashtag", "*requestAccountId", "*firstTimelineIndex", "*limit").out("*results")
                .hashPartition("*hashtag")
                .each((Integer limit) -> SortedRangeFromOptions.excludeStart().maxAmt(limit), "*limit").out("*sortedOptions")
                .localSelect("$$hashtagToStatusPointers",
                  Path.subselect(Path.key("*hashtag")
                                     .sortedMapRangeFrom("*firstTimelineIndex", "*sortedOptions")
                                     .mapVals())).out("*statusPointers")
                .each(() -> new QueryFilterOptions(FilterContext.Public, true)).out("*filterOptions")
                .invokeQuery("*getStatusesFromPointers", "*requestAccountId", "*statusPointers", "*filterOptions").out("*statusQueryResults")
                .each(MastodonHelpers::updateStatusQueryResults, "*statusQueryResults", "*statusPointers", "*limit", false).out("*results")
                .originPartition();

      topologies.query("getFeaturedHashtags", "*accountId").out("*results")
                .hashPartition("*accountId")
                .localSelect("$$featurerToHashtagsById", Path.key("*accountId").mapVals()).out("*hashtag")
                .localSelect("$$accountIdToHashtagActivity", Path.key("*accountId", "*hashtag")
                                                                 .subselect(Path.multiPath(Path.key("timeline").view(Ops.SIZE),
                                                                                           Path.key("lastStatusMillis").nullToVal(-1L)))).out("*tuple")
                .each(Ops.EXPAND, "*tuple").out("*numStatuses", "*lastStatusMillis")
                .each((RamaFunction3<String, Integer, Long, FeaturedHashtagInfo>) FeaturedHashtagInfo::new, "*hashtag", "*numStatuses", "*lastStatusMillis").out("*featuredHashtagInfo")
                .originPartition()
                .agg(Agg.list("*featuredHashtagInfo")).out("*results");
  }
}
