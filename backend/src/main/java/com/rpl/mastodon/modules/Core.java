package com.rpl.mastodon.modules;

import clojure.lang.*;
import com.rpl.mastodon.data.*;
import com.clearspring.analytics.stream.membership.BloomFilter;
import com.rpl.mastodon.*;
import com.rpl.mastodon.navs.*;
import com.rpl.rama.helpers.*;
import com.rpl.rama.integration.*;
import com.rpl.rama.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.thrift.TBase;

import static com.rpl.mastodon.MastodonHelpers.extractFields;

/*
 * This module implements the main parts of Mastodon – timelines, statuses, and profiles. They're
 * kept colocated together because the most important part of the app, rendering a page of a home timeline,
 * needs to fetch a lot of timeline, status, and profile data at the same time. Keeping them colocated
 * increases the efficiency of these queries and lowers the latency.
 */
public class Core implements RamaModule {
  private static final int DESCENDANT_SEARCH_LIMIT = 5000;

  public static final int PINNED_STATUS_MAX_AMOUNT = 5;
  public static final int DEFAULT_TIMELINE_MAX_AMOUNT = 600;

  // not constants so they can be changed in tests
  public int timelineMaxAmount = DEFAULT_TIMELINE_MAX_AMOUNT;
  public int singlePartitionFanoutLimit = 10000;
  public int rangeQueryLimit = 1000;
  public int maxEditCount = 100;
  public int scheduledStatusTickMillis = 30000;
  public boolean enableHomeTimelineRefresh = true;

  // A bloom filter of all of an account's follows is used to reduce PState queries
  // when filtering replies during fanout. These are kept durably in a PState on this
  // module and also cached in memory.
  public static class RBloomFilter implements RamaSerializable {
    public BloomFilter bloom = new BloomFilter(500, 0.01);

    private void writeObject(ObjectOutputStream out) throws IOException {
      byte[] ser = BloomFilter.serialize(bloom);
      out.writeInt(ser.length);
      out.write(ser);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      byte[] ser = new byte[in.readInt()];
      in.read(ser);
      bloom = BloomFilter.deserialize(ser);
    }
  }

  // This is the representation of each home timeline, kept in-memory on the module. To acheive fault-tolerance,
  // these are recomputed from scratch on read if the queried account's home timeline is missing. In this case
  // the home timeline is reconstructed by looking at recent statuses of the account's follows (see the "refreshHomeTimeline"
  // query topology below.)
  public static class Timeline {
    // To reduce memory usage and avoid GC pressure, the timeline is represented with an array of primitives.
    public long[] buffer = null;
    public int startIndex = 0; // index within buffer that contains oldest timeline element
    public int numElems = 0; // number of elements in this timeline
    public long startIndexTimelineIndex = Long.MAX_VALUE; // timeline index (always decreasing for every append) of startIndex
    private int _bufferAmount;
    public boolean isRefreshed = false;
    public long lastFetchAccountId = -1;
    public long lastFetchStatusId = -1;
    public long lastFetchTimelineIndex = -1;
    // This is where the bloom filter of an account's follows is cached.
    public RBloomFilter rbloom = null;

    public Timeline(int bufferAmount, boolean enableRefreshes) {
      _bufferAmount = bufferAmount;
      if(!enableRefreshes) buffer = new long[2*_bufferAmount];
    }

    public void addItem(long authorId, long statusId) {
      if(buffer==null) return;
      int targetIndex = (startIndex + 2 * numElems) % buffer.length;
      buffer[targetIndex] = authorId;
      buffer[targetIndex + 1] = statusId;
      if(numElems==_bufferAmount) {
        startIndex = (startIndex + 2) % buffer.length;
        startIndexTimelineIndex--;
      } else numElems++;
    }

    public void refreshStatuses(List<List> tuples) {
      if(tuples.size() > 0) {
        if(buffer==null) buffer = new long[2*_bufferAmount];
        List<StatusPointer> existing = readTimelineFrom(new StatusPointer(-1, -1), null, numElems);
        Set<StatusPointer> existingSet = new HashSet(existing);

        int numToAdd = _bufferAmount - numElems;

        List<List> appendable = new ArrayList();
        for(List tuple: tuples) {
          if(appendable.size() == numToAdd) break;
          if(!existingSet.contains(new StatusPointer((Long) tuple.get(1), (Long) tuple.get(2)))) appendable.add(tuple);
        }
        numElems = 0;
        startIndexTimelineIndex = Long.MAX_VALUE;
        lastFetchAccountId = -1;
        lastFetchStatusId = -1;
        lastFetchTimelineIndex = -1;
        for(int i=0; i<numToAdd && i<appendable.size(); i++) {
          List tuple = appendable.get(appendable.size() - 1 - i);
          addItem((Long) tuple.get(1), (Long) tuple.get(2));
        }
        Collections.reverse(existing);
        for(StatusPointer sp: existing) addItem(sp.authorId, sp.statusId);
        isRefreshed = true;
      }
    }

    // excludes the start
    public List<StatusPointer> readTimelineFrom(StatusPointer firstStatusPointer, StatusPointer endPointer, int maxAmt) {
      if(buffer==null) return new ArrayList();
      long timelineIndex = -1;
      // - this is an optimization to deal with mastodon/soapbox design of paginating timeline using status ID
      // instead of a timeline index
      // - for soapbox pagination always uses the last status pointer from the previous page
      // - the optimization here will fail if user has two clients open at once, so it falls back on a scan
      //   in this case
      // - the scan is only over 600 entries in memory, per page, so it's not bad
      if(firstStatusPointer.authorId == lastFetchAccountId && firstStatusPointer.statusId == lastFetchStatusId) {
        timelineIndex = lastFetchTimelineIndex;
      } else if(firstStatusPointer.statusId >= 0) {
        for(int i=0; i<numElems; i++) {
          int j = (startIndex + 2 * i) % buffer.length;
          if(buffer[j] == firstStatusPointer.authorId && buffer[j+1] == firstStatusPointer.statusId) {
            timelineIndex = startIndexTimelineIndex - i;
            break;
          }
        }
      }

      timelineIndex++; // to exclude the start
      List<StatusPointer> ret = new ArrayList();
      long distance = startIndexTimelineIndex - timelineIndex;
      if(distance >= 0)  {
        int startDistance = (int) Math.min((long)numElems-1, distance);
        int retrieveStartIndex = (startIndex + 2 * startDistance) % buffer.length;
        long retrieveStartTimelineIndex = startIndexTimelineIndex - startDistance;
        for(int i=0; i < maxAmt && i <= startDistance; i++) {
          int j = retrieveStartIndex - 2*i;
          if(j < 0) j = buffer.length + j;
          StatusPointer next = new StatusPointer(buffer[j], buffer[j+1]);
          if(next.equals(endPointer)) break;
          ret.add(next);
        }
        if(!ret.isEmpty() && endPointer != null) {
          StatusPointer sp = ret.get(ret.size() - 1);
          lastFetchAccountId = sp.authorId;
          lastFetchStatusId = sp.statusId;
          lastFetchTimelineIndex = retrieveStartTimelineIndex + ret.size() - 1;
        }
      }
      return ret;
    }
  }

  // This holds all in-memory home timelines for accounts on this partition of the module.
  // See the call to ".declareObject" below for how a separate instance of this is instantiated
  // on every task.
  public static class HomeTimelines implements TaskGlobalObject {
    public HashMap<Long, Timeline> timelines; // accountId to Timeline
    public HashSet<List> lastMicrobatchWrites; // contains tuples of [target, accountId, statusId]
    public Long lastMicrobatchId = null;
    private int _bufferAmount;
    private boolean _enableRefreshes;

    public HomeTimelines(int bufferAmount, boolean enableRefreshes) {
      _bufferAmount = bufferAmount;
      _enableRefreshes = enableRefreshes;
    }

    @Override
    public void prepareForTask(int taskId, TaskGlobalContext context) {
      timelines = new HashMap();
      lastMicrobatchWrites = new HashSet();
    }

    private Timeline getTimeline(long accountId) {
      Timeline timeline = timelines.get(accountId);
      if(timeline==null) {
        timeline = new Timeline(_bufferAmount, _enableRefreshes);
        timelines.put(accountId, timeline);
      }
      return timeline;
    }

    public Object addTimelineItem(Long targetId, StatusPointer pointer, Long microbatchId) {
      // ensures exactly-once write semantics for failed microbatches, and prevents
      // hashtag fanout and follower fanout from both writing the status to one follower's
      // timeline in same iteration
      if(microbatchId != lastMicrobatchId) {
        lastMicrobatchId = microbatchId;
        lastMicrobatchWrites = new HashSet();
      }
      List tuple = Arrays.asList(targetId, pointer.authorId, pointer.statusId);
      if(lastMicrobatchWrites.contains(tuple)) return null;
      else lastMicrobatchWrites.add(tuple);
      if(!_enableRefreshes || !needsRefresh(targetId)) getTimeline(targetId).addItem(pointer.authorId, pointer.statusId);
      return null;
    }

    public List<StatusPointer> readTimelineFrom(long accountId, StatusPointer firstStatusPointer, int maxAmt) {
      return getTimeline(accountId).readTimelineFrom(firstStatusPointer, null, maxAmt);
    }

    public List<StatusPointer> readTimelineUntil(long accountId, StatusPointer endStatusPointer, int maxAmt) {
      Timeline timeline = timelines.get(accountId);
      if(timeline==null) return new ArrayList();
      return timeline.readTimelineFrom(new StatusPointer(-1, -1), endStatusPointer, maxAmt);
    }

    public boolean needsRefresh(long accountId) {
      return !getTimeline(accountId).isRefreshed;
    }

    public Object refreshStatuses(long accountId, List<List> tuples) {
      getTimeline(accountId).refreshStatuses(tuples);
      return null;
    }

    public RBloomFilter getBloomFilter(long accountId) {
      return getTimeline(accountId).rbloom;
    }

    public Object setBloomFilter(long accountId, RBloomFilter rbloom) {
      getTimeline(accountId).rbloom = rbloom;
      return null;
    }

    @Override
    public void close() { }
  }

  private static RBloomFilter initBloom(RBloomFilter rbloom) {
    return rbloom == null ? new RBloomFilter() : rbloom;
  }

  public static Block fetchBloomMacro(String accountIdVar, String outVar) {
    return Block.each(HomeTimelines::getBloomFilter, "*homeTimelines", accountIdVar).out(outVar)
                .ifTrue(new Expr(Ops.IS_NULL, outVar),
                  Block.localSelect("$$followsBloom", Path.key(accountIdVar).view(Core::initBloom)).out(outVar)
                       .each(HomeTimelines::setBloomFilter, "*homeTimelines", accountIdVar, outVar),
                  Block.each(Ops.IDENTITY, outVar).out(outVar));
  }


  // This topology maintains the follow bloom filters for each account.
  private void declareFollowsBloomFiltersTopology(Topologies topologies) {
    MicrobatchTopology mb = topologies.microbatch("bloom");
    mb.pstate("$$followsBloom", PState.mapSchema(Long.class, RBloomFilter.class));

    mb.source("*followAndBlockAccountDepot").out("*microbatch")
      .batchBlock(
        Block.explodeMicrobatch("*microbatch").out("*data")
             .keepTrue(new Expr(Ops.IS_INSTANCE_OF, FollowAccount.class, "*data"))
             .macro(extractFields("*data", "*accountId", "*targetId"))
             // When generating a social graph from scratch to do load testing, it's
             // much more efficient to do many adds to a bloom filter at the same time
             // rather than read and write them from the PState for every follow.
             .groupBy("*accountId",
               Block.agg(Agg.set("*targetId")).out("*adds"))
             .macro(fetchBloomMacro("*accountId", "*rbloom"))
             .each((RBloomFilter rbloom, Set<Long> accountIds) -> {
               for(Long accountId: accountIds) rbloom.bloom.add("" + accountId);
               return null;
             }, "*rbloom", "*adds")
             .localTransform("$$followsBloom", Path.key("*accountId").termVal("*rbloom")));
  }

  public static Object lastItem(Object c, boolean isMap) {
      return isMap ? ((SortedMap) c).lastKey() : ((SortedSet) c).last();
  }

  public static int rangeResultSize(Object c, boolean isMap) {
      return isMap ? ((SortedMap) c).size() : ((SortedSet) c).size();
  }

