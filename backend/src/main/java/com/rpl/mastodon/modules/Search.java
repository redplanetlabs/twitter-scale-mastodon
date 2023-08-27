package com.rpl.mastodon.modules;

import clojure.lang.*;
import com.rpl.mastodon.*;
import com.rpl.mastodon.data.*;
import com.rpl.mastodon.navs.*;
import com.rpl.rama.*;
import com.rpl.rama.helpers.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;
import org.apache.thrift.TUnion;

import java.util.*;
import java.util.stream.Collectors;

import static com.rpl.mastodon.MastodonHelpers.*;

/*
 * This implements search for users, statuses, and hashtags. Search is term-based,
 * and an implicit 5-prefix term is extracted for usernames and a 2-prefix term is
 * extracted for hashtags.
 *
 * Status search is faithful to how Mastodon does it, only letting you search
 * your own statuses and mentions.
 *
 * This module also implements profile directories, providing views of most
 * recent accounts that have opted-in to be discoverable, as well as most
 * recently active accounts.
 */
public class Search implements RamaModule {
  // these constants can be overrided in tests
  public int pageAmount = 500;
  public int maxDirectorySize = 1000;

  public static class MatchInfo {
    public Object res;
    public int termMatches;

    public MatchInfo(Object res, int termMatches) {
      this.res = res;
      this.termMatches = termMatches;
    }
  }

  public static MatchInfo numStatusTermMatches(Object record, String searchTerm, List<String> allTerms) {
    StatusSearchRecord info = (StatusSearchRecord) record;
    int matches = 0;
    for(String term : allTerms) {
      if(info.getTerms().contains(term)) matches++;
    }
    return new MatchInfo(new ArrayList() {{ add(info.getAccountId()); add(info.getStatusId()); }}, matches);
  }

  public static MatchInfo numProfileTermMatches(Object record, String searchTerm, List<String> allTerms) {
    ProfileSearchRecord info = (ProfileSearchRecord) record;
    int matches = 0;
    for(String term: allTerms) {
      if(info.getOtherProfileTerms().contains(term) ||
         info.getUsername().toLowerCase().startsWith(term) ||
         searchTerm.equals(term))
        matches++;
    }
    return new MatchInfo(info.getUsername(), matches);
  }

  public static MatchInfo numHashtagTermMatches(Object hashtagObj, String searchTerm, Object ignore) {
    return new MatchInfo(hashtagObj, ((String) hashtagObj).startsWith(searchTerm) ? 1 : 0);
  }

  public static void emitHashtagTokens(String hashtag, OutputCollector collector) {
    for(int i = 2; i <= Math.min(hashtag.length(), 5); i++) collector.emit(hashtag.substring(0, i));
  }

  // This is the core function in search, looking at a page of documents and determining which ones are
  // a full match.
  public static Map searchForMatches(RamaFunction3<Object, String, List, MatchInfo> termMatchesFn, SortedMap m, String searchTerm, List<String> allTerms, int pageAmount, int limit) {
    List matchList = new ArrayList();
    int termMatches = 0;
    int matches = 0;
    Long lastMatchingId = null;


    for(Long id: (Set<Long>) new TreeSet(m.keySet())) {
      MatchInfo matchInfo = termMatchesFn.invoke(m.get(id), searchTerm, allTerms);
      termMatches += matchInfo.termMatches;
      if(matchInfo.termMatches == allTerms.size()) {
        matches++;
        if(matchList.size() < limit) {
          lastMatchingId = id;
          matchList.add(matchInfo.res);
        }
      }
    }

    Map ret = new HashMap();
    ret.put("term", searchTerm);
    ret.put("matchList", matchList);
    ret.put("termMatches", termMatches);
    ret.put("totalMatches", matches);
    Long nextId = null;
    if(matches > limit) nextId = lastMatchingId;
    else if(m.size() == pageAmount) nextId = (Long) m.lastKey();
    ret.put("nextId", nextId);
    return ret;
  }