  public Block safeFetchFollowers(boolean isMap, String pstateVar, String keyVar, Object startId, Object fanoutLimit, String outFollowerIdsVar, String outNextIdVar) {
        String loopIdVar = Helpers.genVar("loopId");
        String nextLoopIdVar = Helpers.genVar("nextLoopId");
        String subVar = Helpers.genVar("sub");
        SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(rangeQueryLimit);
        Path.Impl p = Path.key(keyVar);
        p = isMap ? p.sortedMapRangeFrom(loopIdVar, options) : p.sortedSetRangeFrom(loopIdVar, options);

        return Block.each((RamaFunction0) ArrayList::new).out(outFollowerIdsVar)
                    .loopWithVars(LoopVars.var(loopIdVar, startId),
                      Block.yieldIfOvertime()
                           .localSelect(pstateVar, p).out(subVar)
                           .each((List l, Object c, Boolean ism) -> {
                               for(Object o: ism ? ((SortedMap) c).values() : (SortedSet) c) l.add(o);
                               return null;
                           }, outFollowerIdsVar, subVar, isMap)
                           .each((Object c, Boolean ism, Integer rangeQueryLimit) -> rangeResultSize(c, ism) < rangeQueryLimit ? null : lastItem(c, ism), subVar, isMap, rangeQueryLimit).out(nextLoopIdVar)
                           .ifTrue(new Expr(Ops.OR, new Expr(Ops.IS_NULL, nextLoopIdVar),
                                                    new Expr(Ops.GREATER_THAN_OR_EQUAL, new Expr(Ops.SIZE, outFollowerIdsVar), fanoutLimit)),
                             Block.emitLoop(nextLoopIdVar),
                             Block.continueLoop(nextLoopIdVar))).out(outNextIdVar);
    }

  public Block safeFetchMapLocalFollowers(String pstateVar, String keyVar, Object startId, Object fanoutLimit, String outFollowerIdsVar, String outNextIdVar) {
      return safeFetchFollowers(true, pstateVar, keyVar, startId, fanoutLimit, outFollowerIdsVar, outNextIdVar);
  }

  public Block safeFetchSetFollowers(String pstateVar, String keyVar, Object startId, Object fanoutLimit, String outFollowerIdsVar, String outNextIdVar) {
      return safeFetchFollowers(false, pstateVar, keyVar, startId, fanoutLimit, outFollowerIdsVar, outNextIdVar);
  }

  public Block safeFetchMapRemoteFollowers(String pstateVar, String keyVar, Object startId, String outFollowerIdsVar, String outNextIdVar) {
        String loopIdVar = Helpers.genVar("loopId");
        String nextLoopIdVar = Helpers.genVar("nextLoopId");
        String subVar = Helpers.genVar("sub");
        SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(rangeQueryLimit);
        Path.Impl p = Path.subselect(Path.key(keyVar).sortedMapRangeFrom(loopIdVar, options).mapKeys());

        return Block.each((RamaFunction0) ArrayList::new).out(outFollowerIdsVar)
                    .loopWithVars(LoopVars.var(loopIdVar, startId),
                      Block.yieldIfOvertime()
                           .localSelect(pstateVar, p).out(subVar)
                           .each((List l, List c) -> l.addAll(c), outFollowerIdsVar, subVar)
                           .each((List c, Integer rangeQueryLimit) -> c.size() < rangeQueryLimit ? null : c.get(c.size()-1), subVar, rangeQueryLimit).out(nextLoopIdVar)
                           .ifTrue(new Expr(Ops.OR, new Expr(Ops.IS_NULL, nextLoopIdVar),
                                                    new Expr(Ops.GREATER_THAN_OR_EQUAL, new Expr(Ops.SIZE, outFollowerIdsVar), singlePartitionFanoutLimit)),
                             Block.emitLoop(nextLoopIdVar),
                             Block.continueLoop(nextLoopIdVar))).out(outNextIdVar);
  }

  private static boolean needsNewPollVersion(Status prev, Status next) {
    if(prev==null) return false;
    PollContent pcprev = MastodonHelpers.extractPollContent(prev);
    PollContent pcnext = MastodonHelpers.extractPollContent(next);
    if(pcnext == null || pcprev == null) return false;
    else return !pcprev.getChoices().equals(pcnext.getChoices());
  }

  // assumes computation already partitioned by authorIdVar
  public Block conversationFanout(String authorIdVar, String statusVar, String statusIdVar, String contentVar, TaskUniqueIdPState conversationStatusIndex, KeyToLinkedEntitySetPStateGroup accountIdToDirectMessages, KeyToLinkedEntitySetPStateGroup accountIdToConvoIds) {
    String statusPointerVar = Helpers.genVar("statusPointer");
    String convoIdVar = Helpers.genVar("convoId");
    String senderStatusIndexVar = Helpers.genVar("senderStatusIndex");
    String receiverStatusIndexVar = Helpers.genVar("receiverStatusIndex");
    Block ret =
            Block.each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, authorIdVar, statusIdVar).out(statusPointerVar)

                 .macro(extractFields(contentVar, "*text"))
                 .macro(conversationStatusIndex.genId(senderStatusIndexVar))
                 .localSelect("$$statusIdToConvoId", Path.key(statusIdVar).nullToVal(statusIdVar)).out(convoIdVar)

                 .localTransform("$$accountIdToConvoIdToConvo", Path.key(authorIdVar, convoIdVar, "timeline", senderStatusIndexVar).termVal(statusPointerVar))
                 .localTransform("$$accountIdToConvoIdToConvo", Path.key(authorIdVar, convoIdVar, "unread").termVal(true))
                 .macro(accountIdToConvoIds.removeFromLinkedSet(authorIdVar, convoIdVar))
                 .macro(accountIdToConvoIds.addToLinkedSet(authorIdVar, convoIdVar))
                 .macro(accountIdToDirectMessages.addToLinkedSet(authorIdVar, statusPointerVar))

                 .each(Token::parseTokens, "*text").out("*tokens")
                 .each(Ops.EXPLODE, "*tokens").out("*token")
                 .each((Token token) -> token.kind, "*token").out("*kind")
                 .ifTrue(new Expr(Ops.EQUAL, Token.TokenKind.MENTION, "*kind"),
                         Block.each((Token token) -> token.content, "*token").out("*mention")
                              .select("$$nameToUser", Path.key("*mention").must("accountId")).out("*accountId")
                              .keepTrue(new Expr(Ops.NOT_EQUAL, "*accountId", authorIdVar)) // no need to fan out to author since it is already saved there above
                              .hashPartition("*accountId")
                              .macro(conversationStatusIndex.genId(receiverStatusIndexVar))
                              .localTransform("$$accountIdToConvoIdToConvo", Path.key("*accountId", convoIdVar, "timeline", receiverStatusIndexVar).termVal(statusPointerVar))
                              .localTransform("$$accountIdToConvoIdToConvo", Path.key("*accountId", convoIdVar, "unread").termVal(true))
                              .localTransform("$$accountIdToConvoIdToConvo", Path.key("*accountId", convoIdVar, "accountIds").voidSetElem().termVal(authorIdVar))
                              .macro(accountIdToConvoIds.removeFromLinkedSet("*accountId", convoIdVar))
                              .macro(accountIdToConvoIds.addToLinkedSet("*accountId", convoIdVar))
                              .macro(accountIdToDirectMessages.addToLinkedSet("*accountId", statusPointerVar)),
                         Block.ifTrue(new Expr(Ops.EQUAL, Token.TokenKind.REMOTE_MENTION, "*kind"),
                                      Block.select("$$accountIdToAccount", Path.key(authorIdVar).filterPred(Ops.IS_NOT_NULL)).out("*author")
                                           .each((Account account) -> account.content.isSetLocal() ? account.content.getLocal() : null, "*author").out("*authorContent")
                                           .keepTrue(new Expr(Ops.IS_NOT_NULL, "*authorContent"))
                                           // get the parent status (if reply)
                                           .ifTrue(new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content"),
                                                   Block.macro(extractFields("*content", "*parent"))
                                                        .each((StatusPointer parent) -> parent.authorId, "*parent").out("*parentAuthorId")
                                                        .each((StatusPointer parent) -> parent.statusId, "*parent").out("*parentStatusId")
                                                        .select("$$accountIdToStatuses", Path.key("*parentAuthorId", "*parentStatusId").first()).out("*parentStatus"),
                                                   Block.each(Ops.IDENTITY, null).out("*parentStatus"))
                                           // get the boosted status (if boost)
                                           .ifTrue(new Expr(Ops.IS_INSTANCE_OF, BoostStatusContent.class, "*content"),
                                                   Block.macro(extractFields("*content", "*boosted"))
                                                        .each((StatusPointer boosted) -> boosted.authorId, "*boosted").out("*boostedAuthorId")
                                                        .each((StatusPointer boosted) -> boosted.statusId, "*boosted").out("*boostedStatusId")
                                                        .select("$$accountIdToStatuses", Path.key("*boostedAuthorId", "*boostedStatusId").first()).out("*boostedStatus"),
                                                   Block.each(Ops.IDENTITY, null).out("*boostedStatus"))
                                           // get mentioned account
                                           .each((Token token) -> token.content, "*token").out("*mention")
                                           .select("$$nameToUser", Path.key("*mention").must("accountId")).out("*accountId")
                                           .select("$$accountIdToAccount", Path.key("*accountId").filterPred(Ops.IS_NOT_NULL)).out("*account")
                                           .each((Account account) -> account.content.isSetRemote() ? account.content.getRemote() : null, "*account").out("*accountContent")
                                           .keepTrue(new Expr(Ops.IS_NOT_NULL, "*accountContent"))
                                           .macro(extractFields("*accountContent", "*mainUrl", "*inboxUrl"))
                                           // send status to mentioned remote account
                                           .each(MastodonWebHelpers::sendRemoteStatus, FanoutAction.Add, "*author", statusIdVar, statusVar, "*parentStatus", "*boostedStatus", "*mainUrl", "*inboxUrl")));
    return Block.atomicBlock(ret);
  }

  private void declareMicrobatchTopologies(Topologies topologies) {
    MicrobatchTopology fan = topologies.microbatch("fanout");

    // fanout pstates
    fan.pstate("$$statusIdToLocalFollowerFanouts", PState.mapSchema(Long.class, List.class)); // List<FollowerFanout>
    fan.pstate("$$statusIdToRemoteFollowerFanout", PState.mapSchema(Long.class, FollowerFanout.class));
    fan.pstate("$$statusIdToListFanout", PState.mapSchema(Long.class, ListFanout.class));
    fan.pstate("$$hashtagFanoutToIndex", PState.mapSchema(HashtagFanout.class, Long.class));


    // conversations
    TaskUniqueIdPState conversationStatusIndex = new TaskUniqueIdPState("$$conversationStatusIndex").descending();
    conversationStatusIndex.declarePState(fan);
    KeyToLinkedEntitySetPStateGroup accountIdToConvoIds =
            new KeyToLinkedEntitySetPStateGroup("$$accountIdToConvoIds", Long.class, Long.class).descending();
    accountIdToConvoIds.declarePStates(fan);
    fan.pstate("$$accountIdToConvoIdToConvo",
               PState.mapSchema(Long.class, // account id
                                PState.mapSchema(Long.class, // conversation id
                                                 PState.fixedKeysSchema("timeline", PState.mapSchema(Long.class, // status index
                                                                                                     StatusPointer.class).subindexed(),
                                                                        "unread", Boolean.class,
                                                                        "accountIds", PState.setSchema(Long.class))).subindexed()));

    fan.source("*conversationDepot").out("*microbatch")
       .explodeMicrobatch("*microbatch").out("*data")
       .subSource("*data",
           SubSource.create(EditConversation.class)
                    .macro(extractFields("*data", "*accountId", "*conversationId", "*unread"))
                    .localTransform("$$accountIdToConvoIdToConvo", Path.key("*accountId", "*conversationId", "unread").termVal("*unread")),
           SubSource.create(RemoveConversation.class)
                    .macro(extractFields("*data", "*accountId", "*conversationId"))
                    .localTransform("$$accountIdToConvoIdToConvo", Path.key("*accountId", "*conversationId").termVoid())
                    .macro(accountIdToConvoIds.removeFromLinkedSet("*accountId", "*conversationId")));

    KeyToUniqueFixedItemsPStateGroup listIdToListTimeline = new KeyToUniqueFixedItemsPStateGroup("$$listIdToListTimeline", timelineMaxAmount, Long.class, StatusPointer.class);
    listIdToListTimeline.declarePStates(fan);
    KeyToLinkedEntitySetPStateGroup accountIdToDirectMessages =
        // stores all DMs in a flat list
        // necessary for the old (deprecated) DM timeline and for the streaming API
        new KeyToLinkedEntitySetPStateGroup("$$accountIdToDirectMessages", Long.class, StatusPointer.class).descending();
    accountIdToDirectMessages.declarePStates(fan);

    fan.source("*statusWithIdDepot").out("*microbatch")
       .anchor("FanoutRoot")

       // continue fanout of new statuses to local followers
       .allPartition()
       .localSelect("$$statusIdToLocalFollowerFanouts", Path.all()).out("*keyAndVal")
       .each(Ops.EXPAND, "*keyAndVal").out("*statusId", "*followerFanouts")
       .localTransform("$$statusIdToLocalFollowerFanouts", Path.key("*statusId").termVoid())
       .each(Ops.EXPLODE, "*followerFanouts").out("*followerFanout")
       .macro(extractFields("*followerFanout", "*authorId", "*nextIndex", "*fanoutAction", "*status", "*task"))
       .each(FanoutAction::getValue, "*fanoutAction").out("*fanoutActionValue")
       .macro(extractFields("*status", "*content", "*language"))
       .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, "*authorId", "*statusId").out("*statusPointer")
       .directPartition("$$partitionedFollowers", "*task")
       .anchor("LocalFollowerFanoutContinue")

       // continue fanout of new/edited/removed statuses to remote followers
       .hook("FanoutRoot")
       .allPartition()
       .localSelect("$$statusIdToRemoteFollowerFanout", Path.all()).out("*keyAndVal")
       .each(Ops.EXPAND, "*keyAndVal").out("*statusId", "*followerFanout")
       .localTransform("$$statusIdToRemoteFollowerFanout", Path.key("*statusId").termVoid())
       .macro(extractFields("*followerFanout", "*authorId", "*nextIndex", "*fanoutAction", "*status"))
       .each(FanoutAction::getValue, "*fanoutAction").out("*fanoutActionValue")
       .macro(extractFields("*status", "*content", "*language"))
       .anchor("RemoteFollowerFanoutContinue")

       // continue fanout of new statuses to lists
       .hook("FanoutRoot")
       .allPartition()
       .localSelect("$$statusIdToListFanout", Path.all()).out("*keyAndVal")
       .each(Ops.EXPAND, "*keyAndVal").out("*statusId", "*listFanout")
       .localTransform("$$statusIdToListFanout", Path.key("*statusId").termVoid())
       .macro(extractFields("*listFanout", "*authorId", "*nextIndex", "*status"))
       .macro(extractFields("*status", "*content"))
       .anchor("ListFanoutContinue")

       // continue fanout of new statuses to hashtags
       .hook("FanoutRoot")
       .allPartition()
       .localSelect("$$hashtagFanoutToIndex", Path.all()).out("*keyAndVal")
       .each(Ops.EXPAND, "*keyAndVal").out("*hashtagFanout", "*nextIndex")
       .localTransform("$$hashtagFanoutToIndex", Path.key("*hashtagFanout").termVoid())
       .macro(extractFields("*hashtagFanout", "*authorId", "*statusId", "*hashtag"))
       .anchor("HashtagFanoutContinue")

       // handle incoming depot appends
       .hook("FanoutRoot")
       .explodeMicrobatch("*microbatch").out("*data")
       .subSource("*data",
          SubSource.create(EditStatus.class)
                   .macro(extractFields("*data", "*statusId", "*status"))
                   .macro(extractFields("*status", "*authorId", "*content"))
                   .each(Ops.IDENTITY, 0L).out("*nextIndex")
                   .each(Ops.IDENTITY, FanoutAction.Edit.getValue()).out("*fanoutActionValue")
                   .anchor("EditRemoteFollowerFanout"),
          SubSource.create(RemoveStatusWithId.class)
                   .macro(extractFields("*data", "*statusId", "*status"))
                   .macro(extractFields("*status", "*authorId", "*content"))
                   .each(Ops.IDENTITY, 0L).out("*nextIndex")
                   .each(Ops.IDENTITY, FanoutAction.Remove.getValue()).out("*fanoutActionValue")
                   .anchor("RemoveRemoteFollowerFanout"),
          SubSource.create(StatusWithId.class)
                   .macro(extractFields("*data", "*statusId", "*status"))
                   .macro(extractFields("*status", "*authorId", "*content", "*language"))
                   // get the visibility
                   .each(MastodonHelpers::getStatusVisibility, "*status").out("*visibility")

                   // fan out to timelines
                   .ifTrue(new Expr(Ops.EQUAL, "*visibility", StatusVisibility.Direct),
                           Block.macro(conversationFanout("*authorId", "*status", "*statusId", "*content", conversationStatusIndex, accountIdToDirectMessages, accountIdToConvoIds)),
                           Block.each(Ops.IDENTITY, -1L).out("*nextIndex")
                                .each(Ops.IDENTITY, FanoutAction.Add.getValue()).out("*fanoutActionValue")
                                .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, "*authorId", "*statusId").out("*statusPointer")
                                .each(HomeTimelines::addTimelineItem, "*homeTimelines", "*authorId", "*statusPointer", new Expr(Ops.CURRENT_MICROBATCH_ID))
                                .anchor("NormalFanoutBegin")
                                // if status is not a boost, parse and fan out to the hashtags
                                .ifTrue(new Expr(Ops.OR, new Expr(Ops.IS_INSTANCE_OF, NormalStatusContent.class, "*content"),
                                                         new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content")),
                                        Block.keepTrue(new Expr(Ops.NOT_EQUAL, "*visibility", StatusVisibility.Unlisted))
                                             .macro(extractFields("*content", "*text"))
                                             .each(Token::parseTokens, "*text").out("*tokens")
                                             .each(Token::filterHashtags, "*tokens").out("*hashtags")
                                             .each(Ops.EXPLODE, "*hashtags").out("*hashtag")
                                             .anchor("NormalHashtagFanout"))))

       .hook("NormalFanoutBegin")
       .select("$$partitionedFollowersControl", Path.key("*authorId")).out("*tasks")
       .each(Ops.EXPLODE_INDEXED, "*tasks").out("*i", "*task")
       // because the first task is always the same as $$partitionedFollowersControl task for *authorId
       .ifTrue(new Expr(Ops.NOT_EQUAL, 0, "*i"), Block.directPartition("$$partitionedFollowers", "*task"))
       .anchor("NormalFanout")

       // fanout new status to local followers
       .unify("NormalFanout", "LocalFollowerFanoutContinue")
       .macro(safeFetchMapLocalFollowers("$$partitionedFollowers", "*authorId", "*nextIndex", rangeQueryLimit, "*fetchedFollowers", "*nextFollowerId"))
       // update fanout pstate if necessary
       .ifTrue(new Expr(Ops.IS_NOT_NULL, "*nextFollowerId"),
               Block.each((RamaFunction5<Long, Long, FanoutAction, Status, Integer, FollowerFanout>) FollowerFanout::new, "*authorId", "*nextFollowerId", new Expr(FanoutAction::findByValue, "*fanoutActionValue"), "*status", "*task").out("*followerFanout")
                    .localTransform("$$statusIdToLocalFollowerFanouts", Path.key("*statusId").nullToList().afterElem().termVal("*followerFanout")))
       .each(Ops.EXPLODE, "*fetchedFollowers").out("*follower")
       .each((Follower follower) -> follower.accountId, "*follower").out("*followerId")
       .each((Follower follower) -> follower.sharedInboxUrl, "*follower").out("*followerSharedInboxUrl")
       .each((Follower follower) -> follower.isShowBoosts(), "*follower").out("*showBoosts")
       .each((Follower follower) -> follower.getLanguages(), "*follower").out("*languages")
       .keepTrue(new Expr(Ops.IS_NULL, "*followerSharedInboxUrl")) // skip remote followers
       // stop if it's a boost of the recipient or of someone for whom the recipient disabled boosts
       .ifTrue(new Expr(Ops.IS_INSTANCE_OF, BoostStatusContent.class, "*content"),
               Block.macro(extractFields("*content", "*boosted"))
                    .each((StatusPointer boosted) -> boosted.authorId, "*boosted").out("*boostedAuthorId")
                    .keepTrue(new Expr(Ops.NOT_EQUAL, "*boostedAuthorId", "*followerId"))
                    .keepTrue("*showBoosts"))
       // stop if language is set on both status and follower and the language doesn't match
       .keepTrue(new Expr((List<String> languages, String statusLanguage) -> languages == null || statusLanguage == null || languages.contains(statusLanguage), "*languages", "*language"))
       // stop if it's a reply and the recipient isn't following the parent
       .ifTrue(new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content"),
               Block.macro(extractFields("*content", "*parent"))
                    .each((StatusPointer parent) -> parent.authorId, "*parent").out("*parentAuthorId")
                    .hashPartition("*followerId")
                    .macro(fetchBloomMacro("*followerId", "*rbloom"))
                    .keepTrue(new Expr((RBloomFilter rbloom, Long accountId) -> rbloom.bloom.isPresent("" + accountId), "*rbloom", "*parentAuthorId"))
                    .select("$$followerToFollowees", Path.key("*followerId").must("*parentAuthorId")))
       .hashPartition("*followerId")
       .each(HomeTimelines::addTimelineItem, "*homeTimelines", "*followerId", "*statusPointer", new Expr(Ops.CURRENT_MICROBATCH_ID))

       // fanout new/edited/removed status to remote followers
       .unify("NormalFanoutBegin", "EditRemoteFollowerFanout", "RemoveRemoteFollowerFanout", "RemoteFollowerFanoutContinue")
       // get author's account
       .select("$$accountIdToAccount", Path.key("*authorId").filterPred(Ops.IS_NOT_NULL)).out("*author")
       .each((Account account) -> account.content.isSetLocal() ? account.content.getLocal() : null, "*author").out("*authorContent")
       .keepTrue(new Expr(Ops.IS_NOT_NULL, "*authorContent"))
       // get the parent status (if reply)
       .ifTrue(new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content"),
               Block.macro(extractFields("*content", "*parent"))
                    .each((StatusPointer parent) -> parent.authorId, "*parent").out("*parentAuthorId")
                    .each((StatusPointer parent) -> parent.statusId, "*parent").out("*parentStatusId")
                    .select("$$accountIdToStatuses", Path.key("*parentAuthorId", "*parentStatusId").first()).out("*parentStatus"),
               Block.each(Ops.IDENTITY, null).out("*parentStatus"))
        // get the boosted status (if boost)
       .ifTrue(new Expr(Ops.IS_INSTANCE_OF, BoostStatusContent.class, "*content"),
               Block.macro(extractFields("*content", "*boosted"))
                    .each((StatusPointer boosted) -> boosted.authorId, "*boosted").out("*boostedAuthorId")
                    .each((StatusPointer boosted) -> boosted.statusId, "*boosted").out("*boostedStatusId")
                    .select("$$accountIdToStatuses", Path.key("*boostedAuthorId", "*boostedStatusId").first()).out("*boostedStatus"),
               Block.each(Ops.IDENTITY, null).out("*boostedStatus"))
       .atomicBlock(
           // fanout to remote followers
           Block.hashPartition("$$followeeToRemoteServerToFollowers", "*authorId")
                // get followers
                .macro(safeFetchMapRemoteFollowers("$$followeeToRemoteServerToFollowers", "*authorId", "*nextIndex", "*fetchedFollowers", "*nextFollowerId"))
                // update fanout pstate if necessary
                .ifTrue(new Expr(Ops.IS_NOT_NULL, "*nextFollowerId"),
                        Block.each((RamaFunction5<Long, Long, FanoutAction, Status, Integer, FollowerFanout>) FollowerFanout::new, "*authorId", "*nextFollowerId", new Expr(FanoutAction::findByValue, "*fanoutActionValue"), "*status", -1).out("*followerFanout")
                             .localTransform("$$statusIdToRemoteFollowerFanout", Path.key("*statusId").termVal("*followerFanout")))
                .each(Ops.EXPLODE, "*fetchedFollowers").out("*sharedInboxUrl")
                .each(MastodonWebHelpers::sendRemoteStatus, new Expr(FanoutAction::findByValue, "*fanoutActionValue"), "*author", "*statusId", "*status", "*parentStatus", "*boostedStatus", null, "*sharedInboxUrl"))
       .atomicBlock(
           // fanout to remote mentions
           Block.each(MastodonHelpers::getRemoteMentionsFromStatus, "*status").out("*mentions")
                .each(Ops.EXPLODE, "*mentions").out("*mention")
                .select("$$nameToUser", Path.key("*mention").must("accountId")).out("*accountId")
                .select("$$accountIdToAccount", Path.key("*accountId").filterPred(Ops.IS_NOT_NULL)).out("*account")
                .each((Account account) -> account.content.isSetRemote() ? account.content.getRemote() : null, "*account").out("*accountContent")
                .keepTrue(new Expr(Ops.IS_NOT_NULL, "*accountContent"))
                .macro(extractFields("*accountContent", "*mainUrl", "*inboxUrl"))
                .each(MastodonWebHelpers::sendRemoteStatus, new Expr(FanoutAction::findByValue, "*fanoutActionValue"), "*author", "*statusId", "*status", "*parentStatus", "*boostedStatus", "*mainUrl", "*inboxUrl"))

        // fanout new status to lists
       .unify("NormalFanoutBegin", "ListFanoutContinue")
       .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, "*authorId", "*statusId").out("*statusPointer")
       .hashPartition("$$memberIdToListIds", "*authorId")
       .macro(safeFetchSetFollowers("$$memberIdToListIds", "*authorId", "*nextIndex", singlePartitionFanoutLimit, "*fetchedListIds", "*nextListId"))
       // update fanout pstate if necessary
       .ifTrue(new Expr(Ops.IS_NOT_NULL, "*nextListId"),
               Block.each((RamaFunction3<Long, Long, Status, ListFanout>) ListFanout::new, "*authorId", "*nextListId", "*status").out("*listFanout")
                    .localTransform("$$statusIdToListFanout", Path.key("*statusId").termVal("*listFanout")))
       .each(Ops.EXPLODE, "*fetchedListIds").out("*listId")
       .ifTrue(new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content"),
               Block.macro(extractFields("*content", "*parent"))
                    .each((StatusPointer parent) -> parent.authorId, "*parent").out("*parentAuthorId")
                    .select("$$listIdToMemberIds", Path.key("*listId").view(Ops.CONTAINS, "*parentAuthorId").filterPred(Ops.IDENTITY)))

       .hashPartition("*listId")
       .macro(listIdToListTimeline.addItem("*listId", "*statusPointer"))

       // fan out new status to hashtags
       .unify("NormalHashtagFanout", "HashtagFanoutContinue")
       .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, "*authorId", "*statusId").out("*statusPointer")
       .hashPartition("$$hashtagToFollowers", "*hashtag")
       .macro(safeFetchSetFollowers("$$hashtagToFollowers", "*hashtag", "*nextIndex", singlePartitionFanoutLimit, "*fetchedFollowerIds", "*nextFollowerId"))
       // update fanout pstate if necessary
       .ifTrue(new Expr(Ops.IS_NOT_NULL, "*nextFollowerId"),
               Block.each((RamaFunction3<Long, Long, String, HashtagFanout>) HashtagFanout::new, "*authorId", "*statusId", "*hashtag").out("*followerFanout")
                    .localTransform("$$hashtagFanoutToIndex", Path.key("*followerFanout").termVal("*nextFollowerId")))
       .each(Ops.EXPLODE, "*fetchedFollowerIds").out("*followerId")
       .hashPartition("*followerId")
       .each(HomeTimelines::addTimelineItem, "*homeTimelines", "*followerId", "*statusPointer", new Expr(Ops.CURRENT_MICROBATCH_ID));


    // This topology handles other status-related features that are ok with latency in the 300ms range
    MicrobatchTopology core = topologies.microbatch("core");

    KeyToLinkedEntitySetPStateGroup favoriterToStatusPointers = new KeyToLinkedEntitySetPStateGroup("$$favoriterToStatusPointers", Long.class, StatusPointer.class).descending();
    KeyToLinkedEntitySetPStateGroup statusIdToFavoriters = new KeyToLinkedEntitySetPStateGroup("$$statusIdToFavoriters", Long.class, Long.class).descending();
    favoriterToStatusPointers.declarePStates(core);
    statusIdToFavoriters.declarePStates(core);

    core.source("*favoriteStatusDepot").out("*mb")
        .explodeMicrobatch("*mb").out("*data")
        .subSource("*data",
                SubSource.create(FavoriteStatus.class)
                         .macro(extractFields("*data", "*accountId", "*target"))
                         .macro(extractFields("*target", "*authorId", "*statusId"))
                         .macro(favoriterToStatusPointers.addToLinkedSet("*accountId", "*target"))
                         .hashPartition("*authorId")
                         .macro(statusIdToFavoriters.addToLinkedSet("*statusId", "*accountId")),
                SubSource.create(RemoveFavoriteStatus.class)
                         .macro(extractFields("*data", "*accountId", "*target"))
                         .macro(extractFields("*target", "*authorId", "*statusId"))
                         .macro(favoriterToStatusPointers.removeFromLinkedSet("*accountId", "*target"))
                         .hashPartition("*authorId")
                         .macro(statusIdToFavoriters.removeFromLinkedSet("*statusId", "*accountId")));

    KeyToLinkedEntitySetPStateGroup bookmarkerToStatusPointers = new KeyToLinkedEntitySetPStateGroup("$$bookmarkerToStatusPointers", Long.class, StatusPointer.class).descending();
    bookmarkerToStatusPointers.declarePStates(core);
    core.pstate("$$statusIdToBookmarkers", PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));
    core.source("*bookmarkStatusDepot").out("*mb")
        .explodeMicrobatch("*mb").out("*data")
        .subSource("*data",
                SubSource.create(BookmarkStatus.class)
                         .macro(extractFields("*data", "*accountId", "*target"))
                         .macro(extractFields("*target", "*authorId", "*statusId"))
                         .macro(bookmarkerToStatusPointers.addToLinkedSet("*accountId", "*target"))
                         .hashPartition("*authorId")
                         .localTransform("$$statusIdToBookmarkers", Path.key("*statusId").voidSetElem().termVal("*accountId")),
                SubSource.create(RemoveBookmarkStatus.class)
                         .macro(extractFields("*data", "*accountId", "*target"))
                         .macro(extractFields("*target", "*authorId", "*statusId"))
                         .macro(bookmarkerToStatusPointers.removeFromLinkedSet("*accountId", "*target"))
                         .hashPartition("*authorId")
                         .localTransform("$$statusIdToBookmarkers", Path.key("*statusId").setElem("*accountId").termVoid()));

    core.pstate("$$muterToStatusIds", PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));
    core.pstate("$$statusIdToMuters", PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));
    core.source("*muteStatusDepot").out("*mb")
        .explodeMicrobatch("*mb").out("*data")
        .subSource("*data",
           SubSource.create(MuteStatus.class).macro(extractFields("*data", "*accountId", "*target"))
                    .macro(extractFields("*target", "*authorId", "*statusId"))
                    .localTransform("$$muterToStatusIds", Path.key("*accountId").voidSetElem().termVal("*statusId"))
                    .hashPartition("*authorId")
                    .localTransform("$$statusIdToMuters", Path.key("*statusId").voidSetElem().termVal("*accountId")),
           SubSource.create(RemoveMuteStatus.class)
                    .macro(extractFields("*data", "*accountId", "*target"))
                    .macro(extractFields("*target", "*authorId", "*statusId"))
                    .localTransform("$$muterToStatusIds", Path.key("*accountId").setElem("*statusId").termVoid())
                    .hashPartition("*authorId")
                    .localTransform("$$statusIdToMuters", Path.key("*statusId").setElem("*accountId").termVoid()));

    KeyToUniqueFixedItemsPStateGroup pinnerToStatusIds = new KeyToUniqueFixedItemsPStateGroup("$$pinnerToStatusIds", PINNED_STATUS_MAX_AMOUNT, Long.class, Long.class);
    pinnerToStatusIds.declarePStates(core);
    core.source("*pinStatusDepot").out("*mb")
        .explodeMicrobatch("*mb").out("*data")
        .subSource("*data",
           SubSource.create(PinStatus.class)
                    .macro(extractFields("*data", "*accountId", "*statusId"))
                    .macro(pinnerToStatusIds.addItem("*accountId", "*statusId")),
           SubSource.create(RemovePinStatus.class)
                    .macro(extractFields("*data", "*accountId", "*statusId"))
                    .macro(pinnerToStatusIds.removeItem("*accountId", "*statusId")));
  }

  /*
    Accounts require low latency updates (a few millis) so streaming is used for processing (instead
    of microbatching). Streaming integrates with depot appends as well, allowing for coordination of
    updates with the frontend. Depot appends done with AckLevel.ACK (the default) only return when
    all colocated streaming topologies have finished processing the data in that append. This is used
    in the frontend so it knows when an account update has gone through (e.g. to reload page or
    re-enable a submit button).
   */
  private static void declareAccountsTopology(Topologies topologies) {
      StreamTopology stream = topologies.stream("accounts");
      ModuleUniqueIdPState accountIdGen = new ModuleUniqueIdPState("$$accountIdGen");
      accountIdGen.declarePState(stream);
      stream.pstate("$$nameToUser", PState.mapSchema(String.class,
                                                     PState.fixedKeysSchema("accountId", Long.class,
                                                                            "uuid", String.class)));
      stream.pstate("$$accountIdToAccount", PState.mapSchema(Long.class, Account.class));

      /*
        User registration does three things when that name is not already registered:
          - generates a user id for that user
          - updates $$nameToUser PState (which contains a mapping from name -> user id)
          - updates $$accountIdToAccount PState (which maps user id to Account)

        User registration is implemented to correctly handle:
          - Concurrent registration of same name (first one wins)
          - Failures of topology (e.g. a machine involved in the processing dies midway through
            processing). Streaming failures are handled by retrying from the start of the topology.
       */
      stream.source("*accountDepot").out("*data")
            .macro(extractFields("*data", "*name", "*uuid"))
            .localSelect("$$nameToUser", Path.key("*name")).out("*currInfo")
            .each(Ops.GET, "*currInfo", "uuid").out("*currUUID")
            // By including a UUID with each registration request, we can distinguish between:
            //   - this name is already registered by a different request so we shouldn't override it
            //   - this name was registered by the same request, so we should continue finishing the
            //     registration
            .ifTrue(new Expr(Ops.OR, new Expr(Ops.IS_NULL, "*currInfo"),
                                     new Expr(Ops.EQUAL, "*uuid", "*currUUID")),
              Block.macro(accountIdGen.genId("*accountId"))
                   .localTransform("$$nameToUser", Path.key("*name").multiPath(Path.key("accountId").termVal("*accountId"),
                                                                               Path.key("uuid").termVal("*uuid")))
                   .hashPartition("*accountId")
                   .localTransform("$$accountIdToAccount", Path.key("*accountId").termVal("*data"))
                   .invokeQuery("getAccountMetadata", null, "*accountId").out("*metadata")
                   .each((RamaFunction3<Long, Account, AccountMetadata, AccountWithId>) AccountWithId::new, "*accountId", "*data", "*metadata").out("*accountWithId")
                   .depotPartitionAppend("*accountWithIdDepot", "*accountWithId"));

      stream.source("*accountEditDepot", StreamSourceOptions.retryNone()).out("*editAccount")
            .macro(extractFields("*editAccount", "*accountId", "*edits"))
            .each(Ops.EXPLODE, "*edits").out("*edit")
            .each((EditAccountField editAccount, OutputCollector collector) -> {
                collector.emit(editAccount.getSetField().getFieldName(), editAccount.getFieldValue());
            }, "*edit").out("*fieldName", "*fieldValue")
            .localTransform("$$accountIdToAccount", Path.must("*accountId")
                                                        .customNavBuilder(TField::new, "*fieldName")
                                                        .termVal("*fieldValue"));
  }

  private static Block removeStatusMacro(String accountIdVar, String statusIdVar) {
    String statusVar = Helpers.genVar("status");
    String removeStatusWithIdVar = Helpers.genVar("removeStatusWithId");
    return Block.select("$$accountIdToStatuses", Path.key(accountIdVar, statusIdVar).view(Ops.FIRST)).out(statusVar)
                .localTransform("$$accountIdToStatuses", Path.key(accountIdVar, statusIdVar).termVoid())
                .localTransform("$$accountIdToAccountTimeline", Path.key(accountIdVar).setElem(statusIdVar).termVoid())
                .localTransform("$$accountIdToAttachmentStatusIds", Path.key(accountIdVar).setElem(statusIdVar).termVoid())
                .ifTrue(new Expr(Ops.IS_NOT_NULL, statusVar),
                    Block.each((Long statusId, Status status) -> new RemoveStatusWithId(statusId, status), statusIdVar, statusVar).out(removeStatusWithIdVar)
                         .depotPartitionAppend("*statusWithIdDepot", removeStatusWithIdVar));
  }

  private void declareStatusTopology(Topologies topologies) {
      StreamTopology stream = topologies.stream("status");

      ModuleUniqueIdPState statusIdGen = new ModuleUniqueIdPState("$$statusIdGen").descending();
      statusIdGen.declarePState(stream);

      stream.pstate("$$accountIdToStatuses", PState.mapSchema(Long.class, // account id
                                                              PState.mapSchema(Long.class, // status id
                                                                               PState.listSchema(Status.class)).subindexed()));
      stream.pstate("$$accountIdToAccountTimeline", PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));

      stream.pstate("$$postUUIDToStatusId", PState.mapSchema(String.class, Long.class));
      stream.pstate("$$remoteUrlToStatusId", PState.mapSchema(String.class, Long.class));

      KeyToLinkedEntitySetPStateGroup statusIdToReplies = new KeyToLinkedEntitySetPStateGroup("$$statusIdToReplies", Long.class, StatusPointer.class);
      statusIdToReplies.declarePStates(stream);

      stream.pstate("$$accountIdToAttachmentStatusIds", PState.mapSchema(Long.class, PState.setSchema(Long.class).subindexed()));
      stream.pstate("$$uuidToAttachment", PState.mapSchema(String.class, Attachment.class));

      KeyToLinkedEntitySetPStateGroup statusIdToBoosters = new KeyToLinkedEntitySetPStateGroup("$$statusIdToBoosters", Long.class, StatusPointer.class)
          .entityIdFunction(Long.class, (Object p) -> ((StatusPointer) p).authorId)
          .descending();
      statusIdToBoosters.declarePStates(stream);

      stream.pstate("$$statusIdToConvoId",  PState.mapSchema(Long.class, Long.class));

      TopologyScheduler scheduledStatuses = new TopologyScheduler("$$scheduledStatuses");
      scheduledStatuses.declarePStates(stream);

      stream.pstate("$$accountIdToScheduledStatuses", PState.mapSchema(Long.class, // accountId
                                                                       PState.mapSchema(Long.class, // id
                                                                                        PState.fixedKeysSchema("publishMillis", Long.class,
                                                                                                               "uuid", String.class,
                                                                                                               "status", Status.class)).subindexed()));

      stream.pstate("$$pollVotes",
        PState.mapSchema(
          Long.class, // statusId
          PState.fixedKeysSchema("allVoters", PState.mapSchema(Long.class, Set.class).subindexed(), // accountId -> choicesIndexes
                                 "choices", PState.mapSchema(Integer.class, PState.setSchema(Long.class).subindexed())))); // choiceIndex -> set of accountId


      stream.source("*statusDepot").out("*initialData")
            .ifTrue(new Expr(Ops.IS_INSTANCE_OF, BoostStatus.class, "*initialData"),
                Block.each(MastodonHelpers::createAddStatusFromBoost, "*initialData").out("*data"),
                Block.each(Ops.IDENTITY, "*initialData").out("*data"))
            .subSource("*data",
                SubSource.create(AddStatus.class)
                         .macro(extractFields("*data", "*uuid", "*status"))
                         .macro(extractFields("*status", "*authorId", "*content", "*remoteUrl"))

                         // get status id if it was already generated
                         .localSelect("$$postUUIDToStatusId", Path.key("*uuid")).out("*statusIdMaybe")
                         .ifTrue(new Expr(Ops.IS_NOT_NULL, "*statusIdMaybe"),
                             Block.each(Ops.IDENTITY, "*statusIdMaybe").out("*statusId"),
                             // otherwise, generate status id and associate it with the uuid
                             Block.macro(statusIdGen.genId("*statusId"))
                                  .localTransform("$$postUUIDToStatusId", Path.key("*uuid").termVal("*statusId")))

                         .localTransform("$$accountIdToStatuses", Path.key("*authorId", "*statusId").beforeElem().termVal("*status"))

                         .ifTrue(new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content"),
                             Block.macro(MastodonHelpers.extractFields("*content", "*parent"))
                                  .each((StatusPointer parent) -> parent.authorId, "*parent").out("*parentAuthorId")
                                  .each((StatusPointer parent) -> parent.statusId, "*parent").out("*parentStatusId")
                                  .hashPartition("*parentAuthorId")
                                  .localSelect("$$statusIdToConvoId", Path.key("*parentStatusId").nullToVal("*parentStatusId")).out("*convoId")
                                  .hashPartition("*authorId")
                                  .localTransform("$$statusIdToConvoId", Path.key("*statusId").termVal("*convoId")))
                         // to ensure $$accountIdToStatuses and $$statusIdToConvoId is updated before fanout happens
                         .hashPartition("*authorId")
                         .each((RamaFunction2<Long, Status, StatusWithId>) StatusWithId::new, "*statusId", "*status").out("*statusWithId")
                         .depotPartitionAppend("*statusWithIdDepot", "*statusWithId")


                         .ifTrue(new Expr(Ops.IS_NOT_NULL, "*remoteUrl"),
                                 Block.hashPartition("*remoteUrl")
                                      .localSelect("$$remoteUrlToStatusId", Path.key("*remoteUrl")).out("*existingStatusId")
                                      .keepTrue(new Expr(Ops.IS_NULL, "*existingStatusId"))
                                      .hashPartition("*authorId"))

                         // include in account timeline unless it is a direct message.
                         // mastodon actually does include DMs in this timeline, but soapbox does not,
                         // and it really isn't a great idea to include them anyway since it would be
                         // unexpected to see DMs in a place you think is public, so we're excluding them.
                         .ifTrue(new Expr(Ops.NOT_EQUAL, new Expr(MastodonHelpers::getStatusVisibility, "*status"), StatusVisibility.Direct),
                                 Block.localTransform("$$accountIdToAccountTimeline", Path.key("*authorId").voidSetElem().termVal("*statusId")))

                         .ifTrue(new Expr(Ops.OR, new Expr(Ops.IS_INSTANCE_OF, NormalStatusContent.class, "*content"),
                                                  new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content")),
                                 Block.macro(extractFields("*content", "*attachments"))
                                      .ifTrue(new Expr(Ops.AND, new Expr(Ops.IS_NOT_NULL, "*attachments"),
                                                                new Expr(Ops.GREATER_THAN, new Expr(Ops.SIZE, "*attachments"), 0)),
                                                   // add status id to set so we can query all statuses with attachments
                                              Block.localTransform("$$accountIdToAttachmentStatusIds", Path.key("*authorId").voidSetElem().termVal("*statusId"))))

                         .ifTrue(new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content"),
                                 Block.each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, "*authorId", "*statusId").out("*statusPointer")
                                      .macro(MastodonHelpers.extractFields("*content", "*parent"))
                                      .each((StatusPointer parent) -> parent.authorId, "*parent").out("*parentAuthorId")
                                      .each((StatusPointer parent) -> parent.statusId, "*parent").out("*parentStatusId")
                                      .hashPartition("*parentAuthorId")
                                      .macro(statusIdToReplies.addToLinkedSet("*parentStatusId", "*statusPointer")))

                         .ifTrue(new Expr(Ops.IS_INSTANCE_OF, BoostStatusContent.class, "*content"),
                                 Block.macro(extractFields("*content", "*boosted"))
                                      .each((StatusPointer boosted) -> boosted.authorId, "*boosted").out("*boostedAuthorId")
                                      .each((StatusPointer boosted) -> boosted.statusId, "*boosted").out("*boostedStatusId")
                                      .hashPartition("*boostedAuthorId")
                                      .each((RamaFunction2<Long, Long, StatusPointer>) StatusPointer::new, "*authorId", "*statusId").out("*sp")
                                      .macro(statusIdToBoosters.addToLinkedSet("*boostedStatusId", "*sp")))

                         .ifTrue(new Expr(Ops.IS_NOT_NULL, "*remoteUrl"),
                                 Block.hashPartition("*remoteUrl")
                                      .localTransform("$$remoteUrlToStatusId", Path.key("*remoteUrl").termVal("*statusId"))),
                SubSource.create(EditStatus.class)
                         .macro(extractFields("*data", "*statusId", "*status"))
                         .macro(extractFields("*status", "*authorId"))
                         .localSelect("$$accountIdToStatuses", Path.key("*authorId", "*statusId")
                                                                   .view((List l, Integer max) -> {
                                                                     if(l==null || l.size() >= max) return null;
                                                                     else return l.get(0);
                                                                   }, maxEditCount)).out("*prevStatus")
                         .localTransform("$$accountIdToStatuses", Path.key("*authorId").must("*statusId")
                                                                      .filterSelected(Path.view(Ops.SIZE).filterLessThan(maxEditCount))
                                                                      .beforeElem()
                                                                      .termVal("*status"))
                         // if the edited status had a poll and still has a poll, clear the votes
                         .ifTrue(new Expr(Core::needsNewPollVersion, "*prevStatus", "*status"),
                           Block.localTransform("$$pollVotes", Path.key("*statusId").termVal(null)))
                         .depotPartitionAppend("*statusWithIdDepot", "*data"),
                SubSource.create(RemoveStatus.class)
                         .macro(extractFields("*data", "*accountId", "*statusId"))
                         .macro(removeStatusMacro("*accountId", "*statusId")),
                SubSource.create(RemoveBoostStatus.class)
                         .macro(extractFields("*data", "*accountId", "*target"))
                         .macro(extractFields("*target", "*authorId", "*statusId"))
                         .hashPartition("*authorId")
                         .localSelect("$$statusIdToBoosters", Path.key("*statusId").must("*accountId")).out("*id")
                         .localSelect("$$statusIdToBoostersById", Path.key("*statusId", "*id")).out("*sp")
                         .each((StatusPointer sp) -> sp.statusId, "*sp").out("*boostStatusId")
                         .macro(removeStatusMacro("*accountId", "*boostStatusId"))
                         .hashPartition("*authorId")
                         .macro(statusIdToBoosters.removeFromLinkedSetByEntityId("*statusId", "*accountId")));

      stream.source("*statusAttachmentWithIdDepot").out("*data")
            .subSource("*data",
                SubSource.create(AttachmentWithId.class)
                         .macro(extractFields("*data", "*uuid", "*attachment"))
                         .localTransform("$$uuidToAttachment", Path.key("*uuid").termVal("*attachment")));

      stream.source("*pollVoteDepot").out("*data")
            .macro(extractFields("*data", "*accountId", "*target", "*choices"))
            .macro(extractFields("*target", "*statusId"))
            .localTransform("$$pollVotes", Path.key("*statusId")
                                               .multiPath(Path.key("allVoters").key("*accountId").termVal("*choices"),
                                                          Path.key("choices")
                                                              .pathBuilder((Set choices) -> {
                                                                Path.Impl ret = Path.stop();
                                                                for(Object c: choices) ret = Path.multiPath(ret, Path.key(c));
                                                                return ret;
                                                              }, "*choices")
                                                              .voidSetElem()
                                                              .termVal("*accountId")));

      stream.source("*scheduledStatusDepot").out("*data")
            .subSource("*data",
              SubSource.create(AddScheduledStatus.class)
                       .macro(extractFields("*data", "*uuid", "*status", "*publishMillis"))
                       .macro(extractFields("*status", "*authorId"))
                       .localSelect("$$postUUIDToStatusId", Path.key("*uuid")).out("*statusIdMaybe")
                         .ifTrue(new Expr(Ops.IS_NOT_NULL, "*statusIdMaybe"),
                                 Block.each(Ops.IDENTITY, "*statusIdMaybe").out("*id"),
                                 Block.macro(statusIdGen.genId("*id"))
                                      .localTransform("$$postUUIDToStatusId", Path.key("*uuid").termVal("*id")))
                       .localTransform("$$accountIdToScheduledStatuses", Path.key("*authorId", "*id")
                                                                             .multiPath(Path.key("status").termVal("*status"),
                                                                                        Path.key("uuid").termVal("*uuid"),
                                                                                        Path.key("publishMillis").termVal("*publishMillis")))
                       .macro(scheduledStatuses.scheduleItem("*publishMillis", new Expr(Ops.TUPLE, "*authorId", "*id", "*uuid"))),
              SubSource.create(EditStatus.class)
                       .macro(extractFields("*data", "*statusId", "*status"))
                       .macro(extractFields("*status", "*authorId"))
                       .localTransform("$$accountIdToScheduledStatuses", Path.key("*authorId").must("*statusId", "status").termVal("*status")),
              SubSource.create(RemoveStatus.class)
                       .macro(extractFields("*data", "*accountId", "*statusId"))
                       .localTransform("$$accountIdToScheduledStatuses", Path.key("*accountId", "*statusId").termVoid()),
              SubSource.create(EditScheduledStatusPublishTime.class)
                       .macro(extractFields("*data", "*accountId", "*id", "*publishMillis"))
                       .localTransform("$$accountIdToScheduledStatuses", Path.key("*accountId").must("*id", "publishMillis").termVal("*publishMillis"))
                       .localSelect("$$accountIdToScheduledStatuses", Path.key("*accountId").must("*id", "uuid")).out("*uuid")
                       .macro(scheduledStatuses.scheduleItem("*publishMillis", new Expr(Ops.TUPLE, "*accountId", "*id", "*uuid"))));

      stream.source("*scheduledStatusTick")
            .macro(scheduledStatuses.handleExpirations("*tuple", "*currentTimeMillis",
              Block.each(Ops.EXPAND, "*tuple").out("*accountId", "*id", "*uuid")
                   .localSelect("$$accountIdToScheduledStatuses", Path.key("*accountId", "*id")).out("*m")
                   .ifTrue(new Expr(Ops.IS_NOT_NULL, "*m"),
                     Block.each(Ops.GET, "*m", "publishMillis").out("*publishMillis")
                          .each(Ops.GET, "*m", "status").out("*status")
                          .ifTrue(new Expr(Ops.GREATER_THAN_OR_EQUAL, "*currentTimeMillis", "*publishMillis"),
                             Block.each((Status status, Long currentTimeMillis, String uuid) -> {
                                    long origTimestamp = status.getTimestamp();
                                    status.setTimestamp(currentTimeMillis);
                                    PollContent pc = MastodonHelpers.extractPollContent(status);
                                    if(pc != null) pc.setExpirationMillis(pc.getExpirationMillis() + Math.max(0, currentTimeMillis - origTimestamp));
                                    return new AddStatus(uuid, status);
                                  }, "*status", "*currentTimeMillis", "*uuid").out("*addStatus")
                                  .depotPartitionAppend("*statusDepot", "*addStatus")
                                  .localTransform("$$accountIdToScheduledStatuses", Path.key("*accountId", "*id").termVoid())))));
  }

  private void declareQueries(Topologies topologies) {
    topologies.query("getAccountTimeline", "*requestAccountId", "*timelineAccountId", "*firstStatusId", "*limit", "*includeReplies").out("*results")
              .hashPartition("*timelineAccountId")
              .each((Integer limit) -> SortedRangeFromOptions.excludeStart().maxAmt(limit), "*limit").out("*sortedOptions")
              .localSelect("$$accountIdToAccountTimeline", Path.key("*timelineAccountId")
                                                               .sortedSetRangeFrom("*firstStatusId", "*sortedOptions")
                                                               .all()).out("*statusId")
              .localSelect("$$accountIdToStatuses", Path.must("*timelineAccountId", "*statusId").first()).out("*status")

              // determine whether we are excluding this status from the results.
              // instead of excluding it directly via keepTrue, we are setting a boolean
              // in the status pointer so the getStatusesFromPointers query can exclude it.
              // this is necessary because if we exclude it now, then updateStatusQueryResults
              // will incorrectly think we've reached the end with respect to pagination.
              .macro(extractFields("*status", "*content"))
              // exclude replies if necessary
              .each(Ops.IDENTITY, new Expr(Ops.AND, new Expr(Ops.NOT, "*includeReplies"),
                                                    new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content"))).out("*shouldExclude")

              .each((Long authorId, Long statusId, Boolean shouldExclude) -> new StatusPointer(authorId, statusId).setShouldExclude(shouldExclude),
                      "*timelineAccountId", "*statusId", "*shouldExclude").out("*statusPointer")

              .originPartition()
              .agg(Agg.list("*statusPointer")).out("*statusPointers")
              .each(() -> new QueryFilterOptions(FilterContext.Account, false)).out("*filterOptions")
              .invokeQuery("getStatusesFromPointers", "*requestAccountId", "*statusPointers", "*filterOptions").out("*statusQueryResults")
              .each(MastodonHelpers::updateStatusQueryResults, "*statusQueryResults", "*statusPointers", "*limit", false)
              .out("*results");

    topologies.query("refreshHomeTimeline", "*accountId").out("*ret")
              .anchor("RefreshRoot")

              .select("$$followerToFolloweesById", Path.key("*accountId").sortedMapRangeFrom(0L, 300).mapVals()).out("*followeeFollower")
              .each((Follower f) -> f.accountId, "*followeeFollower").out("*followee")
              .macro(extractFields("*followeeFollower", "*showBoosts"))
              .anchor("RefreshFollowers")

              .hook("RefreshRoot")
              .each(Ops.IDENTITY, "*accountId").out("*followee")
              .each(Ops.IDENTITY, true).out("*showBoosts")
              .anchor("RefreshSelf")

              .unify("RefreshFollowers", "RefreshSelf")
              .select("$$accountIdToStatuses", Path.key("*followee").sortedMapRangeFrom(0L, 30).transformed(Path.mapVals().term(Ops.FIRST))).out("*statusMap")
              .each(Ops.EXPLODE_MAP, "*statusMap").out("*statusId", "*status")
              .macro(extractFields("*status", "*content", "*timestamp"))
              .subSource("*content",
                SubSource.create(ReplyStatusContent.class).keepTrue(false),
                SubSource.create(BoostStatusContent.class).keepTrue("*showBoosts"),
                SubSource.create(NormalStatusContent.class)
                         .macro(extractFields("*content", "*visibility"))
                         .keepTrue(new Expr(Ops.NOT_EQUAL, "*visibility", StatusVisibility.Direct)))
              .each(Ops.TUPLE, "*timestamp", "*followee", "*statusId").out("*tuple")
              .originPartition()
              .agg(Agg.topMonotonic(timelineMaxAmount, "*tuple").sortValFunction(Ops.FIRST)).out("*toAdd")
              .each(HomeTimelines::refreshStatuses, "*homeTimelines", "*accountId", "*toAdd").out("*ret");

    topologies.query("getHomeTimeline", "*requestAccountId", "*firstStatusPointer", "*limit").out("*ret")
              .hashPartition("*requestAccountId")
              .ifTrue(new Expr(Ops.AND, enableHomeTimelineRefresh, new Expr(HomeTimelines::needsRefresh, "*homeTimelines", "*requestAccountId")),
                Block.invokeQuery("refreshHomeTimeline", "*requestAccountId")
                     .each(Ops.IDENTITY, true).out("*refreshed"),
                Block.each(Ops.IDENTITY, false).out("*refreshed"))
              .each(HomeTimelines::readTimelineFrom, "*homeTimelines", "*requestAccountId", "*firstStatusPointer", "*limit").out("*statusPointers")
              .each(() -> new QueryFilterOptions(FilterContext.Home, true)).out("*filterOptions")
              .invokeQuery("getStatusesFromPointers", "*requestAccountId", "*statusPointers", "*filterOptions").out("*statusQueryResults")
              .each(MastodonHelpers::updateStatusQueryResults, "*statusQueryResults", "*statusPointers", "*limit", "*refreshed").out("*ret")
              .originPartition();

    // tuples are pairs of [accountId, endStatusPointer (exclusive)]
    // returns statusIds with newest first and oldest last
    topologies.query("getHomeTimelinesUntil", "*tuples", "*limitPerTimeline").out("*ret")
              .each(Ops.EXPLODE_INDEXED, "*tuples").out("*i", "*tuple")
              .each(Ops.EXPAND, "*tuple").out("*accountId", "*endStatusPointer")
              .hashPartition("*accountId")
              .each(HomeTimelines::readTimelineUntil, "*homeTimelines", "*accountId", "*endStatusPointer", "*limitPerTimeline").out("*newPointers")
              .originPartition()
              .agg(Agg.map("*i", "*newPointers")).out("*ret");

    topologies.query("getListTimeline", "*listId", "*firstTimelineIndex", "*limit").out("*results")
              .hashPartition("*listId")
              .each((Integer limit) -> SortedRangeFromOptions.excludeStart().maxAmt(limit), "*limit").out("*sortedOptions")
              .localSelect("$$listIdToListTimeline", Path.subselect(Path.key("*listId")
                                                         .sortedMapRangeFrom("*firstTimelineIndex", "*sortedOptions")
                                                         .mapVals())).out("*statusPointers")
              .select("$$listIdToList", Path.key("*listId")).out("*list")
              .macro(extractFields("*list", "*authorId"))
              .each(() -> new QueryFilterOptions(FilterContext.Home, false)).out("*filterOptions")
              .invokeQuery("getStatusesFromPointers", "*authorId", "*statusPointers", "*filterOptions").out("*statusQueryResults")
              .each(MastodonHelpers::updateStatusQueryResults, "*statusQueryResults", "*statusPointers", "*limit", false).out("*results")
              .originPartition();

    topologies.query("getDirectTimeline", "*requestAccountId", "*firstTimelineIndex", "*limit").out("*results")
              .hashPartition("*requestAccountId")
              .each((Integer limit) -> SortedRangeFromOptions.excludeStart().maxAmt(limit), "*limit").out("*sortedOptions")
              .localSelect("$$accountIdToDirectMessagesById", Path.subselect(Path.key("*requestAccountId")
                                                                  .sortedMapRangeFrom("*firstTimelineIndex", "*sortedOptions")
                                                                  .mapVals())).out("*statusPointers")
              .each(() -> new QueryFilterOptions(FilterContext.Thread, true)).out("*filterOptions")
              .invokeQuery("getStatusesFromPointers", "*requestAccountId", "*statusPointers", "*filterOptions").out("*statusQueryResults")
              .each(MastodonHelpers::updateStatusQueryResults, "*statusQueryResults", "*statusPointers", "*limit", false).out("*results")
              .originPartition();

    topologies.query("getConversationTimeline", "*requestAccountId", "*firstTimelineIndex", "*limit").out("*results")
              .hashPartition("*requestAccountId")
              .each((Integer limit) -> SortedRangeFromOptions.excludeStart().maxAmt(limit), "*limit").out("*sortedOptions")
              .localSelect("$$accountIdToConvoIdsById", Path.key("*requestAccountId")
                                                            .sortedMapRangeFrom("*firstTimelineIndex", "*sortedOptions")
                                                            .all()).out("*timelineIndexAndConvoId")
              .each(Ops.EXPAND, "*timelineIndexAndConvoId").out("*timelineIndex", "*convoId")
              .invokeQuery("getConversation", "*requestAccountId", "*convoId").out("*conversation")
              .each((RamaFunction2<Long, Conversation, IndexedConversation>) IndexedConversation::new, "*timelineIndex", "*conversation").out("*indexedConversation")
              .originPartition()
              .agg(Agg.list("*indexedConversation")).out("*unsortedResults")
              .each((List<IndexedConversation> unsortedResults) -> {
                ArrayList<IndexedConversation> results = new ArrayList<>(unsortedResults);
                results.sort((IndexedConversation a, IndexedConversation b) -> (int) (a.index - b.index));
                return results.stream().map(o -> o.conversation).collect(Collectors.toList());
              }, "*unsortedResults").out("*results");

    topologies.query("getConversation", "*requestAccountId", "*convoId").out("*conversation")
              .hashPartition("*requestAccountId")
              .localSelect("$$accountIdToConvoIdToConvo", Path.subselect(Path.key("*requestAccountId", "*convoId").mapKeys())).out("*keys")
              .ifTrue(new Expr(Ops.EQUAL, 0, new Expr(Ops.SIZE, "*keys")),
                      Block.each(Ops.IDENTITY, null).out("*conversation"),
                      Block.localSelect("$$accountIdToConvoIdToConvo", Path.key("*requestAccountId", "*convoId", "timeline").view(Ops.FIRST)).out("*statusIndexAndStatusPointer")
                           .ifTrue(new Expr(Ops.IS_NULL, "*statusIndexAndStatusPointer"),
                                   Block.each(Ops.IDENTITY, null).out("*lastStatus"),
                                   Block.each(Ops.EXPAND, "*statusIndexAndStatusPointer").out("*statusIndex", "*statusPointer")
                                           .each(Ops.TUPLE, "*statusPointer").out("*statusPointers")
                                           .each(() -> new QueryFilterOptions(FilterContext.Thread, false)).out("*filterOptions")
                                           .invokeQuery("getStatusesFromPointers", "*requestAccountId", "*statusPointers", "*filterOptions").out("*statusQueryResults")
                                           .each((StatusQueryResults statusQueryResults) -> {
                                               if (statusQueryResults.results.size() == 0) return null;
                                               else return new StatusQueryResult(statusQueryResults.results.get(0), statusQueryResults.mentions);
                                           }, "*statusQueryResults").out("*lastStatus"))
                           .localSelect("$$accountIdToConvoIdToConvo",
                                        Path.key("*requestAccountId", "*convoId")
                                            .subselect(Path.multiPath(Path.key("unread"),
                                                                      Path.key("accountIds")))).out("*tuple")
                           .each(Ops.EXPAND, "*tuple").out("*unread", "*accountIds")
                           .invokeQuery("getAccountsFromAccountIds", null, "*accountIds").out("*accounts")
                           .each((Long cid, Boolean unread, List<AccountWithId> accounts, StatusQueryResult lastStatus) -> {
                             Conversation convo = new Conversation(cid, unread, accounts);
                             if (lastStatus != null) convo.setLastStatus(lastStatus);
                             return convo;
                           }, "*convoId", "*unread", "*accounts", "*lastStatus").out("*conversation"))
              .originPartition();

    topologies.query("getAncestors", "*requestAccountId", "*childAuthorId", "*childStatusId", "*limit").out("*results")
              .each((Long authorId, Long statusId) -> new StatusPointer(authorId, statusId), "*childAuthorId", "*childStatusId").out("*initialStatusPointer")
              .loopWithVars(LoopVars.var("*count", 0)
                                    .var("*statusPointer", "*initialStatusPointer")
                                    .var("*statusPointers", PersistentList.EMPTY),
                      Block.ifTrue(new Expr(Ops.GREATER_THAN_OR_EQUAL, "*count", "*limit"),
                                   Block.emitLoop("*statusPointers"),
                                   Block.macro(extractFields("*statusPointer", "*authorId", "*statusId"))
                                        .select("$$accountIdToStatuses", Path.key("*authorId", "*statusId").view(Ops.FIRST)).out("*status")
                                        .ifTrue(new Expr(Ops.IS_NULL, "*status"),
                                                Block.emitLoop("*statusPointers"),

                                                     // add status to the output if it's not the original status
                                                Block.ifTrue(new Expr(Ops.NOT_EQUAL, "*statusId", "*childStatusId"),
                                                             Block.each(Ops.INC, "*count").out("*nextCount")
                                                                  .each((IPersistentList statusPointers, StatusPointer statusPointer) -> statusPointers.cons(statusPointer),
                                                                          "*statusPointers", "*statusPointer").out("*nextStatusPointers"),
                                                             Block.each(Ops.IDENTITY, "*count").out("*nextCount")
                                                                  .each(Ops.IDENTITY, "*statusPointers").out("*nextStatusPointers"))

                                                     // continue the loop if there is a parent
                                                     .macro(extractFields("*status", "*content"))
                                                     .ifTrue(new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content"),
                                                             Block.macro(extractFields("*content", "*parent"))
                                                                  .each((StatusPointer parent) -> parent.authorId, "*parent").out("*parentAuthorId")
                                                                  .each((StatusPointer parent) -> parent.statusId, "*parent").out("*parentStatusId")
                                                                  .each((Long authorId, Long statusId) -> new StatusPointer(authorId, statusId), "*parentAuthorId", "*parentStatusId").out("*nextStatusPointer")
                                                                  .continueLoop("*nextCount", "*nextStatusPointer", "*nextStatusPointers"),
                                                             Block.emitLoop("*nextStatusPointers"))))).out("*statusPointers")
              .each(() -> new QueryFilterOptions(FilterContext.Public, true)).out("*filterOptions")
              .invokeQuery("getStatusesFromPointers", "*requestAccountId", "*statusPointers", "*filterOptions").out("*results")
              .originPartition();

    topologies.query("getDescendants", "*requestAccountId", "*parentAuthorId", "*parentStatusId", "*limit").out("*results")
              .each((Long authorId, Long statusId) -> Arrays.asList(new StatusPointer(authorId, statusId)), "*parentAuthorId", "*parentStatusId").out("*initialStatusPointers")
              .loopWithVars(LoopVars.var("*count", 0)
                                    .var("*statusPointerQueue", "*initialStatusPointers")
                                    .var("*statusPointers", PersistentVector.EMPTY),
                      Block.each((List<StatusPointer> descendants) -> descendants.get(0), "*statusPointerQueue").out("*nextStatusPointer")
                           .ifTrue(new Expr(Ops.GREATER_THAN_OR_EQUAL, "*count", "*limit"),
                                   Block.emitLoop("*statusPointers"),
                                   Block.macro(extractFields("*nextStatusPointer", "*authorId", "*statusId"))
                                        .select("$$accountIdToStatuses", Path.key("*authorId", "*statusId").view(Ops.FIRST)).out("*nextStatus")
                                        .ifTrue(new Expr(Ops.IS_NULL, "*nextStatus"),
                                                Block.emitLoop("*statusPointers"),

                                                     // add status to the output if it's not the original status
                                                Block.ifTrue(new Expr(Ops.NOT_EQUAL, "*statusId", "*parentStatusId"),
                                                             Block.each(Ops.INC, "*count").out("*nextCount")
                                                                  .each((PersistentVector statusPointers, Long authorId, Long statusId) -> statusPointers.cons(new StatusPointer(authorId, statusId)),
                                                                          "*statusPointers", "*authorId", "*statusId").out("*nextStatusPointers"),
                                                             Block.each(Ops.IDENTITY, "*count").out("*nextCount")
                                                                  .each(Ops.IDENTITY, "*statusPointers").out("*nextStatusPointers"))

                                                     // get the direct children of the status
                                                     .localSelect("$$statusIdToRepliesById", Path.subselect(Path.key("*statusId").sortedMapRangeFrom(0L, "*limit").mapVals())).out("*childStatusPointers")

                                                     // remove the first item from the list since we just processed it,
                                                     // then prepend its children to the list, so we get to them first (depth-first)
                                                     .each((List<StatusPointer> descendants, List<StatusPointer> children) -> {
                                                       ArrayList<StatusPointer> results = new ArrayList<>(children);
                                                       Iterator<StatusPointer> iter = descendants.iterator();
                                                       if (iter.hasNext()) {
                                                         iter.next(); // skip the first one
                                                         while (iter.hasNext() && results.size() < DESCENDANT_SEARCH_LIMIT) results.add(iter.next());
                                                       }
                                                       return results;
                                                     }, "*statusPointerQueue", "*childStatusPointers").out("*nextStatusPointerQueue")

                                                     // continue the loop if there is more in the queue
                                                     .ifTrue(new Expr(Ops.GREATER_THAN, new Expr(Ops.SIZE, "*nextStatusPointerQueue"), 0),
                                                             Block.continueLoop("*nextCount", "*nextStatusPointerQueue", "*nextStatusPointers"),
                                                             Block.emitLoop("*nextStatusPointers"))))
              ).out("*statusPointers")
              .each(() -> new QueryFilterOptions(FilterContext.Public, true)).out("*filterOptions")
              .invokeQuery("getStatusesFromPointers", "*requestAccountId", "*statusPointers", "*filterOptions").out("*results")
              .originPartition();

    topologies.query("getAccountMetadata", "*requestAccountId", "*accountId").out("*result")
              .hashPartition("*accountId")
              .localSelect("$$accountIdToAccountTimeline", Path.key("*accountId").view(Ops.SIZE)).out("*statusCount")
              .localSelect("$$accountIdToStatuses", Path.key("*accountId").subselect(Path.first().last().first()).view(Ops.FIRST)).out("*lastStatus")
              .select("$$followeeToFollowers", Path.key("*accountId")
                                                   .subselect(Path.multiPath(Path.view(Ops.CONTAINS, "*requestAccountId"),
                                                                             Path.view(Ops.SIZE)))).out("*followerTuple")
              .each(Ops.EXPAND, "*followerTuple").out("*isFollowedByRequester", "*followerCount")
              .select("$$followerToFollowees", Path.key("*accountId")
                                                   .subselect(Path.multiPath(Path.view(Ops.CONTAINS, "*requestAccountId"),
                                                                             Path.view(Ops.SIZE)))).out("*followeeTuple")
              .each(Ops.EXPAND, "*followeeTuple").out("*isFollowingRequester", "*followeeCount")
              .each((Integer statusCount, Integer followerCount, Integer followeeCount, Boolean isFollowedByRequester, Boolean isFollowingRequester, Status lastStatus) -> {
                  AccountMetadata metadata = new AccountMetadata(statusCount, followerCount, followeeCount, isFollowedByRequester, isFollowingRequester);
                  if (lastStatus != null) metadata.setLastStatusTimestamp(lastStatus.timestamp);
                  return metadata;
              },
              "*statusCount", "*followerCount", "*followeeCount", "*isFollowedByRequester", "*isFollowingRequester", "*lastStatus").out("*result")
              .originPartition();

    topologies.query("getAccountIdToMetadata", "*requestAccountId", "*accountIds").out("*accountIdToMetadata")
              .each(Ops.EXPLODE, "*accountIds").out("*accountId")
              .invokeQuery("getAccountMetadata", "*requestAccountId", "*accountId").out("*metadata")
              .each(Ops.TUPLE, "*accountId", "*metadata").out("*accountIdAndMetadata")
              .originPartition()
              .agg(Agg.list("*accountIdAndMetadata")).out("*accountIdAndMetadatas")
              .each((List<List> accountIdAndMetadatas) -> {
                  Map<Long, AccountMetadata> accountIdToMetadata = new HashMap<>();
                  for (List accountIdAndMetadata : accountIdAndMetadatas) {
                      long accountId = (long) accountIdAndMetadata.get(0);
                      AccountMetadata metadata = (AccountMetadata) accountIdAndMetadata.get(1);
                      accountIdToMetadata.put(accountId, metadata);
                  }
                  return accountIdToMetadata;
              }, "*accountIdAndMetadatas").out("*accountIdToMetadata");

    topologies.query("getAccountsFromAccountIds", "*requestAccountId", "*accountIds").out("*results")
              .each(Ops.EXPLODE_INDEXED, "*accountIds").out("*index", "*accountId")
              .select("$$accountIdToAccount", Path.key("*accountId").filterPred(Ops.IS_NOT_NULL)).out("*account")
              .ifTrue(new Expr(Ops.IS_NOT_NULL, "*requestAccountId"),
                      Block.select("$$accountIdToSuppressions", Relationships.isBlockedOrMutedPath("*requestAccountId", "*accountId", false)).out("*isBlockedOrMuted")
                           .keepTrue(new Expr(Ops.NOT, "*isBlockedOrMuted")))
              .invokeQuery("getAccountMetadata", "*requestAccountId", "*accountId").out("*metadata")
              .each((RamaFunction3<Long, Account, AccountMetadata, AccountWithId>) AccountWithId::new, "*accountId", "*account", "*metadata").out("*accountWithId")
              .each((RamaFunction2<Integer, AccountWithId, IndexedAccountWithId>) IndexedAccountWithId::new, "*index", "*accountWithId").out("*indexedAccountWithId")
              .originPartition()
              .agg(Agg.list("*indexedAccountWithId")).out("*unsortedResults")
              .each((List<IndexedAccountWithId> unsortedResults) -> {
                ArrayList<IndexedAccountWithId> results = new ArrayList<>(unsortedResults);
                results.sort((IndexedAccountWithId a, IndexedAccountWithId b) -> (int) (a.index - b.index));
                return results.stream().map(o -> o.accountWithId).collect(Collectors.toList());
              }, "*unsortedResults").out("*results");

    topologies.query("getAccountsFromNames", "*requestAccountId", "*names").out("*results")
              .each(Ops.EXPLODE_INDEXED, "*names").out("*index", "*name")
              .select("$$nameToUser", Path.key("*name").must("accountId")).out("*accountId")
              .invokeQuery("getAccountMetadata", "*requestAccountId", "*accountId").out("*metadata")
              .select("$$accountIdToAccount", Path.key("*accountId").filterPred(Ops.IS_NOT_NULL)).out("*account")
              .each((RamaFunction3<Long, Account, AccountMetadata, AccountWithId>) AccountWithId::new, "*accountId", "*account", "*metadata").out("*accountWithId")
              .each((RamaFunction2<Integer, AccountWithId, IndexedAccountWithId>) IndexedAccountWithId::new, "*index", "*accountWithId").out("*indexedAccountWithId")
              .originPartition()
              .agg(Agg.list("*indexedAccountWithId")).out("*unsortedResults")
              .each((List<IndexedAccountWithId> unsortedResults) -> {
                ArrayList<IndexedAccountWithId> results = new ArrayList<>(unsortedResults);
                results.sort((IndexedAccountWithId a, IndexedAccountWithId b) -> (int) (a.index - b.index));
                return results.stream().map(o -> o.accountWithId).collect(Collectors.toList());
              }, "*unsortedResults").out("*results");

    topologies.query("getAccountsFromMentions", "*requestAccountId", "*statusResultWithIds").out("*results")
              .each(MastodonHelpers::getMentionsFromStatusResults, "*statusResultWithIds").out("*mentions")
              .each(Ops.EXPLODE, "*mentions").out("*mention")
              .select("$$nameToUser", Path.key("*mention").must("accountId")).out("*accountId")
              .invokeQuery("getAccountMetadata", "*requestAccountId", "*accountId").out("*metadata")
              .select("$$accountIdToAccount", Path.key("*accountId").filterPred(Ops.IS_NOT_NULL)).out("*account")
              .each((RamaFunction3<Long, Account, AccountMetadata, AccountWithId>) AccountWithId::new, "*accountId", "*account", "*metadata").out("*accountWithId")
              .originPartition()
              .agg(Agg.list("*accountWithId")).out("*results");

    topologies.query("getStatusesFromPointers", "*requestAccountId", "*statusPointers", "*filterOptions").out("*results")
              .ifTrue(new Expr(Ops.IS_NOT_NULL, "*requestAccountId"),
                      Block.select("$$accountIdToFilterIdToFilter", Path.key("*requestAccountId").subselect(Path.all())).out("*filterIdsAndFilters"),
                      Block.each(Ops.IDENTITY, PersistentList.EMPTY).out("*filterIdsAndFilters"))
              .each(MastodonHelpers::createFiltersWithIds, "*filterIdsAndFilters").out("*filters")

              .macro(extractFields("*filterOptions", "*filterContext", "*excludeBlockedAndMuted"))
              .each(FilterContext::getValue, "*filterContext").out("*filterContextValue")

              .each(Ops.EXPLODE_INDEXED, "*statusPointers").out("*index", "*statusPointer")
              .macro(extractFields("*statusPointer", "*authorId", "*statusId", "*shouldExclude"))

              // stop if the status pointer is marked as excluded
              .keepTrue(new Expr(Ops.NOT, "*shouldExclude"))

              // stop if status author is blocked/muted
              .ifTrue(new Expr(Ops.AND, "*excludeBlockedAndMuted",
                                        new Expr(Ops.IS_NOT_NULL, "*requestAccountId"),
                                        new Expr(Ops.NOT_EQUAL, "*requestAccountId", "*authorId")),
                      Block.select("$$accountIdToSuppressions", Relationships.isBlockedOrMutedPath("*requestAccountId", "*authorId", false)).out("*isBlockedOrMuted")
                           .keepTrue(new Expr(Ops.NOT, "*isBlockedOrMuted"))
                           .select("$$accountIdToSuppressions", Path.key("*authorId", "blocked").view(Ops.CONTAINS, "*requestAccountId").filterPred(Ops.NOT)))

              // get the status
              .select("$$accountIdToStatuses", Path.key("*authorId", "*statusId")).out("*statusEdits")
              .each(Ops.FIRST, "*statusEdits").out("*status")
              .keepTrue(new Expr(Ops.IS_NOT_NULL, "*status"))
              // get the original status if there were any edits
              .ifTrue(new Expr(Ops.GREATER_THAN, new Expr(Ops.SIZE, "*statusEdits"), 1),
                      Block.each(Ops.LAST, "*statusEdits").out("*originalStatus"),
                      Block.each(Ops.IDENTITY, null).out("*originalStatus"))
              // resolve the status
              .macro(extractFields("*status", "*content"))
              .macro(MastodonHelpers.resolveStatusResult("*requestAccountId", "*filters", "*filterContextValue", "*authorId", "*statusId", "*status", "*content", "*statusResult"))

              // if the status is boosting a blocked or muted author, we need to drop it
              .ifTrue(new Expr(Ops.AND, "*excludeBlockedAndMuted",
                                        new Expr((StatusResult s) -> s.getContent().isSetBoost(), "*statusResult"),
                                        new Expr(Ops.NOT_EQUAL, "*requestAccountId", "*authorId")),
                     Block.macro(extractFields("*content", "*boosted"))
                           .each((StatusPointer boosted) -> boosted.authorId, "*boosted").out("*boostedAuthorId")
                          .select("$$accountIdToSuppressions", Relationships.isBlockedOrMutedPath("*requestAccountId", "*boostedAuthorId", false)).out("*isBoostingBlockedOrMuted")
                          .keepTrue(new Expr(Ops.NOT, "*isBoostingBlockedOrMuted"))
                          .select("$$accountIdToSuppressions", Path.key("*boostedAuthorId", "blocked").view(Ops.CONTAINS, "*requestAccountId").filterPred(Ops.NOT)))

              // get the poll data
              .ifTrue(new Expr(MastodonHelpers::statusResultHasPoll, "*statusResult"),
                      Block.each(MastodonHelpers::origAuthorId, "*authorId", "*status").out("*origAuthorId")
                           .each(MastodonHelpers::origStatusId, "*statusId", "*status").out("*origStatusId")
                           .hashPartition("*origAuthorId")
                           .localSelect("$$pollVotes",
                              Path.key("*origStatusId")
                                  .subselect(
                                    Path.multiPath(
                                      Path.subselect(Path.key("choices").all().collectOne(Path.first()).last().view(Ops.SIZE)),
                                      Path.key("allVoters")
                                          .multiPath(
                                            Path.view(Ops.SIZE),
                                            Path.ifPath(Path.putCollected("*requestAccountId").isCollected((List l) -> l.get(0) != null),
                                              Path.key("*requestAccountId")))))).out("*pollData"),
                      Block.each(Ops.IDENTITY, null).out("*pollData"))

              .each(MastodonHelpers::getStatusResultText, "*statusResult").out("*text")
              .each(MastodonHelpers::getStatusResultVisibility, "*statusResult").out("*visibility")
              // stop if requester is not allowed to see the status
              .ifTrue(new Expr(Ops.NOT_EQUAL, "*requestAccountId", "*authorId"),
                      // if the request account id is null, only return public/unlisted statuses
                      Block.ifTrue(new Expr(Ops.IS_NULL, "*requestAccountId"),
                              Block.keepTrue(new Expr(Ops.OR, new Expr(Ops.EQUAL, "*visibility", StatusVisibility.Public),
                                                              new Expr(Ops.EQUAL, "*visibility", StatusVisibility.Unlisted))),
                              // otherwise, return private statuses if they are a follower
                              Block.ifTrue(new Expr(Ops.EQUAL, "*visibility", StatusVisibility.Private),
                                      Block.select("$$followerToFollowees", Path.key("*requestAccountId", "*authorId")).out("*followee")
                                           .keepTrue(new Expr(Ops.IS_NOT_NULL, "*followee")),
                                      // return direct statuses if they are mentioned
                                      Block.ifTrue(new Expr(Ops.EQUAL, "*visibility", StatusVisibility.Direct),
                                              Block.select("$$accountIdToAccount", Path.key("*requestAccountId").filterPred(Ops.IS_NOT_NULL)).out("*requestAccount")
                                                   .macro(MastodonHelpers.extractFields("*requestAccount", "*name"))
                                                   .each(Token::parseTokens, "*text").out("*tokens")
                                                   .each(Token::filterMentions, "*tokens").out("*mentions")
                                                   .each((Set<String> mentions, String accountName) -> mentions.contains(accountName), "*mentions", "*name").out("*isMentioned")
                                                   .keepTrue("*isMentioned")))))

              // create query result
              .each((Long statusId, StatusResult statusResult, List pollData, Status originalStatus) -> {
                         if (pollData!=null) {
                           PollInfo p = new PollInfo();
                           List<List> votes = (List) pollData.get(0);
                           Map votesm = new HashMap();
                           for(List l: votes) votesm.put(l.get(0), l.get(1));
                           p.setVoteCounts(votesm);
                           p.setTotalVoters((int) pollData.get(1));
                           if(pollData.size() > 2 && pollData.get(2) != null) p.setOwnVotes((Set) pollData.get(2));
                           else p.setOwnVotes(new HashSet());
                           statusResult.setPollInfo(p);
                         }
                         if (originalStatus != null) {
                             statusResult.setEditTimestamp(statusResult.timestamp);
                             statusResult.setTimestamp(originalStatus.timestamp);
                         }
                         return new StatusResultWithId(statusId, statusResult);
                      }, "*statusId", "*statusResult", "*pollData", "*originalStatus").out("*statusQueryResult")
              .each((RamaFunction2<Integer, StatusResultWithId, IndexedStatusResultWithId>) IndexedStatusResultWithId::new, "*index", "*statusQueryResult").out("*indexedStatusResultWithId")

              .originPartition()
              .agg(Agg.list("*indexedStatusResultWithId")).out("*unsortedResults")
              // get the author ids
              .each((List<IndexedStatusResultWithId> unsortedResults) ->
                  unsortedResults.stream().flatMap(o -> MastodonHelpers.getAuthorIds(o.statusResultWithId.status).stream()).collect(Collectors.toSet()),
                  "*unsortedResults").out("*authorIds")
              // get the account metadata
              // this could have been queried individually for each status in `resolveStatusResult`,
              // but as a perf optimization we are querying it here to avoid wasteful duplicate queries.
              .invokeQuery("getAccountIdToMetadata", "*requestAccountId", "*authorIds").out("*accountIdToMetadata")
              // sort the results and update the account metadata
              .each((List<IndexedStatusResultWithId> unsortedResults, Map<Long, AccountMetadata> accountIdToMetadata) -> {
                ArrayList<IndexedStatusResultWithId> results = new ArrayList<>(unsortedResults);
                results.sort((IndexedStatusResultWithId a, IndexedStatusResultWithId b) -> (int) (a.index - b.index));
                for (IndexedStatusResultWithId res : results) {
                    MastodonHelpers.updateAccountMetadata(res.statusResultWithId.status, accountIdToMetadata);
                }
                return results.stream().map(o -> o.statusResultWithId).collect(Collectors.toList());
              }, "*unsortedResults", "*accountIdToMetadata").out("*sortedResults")
              // get the mentioned accounts
              .invokeQuery("getAccountsFromMentions", "*requestAccountId", "*sortedResults").out("*mentionedAccounts")
              // get the parent accounts for replies
              .each(MastodonHelpers::getParentAccountIdsFromStatusResults, "*sortedResults").out("*parentAccountIds")
              .invokeQuery("getAccountsFromAccountIds", "*requestAccountId", "*parentAccountIds").out("*parentAccounts")
              // create the final object
              .each((List<StatusResultWithId> sortedResults, List<AccountWithId> mentionedAccounts, List<AccountWithId> parentAccounts) -> {
                Map<String, AccountWithId> mentions = new HashMap<>();
                for(AccountWithId accountWithId : mentionedAccounts) mentions.put(accountWithId.account.name, accountWithId);
                for(AccountWithId accountWithId : parentAccounts) mentions.put(accountWithId.account.name, accountWithId);
                return new StatusQueryResults(sortedResults, mentions, false, false);
              }, "*sortedResults", "*mentionedAccounts", "*parentAccounts").out("*results");
  }

  public static class StatusDepotExtractor implements RamaFunction1<TBase, Long> {
    @Override
    public Long invoke(TBase o) {
      if(o instanceof BoostStatus) return ((BoostStatus) o).accountId;
      else if(o instanceof RemoveBoostStatus) return ((RemoveBoostStatus) o).accountId;
      else if(o instanceof AddStatus) return ((AddStatus) o).status.authorId;
      else if(o instanceof EditStatus) return ((EditStatus) o).status.authorId;
      else if(o instanceof RemoveStatus) return ((RemoveStatus) o).accountId;
      else throw new RuntimeException("Unexpected type " + o.getClass());
    }
  }

  public static class ScheduledStatusDepotExtractor implements RamaFunction1<TBase, Long> {
    @Override
    public Long invoke(TBase o) {
      if(o instanceof AddScheduledStatus) return ((AddScheduledStatus) o).status.authorId;
      else if(o instanceof EditStatus) return ((EditStatus) o).status.authorId;
      else if(o instanceof RemoveStatus) return ((RemoveStatus) o).accountId;
      else if(o instanceof EditScheduledStatusPublishTime) return((EditScheduledStatusPublishTime) o).accountId;
      else throw new RuntimeException("Unexpected type " + o.getClass());
    }
  }

  @Override
  public void define(Setup setup, Topologies topologies) {
    setup.declareDepot("*statusDepot", Depot.hashBy(StatusDepotExtractor.class));
    setup.declareDepot("*scheduledStatusDepot", Depot.hashBy(ScheduledStatusDepotExtractor.class));
    setup.declareDepot("*statusWithIdDepot", Depot.disallow());
    setup.declareDepot("*statusAttachmentWithIdDepot", Depot.hashBy(MastodonHelpers.ExtractUuid.class));
    setup.declareDepot("*accountDepot", Depot.hashBy(MastodonHelpers.ExtractName.class));
    setup.declareDepot("*accountWithIdDepot", Depot.disallow());
    setup.declareDepot("*accountEditDepot", Depot.hashBy(MastodonHelpers.ExtractAccountId.class));
    setup.declareDepot("*favoriteStatusDepot", Depot.hashBy(MastodonHelpers.ExtractAccountId.class));
    setup.declareDepot("*bookmarkStatusDepot", Depot.hashBy(MastodonHelpers.ExtractAccountId.class));
    setup.declareDepot("*muteStatusDepot", Depot.hashBy(MastodonHelpers.ExtractAccountId.class));
    setup.declareDepot("*pinStatusDepot", Depot.hashBy(MastodonHelpers.ExtractAccountId.class));
    setup.declareDepot("*conversationDepot", Depot.hashBy(MastodonHelpers.ExtractAccountId.class));
    setup.declareDepot("*pollVoteDepot", Depot.hashBy(MastodonHelpers.ExtractTargetAuthorId.class));
    setup.declareTickDepot("*scheduledStatusTick", scheduledStatusTickMillis);

    setup.declareObject("*homeTimelines", new HomeTimelines(timelineMaxAmount, enableHomeTimelineRefresh));

    setup.clusterDepot("*followAndBlockAccountDepot", Relationships.class.getName(), "*followAndBlockAccountDepot");

    setup.clusterPState("$$accountIdToSuppressions", Relationships.class.getName(), "$$accountIdToSuppressions");
    setup.clusterPState("$$followerToFollowees", Relationships.class.getName(), "$$followerToFollowees");
    setup.clusterPState("$$followerToFolloweesById", Relationships.class.getName(), "$$followerToFolloweesById");
    setup.clusterPState("$$followeeToFollowers", Relationships.class.getName(), "$$followeeToFollowers");

    setup.clusterPState("$$partitionedFollowersControl", Relationships.class.getName(), "$$partitionedFollowersControl");
    setup.clusterPState("$$partitionedFollowers", Relationships.class.getName(), "$$partitionedFollowers");

    setup.clusterPState("$$hashtagToFollowers", Relationships.class.getName(), "$$hashtagToFollowers");
    setup.clusterPState("$$memberIdToListIds", Relationships.class.getName(), "$$memberIdToListIds");
    setup.clusterPState("$$listIdToMemberIds", Relationships.class.getName(), "$$listIdToMemberIds");
    setup.clusterPState("$$listIdToList", Relationships.class.getName(), "$$listIdToList");
    setup.clusterPState("$$accountIdToFilterIdToFilter", Relationships.class.getName(), "$$accountIdToFilterIdToFilter");
    setup.clusterPState("$$followeeToRemoteServerToFollowers", Relationships.class.getName(), "$$followeeToRemoteServerToFollowers");

    declareFollowsBloomFiltersTopology(topologies);
    declareMicrobatchTopologies(topologies);
    declareAccountsTopology(topologies);
    declareStatusTopology(topologies);
    declareQueries(topologies);
  }
}