  private Block finishProfileTermIndexing(KeyToFixedItemsPStateGroup profileTerms, String nameVar, String displayNameVar) {
    String termSetVar = Helpers.genVar("termSet");
    String termVar = Helpers.genVar("term");
    String profileSearchRecordVar = Helpers.genVar("profileSearchRecord");
    return Block.each((String username, String displayName) -> {
                  PersistentHashSet ret = PersistentHashSet.EMPTY;
                  if(displayName!=null) {
                    for(String term: displayName.toLowerCase().split(" ")) {
                      if(!term.isEmpty()) ret = (PersistentHashSet) ret.cons(term);
                    }
                  }
                  username = username.toLowerCase();
                  ret = (PersistentHashSet) ret.cons(username);
                  if(username.length() > 5)  ret = (PersistentHashSet) ret.cons(username.substring(0, 5));
                  return ret;
                }, nameVar, displayNameVar).out(termSetVar)
                .each(Ops.EXPLODE, termSetVar).out(termVar)
                .hashPartition(termVar)
                .each((PersistentHashSet termSet, String term, String username) ->
                  new ProfileSearchRecord((Set) termSet.disjoin(term).disjoin(username.toLowerCase()), username),
                  termSetVar, termVar, nameVar).out(profileSearchRecordVar)
                .macro(profileTerms.addItem(termVar, profileSearchRecordVar));
  }

  public static Map basicSearchStartingInfo(Object o) {
    Map ret = new HashMap();
    ret.put("term", o.toString());
    ret.put("nextId", 0L);
    return ret;
  }

  // - Returns map containing "term", "matchList" (at most "*limit"), "nextId", "termMatches".
  // - this macro is coded for conciseness by taking advantage that it's supposed to be the
  //   entire query implementation
  //   - as such it doesn't generate any intermediate vars
  //   - instead, other vars used by caller (e.g. for partitioner function) should be generated
  //   - this macro requires vars called "*terms" and "*limit" to be in scope
  private Block bestTermSearchQuery(String pstateVar, RamaFunction2<String, String, Block> partitionerMacro, RamaFunction3<Object, String, List, MatchInfo> itemTermMatches) {
    return Block.each(Ops.SIZE, "*terms").out("*numTerms")
                .loopWithVars(LoopVars.var("*i", 0)
                                      .var("*best", null),
                  Block.each(Ops.GET, "*terms", "*i").out("*term")
                       .macro(partitionerMacro.invoke("*term", "*queryKey"))
                       .localSelect(pstateVar, Path.key("*queryKey").sortedMapRangeFrom(0, pageAmount)).out("*infoMap")
                       .each(Search::searchForMatches, new Constant(itemTermMatches), "*infoMap", "*term", "*terms", pageAmount, "*limit").out("*info")
                       .each((Map best, Map curr) -> {
                         if(best==null) return curr;
                         else {
                           int bestMatches = (int) best.get("totalMatches");
                           int bestTermMatches = (int) best.get("termMatches");
                           int bestTermLength = ((String) best.get("term")).length();
                           int currMatches = (int) curr.get("totalMatches");
                           int currTermMatches = (int) curr.get("termMatches");
                           int currTermLength = ((String) curr.get("term")).length();
                           if(bestMatches > currMatches) return best;
                           else if(currMatches > bestMatches) return curr;
                           else if(bestTermMatches > currTermMatches) return best;
                           else if(currTermMatches > bestTermMatches) return curr;
                           else if(bestTermLength > currTermLength) return best;
                           else return curr;
                         }
                       }, "*best", "*info").out("*nextBest")
                       .each(Ops.SIZE, new Expr(Ops.GET, "*nextBest", "matchList")).out("*numMatches")
                       .ifTrue(new Expr(Ops.EQUAL, "*i", new Expr(Ops.DEC, "*numTerms")),
                         Block.emitLoop("*nextBest"),
                         Block.continueLoop(new Expr(Ops.INC, "*i"), "*nextBest"))).out("*info")
                .originPartition();
  }

  // - Returns map with "term", "nextId", "matchList"
  // - See notes for bestTermSearchQuery regarding unusual way this macro is structured
  // - requires "*terms", "*startParams", and "*limit" to be in scope
  private Block termsSearchQuery(String pstateVar, RamaFunction1<String, Block> bestTermSearchMacro, RamaFunction2<String, String, Block> partitionerMacro, RamaFunction3<Object, String, List, MatchInfo> itemTermMatches) {
    return Block.ifTrue(new Expr(Ops.IS_NULL, "*startParams"),
                   Block.macro(bestTermSearchMacro.invoke("*params")),
                   Block.each(Ops.IDENTITY, "*startParams").out("*params"))
                .each(Ops.GET, "*params", "term").out("*bestTerm")
                .each(Ops.GET, "*params", "nextId").out("*startId")
                .each(Ops.OR, new Expr(Ops.GET, "*params", "matchList"), new Expr(ArrayList::new)).out("*matchList")
                .macro(partitionerMacro.invoke("*bestTerm", "*queryKey"))
                .loopWithVars(LoopVars.var("*id", "*startId"),
                  Block.ifTrue(new Expr(Ops.OR, new Expr(Ops.GREATER_THAN_OR_EQUAL, new Expr(Ops.SIZE, "*matchList"), "*limit"),
                                                new Expr(Ops.IS_NULL, "*id")),
                    Block.emitLoop("*id"),
                    Block.localSelect(pstateVar, Path.key("*queryKey")
                                                     .sortedMapRangeFrom("*id", SortedRangeFromOptions.excludeStart()
                                                                                                      .maxAmt(pageAmount))).out("*infoMap")
                         .each(Ops.MINUS, "*limit", new Expr(Ops.SIZE, "*matchList")).out("*iterLimit")
                         .each(Search::searchForMatches, new Constant(itemTermMatches), "*infoMap", "*bestTerm", "*terms", pageAmount, "*iterLimit").out("*m")
                         .each(Ops.GET, "*m", "nextId").out("*nextId")
                         .each(Ops.GET, "*m", "matchList").out("*newMatchList")
                         .each((List ret, List newMatchList) -> ret.addAll(newMatchList), "*matchList", "*newMatchList")
                         .ifTrue(new Expr(Ops.IS_NULL, "*nextId"),
                           Block.emitLoop("*nextId"),
                           Block.yieldIfOvertime()
                                .continueLoop("*nextId")))).out("*nextId")
                .each((String bestTerm, Long nextId, List inputMatchList) -> {
                  Set matches = new HashSet();
                  List matchList = new ArrayList();
                  for(Object o: inputMatchList) {
                    if(!matches.contains(o)) matchList.add(o);
                    matches.add(o);
                  }
                  Map ret = new HashMap();
                  ret.put("term", bestTerm);
                  ret.put("nextId", nextId);
                  ret.put("matchList", matchList);
                  return ret;
                }, "*bestTerm", "*nextId", "*matchList").out("*info")
                .originPartition();
  }

  private SubBatch filterStatusWithIdSubBatch(String microbatchVar) {
    Block b = Block.explodeMicrobatch(microbatchVar).out("*data")
                   .keepTrue(new Expr(Ops.IS_INSTANCE_OF, StatusWithId.class, "*data"))
                   .macro(extractFields("*data", "*statusId", "*status"))
                   .macro(extractFields("*status", "*authorId", "*content", "*timestamp"))
                   .each(Ops.MODULE_INSTANCE_INFO).out("*moduleInfo")
                   .each(Ops.TUPLE, new Expr(Ops.TUPLE, new Expr(Ops.TUPLE, "*data", "*timestamp"), "*moduleInfo")).out("*tupleInit")
                   .globalPartition()
                   .agg(Agg.combiner(new GlobalTimelines.DataFilter(maxDirectorySize), "*tupleInit")).out("*allTuples")
                   // sort by timestamp so status IDs are added in correct order
                   .each((PersistentVector tuples) -> {
                     List<PersistentVector> l = new ArrayList(tuples);
                     l.sort((Object o1, Object o2) -> {
                       PersistentVector v1 = (PersistentVector) o1;
                       PersistentVector v2 = (PersistentVector) o2;
                       return ((Long) v1.nth(1)).compareTo((Long) v2.nth(1));
                     });
                     return l.stream().map(v -> v.nth(0)).collect(Collectors.toList());
                   }, "*allTuples").out("*statusWithIds");
    return new SubBatch(b, "*statusWithIds");
  }

  private static PersistentVector prependId(List<Long> v, long id, int maxSize, Boolean shouldInclude) {
    if (shouldInclude != null && shouldInclude) {
      List<Long> l = new ArrayList<>();
      l.add(id);
      l.addAll(v);
      l = l.stream().distinct().collect(Collectors.toList());
      if (l.size() > maxSize) l = l.subList(0, maxSize);
      return PersistentVector.create(l);
    } else {
      // remove the id
      return PersistentVector.create(v.stream().filter((Long aid) -> aid != id).collect(Collectors.toList()));
    }
  }

  private static PersistentVector prependIds(List<Long> v, List<Long> ids, int maxSize) {
    List<Long> l = new ArrayList<>(ids);
    l.addAll(v);
    l = l.stream().distinct().collect(Collectors.toList());;
    if (l.size() > maxSize) l = l.subList(0, maxSize);
    return PersistentVector.create(l);
  }

  private static PersistentVector removeId(List<List<Long>> v, long id) {
    return PersistentVector.create(v.stream().filter((List<Long> tuple) -> tuple.get(0) != id).collect(Collectors.toList()));
  }

  @Override
  public void define(Setup setup, Topologies topologies) {
    setup.clusterDepot("*statusWithIdDepot", Core.class.getName(), "*statusWithIdDepot");
    setup.clusterDepot("*accountWithIdDepot", Core.class.getName(), "*accountWithIdDepot");
    setup.clusterDepot("*accountEditDepot", Core.class.getName(), "*accountEditDepot");
    setup.clusterDepot("*reviewHashtagDepot", TrendsAndHashtags.class.getName(), "*reviewHashtagDepot");
    setup.clusterPState("$$nameToUser", Core.class.getName(), "$$nameToUser");
    setup.clusterPState("$$accountIdToAccount", Core.class.getName(), "$$accountIdToAccount");
    setup.clusterQuery("*getAccountsFromAccountIds", Core.class.getName(), "getAccountsFromAccountIds");

    MicrobatchTopology search = topologies.microbatch("search");
    KeyToFixedItemsPStateGroup statusTerms = new KeyToFixedItemsPStateGroup("$$statusTerms", 1000, String.class, StatusSearchRecord.class);
    statusTerms.declarePStates(search);

    // key is term or prefix
    KeyToFixedItemsPStateGroup profileTerms = new KeyToFixedItemsPStateGroup("$$profileTerms", 10000, String.class, ProfileSearchRecord.class);
    profileTerms.declarePStates(search);

    // hashtag prefix -> full hashtags
    KeyToUniqueFixedItemsPStateGroup hashtagPrefixes = new KeyToUniqueFixedItemsPStateGroup("$$hashtagPrefixes", 10000, String.class, String.class);
    hashtagPrefixes.declarePStates(search);
    // hashtag prefix -> full hashtags
    KeyToUniqueFixedItemsPStateGroup reviewedHashtagPrefixes = new KeyToUniqueFixedItemsPStateGroup("$$reviewedHashtagPrefixes", 10000, String.class, String.class);
    reviewedHashtagPrefixes.declarePStates(search);

    search.pstate("$$allNewAccountIds", List.class).global().initialValue(PersistentVector.EMPTY);
    search.pstate("$$allActiveAccountIds", List.class).global().initialValue(PersistentVector.EMPTY);
    search.pstate("$$localNewAccountIds", List.class).global().initialValue(PersistentVector.EMPTY);
    search.pstate("$$localActiveAccountIds", List.class).global().initialValue(PersistentVector.EMPTY);

    search.source("*statusWithIdDepot").out("*microbatch")
          // update directories
          .batchBlock(
            Block.subBatch(filterStatusWithIdSubBatch("*microbatch")).out("*statusWithIds")
                 .each((List<StatusWithId> swids) -> swids.stream().map(swid -> swid.status.authorId).distinct().collect(Collectors.toList()), "*statusWithIds").out("*allAccountIds")
                 .invokeQuery("*getAccountsFromAccountIds", null, "*allAccountIds").out("*allAccounts")
                 .each((List<AccountWithId> awids) -> awids.stream().filter(awid -> awid.account.discoverable).map(awid -> awid.accountId).collect(Collectors.toList()), "*allAccounts").out("*discoverableAccountIds")
                 .each((List<AccountWithId> awids) -> awids.stream().filter(awid -> awid.account.discoverable && awid.account.content.isSetLocal()).map(awid -> awid.accountId).collect(Collectors.toList()), "*allAccounts").out("*localDiscoverableAccountIds")
                 .globalPartition()
                 .localTransform("$$allActiveAccountIds", Path.term(Search::prependIds, "*discoverableAccountIds", maxDirectorySize))
                 .localTransform("$$localActiveAccountIds", Path.term(Search::prependIds, "*localDiscoverableAccountIds", maxDirectorySize)))

          .explodeMicrobatch("*microbatch").out("*statusWithId")
          .keepTrue(new Expr(Ops.OR, new Expr(Ops.IS_INSTANCE_OF, EditStatus.class, "*statusWithId"),
                                     new Expr(Ops.IS_INSTANCE_OF, StatusWithId.class, "*statusWithId")))
          .macro(extractFields("*statusWithId", "*statusId", "*status"))
          // NOTE:
          //  - visibility doesn't matter since statuses are visible to mentioned users
          // in all cases of visibility
          //  - semantics of edits are unclear – should a status still show up for search
          //    if mention is removed in an edit?
          //  - choosing to still return those as search results in this case
          .macro(extractFields("*status", "*authorId", "*content"))

          // ignore boosts
          .keepTrue(new Expr(Ops.OR, new Expr(Ops.IS_INSTANCE_OF, NormalStatusContent.class, "*content"),
                                     new Expr(Ops.IS_INSTANCE_OF, ReplyStatusContent.class, "*content")))
          .macro(extractFields("*content", "*visibility", "*text"))
          .anchor("FanoutRoot")

          // index hashtags
          .keepTrue(new Expr(Ops.EQUAL, StatusVisibility.Public, "*visibility"))
          .each(Token::parseTokens, "*text").out("*tokens")
          .each(Token::filterHashtags, "*tokens").out("*hashtags")
          .each(Ops.EXPLODE, "*hashtags").out("*hashtag")
          .each(Search::emitHashtagTokens, "*hashtag").out("*prefix")
          .hashPartition("*prefix")
          .macro(hashtagPrefixes.addItem("*prefix", "*hashtag"))

          .hook("FanoutRoot")
          .each(Ops.IDENTITY, "*authorId").out("*targetAccountId")
          .anchor("SelfFanout")

          .hook("FanoutRoot")
          .each((String text, OutputCollector collector) -> {
            for(String mention: Token.filterMentions(Token.parseTokens(text))) collector.emit(mention);
          }, "*text").out("*targetUsername")
          .select("$$nameToUser", Path.key("*targetUsername", "accountId")).out("*targetAccountId")
          .keepTrue(new Expr(Ops.IS_NOT_NULL, "*targetAccountId"))
          .anchor("MentionsFanout")

          .unify("SelfFanout", "MentionsFanout")
          .hashPartition("*targetAccountId")

          .each((String text, Long authorId, Long statusId) -> {
            IPersistentSet ret = PersistentHashSet.EMPTY;
            List<Token> tokens = Token.parseTokens(text);
            for(Token t: tokens) ret = (IPersistentSet) ret.cons(t.getOrigContent().toLowerCase());
            return new StatusSearchRecord((Set<String>)ret, authorId, statusId);
          }, "*text", "*authorId", "*statusId").out("*statusSearchRecord")
          .macro(statusTerms.addItem(new Expr(Object::toString, "*targetAccountId"), "*statusSearchRecord"));

    search.source("*accountWithIdDepot").out("*microbatch")
          .explodeMicrobatch("*microbatch").out("*accountWithId")
          .macro(extractFields("*accountWithId", "*accountId", "*account"))
          .macro(extractFields("*account", "*name", "*timestamp", "*displayName", "*content", "*discoverable"))
          .atomicBlock(Block.macro(finishProfileTermIndexing(profileTerms, "*name", "*displayName")))
          // update directories
          .globalPartition()
          // update active account directory
          .localTransform("$$allActiveAccountIds", Path.term(Search::prependId, "*accountId", maxDirectorySize, "*discoverable"))
          .ifTrue(new Expr(Ops.IS_INSTANCE_OF, LocalAccount.class, "*content"),
                  Block.localTransform("$$localActiveAccountIds", Path.term(Search::prependId, "*accountId", maxDirectorySize, "*discoverable")))
          // update new account directory
          .keepTrue("*discoverable")
          .each(Ops.TUPLE, "*accountId", "*timestamp").out("*tuple")
          .agg("$$allNewAccountIds", Agg.topMonotonic(maxDirectorySize, "*tuple").idFunction(Ops.FIRST).sortValFunction(Ops.LAST))
          .keepTrue(new Expr(Ops.IS_INSTANCE_OF, LocalAccount.class, "*content"))
          .agg("$$localNewAccountIds", Agg.topMonotonic(maxDirectorySize, "*tuple").idFunction(Ops.FIRST).sortValFunction(Ops.LAST));

    search.source("*accountEditDepot").out("*microbatch")
          .explodeMicrobatch("*microbatch").out("*editAccount")
          .macro(extractFields("*editAccount", "*accountId"))
          .select("$$accountIdToAccount", Path.key("*accountId")).out("*account")
          .macro(extractFields("*account", "*name", "*timestamp", "*content"))
          // index display name
          .each((EditAccount e) -> e.getEdits().stream().filter(EditAccountField::isSetDisplayName).findFirst().map(TUnion::getFieldValue).orElse(null),
                  "*editAccount").out("*editDisplayName")
          .ifTrue(new Expr(Ops.IS_NOT_NULL, "*editDisplayName"),
                  Block.atomicBlock(Block.macro(finishProfileTermIndexing(profileTerms, "*name", "*editDisplayName"))))
          // update directories
          .each((EditAccount e) -> e.getEdits().stream().filter(EditAccountField::isSetDiscoverable).findFirst().map(TUnion::getFieldValue).orElse(null),
                  "*editAccount").out("*editDiscoverable")
          .keepTrue(new Expr(Ops.IS_NOT_NULL, "*editDiscoverable"))
          .globalPartition()
          // update active account directory
          .localTransform("$$allActiveAccountIds", Path.term(Search::prependId, "*accountId", maxDirectorySize, "*editDiscoverable"))
          .ifTrue(new Expr(Ops.IS_INSTANCE_OF, LocalAccount.class, "*content"),
                  Block.localTransform("$$localActiveAccountIds", Path.term(Search::prependId, "*accountId", maxDirectorySize, "*editDiscoverable")))
          // update new account directory
          .ifTrue("*editDiscoverable",
                  Block.each(Ops.TUPLE, "*accountId", "*timestamp").out("*tuple")
                       .agg("$$allNewAccountIds", Agg.topMonotonic(maxDirectorySize, "*tuple").idFunction(Ops.FIRST).sortValFunction(Ops.LAST))
                       .keepTrue(new Expr(Ops.IS_INSTANCE_OF, LocalAccount.class, "*content"))
                       .agg("$$localNewAccountIds", Agg.topMonotonic(maxDirectorySize, "*tuple").idFunction(Ops.FIRST).sortValFunction(Ops.LAST)),
                  Block.localTransform("$$allNewAccountIds", Path.term(Search::removeId, "*accountId"))
                       .localTransform("$$localNewAccountIds", Path.term(Search::removeId, "*accountId")));

    search.source("*reviewHashtagDepot").out("*microbatch")
          .explodeMicrobatch("*microbatch").out("*data")
          .macro(extractFields("*data", "*item"))
          .subSource("*data",
            SubSource.create(ReviewItem.class)
                     .each(Search::emitHashtagTokens, "*item").out("*prefix")
                     .hashPartition("*prefix")
                     .macro(reviewedHashtagPrefixes.addItem("*prefix", "*item")),
            SubSource.create(RemoveReviewItem.class)
                     .each(Search::emitHashtagTokens, "*item").out("*prefix")
                     .hashPartition("*prefix")
                     .macro(reviewedHashtagPrefixes.removeItem("*prefix", "*item")));

    String accountIdVar = Helpers.genVar("accountId");
    // Returns map with "term", "nextId", "matchList".
    topologies.query("statusTermsSearch", accountIdVar, "*terms", "*startParams", "*limit").out("*info")
              .macro(termsSearchQuery(
                      "$$statusTerms",
                      (String paramsVar) -> Block.each(Search::basicSearchStartingInfo, accountIdVar).out(paramsVar),
                      (String termVar, String keyVar) ->
                        Block.hashPartition(accountIdVar)
                             .each(Ops.IDENTITY, termVar).out(keyVar),
                      Search::numStatusTermMatches));

    topologies.query("bestProfileTermSearch", "*terms", "*limit").out("*info")
              .macro(bestTermSearchQuery(
                "$$profileTerms",
                (String termVar, String keyVar) ->
                  Block.hashPartition(termVar)
                       .each(Ops.IDENTITY, termVar).out(keyVar),
                Search::numProfileTermMatches));

    // - Returns map with "term", "nextId", "matchList".
    // - NOTE: display name could have changed since the ProfileSearchRecord was indexed,
    // so this could return outdated matches. This could be extended to verify
    // username's still match with current display name, but Mastodon does not appear to
    // do so and will sometimes return outdated records that don't currently match. (People
    // change their display names infrequently enough that this isn't a concern)
    // - NOTE: this doesn't search for exact match since it doesn't fit the character of this
    // query topology, which is paginated searching
    topologies.query("profileTermsSearch", "*inputTerms", "*startParams", "*limit").out("*info")
              .each((List<String> terms) -> {
                if(terms.size()!=1) return terms;
                List ret = new ArrayList(terms);
                String term = terms.get(0);
                if(term.length() > 5) ret.add(term.substring(0, 5));
                return ret;
              }, "*inputTerms").out("*terms")
              .macro(termsSearchQuery(
                "$$profileTerms",
                (String paramsVar) -> Block.invokeQuery("bestProfileTermSearch", "*terms", "*limit").out(paramsVar),
                (String termVar, String keyVar) ->
                  Block.hashPartition(termVar)
                       .each(Ops.IDENTITY, termVar).out(keyVar),
                Search::numProfileTermMatches));

    topologies.query("hashtagSearch", "*str", "*startParams", "*limit").out("*info")
              .each((String str) -> Arrays.asList(str), "*str").out("*terms")
              .macro(termsSearchQuery(
                "$$hashtagPrefixes",
                (String paramsVar) -> Block.each(Search::basicSearchStartingInfo, "*str").out(paramsVar),
                (String termVar, String keyVar) ->
                  Block.each((String str) -> str.substring(0, Math.min(5, str.length())), termVar).out(keyVar)
                       .hashPartition(keyVar),
                Search::numHashtagTermMatches));

    topologies.query("reviewedHashtagSearch", "*str", "*startParams", "*limit").out("*info")
              .each((String str) -> Arrays.asList(str), "*str").out("*terms")
              .macro(termsSearchQuery(
                "$$reviewedHashtagPrefixes",
                (String paramsVar) -> Block.each(Search::basicSearchStartingInfo, "*str").out(paramsVar),
                (String termVar, String keyVar) ->
                  Block.each((String str) ->  str.substring(0, Math.min(5, str.length())), termVar).out(keyVar)
                       .hashPartition(keyVar),
                Search::numHashtagTermMatches));
  }
}
