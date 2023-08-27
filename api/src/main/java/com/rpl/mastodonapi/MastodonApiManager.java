package com.rpl.mastodonapi;

import clojure.lang.PersistentVector;

import com.google.common.collect.Lists;
import com.rpl.mastodon.*;
import com.rpl.mastodon.data.*;
import com.rpl.mastodon.modules.*;
import com.rpl.mastodonapi.pojos.*;
import com.rpl.rama.*;
import com.rpl.rama.cluster.ClusterManagerBase;
import com.rpl.rama.diffs.*;
import com.rpl.rama.ops.Ops;

import java.io.IOException;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.AbstractMap.SimpleEntry;

public class MastodonApiManager {
    public static final String RELATIONSHIPS_MODULE_NAME = Relationships.class.getName();
    public static final String CORE_MODULE_NAME = Core.class.getName();
    public static final String HASHTAGS_MODULE_NAME = TrendsAndHashtags.class.getName();
    public static final String GLOBAL_TIMELINES_MODULE_NAME = GlobalTimelines.class.getName();
    public static final String SEARCH_MODULE_NAME = Search.class.getName();
    public static final String NOTIFICATIONS_MODULE_NAME = Notifications.class.getName();

    private static final int MAX_LIMIT = 40;
    private static final int DEFAULT_LIMIT = 20;
    private static final int ANCESTORS_LIMIT = 20;
    private static final int DESCENDANTS_LIMIT = 20;
    private static final int MAX_PAGING_ITERATIONS = 10;
    private static final int STREAM_QUERY_LIMIT = 50;

    private final Depot accountDepot;
    private final Depot statusDepot;
    private final Depot scheduledStatusDepot;
    private final Depot statusAttachmentWithIdDepot;
    private final Depot followAndBlockAccountDepot;
    private final Depot muteAccountDepot;
    private final Depot listDepot;
    private final Depot conversationDepot;
    private final Depot featureAccountDepot;
    private final Depot featureHashtagDepot;
    private final Depot favoriteStatusDepot;
    private final Depot bookmarkStatusDepot;
    private final Depot muteStatusDepot;
    private final Depot pinStatusDepot;
    private final Depot pollVoteDepot;
    private final Depot accountEditDepot;
    private final Depot dismissDepot;
    private final Depot filterDepot;
    private final Depot authCodeDepot;
    private final Depot followHashtagDepot;
    private final Depot noteDepot;
    private final Depot removeFollowSuggestionDepot;

    private final PState nameToUser;
    private final PState accountIdToStatuses;
    private final PState accountIdToScheduledStatuses;
    private final PState accountIdToFollowRequests;
    private final PState accountIdToFollowRequestsById;
    private final PState followerToFolloweesById;
    private final PState followeeToFollowersById;
    private final PState authorIdToListIds;
    private final PState listIdToList;
    private final PState listIdToMemberIds;
    private final PState listIdToListTimeline;
    private final PState listIdToListTimelineReverse;
    private final PState featurerToFeaturees;
    private final PState accountIdToSuppressions;
    private final PState statusIdToBoosters;
    private final PState statusIdToFavoriters;
    private final PState pinnerToStatusIds;
    private final PState postUUIDToStatusId;
    private final PState globalTimelines;
    private final PState globalTimelinesReverse;
    private final PState hashtagToStatusPointers;
    private final PState hashtagToStatusPointersReverse;
    private final PState accountIdToDirectMessages;
    private final PState accountIdToDirectMessagesById;
    private final PState statusIdToConvoId;
    private final PState accountIdToConvoIds;
    private final PState uuidToAttachment;
    private final PState accountIdToAttachmentStatusIds;
    private final PState favoriterToStatusPointers;
    private final PState bookmarkerToStatusPointers;
    private final PState featurerToHashtags;
    private final PState accountIdToHashtagActivity;
    private final PState remoteUrlToStatusId;
    private final PState pollVotes;
    private final PState accountIdToNotificationsTimeline;
    private final PState accountIdToFilterIdToFilter;
    private final PState postUUIDToGeneratedId;
    private final PState authCodeToAccountId;
    private final PState accountIdToRecentHashtags;
    private final PState hashtagToFollowers;
    private final PState hashtagTrends;
    private final PState linkTrends;
    private final PState statusTrends;
    private final PState allNewAccountIds;
    private final PState allActiveAccountIds;
    private final PState localNewAccountIds;
    private final PState localActiveAccountIds;
    private final PState followeeToRemoteServerToFollowers;

    private final QueryTopologyClient<StatusQueryResults> getAccountTimeline;
    private final QueryTopologyClient<StatusQueryResults> getAncestors;
    private final QueryTopologyClient<StatusQueryResults> getDescendants;
    private final QueryTopologyClient<List<AccountWithId>> getAccountsFromAccountIds;
    private final QueryTopologyClient<List<AccountWithId>> getAccountsFromNames;
    private final QueryTopologyClient<AccountRelationshipQueryResult> getAccountRelationship;
    private final QueryTopologyClient<List<Long>> getFamiliarFollowers;
    private final QueryTopologyClient<StatusQueryResults> getHomeTimeline;
    private final QueryTopologyClient<Map<Integer, List<StatusPointer>>> getHomeTimelinesUntil;
    private final QueryTopologyClient<StatusQueryResults> getDirectTimeline;
    private final QueryTopologyClient<StatusQueryResults> getHashtagTimeline;
    private final QueryTopologyClient<StatusQueryResults> getListTimeline;
    private final QueryTopologyClient<List<AccountListWithId>> getListsFromAuthor;
    private final QueryTopologyClient<List<Conversation>> getConversationTimeline;
    private final QueryTopologyClient<Conversation> getConversation;
    private final QueryTopologyClient<List<FeaturedHashtagInfo>> getFeaturedHashtags;
    private final QueryTopologyClient<StatusQueryResults> getStatusesFromPointers;
    private final QueryTopologyClient<Map> profileTermsSearch;
    private final QueryTopologyClient<Map> statusTermsSearch;
    private final QueryTopologyClient<Map> hashtagSearch;
    private final QueryTopologyClient<Set<Long>> getWhoToFollowSuggestions;
    private final QueryTopologyClient<Map<String, ItemStats>> batchHashtagStats;
    private final QueryTopologyClient<Map<String, ItemStats>> batchLinkStats;

    public MastodonApiManager(ClusterManagerBase cluster) {
        accountDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*accountDepot");
        statusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*statusDepot");
        scheduledStatusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*scheduledStatusDepot");
        statusAttachmentWithIdDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*statusAttachmentWithIdDepot");
        followAndBlockAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*followAndBlockAccountDepot");
        muteAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*muteAccountDepot");
        listDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*listDepot");
        conversationDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*conversationDepot");
        featureAccountDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*featureAccountDepot");
        featureHashtagDepot = cluster.clusterDepot(HASHTAGS_MODULE_NAME, "*featureHashtagDepot");
        favoriteStatusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*favoriteStatusDepot");
        bookmarkStatusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*bookmarkStatusDepot");
        muteStatusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*muteStatusDepot");
        pinStatusDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*pinStatusDepot");
        pollVoteDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*pollVoteDepot");
        accountEditDepot = cluster.clusterDepot(CORE_MODULE_NAME, "*accountEditDepot");
        dismissDepot = cluster.clusterDepot(NOTIFICATIONS_MODULE_NAME, "*dismissDepot");
        filterDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*filterDepot");
        authCodeDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*authCodeDepot");
        followHashtagDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*followHashtagDepot");
        noteDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*noteDepot");
        removeFollowSuggestionDepot = cluster.clusterDepot(RELATIONSHIPS_MODULE_NAME, "*removeFollowSuggestionDepot");

        nameToUser = cluster.clusterPState(CORE_MODULE_NAME, "$$nameToUser");
        accountIdToStatuses = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToStatuses");
        accountIdToScheduledStatuses = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToScheduledStatuses");
        accountIdToFollowRequests = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToFollowRequests");
        accountIdToFollowRequestsById = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToFollowRequestsById");
        followerToFolloweesById = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$followerToFolloweesById");
        followeeToFollowersById = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$followeeToFollowersById");
        authorIdToListIds = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$authorIdToListIds");
        listIdToList = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$listIdToList");
        listIdToMemberIds = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$listIdToMemberIds");
        listIdToListTimeline = cluster.clusterPState(CORE_MODULE_NAME, "$$listIdToListTimeline");
        listIdToListTimelineReverse = cluster.clusterPState(CORE_MODULE_NAME, "$$listIdToListTimelineReverse");
        featurerToFeaturees = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$featurerToFeaturees");
        accountIdToSuppressions = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToSuppressions");
        statusIdToBoosters = cluster.clusterPState(CORE_MODULE_NAME, "$$statusIdToBoosters");
        statusIdToFavoriters = cluster.clusterPState(CORE_MODULE_NAME, "$$statusIdToFavoriters");
        pinnerToStatusIds = cluster.clusterPState(CORE_MODULE_NAME, "$$pinnerToStatusIds");
        postUUIDToStatusId = cluster.clusterPState(CORE_MODULE_NAME, "$$postUUIDToStatusId");
        globalTimelines = cluster.clusterPState(GLOBAL_TIMELINES_MODULE_NAME, "$$globalTimelines");
        globalTimelinesReverse = cluster.clusterPState(GLOBAL_TIMELINES_MODULE_NAME, "$$globalTimelinesReverse");
        hashtagToStatusPointers = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$hashtagToStatusPointers");
        hashtagToStatusPointersReverse = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$hashtagToStatusPointersReverse");
        accountIdToDirectMessages = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToDirectMessages");
        accountIdToDirectMessagesById = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToDirectMessagesById");
        statusIdToConvoId = cluster.clusterPState(CORE_MODULE_NAME, "$$statusIdToConvoId");
        accountIdToConvoIds = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToConvoIds");
        uuidToAttachment = cluster.clusterPState(CORE_MODULE_NAME, "$$uuidToAttachment");
        accountIdToAttachmentStatusIds = cluster.clusterPState(CORE_MODULE_NAME, "$$accountIdToAttachmentStatusIds");
        favoriterToStatusPointers = cluster.clusterPState(CORE_MODULE_NAME, "$$favoriterToStatusPointers");
        bookmarkerToStatusPointers = cluster.clusterPState(CORE_MODULE_NAME, "$$bookmarkerToStatusPointers");
        featurerToHashtags = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$featurerToHashtags");
        accountIdToHashtagActivity = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$accountIdToHashtagActivity");
        remoteUrlToStatusId = cluster.clusterPState(CORE_MODULE_NAME, "$$remoteUrlToStatusId");
        pollVotes = cluster.clusterPState(CORE_MODULE_NAME, "$$pollVotes");
        accountIdToNotificationsTimeline = cluster.clusterPState(NOTIFICATIONS_MODULE_NAME, "$$accountIdToNotificationsTimeline");
        accountIdToFilterIdToFilter = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$accountIdToFilterIdToFilter");
        postUUIDToGeneratedId = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$postUUIDToGeneratedId");
        authCodeToAccountId = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$authCodeToAccountId");
        accountIdToRecentHashtags = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$accountIdToRecentHashtags");
        hashtagToFollowers = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$hashtagToFollowers");
        hashtagTrends = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$hashtagTrends");
        linkTrends = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$linkTrends");
        statusTrends = cluster.clusterPState(HASHTAGS_MODULE_NAME, "$$statusTrends");
        allNewAccountIds = cluster.clusterPState(SEARCH_MODULE_NAME, "$$allNewAccountIds");
        allActiveAccountIds = cluster.clusterPState(SEARCH_MODULE_NAME, "$$allActiveAccountIds");
        localNewAccountIds = cluster.clusterPState(SEARCH_MODULE_NAME, "$$localNewAccountIds");
        localActiveAccountIds = cluster.clusterPState(SEARCH_MODULE_NAME, "$$localActiveAccountIds");
        followeeToRemoteServerToFollowers = cluster.clusterPState(RELATIONSHIPS_MODULE_NAME, "$$followeeToRemoteServerToFollowers");

        getAccountTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountTimeline");
        getAncestors = cluster.clusterQuery(CORE_MODULE_NAME, "getAncestors");
        getDescendants = cluster.clusterQuery(CORE_MODULE_NAME, "getDescendants");
        getAccountsFromAccountIds = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountsFromAccountIds");
        getAccountsFromNames = cluster.clusterQuery(CORE_MODULE_NAME, "getAccountsFromNames");
        getAccountRelationship = cluster.clusterQuery(RELATIONSHIPS_MODULE_NAME, "getAccountRelationship");
        getFamiliarFollowers = cluster.clusterQuery(RELATIONSHIPS_MODULE_NAME, "getFamiliarFollowers");
        getHomeTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getHomeTimeline");
        getHomeTimelinesUntil = cluster.clusterQuery(CORE_MODULE_NAME, "getHomeTimelinesUntil");
        getDirectTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getDirectTimeline");
        getHashtagTimeline = cluster.clusterQuery(HASHTAGS_MODULE_NAME, "getHashtagTimeline");
        getListTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getListTimeline");
        getListsFromAuthor = cluster.clusterQuery(RELATIONSHIPS_MODULE_NAME, "getListsFromAuthor");
        getConversationTimeline = cluster.clusterQuery(CORE_MODULE_NAME, "getConversationTimeline");
        getConversation = cluster.clusterQuery(CORE_MODULE_NAME, "getConversation");
        getStatusesFromPointers = cluster.clusterQuery(CORE_MODULE_NAME, "getStatusesFromPointers");
        profileTermsSearch = cluster.clusterQuery(SEARCH_MODULE_NAME, "profileTermsSearch");
        statusTermsSearch = cluster.clusterQuery(SEARCH_MODULE_NAME, "statusTermsSearch");
        hashtagSearch = cluster.clusterQuery(SEARCH_MODULE_NAME, "hashtagSearch");
        getWhoToFollowSuggestions = cluster.clusterQuery(RELATIONSHIPS_MODULE_NAME, "getWhoToFollowSuggestions");
        getFeaturedHashtags = cluster.clusterQuery(HASHTAGS_MODULE_NAME, "getFeaturedHashtags");
        batchHashtagStats = cluster.clusterQuery(HASHTAGS_MODULE_NAME, "batchHashtagStats");
        batchLinkStats = cluster.clusterQuery(HASHTAGS_MODULE_NAME, "batchLinkStats");
    }

    public static CompletableFuture<StatusQueryResults> queryStatusesWithPaging(BiFunction<StatusPointer, Integer, CompletableFuture<StatusQueryResults>> fn, StatusPointer offsetMaybe, Integer limitMaybe, int iterationsLeft) {
        if (iterationsLeft == 0) return CompletableFuture.completedFuture(new StatusQueryResults(new ArrayList(), new HashMap(), true, false));

        StatusPointer offset = offsetMaybe == null ? new StatusPointer(-1, -1) : offsetMaybe;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);

        return fn.apply(offset, limit)
                 .thenCompose(statusQueryResults -> {
                     // if the results are less than the limit and we haven't reached the end...
                     if (statusQueryResults.results.size() < limit && !statusQueryResults.reachedEnd) {
                         StatusPointer nextOffset;
                         int nextLimit = limit - statusQueryResults.results.size();
                         if (statusQueryResults.isSetLastStatusPointer()) nextOffset = statusQueryResults.lastStatusPointer;
                         else return CompletableFuture.completedFuture(statusQueryResults);
                         // recursively make the new request and concat the results.
                         return queryStatusesWithPaging(fn, nextOffset, nextLimit, iterationsLeft-1)
                                .thenApply(nextResults -> {
                                    List<StatusResultWithId> results = new ArrayList<>(statusQueryResults.results);
                                    results.addAll(nextResults.results);
                                    HashMap<String, AccountWithId> mentions = new HashMap<>(statusQueryResults.mentions);
                                    mentions.putAll(nextResults.mentions);
                                    StatusQueryResults combinedResults = new StatusQueryResults(results, mentions, nextResults.reachedEnd, nextResults.refreshed);
                                    if (nextResults.isSetLastStatusPointer()) combinedResults.setLastStatusPointer(nextResults.lastStatusPointer);
                                    return combinedResults;
                                });
                     } else return CompletableFuture.completedFuture(statusQueryResults);
                 });
    }

    public static class QueryResults<T, O> {
        public List<T> results;
        public boolean reachedEnd;
        public O offset; // offset to use in the next query
        public List<SimpleEntry<String, String>> linkHeaderParams; // query params to send to the client via the Link header

        public QueryResults(List<T> results, boolean reachedEnd, O offset, List<SimpleEntry<String, String>> linkHeaderParams) {
            this.results = results;
            this.reachedEnd = reachedEnd;
            this.offset = offset;
            this.linkHeaderParams = linkHeaderParams;
        }
    }

    public static <T, O> CompletableFuture<QueryResults<T, O>> queryWithPaging(BiFunction<O, Integer, CompletableFuture<QueryResults<T, O>>> fn, O offset, int limit, int iterationsLeft) {
        if (iterationsLeft == 0) return CompletableFuture.completedFuture(new QueryResults<>(new ArrayList<>(), true, null, null));

        return fn.apply(offset, limit)
                 .thenCompose(queryResults -> {
                     // if the results are less than the limit and we haven't reached the end...
                     if (queryResults.results.size() < limit && !queryResults.reachedEnd) {
                         O nextOffset;
                         int nextLimit = limit - queryResults.results.size();
                         if (queryResults.offset != null) nextOffset = queryResults.offset;
                         else return CompletableFuture.completedFuture(queryResults);
                         // recursively make the new request and concat the results.
                         return queryWithPaging(fn, nextOffset, nextLimit, iterationsLeft-1)
                                .thenApply(nextResults -> {
                                    List<T> results = new ArrayList<>(queryResults.results);
                                    results.addAll(nextResults.results);
                                    return new QueryResults<>(results, nextResults.reachedEnd, nextResults.offset, nextResults.linkHeaderParams);
                                });
                     } else return CompletableFuture.completedFuture(queryResults);
                 });
    }

    // synchronous queries

    public SortedMap<Long, StatusQueryResult> getGlobalTimeline(GlobalTimeline timeline, int limit) {
        // query the pstate
        SortedMap<Long, StatusPointer> indexToStatusPointer = globalTimelines.selectOne(Path.key(timeline.getValue()).sortedMapRangeFrom(0L, limit));
        // populate reverse index
        Map<StatusPointer, Long> statusPointerToIndex = new HashMap<>();
        for (Map.Entry<Long, StatusPointer> entry : indexToStatusPointer.entrySet()) {
            long timelineIndex = entry.getKey();
            StatusPointer statusPointer = entry.getValue();
            statusPointerToIndex.put(statusPointer, timelineIndex);
        }
        // build and return the indexToStatus map
        SortedMap<Long, StatusQueryResult> indexToStatus = new TreeMap<>();
        resolveStatusPointers(statusPointerToIndex, indexToStatus);
        return indexToStatus;
    }

    public void resolveStatusPointers(Map<StatusPointer, Long> statusPointerToIndex, Map<Long, StatusQueryResult> indexToStatus) {
        // query the statuses
        List<StatusPointer> statusPointers = new ArrayList<>(statusPointerToIndex.keySet());
        QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
        StatusQueryResults statusQueryResults = getStatusesFromPointers.invoke(null, statusPointers, filterOptions);
        // make a new sorted map with the results in it
        for (StatusResultWithId result : statusQueryResults.results) {
            Long timelineIndex = statusPointerToIndex.get(new StatusPointer(result.status.author.accountId, result.statusId));
            if (timelineIndex != null) indexToStatus.put(timelineIndex, new StatusQueryResult(result, statusQueryResults.mentions));
        }
    }

    // reactive queries

    public static class HomeTimelineProxyState implements ProxyState<SortedMap> {
      public long accountId;
      public StatusPointer mostRecentStatusPointer;
      public ProxyState.Callback<SortedMap> callback;

      public HomeTimelineProxyState(long accountId, StatusPointer mostRecentStatusPointer, ProxyState.Callback<SortedMap> callback) {
        this.accountId = accountId;
        this.mostRecentStatusPointer = mostRecentStatusPointer;
        this.callback = callback;
      }

      @Override
      public SortedMap get() { throw new RuntimeException("Not implemented"); }

      @Override
      public void close() throws IOException { }
    }


    public void refreshHomeTimelineProxies(List<HomeTimelineProxyState> activeProxies) {
      List<List<HomeTimelineProxyState>> partitions = Lists.partition(activeProxies, 100);
      for(List<HomeTimelineProxyState> partition: partitions) {
        List tuples = new ArrayList();
        for(HomeTimelineProxyState p: partition) tuples.add(Arrays.asList(p.accountId, p.mostRecentStatusPointer));
        Map<Integer, List<StatusPointer>> res = getHomeTimelinesUntil.invoke(tuples, 50);
        for(int i=0; i<partition.size(); i++) {
          HomeTimelineProxyState p = partition.get(i);
          List<StatusPointer> pointers = res.get(i);
          if(!pointers.isEmpty()) p.mostRecentStatusPointer = pointers.get(0);
          // diff processor doesn't need old/new values since it handles KeyDiff
          for(int j=pointers.size() - 1; j>=0; j--) p.callback.change(null, new KeyDiff((long)j, new NewValueDiff(pointers.get(j))), null);
        }
      }
    }

    public CompletableFuture<HomeTimelineProxyState> proxyHomeTimeline(long accountId, ProxyState.Callback<SortedMap> callback) {
        return getHomeTimelinesUntil.invokeAsync(Arrays.asList(Arrays.asList(accountId, new StatusPointer(-1, -1))), 1)
                                    .thenApply((Map<Integer, List<StatusPointer>> m) -> {
                                       StatusPointer mostRecent = null;
                                       if(!m.get(0).isEmpty()) mostRecent = m.get(0).get(0);
                                       return new HomeTimelineProxyState(accountId, mostRecent, callback);
                                    });
    }

    public CompletableFuture<ProxyState<SortedMap>> proxyNotificationsTimeline(long accountId, ProxyState.Callback<SortedMap> callback) {
        return accountIdToNotificationsTimeline.proxyAsync(Path.key(accountId).sortedMapRangeFrom(0L, STREAM_QUERY_LIMIT), callback);
    }

    public CompletableFuture<ProxyState<SortedMap>> proxyHashtagTimeline(String hashtag, ProxyState.Callback<SortedMap> callback) {
        return hashtagToStatusPointers.proxyAsync(Path.key(hashtag).sortedMapRangeFrom(0L, STREAM_QUERY_LIMIT), callback);
    }

    public CompletableFuture<ProxyState<SortedMap>> proxyListTimeline(long listId, ProxyState.Callback<SortedMap> callback) {
        return listIdToListTimeline.proxyAsync(Path.key(listId).sortedMapRangeFrom(0L, STREAM_QUERY_LIMIT), callback);
    }

    public CompletableFuture<ProxyState<SortedMap>> proxyDirectTimeline(long accountId, ProxyState.Callback<SortedMap> callback) {
        return accountIdToDirectMessagesById.proxyAsync(Path.key(accountId).sortedMapRangeFrom(0L, STREAM_QUERY_LIMIT), callback);
    }

    public CompletableFuture<Boolean> postAccount(PostAccount params) {
        String pwdHash = MastodonApiHelpers.encodePassword(params.password);
        String uuid = UUID.randomUUID().toString();
        final MastodonWebHelpers.SigningKeyPair keys;
        try {
            keys = MastodonWebHelpers.generateKeys();
        } catch (NoSuchProviderException | NoSuchAlgorithmException | IOException e) {
            return CompletableFuture.completedFuture(false);
        }
        return accountDepot.appendAsync(new Account(params.username, params.email, pwdHash, params.locale, uuid, keys.publicKey, AccountContent.local(new LocalAccount(keys.privateKey)), System.currentTimeMillis()))
                           .thenCompose(res -> this.getAccountUUID(params.username))
                           .thenApply(accountUUID -> accountUUID.equals(uuid));
    }

    public CompletableFuture<Boolean> postRemoteAccount(String nameWithHost, RemoteAccount remoteAccount, String publicKey) {
        String uuid = UUID.randomUUID().toString();
        return accountDepot.appendAsync(new Account(nameWithHost, "", "", "", uuid, publicKey, AccountContent.remote(remoteAccount), System.currentTimeMillis()))
                           .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoteAccountIfNotExists(String nameWithHost) {
        return nameToUser.selectOneAsync(Path.key(nameWithHost, "accountId"))
                         .thenCompose(accountIdMaybe -> {
                             if (accountIdMaybe != null) return CompletableFuture.completedFuture(true);
                             else {
                                 return MastodonApiHelpers.getProfile(nameWithHost)
                                                          .thenCompose(profile -> {
                                                              if (profile == null) return CompletableFuture.completedFuture(false);
                                                              else {
                                                                  return this.postRemoteAccount(nameWithHost, new RemoteAccount(profile.id, profile.inbox, profile.endpoints.getOrDefault("sharedInbox", null)), profile.publicKey.publicKeyPem);
                                                              }
                                                          });
                             }
                         });
    }

    public CompletableFuture<Boolean> postEditAccount(long accountId, List<EditAccountField> edits) {
        if (edits.size() == 0) return CompletableFuture.completedFuture(true);
        return accountEditDepot.appendAsync(new EditAccount(accountId, edits, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postAuthCode(long accountId, String code) {
        return authCodeDepot.appendAsync(new AddAuthCode(code, accountId)).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveAuthCode(String code) {
        return authCodeDepot.appendAsync(new RemoveAuthCode(code)).thenApply(res -> true);
    }

    CompletableFuture<Status> createStatusFromParams(long accountId, PostStatus params) {
        List<CompletableFuture<Object>> mediaFutures =
            params.media_ids.stream()
                            .distinct()
                            .map(attachmentId -> uuidToAttachment.selectOneAsync(Path.key(attachmentId)))
                            .collect(Collectors.toList());
        List<CompletableFuture> mentionFutures = new ArrayList<>();
        for (Token token : Token.parseTokens(params.status)) {
            if (token.kind == Token.TokenKind.REMOTE_MENTION) mentionFutures.add(this.postRemoteAccountIfNotExists(token.content));
        }

        return CompletableFuture.allOf(mediaFutures.toArray(new CompletableFuture<?>[0]))
            .allOf(mentionFutures.toArray(new CompletableFuture<?>[0]))
            .thenApply(_result -> {
                List<AttachmentWithId> attachments = new ArrayList<>();
                for (int i = 0; i < mediaFutures.size(); i++) {
                  attachments.add(new AttachmentWithId(params.media_ids.get(i), (Attachment) mediaFutures.get(i).join()));
                }

                // create status
                StatusVisibility visibility = MastodonApiHelpers.createStatusVisibility(params.visibility);
                long ts = System.currentTimeMillis();
                final Status status;
                if (params.in_reply_to_id != null) {
                    StatusPointer parentPointer = MastodonHelpers.parseStatusPointer(params.in_reply_to_id);
                    ReplyStatusContent content = new ReplyStatusContent(params.status, visibility, parentPointer);
                    content.setAttachments(attachments);
                    if (params.poll != null) content.setPollContent(new PollContent(params.poll.options, ts + (params.poll.expires_in * 1000), params.poll.multiple));
                    if (params.sensitive != null && params.sensitive) content.setSensitiveWarning(params.spoiler_text != null ? params.spoiler_text : "");
                    status = new Status(accountId, StatusContent.reply(content), ts);
                } else {
                    NormalStatusContent content = new NormalStatusContent(params.status, visibility);
                    content.setAttachments(attachments);
                    if (params.poll != null) content.setPollContent(new PollContent(params.poll.options, ts + (params.poll.expires_in * 1000), params.poll.multiple));
                    if (params.sensitive != null && params.sensitive) content.setSensitiveWarning(params.spoiler_text != null ? params.spoiler_text : "");
                    status = new Status(accountId, StatusContent.normal(content), ts);
                }

                return status;
            });
    }

    public CompletableFuture<StatusQueryResult> postStatus(long accountId, PostStatus params, String remoteUrl) {
        String uuid = UUID.randomUUID().toString();
        return createStatusFromParams(accountId, params)
            .thenComposeAsync(status -> {
                if (remoteUrl != null) status.setRemoteUrl(remoteUrl);
                AddStatus addStatus = new AddStatus(uuid, status);
                return statusDepot.appendAsync(addStatus);
            })
            .thenCompose(res -> postUUIDToStatusId.selectOneAsync(accountId, Path.key(uuid)))
            .thenCompose(statusId -> {
                if (statusId == null) return CompletableFuture.completedFuture(null);
                StatusPointer newPointer = new StatusPointer(accountId, (Long) statusId);
                return this.getStatus(accountId, newPointer);
          });
    }

    public CompletableFuture<StatusWithId> postScheduledStatus(long accountId, PostStatus params, Object object) {
        String uuid = UUID.randomUUID().toString();
        return createStatusFromParams(accountId, params)
            .thenComposeAsync(status -> {
                AddScheduledStatus addScheduledStatus = new AddScheduledStatus(uuid, status, Instant.parse(params.scheduled_at).toEpochMilli());
                return scheduledStatusDepot.appendAsync(addScheduledStatus);
            })
            .thenCompose(res -> postUUIDToStatusId.selectOneAsync(accountId, Path.key(uuid)))
            .thenCompose(statusId -> {
                if (statusId == null) return CompletableFuture.completedFuture(null);
                return accountIdToScheduledStatuses.selectOneAsync(Path.key(accountId, statusId, "status"))
                                                   .thenApply(status -> new StatusWithId((long) statusId, (Status) status));
            });
    }

    public CompletableFuture<Boolean> postPollVote(long accountId, StatusPointer pointer, Set<Integer> choices) {
        return pollVoteDepot.appendAsync(new PollVote(accountId, pointer, choices, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postFollowAccount(long followerId, long followeeId, String sharedInboxUrl, PostFollow params) {
        return getAccountWithId(followeeId)
            .thenCompose((followee) -> {
                if (followee != null && followee.account != null && followee.account.locked) {
                    FollowLockedAccount req = new FollowLockedAccount(followeeId, followerId, System.currentTimeMillis());
                    if (params != null) {
                        if (params.reblogs != null) req.setShowBoosts(params.reblogs);
                        if (params.notify != null) req.setNotify(params.notify);
                        if (params.languages != null) req.setLanguages(params.languages);
                    }
                    return followAndBlockAccountDepot.appendAsync(req);
                } else {
                    FollowAccount req = new FollowAccount(followerId, followeeId, System.currentTimeMillis());
                    if (params != null) {
                        if (params.reblogs != null ) req.setShowBoosts(params.reblogs);
                        if (params.notify != null) req.setNotify(params.notify);
                        if (params.languages != null) req.setLanguages(params.languages);
                    }
                    if (sharedInboxUrl != null) req.setFollowerSharedInboxUrl(sharedInboxUrl);
                    return followAndBlockAccountDepot.appendAsync(req);
                }
            }).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveFollowAccount(long followerId, long followeeId, String sharedInboxUrl) {
        RemoveFollowAccount removeFollowAccount = new RemoveFollowAccount(followerId, followeeId, System.currentTimeMillis());
        if (sharedInboxUrl != null) removeFollowAccount.setFollowerSharedInboxUrl(sharedInboxUrl);
        return followAndBlockAccountDepot.appendAsync(removeFollowAccount).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postMuteAccount(long muterId, long muteeId, PostMute params) {
        MuteAccountOptions options = new MuteAccountOptions(params.notifications);
        if(params.duration != null) options.setExpirationMillis(System.currentTimeMillis() + params.duration * 1000);
        return muteAccountDepot.appendAsync(new MuteAccount(muterId, muteeId, options, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveMuteAccount(long muterId, long muteeId) {
        return muteAccountDepot.appendAsync(new RemoveMuteAccount(muterId, muteeId, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postBlockAccount(long blockerId, long blockeeId) {
        return followAndBlockAccountDepot.appendAsync(new BlockAccount(blockerId, blockeeId, System.currentTimeMillis()))
                                         .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveBlockAccount(long blockerId, long blockeeId) {
        return followAndBlockAccountDepot.appendAsync(new RemoveBlockAccount(blockerId, blockeeId, System.currentTimeMillis()))
                                         .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postFeatureAccount(long featurerId, long featureeId) {
        return featureAccountDepot.appendAsync(new FeatureAccount(featurerId, featureeId, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveFeatureAccount(long featurerId, long featureeId) {
        return featureAccountDepot.appendAsync(new RemoveFeatureAccount(featurerId, featureeId, System.currentTimeMillis()))
                                  .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postList(long accountId, String title, String repliesPolicy) {
        return listDepot.appendAsync(new AccountList(accountId, title, repliesPolicy, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postListMember(long listId, long memberId) {
        return listDepot.appendAsync(new AccountListMember(listId, memberId, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Conversation> postConversation(long accountId, long conversationId, boolean unread) {
        return conversationDepot.appendAsync(new EditConversation(accountId, conversationId, unread))
                                .thenCompose(result -> getConversation.invokeAsync(accountId, conversationId)
                                                                      .thenApply(convoMaybe -> {
                                                                         if (convoMaybe == null) return null;
                                                                         // the change was processed in a microbatch
                                                                         // so the query won't necessarily return the
                                                                         // most up-to-date value, so we're updating it manually.
                                                                         convoMaybe.unread = unread;
                                                                         return convoMaybe;
                                                                      }));
    }

    public CompletableFuture<FeaturedHashtagInfo> postFeatureHashtag(long accountId, String hashtag) {
        return featurerToHashtags.selectAsync(Path.key(accountId).multiPath(Path.view(Ops.SIZE), Path.key(hashtag)))
                                 .thenCompose((List l) -> {
                                   int size = (Integer) l.get(0);
                                   Long id = (Long) l.get(1);
                                   if(size>=10 || id!=null) return CompletableFuture.completedFuture(null);
                                   else return featureHashtagDepot.appendAsync(new FeatureHashtag(accountId, hashtag, System.currentTimeMillis()));
                                 })
                                 .thenCompose((Object o) ->
                                   accountIdToHashtagActivity.selectAsync(Path.key(accountId, hashtag)
                                                                              .multiPath(Path.key("timeline").view(Ops.SIZE),
                                                                                         Path.key("lastStatusMillis").nullToVal(-1L))))
                                 .thenApply((Object o) -> {
                                   List l = (List) o;
                                   int size = (int) l.get(0);
                                   long timestamp = (long) l.get(1);
                                   return new FeaturedHashtagInfo(hashtag, size, timestamp);
                                 });
    }

    public CompletableFuture<Boolean> postRemoveFeatureHashtag(long accountId, String hashtag) {
        return featurerToHashtags.selectOneAsync(Path.key(accountId, hashtag))
                                 .thenCompose(idMaybe -> {
                                    if (idMaybe == null) return CompletableFuture.completedFuture(null);
                                    return featureHashtagDepot.appendAsync(new RemoveFeatureHashtag(accountId, hashtag, System.currentTimeMillis()))
                                                              .thenApply(res -> true);
                                 });
    }

    public CompletableFuture<StatusQueryResult> postFavoriteStatus(long favoriterId, StatusPointer pointer) {
        return favoriteStatusDepot.appendAsync(new FavoriteStatus(favoriterId, pointer, System.currentTimeMillis()))
                                  .thenCompose(res -> this.getStatus(favoriterId, pointer))
                                  .thenApply(resultMaybe -> {
                                      if (resultMaybe == null) return null;
                                      // the change was processed in a microbatch
                                      // so the query won't necessarily return the
                                      // most up-to-date value, so we're updating it manually.
                                      StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                                      statusQueryResult.result.status.metadata.favorited = true;
                                      return statusQueryResult;
                                  });
    }

    public CompletableFuture<StatusQueryResult> postRemoveFavoriteStatus(long favoriterId, StatusPointer pointer) {
        return favoriteStatusDepot.appendAsync(new RemoveFavoriteStatus(favoriterId, pointer, System.currentTimeMillis()))
                                  .thenCompose(res -> this.getStatus(favoriterId, pointer))
                                  .thenApply(resultMaybe -> {
                                      if (resultMaybe == null) return null;
                                      // the change was processed in a microbatch
                                      // so the query won't necessarily return the
                                      // most up-to-date value, so we're updating it manually.
                                      StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                                      statusQueryResult.result.status.metadata.favorited = false;
                                      return statusQueryResult;
                                  });
    }

    public CompletableFuture<StatusQueryResult> postBoostStatus(long boosterId, StatusPointer pointer, String remoteUrl) {
        BoostStatus boostStatus = new BoostStatus(UUID.randomUUID().toString(), boosterId, pointer, System.currentTimeMillis());
        if (remoteUrl != null) boostStatus.setRemoteUrl(remoteUrl);
        return statusDepot.appendAsync(boostStatus)
                          .thenCompose(res -> this.getStatus(boosterId, pointer))
                          .thenApply(resultMaybe -> {
                              if (resultMaybe == null) return null;
                              // the change was processed in a microbatch
                              // so the query won't necessarily return the
                              // most up-to-date value, so we're updating it manually.
                              StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                              statusQueryResult.result.status.metadata.boosted = true;
                              return statusQueryResult;
                          });
    }

    public CompletableFuture<StatusQueryResult> postRemoveBoostStatus(long boosterId, StatusPointer pointer) {
        return statusDepot.appendAsync(new RemoveBoostStatus(boosterId, pointer, System.currentTimeMillis()))
                          .thenCompose(res -> this.getStatus(boosterId, pointer))
                          .thenApply(resultMaybe -> {
                              if (resultMaybe == null) return null;
                              // the change was processed in a microbatch
                              // so the query won't necessarily return the
                              // most up-to-date value, so we're updating it manually.
                              StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                              statusQueryResult.result.status.metadata.boosted = false;
                              return statusQueryResult;
                          });
    }

    public CompletableFuture<StatusQueryResult> postBookmarkStatus(long bookmarkerId, StatusPointer pointer) {
        return bookmarkStatusDepot.appendAsync(new BookmarkStatus(bookmarkerId, pointer, System.currentTimeMillis()))
                                  .thenCompose(res -> this.getStatus(bookmarkerId, pointer))
                                  .thenApply(resultMaybe -> {
                                      if (resultMaybe == null) return null;
                                      // the change was processed in a microbatch
                                      // so the query won't necessarily return the
                                      // most up-to-date value, so we're updating it manually.
                                      StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                                      statusQueryResult.result.status.metadata.bookmarked = true;
                                      return statusQueryResult;
                                  });
    }

    public CompletableFuture<StatusQueryResult> postRemoveBookmarkStatus(long bookmarkerId, StatusPointer pointer) {
        return bookmarkStatusDepot.appendAsync(new RemoveBookmarkStatus(bookmarkerId, pointer, System.currentTimeMillis()))
                                  .thenCompose(res -> this.getStatus(bookmarkerId, pointer))
                                  .thenApply(resultMaybe -> {
                                      if (resultMaybe == null) return null;
                                      // the change was processed in a microbatch
                                      // so the query won't necessarily return the
                                      // most up-to-date value, so we're updating it manually.
                                      StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                                      statusQueryResult.result.status.metadata.bookmarked = false;
                                      return statusQueryResult;
                                  });
    }

    public CompletableFuture<StatusQueryResult> postMuteStatus(long muterId, StatusPointer pointer) {
        return muteStatusDepot.appendAsync(new MuteStatus(muterId, pointer, System.currentTimeMillis()))
                              .thenCompose(res -> this.getStatus(muterId, pointer))
                              .thenApply(resultMaybe -> {
                                  if (resultMaybe == null) return null;
                                  // the change was processed in a microbatch
                                  // so the query won't necessarily return the
                                  // most up-to-date value, so we're updating it manually.
                                  StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                                  statusQueryResult.result.status.metadata.muted = true;
                                  return statusQueryResult;
                              });
    }

    public CompletableFuture<StatusQueryResult> postRemoveMuteStatus(long muterId, StatusPointer pointer) {
        return muteStatusDepot.appendAsync(new RemoveMuteStatus(muterId, pointer, System.currentTimeMillis()))
                              .thenCompose(res -> this.getStatus(muterId, pointer))
                              .thenApply(resultMaybe -> {
                                  if (resultMaybe == null) return null;
                                  // the change was processed in a microbatch
                                  // so the query won't necessarily return the
                                  // most up-to-date value, so we're updating it manually.
                                  StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                                  statusQueryResult.result.status.metadata.muted = false;
                                  return statusQueryResult;
                              });
    }

    public CompletableFuture<StatusQueryResult> postPinStatus(long pinnerId, StatusPointer pointer) {
        return pinStatusDepot.appendAsync(new PinStatus(pinnerId, pointer.statusId, System.currentTimeMillis()))
                             .thenCompose(res -> this.getStatus(pinnerId, pointer))
                             .thenApply(resultMaybe -> {
                                 if (resultMaybe == null) return null;
                                 // the change was processed in a microbatch
                                 // so the query won't necessarily return the
                                 // most up-to-date value, so we're updating it manually.
                                 StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                                 statusQueryResult.result.status.metadata.pinned = true;
                                 return statusQueryResult;
                             });
    }

    public CompletableFuture<StatusQueryResult> postRemovePinStatus(long pinnerId, StatusPointer pointer) {
        return pinStatusDepot.appendAsync(new RemovePinStatus(pinnerId, pointer.statusId, System.currentTimeMillis()))
                             .thenCompose(res -> this.getStatus(pinnerId, pointer))
                             .thenApply(resultMaybe -> {
                                 if (resultMaybe == null) return null;
                                 // the change was processed in a microbatch
                                 // so the query won't necessarily return the
                                 // most up-to-date value, so we're updating it manually.
                                 StatusQueryResult statusQueryResult = (StatusQueryResult) resultMaybe;
                                 statusQueryResult.result.status.metadata.pinned = false;
                                 return statusQueryResult;
                             });
    }

    public CompletableFuture<Boolean> postNote(long accountId, long targetId, String note) {
        return noteDepot.appendAsync(new Note(accountId, targetId, note)).thenApply(res -> true);
    }

    public CompletableFuture<AttachmentWithId> postAttachment(AttachmentWithId attachment) {
        return statusAttachmentWithIdDepot.appendAsync(attachment).thenApply(res -> attachment);
    }

    public CompletableFuture<Boolean> dismissNotification(long accountId, Long notificationIdMaybe) {
        DismissNotification dismissNotification = new DismissNotification(accountId);
        if (notificationIdMaybe != null) dismissNotification.setNotificationId(notificationIdMaybe);
        return dismissDepot.appendAsync(dismissNotification).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> removeFollowSuggestion(long accountId, long targetId) {
        return removeFollowSuggestionDepot.appendAsync(new RemoveFollowSuggestion(accountId, targetId))
                                          .thenApply(res -> true);
    }

    public CompletableFuture<Boolean> putList(long listId, long accountId, String title, String repliesPolicy) {
        return listDepot.appendAsync(new AccountListWithId(listId, new AccountList(accountId, title, repliesPolicy, System.currentTimeMillis())))
                        .thenApply(res -> true);
    }

    public CompletableFuture<StatusQueryResult> putStatus(StatusPointer statusPointer, PutStatus params) {
        List<CompletableFuture<Object>> mediaFutures =
            params.media_ids.stream()
                            .distinct()
                            .map(attachmentId -> uuidToAttachment.selectOneAsync(Path.key(attachmentId)))
                            .collect(Collectors.toList());
        return CompletableFuture.allOf(mediaFutures.toArray(new CompletableFuture<?>[0]))
                .thenCompose(_result -> {
                    List<AttachmentWithId> attachments = new ArrayList<>();
                    for (int i = 0; i < mediaFutures.size(); i++) {
                      attachments.add(new AttachmentWithId(params.media_ids.get(i), (Attachment) mediaFutures.get(i).join()));
                    }
                    return accountIdToStatuses.selectOneAsync(Path.key(statusPointer.authorId, statusPointer.statusId).first())
                                              .thenCompose(statusMaybe -> {
                                                  if (statusMaybe == null) return CompletableFuture.completedFuture(null);
                                                  Status edit = (Status) statusMaybe;
                                                  if (edit.content.isSetNormal()) {
                                                      NormalStatusContent content = edit.content.getNormal();
                                                      content.text = params.status;
                                                      content.setAttachments(attachments);
                                                      if (params.poll != null && content.isSetPollContent()) content.setPollContent(new PollContent(params.poll.options, content.pollContent.expirationMillis, params.poll.multiple));
                                                      if (params.sensitive != null && params.sensitive) content.setSensitiveWarning(params.spoiler_text != null ? params.spoiler_text : "");
                                                      else content.unsetSensitiveWarning();
                                                  }
                                                  else if (edit.content.isSetReply()) {
                                                      ReplyStatusContent content = edit.content.getReply();
                                                      content.text = params.status;
                                                      content.setAttachments(attachments);
                                                      if (params.poll != null && content.isSetPollContent()) content.setPollContent(new PollContent(params.poll.options, content.pollContent.expirationMillis, params.poll.multiple));
                                                      if (params.sensitive != null && params.sensitive) content.setSensitiveWarning(params.spoiler_text != null ? params.spoiler_text : "");
                                                      else content.unsetSensitiveWarning();
                                                  }
                                                  else if (edit.content.isSetBoost()) return CompletableFuture.completedFuture(null); // you can't edit boosts
                                                  return statusDepot.appendAsync(new EditStatus(statusPointer.statusId, edit))
                                                                    .thenCompose(res -> this.getStatus(statusPointer.authorId, statusPointer));
                                              });
            });
    }

    public CompletableFuture<Boolean> deleteStatus(long accountId, long statusId) {
        return statusDepot.appendAsync(new RemoveStatus(accountId, statusId, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> deleteList(long listId) {
        return listDepot.appendAsync(new RemoveAccountList(listId, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> deleteListMember(long listId, long memberId) {
        return listDepot.appendAsync(new RemoveAccountListMember(listId, memberId, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> deleteConversation(long accountId, long conversationId) {
        return conversationDepot.appendAsync(new RemoveConversation(accountId, conversationId)).thenApply(res -> true);
    }

    public CompletableFuture<Long> getAccountId(String username) {
        return nameToUser.selectOneAsync(Path.key(username, "accountId"));
    }

    public CompletableFuture<String> getAccountUUID(String username) {
        return nameToUser.selectOneAsync(Path.key(username, "uuid"));
    }

    public CompletableFuture<AccountWithId> getAccountWithId(Long requestAccountIdMaybe, long accountId) {
        return getAccountsFromAccountIds.invokeAsync(requestAccountIdMaybe, Arrays.asList(accountId))
                                        .thenApply(accountWithIds -> {
                                            if (accountWithIds.size() == 0) return null;
                                            return accountWithIds.get(0);
                                        });
    }

    public CompletableFuture<AccountWithId> getAccountWithId(long accountId) {
        return this.getAccountWithId(null, accountId);
    }

    public CompletableFuture<SimpleEntry<AccountWithId, AccountWithId>> getAccountWithIdPair(long firstAccountId, long secondAccountId) {
        return getAccountsFromAccountIds.invokeAsync(null, Arrays.asList(firstAccountId, secondAccountId))
                                        .thenApply(accountWithIds -> {
                                            if (accountWithIds.size() != 2) return null;
                                            return new SimpleEntry<>(accountWithIds.get(0), accountWithIds.get(1));
                                        });
    }

    private CompletableFuture<List<AccountWithId>> getAccountsFromAccountIds(List<Long> accountIds) {
        return getAccountsFromAccountIds.invokeAsync(null, accountIds);
    }

    public CompletableFuture<Long> getAccountIdFromAuthCode(String code) {
        return authCodeToAccountId.selectOneAsync(Path.key(code));
    }

    public CompletableFuture<StatusQueryResult> getStatus(Long requestAccountIdMaybe, StatusPointer pointer, QueryFilterOptions filterOptions) {
        return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, PersistentVector.EMPTY.cons(new StatusPointer(pointer.authorId, pointer.statusId)), filterOptions)
                                      .thenApply(statusQueryResults -> {
                                          if (statusQueryResults.results.size() == 0) return null;
                                          StatusResultWithId result = statusQueryResults.results.get(0);
                                          return new StatusQueryResult(result, statusQueryResults.mentions);
                                      });
    }

    public CompletableFuture<StatusQueryResult> getStatus(Long requestAccountIdMaybe, StatusPointer pointer) {
        QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
        return this.getStatus(requestAccountIdMaybe, pointer, filterOptions);
    }

    public CompletableFuture<StatusQueryResult> getRemoteStatus(long accountId, String remoteUrl) {
        return remoteUrlToStatusId.selectOneAsync(Path.key(remoteUrl))
                                  .thenCompose(statusIdMaybe -> {
                                      if (statusIdMaybe == null) return CompletableFuture.completedFuture(null);
                                      return this.getStatus(accountId, new StatusPointer(accountId, (Long) statusIdMaybe));
                                  });
    }

    public CompletableFuture<StatusQueryResults> getAncestors(Long requestAccountIdMaybe, StatusPointer pointer) {
        return getAncestors.invokeAsync(requestAccountIdMaybe, pointer.authorId, pointer.statusId, ANCESTORS_LIMIT);
    }

    public CompletableFuture<StatusQueryResults> getDescendants(Long requestAccountIdMaybe, StatusPointer pointer) {
        return getDescendants.invokeAsync(requestAccountIdMaybe, pointer.authorId, pointer.statusId, DESCENDANTS_LIMIT);
    }

    public CompletableFuture<StatusQueryResults> getAccountTimeline(Long requestAccountIdMaybe, long timelineAccountId, StatusPointer offsetMaybe, Integer limitMaybe, boolean includeReplies, boolean includeBoosts) {
        return this.getPinnedStatuses(requestAccountIdMaybe, timelineAccountId)
                   .thenCompose(pinnedStatuses -> {
                       Set<Long> pinnedIds = pinnedStatuses.results.stream().map(o -> o.statusId).collect(Collectors.toSet());
                       return queryStatusesWithPaging((offset, limit) ->
                               getAccountTimeline.invokeAsync(requestAccountIdMaybe, timelineAccountId, offset.statusId, limit, includeReplies)
                                                 .thenApply(statusQueryResults -> {
                                                     if (pinnedIds.size() > 0) statusQueryResults.results = statusQueryResults.results.stream().filter(statusResult -> !pinnedIds.contains(statusResult.statusId)).collect(Collectors.toList());
                                                     if (!includeBoosts) statusQueryResults.results = statusQueryResults.results.stream().filter(statusResult -> !statusResult.status.content.isSetBoost()).collect(Collectors.toList());
                                                     return statusQueryResults;
                                                 }),
                               offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
                   });
    }

    public CompletableFuture<StatusQueryResults> getPinnedStatuses(Long requestAccountIdMaybe, long authorId) {
        return pinnerToStatusIds.selectAsync(Path.key(authorId).mapVals())
                                .thenCompose(statusIds -> {
                                    List<StatusPointer> pointers = new ArrayList<>();
                                    for (Object statusId : statusIds) pointers.add(new StatusPointer(authorId, (Long) statusId));
                                    QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
                                    return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, pointers, filterOptions);
                                });
    }

    public CompletableFuture<StatusQueryResults> getAttachmentStatuses(Long requestAccountIdMaybe, long authorId, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> {
                  SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                  return accountIdToAttachmentStatusIds.selectAsync(Path.key(authorId).sortedSetRangeFrom(offset.statusId, options).all())
                                                       .thenCompose(statusIds -> {
                                                           List<StatusPointer> pointers = new ArrayList<>();
                                                           for (Object statusId : statusIds) pointers.add(new StatusPointer(authorId, (Long) statusId));
                                                           QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
                                                           return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, pointers, filterOptions);
                                                       });
                }, offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<StatusQueryResults> getTaggedStatuses(Long requestAccountIdMaybe, long authorId, String hashtag, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> {
                  SortedRangeFromOptions rangeOptions = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                  return accountIdToHashtagActivity.selectAsync(Path.key(authorId, hashtag, "timeline").sortedSetRangeFrom(offset.statusId, rangeOptions).all())
                          .thenCompose((List<Object> statusIds) -> {
                              QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
                              List<StatusPointer> pointers = statusIds.stream()
                                                                      .map((statusId) -> new StatusPointer(authorId, (Long) statusId))
                                                                      .collect(Collectors.toList());
                              return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, pointers, filterOptions);
                          });
                }, offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<SimpleEntry<List<SimpleEntry<Long, AccountWithId>>, Boolean>> getAccountWithTimelineIndexes(List<List> keyVals, long limit) {
        List<Long> accountIds = keyVals.stream().map(l -> ((Follower) l.get(1)).accountId).collect(Collectors.toList());
        return this.getAccountsFromAccountIds(accountIds)
                   .thenApply(accountWithIds -> {
                       List<SimpleEntry<Long, AccountWithId>> accountWithTimelineIndexes = new ArrayList<>();
                       int i = 0;
                       for (AccountWithId accountWithId : accountWithIds) {
                           accountWithTimelineIndexes.add(new SimpleEntry<>((Long) keyVals.get(i).get(0), accountWithId));
                           i++;
                       }
                       return new SimpleEntry<>(accountWithTimelineIndexes, accountWithIds.size() < limit);
                   });
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getAccountFollowees(long followerId, Long offsetMaybe, Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
        CompletableFuture<List<List>> followeesFuture = followerToFolloweesById.selectAsync(Path.key(followerId).sortedMapRangeFrom(offset, options).all());
        return followeesFuture.thenCompose(keyVals -> getAccountWithTimelineIndexes(keyVals, limit))
                              .thenApply(accountWithTimelineIndexes -> {
                                  List<SimpleEntry<Long, AccountWithId>> results = accountWithTimelineIndexes.getKey();
                                  List<AccountWithId> accountWithIds = results.stream().map(SimpleEntry::getValue).collect(Collectors.toList());
                                  boolean reachedEnd = accountWithTimelineIndexes.getValue();
                                  Long lastId = null;
                                  List<SimpleEntry<String, String>> linkHeaderParams = null;
                                  if (results.size() > 0) {
                                      lastId = results.get(results.size()-1).getKey();
                                      linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", lastId+""));
                                  }
                                  return new QueryResults<>(accountWithIds, reachedEnd, lastId, linkHeaderParams);
                              });
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getAccountFollowers(long followeeId, Long offsetMaybe, Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
        CompletableFuture<List<List>> followeesFuture = followeeToFollowersById.selectAsync(Path.key(followeeId).sortedMapRangeFrom(offset, options).all());
        return followeesFuture.thenCompose(keyVals -> getAccountWithTimelineIndexes(keyVals, limit))
                              .thenApply(accountWithTimelineIndexes -> {
                                  List<SimpleEntry<Long, AccountWithId>> results = accountWithTimelineIndexes.getKey();
                                  List<AccountWithId> accountWithIds = results.stream().map(SimpleEntry::getValue).collect(Collectors.toList());
                                  boolean reachedEnd = accountWithTimelineIndexes.getValue();
                                  Long lastId = null;
                                  List<SimpleEntry<String, String>> linkHeaderParams = null;
                                  if (results.size() > 0) {
                                      lastId = results.get(results.size()-1).getKey();
                                      linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", lastId+""));
                                  }
                                  return new QueryResults<>(accountWithIds, reachedEnd, lastId, linkHeaderParams);
                              });
    }

    public CompletableFuture<AccountRelationshipQueryResult> getAccountRelationship(long sourceId, long targetId) {
        return getAccountRelationship.invokeAsync(sourceId, targetId);
    }

    public CompletableFuture<List<AccountWithId>> getFamiliarFollowers(long requestAccountId, long targetId) {
        CompletableFuture<List<Long>> familiarFollowersFuture = getFamiliarFollowers.invokeAsync(requestAccountId, targetId);
        return familiarFollowersFuture.thenCompose(this::getAccountsFromAccountIds);
    }

    public CompletableFuture<StatusQueryResults> getHomeTimeline(long accountId, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> getHomeTimeline.invokeAsync(accountId, offset, limit),
                offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<StatusQueryResults> getDirectTimeline(long accountId, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) ->
                  accountIdToDirectMessages.selectOneAsync(Path.key(accountId, offset).nullToVal(-1L))
                                           .thenCompose(timelineIndex -> getDirectTimeline.invokeAsync(accountId, timelineIndex, limit)),
                offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<StatusQueryResults> getGlobalTimeline(GlobalTimeline timelineType, Long requestAccountIdMaybe, StatusPointer offsetMaybe, Integer limitMaybe) {
        // unlike other timelines, this one is queried entirely from an in-memory cache.
        // this is because the global timeline is the same for everyone, so querying the
        // backend every time would be wasteful. if the user is logged in, we still need
        // to query, to ensure that their blocks/mutes are accounted for in the results.
        return queryStatusesWithPaging((offset, limit) -> {
            long timelineIndex = MastodonApiStreamingConfig.GLOBAL_TIMELINE_TO_STATUS_POINTER_TO_INDEX.get(timelineType).getOrDefault(offset, -1L);
            SortedMap<Long, StatusQueryResult> submap = MastodonApiStreamingConfig.GLOBAL_TIMELINE_TO_INDEX_TO_STATUS.get(timelineType).tailMap(timelineIndex, false);
            // if not logged in, return results entirely from cache
            if (requestAccountIdMaybe == null) {
                List<StatusPointer> statusPointers = new ArrayList<>();
                List<StatusResultWithId> results = new ArrayList<>();
                Map<String, AccountWithId> mentions = new HashMap<>();
                for (Map.Entry<Long, StatusQueryResult> entry : submap.entrySet()) {
                    StatusQueryResult r = entry.getValue();
                    statusPointers.add(new StatusPointer(r.result.status.author.accountId, r.result.statusId));
                    results.add(r.result);
                    mentions.putAll(r.mentions);
                    if (statusPointers.size() == limit) break;
                }
                StatusQueryResults statusQueryResults = new StatusQueryResults(results, mentions, false, false);
                MastodonHelpers.updateStatusQueryResults(statusQueryResults, statusPointers, limit, false);
                return CompletableFuture.completedFuture(statusQueryResults);
            }
            // if logged in, get status pointers from cache and then query the backend for the full results.
            // this ensures that blocks/mutes are taken into account.
            else {
                List<StatusPointer> statusPointers = new ArrayList<>();
                for (Map.Entry<Long, StatusQueryResult> entry : submap.entrySet()) {
                    StatusQueryResult r = entry.getValue();
                    statusPointers.add(new StatusPointer(r.result.status.author.accountId, r.result.statusId));
                    if (statusPointers.size() == limit) break;
                }
                QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, true);
                return getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, statusPointers, filterOptions)
                                              .thenApply(statusQueryResults -> MastodonHelpers.updateStatusQueryResults(statusQueryResults, statusPointers, limit, false));
            }
        },
        offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<StatusQueryResults> getHashtagTimeline(String hashtag, Long requestAccountIdMaybe, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) ->
                  hashtagToStatusPointersReverse.selectOneAsync(Path.key(hashtag, offset).nullToVal(-1L))
                                                .thenCompose(timelineIndex -> getHashtagTimeline.invokeAsync(hashtag, requestAccountIdMaybe, timelineIndex, limit)),
                offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<GetNotification.Bundle> getNotification(long requestAccountId, NotificationWithId notificationWithId) {
        // get account associated with the notification
        return this.getAccountWithId(MastodonHelpers.getAccountIdFromNotificationContent(notificationWithId.notification.content))
                   .thenCompose(accountWithId -> {
                       if (accountWithId == null) return CompletableFuture.completedFuture(null);
                       // determine if requester is currently muting this account's notifications
                       CompletableFuture<MuteAccountOptions> optionsFuture = accountIdToSuppressions.selectOneAsync(Path.key(requestAccountId, "muted", accountWithId.accountId));
                       return optionsFuture.thenCompose(muteAccountOptions -> {
                           if (muteAccountOptions != null && muteAccountOptions.muteNotifications) return CompletableFuture.completedFuture(null);
                           // get status associated with the notification
                           StatusPointer statusPointer = MastodonHelpers.getStatusPointerFromNotificationContent(notificationWithId.notification.content);
                           if (statusPointer == null) {
                               return CompletableFuture.completedFuture(new GetNotification.Bundle(notificationWithId, accountWithId, null));
                           } else {
                               QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Notifications, true);
                               return this.getStatus(requestAccountId, statusPointer, filterOptions)
                                          .thenApply(statusQueryResult -> {
                                              if (statusQueryResult == null) return null;
                                              return new GetNotification.Bundle(notificationWithId, accountWithId, statusQueryResult);
                                          });
                           }
                       });
                   });
    }

    public CompletableFuture<GetNotification.Bundle> getNotification(long accountId, long notificationId) {
        return accountIdToNotificationsTimeline.selectOneAsync(Path.key(accountId, notificationId))
                                               .thenCompose(notification -> {
                                                   if (notification == null) return null;
                                                   NotificationWithId notificationWithId = new NotificationWithId(notificationId, (Notification) notification);
                                                   return this.getNotification(accountId, notificationWithId);
                                               });
    }

    public CompletableFuture<QueryResults<GetNotification.Bundle, Long>> getNotificationsTimeline(long accountId, Long offsetMaybe, Integer limitMaybe, List<String> typesMaybe, List<String> excludeTypesMaybe) {
        int defaultLimit = 15;
        int maxLimit = 30;
        Set<String> types = new HashSet<>();
        if (typesMaybe != null) types.addAll(typesMaybe);
        Set<String> excludeTypes = new HashSet<>();
        if (excludeTypesMaybe != null) excludeTypes.addAll(excludeTypesMaybe);
        return queryWithPaging(
            (offset, limit) -> {
                SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                CompletableFuture<List<List>> notificationsFuture = accountIdToNotificationsTimeline.selectAsync(Path.key(accountId).sortedMapRangeFrom(offset, options).all());
                return notificationsFuture.thenCompose(timelineIndexAndNotifications -> {
                    // create notifications and filter them
                    List<NotificationWithId> notificationWithIds = MastodonHelpers.createNotificationWithIds(timelineIndexAndNotifications);
                    List<NotificationWithId> filtered =
                            notificationWithIds.stream()
                                               .filter(nwid -> types.contains(MastodonHelpers.getTypeFromNotificationContent(nwid.notification.content)))
                                               .filter(nwid -> !excludeTypes.contains(MastodonHelpers.getTypeFromNotificationContent(nwid.notification.content)))
                                               .collect(Collectors.toList());
                    // get any accounts/statuses associated with the notifications
                    List<CompletableFuture<GetNotification.Bundle>> bundleFutures =
                            filtered.stream()
                                    .map(notificationWithId -> this.getNotification(accountId, notificationWithId))
                                    .collect(Collectors.toList());
                    return CompletableFuture.allOf(bundleFutures.toArray(new CompletableFuture<?>[0]))
                                            .thenApply(_result -> {
                                                // filter out the nulls. if a bundle null,
                                                // the user that generated the notification is blocked/muted
                                                // or the status it pointed to is gone.
                                                List<GetNotification.Bundle> bundles = new ArrayList<>();
                                                for (CompletableFuture<GetNotification.Bundle> bundleFuture : bundleFutures) {
                                                    GetNotification.Bundle bundle = bundleFuture.join();
                                                    if (bundle != null) bundles.add(bundle);
                                                }
                                                Long lastId = null;
                                                List<SimpleEntry<String, String>> linkHeaderParams = null;
                                                if (notificationWithIds.size() > 0) {
                                                    NotificationWithId lastNotification = notificationWithIds.get(notificationWithIds.size()-1);
                                                    lastId = lastNotification.notificationId;
                                                    linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", MastodonHelpers.serializeNotificationId(lastNotification.notificationId, lastNotification.notification.timestamp)));
                                                }
                                                return new QueryResults<>(bundles, notificationWithIds.size() < limit, lastId, linkHeaderParams);
                                            });
                });
            },
            offsetMaybe == null ? -1L : offsetMaybe,
            Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit),
            MAX_PAGING_ITERATIONS
        );
    }

    public CompletableFuture<Boolean> hasListId(long authorId, long listId) {
        return authorIdToListIds.selectAsync(Path.key(authorId).setElem(listId)).thenApply(results -> results.size() > 0 ? true : null);
    }

    public CompletableFuture<StatusQueryResults> getListTimeline(long listId, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) ->
                      listIdToListTimelineReverse.selectOneAsync(Path.key(listId, offset).nullToVal(-1L))
                                                 .thenCompose(timelineIndex -> getListTimeline.invokeAsync(listId, timelineIndex, limit)),
                offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<AccountListWithId> getLatestList(long accountId) {
        return authorIdToListIds.selectOneAsync(Path.key(accountId).first())
                                .thenCompose(listIdMaybe -> {
                                    if (listIdMaybe == null) return CompletableFuture.completedFuture(null);
                                    return listIdToList.selectOneAsync(Path.key(listIdMaybe))
                                                       .thenApply(list -> {
                                                           if (list == null) return null;
                                                           return new AccountListWithId((long) listIdMaybe, (AccountList) list);
                                                       });
                                });
    }

    public CompletableFuture<AccountListWithId> getList(long listId) {
        return listIdToList.selectOneAsync(Path.key(listId))
                           .thenApply(listMaybe -> {
                               if (listMaybe == null) return null;
                               return new AccountListWithId(listId, (AccountList) listMaybe);
                           });
    }

    public CompletableFuture<List<AccountListWithId>> getListsFromAuthor(long authorId, Long memberIdMaybe) {
        return getListsFromAuthor.invokeAsync(authorId, memberIdMaybe);
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getListMembers(long listId, Long offsetMaybe, Integer limitMaybe) {
        int defaultLimit = 40;
        int maxLimit = 80;
        return queryWithPaging(
            (offset, limit) -> {
                SortedRangeFromOptions options;
                // if limit is 0, return all members
                if (limit == 0) options = SortedRangeFromOptions.excludeStart();
                else options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                CompletableFuture<List<Long>> memberIdsFuture = listIdToMemberIds.selectAsync(Path.key(listId).sortedSetRangeFrom(offset, options).all());
                return memberIdsFuture.thenCompose(this::getAccountsFromAccountIds)
                                      .thenApply(accountWithIds -> {
                                          Long lastId = null;
                                          List<SimpleEntry<String, String>> linkHeaderParams = null;
                                          if (accountWithIds.size() > 0) {
                                              lastId = accountWithIds.get(accountWithIds.size()-1).accountId;
                                              linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", MastodonHelpers.serializeAccountId(lastId)));
                                          }
                                          return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId, linkHeaderParams);
                                      });
            },
            offsetMaybe == null ? -1L : offsetMaybe,
            Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit),
            MAX_PAGING_ITERATIONS
        );
    }

    public CompletableFuture<QueryResults<Conversation, Long>> getConversationTimeline(long accountId, Long offsetMaybe, Integer limitMaybe) {
        CompletableFuture<Long> timelineIndexFuture =
                offsetMaybe == null ? CompletableFuture.completedFuture(-1L)
                                    : accountIdToConvoIds.selectOneAsync(Path.key(accountId, offsetMaybe).nullToVal(-1L));
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        return timelineIndexFuture.thenCompose(timelineIndex ->
                getConversationTimeline.invokeAsync(accountId, timelineIndex, limit)
                                       .thenApply(conversations -> {
                                           Long lastId = null;
                                           List<SimpleEntry<String, String>> linkHeaderParams = null;
                                           if (conversations.size() > 0) {
                                               lastId = conversations.get(conversations.size()-1).conversationId;
                                               linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", lastId+""));
                                           }
                                           return new QueryResults<>(conversations, conversations.size() < limit, lastId, linkHeaderParams);
                                       }));
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getEndorsements(long featurerId, Long offsetMaybe, Integer limitMaybe) {
        return queryWithPaging(
            (offset, limit) -> {
                SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                CompletableFuture<List<Long>> featureeIdsFuture = featurerToFeaturees.selectAsync(Path.key(featurerId).sortedMapRangeFrom(offset, options).mapKeys());
                return featureeIdsFuture.thenCompose(this::getAccountsFromAccountIds)
                                        .thenApply(accountWithIds -> {
                                            Long lastId = null;
                                            List<SimpleEntry<String, String>> linkHeaderParams = null;
                                            if (accountWithIds.size() > 0) {
                                                lastId = accountWithIds.get(accountWithIds.size()-1).accountId;
                                                linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", MastodonHelpers.serializeAccountId(lastId)));
                                            }
                                            return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId, linkHeaderParams);
                                        });
            },
            offsetMaybe == null ? -1L : offsetMaybe,
            Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT),
            MAX_PAGING_ITERATIONS
        );
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getBlocks(long blockerId, Long offsetMaybe, Integer limitMaybe) {
        return queryWithPaging(
            (offset, limit) -> {
                SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                CompletableFuture<List<Long>> blockeeIdsFuture = accountIdToSuppressions.selectAsync(Path.key(blockerId, "blocked").sortedSetRangeFrom(offset, options).all());
                return blockeeIdsFuture.thenCompose(this::getAccountsFromAccountIds)
                                       .thenApply(accountWithIds -> {
                                           Long lastId = null;
                                           List<SimpleEntry<String, String>> linkHeaderParams = null;
                                           if (accountWithIds.size() > 0) {
                                               lastId = accountWithIds.get(accountWithIds.size()-1).accountId;
                                               linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", MastodonHelpers.serializeAccountId(lastId)));
                                           }
                                           return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId, linkHeaderParams);
                                       });
            },
            offsetMaybe == null ? -1L : offsetMaybe,
            Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT),
            MAX_PAGING_ITERATIONS
        );
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getMutes(long muterId, Long offsetMaybe, Integer limitMaybe) {
        return queryWithPaging(
            (offset, limit) -> {
                SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                CompletableFuture<List<Long>> muteeIdsFuture = accountIdToSuppressions.selectAsync(Path.key(muterId, "muted").sortedMapRangeFrom(offset, options).mapKeys());
                return muteeIdsFuture.thenCompose(this::getAccountsFromAccountIds)
                                     .thenApply(accountWithIds -> {
                                         Long lastId = null;
                                         List<SimpleEntry<String, String>> linkHeaderParams = null;
                                         if (accountWithIds.size() > 0) {
                                             lastId = accountWithIds.get(accountWithIds.size()-1).accountId;
                                             linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", MastodonHelpers.serializeAccountId(lastId)));
                                         }
                                         return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId, linkHeaderParams);
                                     });
            },
            offsetMaybe == null ? -1L : offsetMaybe,
            Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT),
            MAX_PAGING_ITERATIONS
        );
    }

    public CompletableFuture<StatusQueryResults> getFavorites(long favoriterId, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> {
                    SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                    return favoriterToStatusPointers.selectAsync(Path.key(favoriterId).sortedMapRangeFrom(offset, options).mapKeys())
                                                    .thenCompose(statusPointers -> {
                                                        QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
                                                        return getStatusesFromPointers.invokeAsync(favoriterId, statusPointers, filterOptions);
                                                    });
                }, offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<StatusQueryResults> getBookmarks(long bookmarkerId, StatusPointer offsetMaybe, Integer limitMaybe) {
        return queryStatusesWithPaging((offset, limit) -> {
                    SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                    return bookmarkerToStatusPointers.selectAsync(Path.key(bookmarkerId).sortedMapRangeFrom(offset, options).mapKeys())
                                                     .thenCompose(statusPointers -> {
                                                         QueryFilterOptions filterOptions = new QueryFilterOptions(FilterContext.Public, false);
                                                         return getStatusesFromPointers.invokeAsync(bookmarkerId, statusPointers, filterOptions);
                                                     });
                }, offsetMaybe, limitMaybe, MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<List<FeaturedHashtagInfo>> getFeaturedHashtags(long accountId) {
        return getFeaturedHashtags.invokeAsync(accountId);
    }

    public CompletableFuture<Map<String, ItemStats>> getSuggestedFeaturedHashtags(Long requestAccountId) {
        return accountIdToRecentHashtags.selectOneAsync(Path.key(requestAccountId))
                .thenCompose(recentHashtags -> {
                    if (recentHashtags == null) return CompletableFuture.completedFuture(new HashMap<>());
                    return batchHashtagStats.invokeAsync(((List<String>) recentHashtags).stream().limit(10).collect(Collectors.toList()));
                });
    }

    public CompletableFuture<Map<String, ItemStats>> getTrendingHashtags(Integer limitMaybe, Integer offsetMaybe) {
        long offset = offsetMaybe == null ? 0 : offsetMaybe;
        int defaultLimit = 10;
        int maxLimit = 20;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        return hashtagTrends.selectAsync(Path.all().first())
                            .thenApply(tags -> tags.stream().skip(offset).limit(limit).collect(Collectors.toList()))
                            .thenCompose(batchHashtagStats::invokeAsync)
                            .thenApply(res -> res == null ? new HashMap<>() : res);
    }

    public CompletableFuture<Map<String, ItemStats>> getTrendingLinks(Integer limitMaybe, Integer offsetMaybe) {
        long offset = offsetMaybe == null ? 0 : offsetMaybe;
        int defaultLimit = 10;
        int maxLimit = 20;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        return linkTrends.selectAsync(Path.all().first())
                         .thenApply(links -> links.stream().skip(offset).limit(limit).collect(Collectors.toList()))
                         .thenCompose(batchLinkStats::invokeAsync)
                         .thenApply(res -> res == null ? new HashMap<>() : res);
    }

    public CompletableFuture<StatusQueryResults> getTrendingStatuses(Long requestAccountIdMaybe, Integer limitMaybe, Integer offsetMaybe) {
        long offset = offsetMaybe == null ? 0 : offsetMaybe;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        return statusTrends.selectAsync(Path.all().first())
                           .thenApply(statuses -> statuses.stream().skip(offset).limit(limit).collect(Collectors.toList()))
                           .thenCompose(statusPointers -> getStatusesFromPointers.invokeAsync(requestAccountIdMaybe, statusPointers, new QueryFilterOptions(FilterContext.Public, false)));
    }

    public CompletableFuture<List<AccountWithId>> getDirectory(boolean showAll, boolean sortByActive, Integer limitMaybe, Integer offsetMaybe) {
        long offset = offsetMaybe == null ? 0 : offsetMaybe;
        int defaultLimit = 40;
        int maxLimit = 80;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        CompletableFuture<List<Long>> future =
                sortByActive ? (showAll ? allActiveAccountIds : localActiveAccountIds)
                                .selectAsync(Path.all())
                             : (showAll ? allNewAccountIds : localNewAccountIds)
                                .selectAsync(Path.all())
                                // results are (account id, timestamp) tuples.
                                // sort by timestamp (descending) and then return the account ids.
                                .thenApply(results -> results.stream()
                                                             .sorted((o1, o2) -> ((List<Long>) o2).get(1).compareTo(((List<Long>) o1).get(1)))
                                                             .map(result -> ((List<Long>) result).get(0))
                                                             .collect(Collectors.toList()));
        return future.thenApply(results -> {
            List<Long> accountIds = new ArrayList<>();
            Map<Long, Integer> accountIdToIndex = new HashMap<>();
            long count = 0;
            for (Long accountId : results) {
                if (count == offset + limit) break;
                // remove existing account id if necessary
                Integer existingIndex = accountIdToIndex.get(accountId);
                if (existingIndex != null) accountIds.set(existingIndex, null);
                else count += 1;
                // add account id
                accountIdToIndex.put(accountId, accountIds.size());
                accountIds.add(accountId);
            }
            return accountIds.stream().filter(Objects::nonNull).skip(offset).collect(Collectors.toList());
        }).thenCompose(this::getAccountsFromAccountIds);
    }

    public CompletableFuture<ItemStats> getHashtagStats(String hashtag) {
        return batchHashtagStats.invokeAsync(Arrays.asList(hashtag)).thenApply(info -> info.get(hashtag));
    }

    public CompletableFuture<Boolean> postFollowHashtag(long accountId, String hashtag) {
        return followHashtagDepot.appendAsync(new FollowHashtag(accountId, hashtag, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> postRemoveFollowHashtag(long accountId, String hashtag) {
        return followHashtagDepot.appendAsync(new RemoveFollowHashtag(accountId, hashtag, System.currentTimeMillis())).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> isFollowingHashtag(long accountId, String hashtag) {
        return hashtagToFollowers.selectOneAsync(Path.key(hashtag).view(Ops.CONTAINS, accountId));
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getStatusBoosters(Long requestAccountIdMaybe, long authorId, long statusId, Long offsetMaybe, Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe;
        int defaultLimit = 40;
        int maxLimit = 80;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        // make sure requester is allowed to see status
        return this.getStatus(requestAccountIdMaybe, new StatusPointer(authorId, statusId))
                   .thenCompose(resultMaybe -> {
                       if (resultMaybe == null) return CompletableFuture.completedFuture(null);
                       // get the boosters
                       SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                       CompletableFuture<List<Long>> boosterIdsFuture = statusIdToBoosters.selectAsync(authorId, Path.key(statusId).sortedMapRangeFrom(offset, options).mapKeys());
                       return boosterIdsFuture.thenCompose(this::getAccountsFromAccountIds)
                                              .thenApply(accountWithIds -> {
                                                  Long lastId = null;
                                                  List<SimpleEntry<String, String>> linkHeaderParams = null;
                                                  if (accountWithIds.size() > 0) {
                                                      lastId = accountWithIds.get(accountWithIds.size()-1).accountId;
                                                      linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", MastodonHelpers.serializeAccountId(lastId)));
                                                  }
                                                  return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId, linkHeaderParams);
                                              });
                   });
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getStatusFavoriters(Long requestAccountIdMaybe, long authorId, long statusId, Long offsetMaybe, Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe;
        int defaultLimit = 40;
        int maxLimit = 80;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        // make sure requester is allowed to see status
        return this.getStatus(requestAccountIdMaybe, new StatusPointer(authorId, statusId))
                   .thenCompose(resultMaybe -> {
                       if (resultMaybe == null) return CompletableFuture.completedFuture(null);
                       // get the favoriters
                       SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                       CompletableFuture<List<Long>> favoriterIdsFuture = statusIdToFavoriters.selectAsync(authorId, Path.key(statusId).sortedMapRangeFrom(offset, options).mapKeys());
                       return favoriterIdsFuture.thenCompose(this::getAccountsFromAccountIds)
                                                .thenApply(accountWithIds -> {
                                                    Long lastId = null;
                                                    List<SimpleEntry<String, String>> linkHeaderParams = null;
                                                    if (accountWithIds.size() > 0) {
                                                        AccountWithId lastAccount = accountWithIds.get(accountWithIds.size()-1);
                                                        lastId = lastAccount.accountId;
                                                        linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", MastodonHelpers.serializeAccountId(lastAccount.accountId)));
                                                    }
                                                    return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId, linkHeaderParams);
                                                });
                   });
    }

    public CompletableFuture<List<Status>> getStatusHistory(long accountId, long statusId) {
        return accountIdToStatuses.selectOneAsync(Path.key(accountId, statusId))
                                  .thenApply(statusesMaybe -> {
                                      if (statusesMaybe == null) return null;
                                      List<Status> statuses = new ArrayList<>((List<Status>) statusesMaybe);
                                      Collections.reverse(statuses);
                                      return statuses;
                                  });
    }

    public CompletableFuture<QueryResults<AccountWithId, Map>> getProfileSearch(long requestAccountId, List<String> terms, Map startParamsMaybe, Integer limitMaybe, boolean followeesOnly) {
        int defaultLimit = 40;
        int maxLimit = 80;
        return queryWithPaging(
                (offset, limit) -> {
                    CompletableFuture<Map> matchListFuture = profileTermsSearch.invokeAsync(terms, offset, limit);
                    return matchListFuture.thenCompose(result -> {
                        Map nextParams = MastodonApiHelpers.createSearchParams(result);
                        List<String> matchList = (List<String>) result.get("matchList");
                        return getAccountsFromNames.invokeAsync(requestAccountId, matchList)
                                                   .thenApply(accountWithIds -> {
                                                       if (followeesOnly) accountWithIds = accountWithIds.stream().filter(o -> o.metadata.isFollowedByRequester).collect(Collectors.toList());
                                                       return new QueryResults<>(accountWithIds, nextParams == null, nextParams, MastodonApiHelpers.createLinkHeaderParams(nextParams));
                                                   });
                    });
                },
                startParamsMaybe,
                Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit),
                MAX_PAGING_ITERATIONS)
          .thenCompose(result -> {
            if(terms.size() == 1 && result.results.isEmpty()) {
              // override prefix search
              List<String> terms2 = Arrays.asList(terms.get(0), terms.get(0));
              return getProfileSearch(requestAccountId, terms2, startParamsMaybe, limitMaybe, followeesOnly);
            } else {
                // deduplicate results
                LinkedHashMap<Long, AccountWithId> dedupedResults = new LinkedHashMap<>();
                for (AccountWithId awid : result.results) dedupedResults.put(awid.accountId, awid);
                result.results = new ArrayList<>(dedupedResults.values());
                return CompletableFuture.completedFuture(result);
            }
          });
    }

    public CompletableFuture<QueryResults<StatusQueryResult, Map>> getStatusSearch(long requestAccountId, Long authorIdMaybe, List<String> terms, Map startParamsMaybe, Integer limitMaybe) {
        int defaultLimit = 40;
        int maxLimit = 80;
        return queryWithPaging(
            (offset, limit) -> {
                CompletableFuture<Map> matchListFuture = statusTermsSearch.invokeAsync(authorIdMaybe != null ? authorIdMaybe : requestAccountId, terms, offset, limit);
                return matchListFuture.thenCompose(result -> {
                    Map nextParams = MastodonApiHelpers.createSearchParams(result);
                    List<StatusPointer> matchList = ((List<List>) result.get("matchList")).stream().map(pair -> new StatusPointer((Long) pair.get(0), (Long) pair.get(1))).collect(Collectors.toList());
                    return getStatusesFromPointers.invokeAsync(requestAccountId, matchList, new QueryFilterOptions(FilterContext.Public, false))
                                                  .thenApply(statusQueryResults -> {
                                                      List<StatusQueryResult> filtered = new ArrayList<>();
                                                      for (StatusResultWithId sqr : statusQueryResults.results) {
                                                          // if authorIdMaybe is set, we are searching for only a particular user's statuses.
                                                          // in that case, only include results written by that user (i.e. filter out mentions)
                                                          if (authorIdMaybe == null || sqr.status.author.accountId == authorIdMaybe) filtered.add(new StatusQueryResult(sqr, statusQueryResults.mentions));
                                                      }
                                                      return new QueryResults<>(filtered, nextParams == null, nextParams, MastodonApiHelpers.createLinkHeaderParams(nextParams));
                                                  });
                });
            },
            startParamsMaybe,
            Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit),
            MAX_PAGING_ITERATIONS);
    }

    public CompletableFuture<QueryResults<SimpleEntry<String, ItemStats>, Map>> getHashtagSearch(String term, Map startParamsMaybe, Integer limitMaybe) {
        int defaultLimit = 40;
        int maxLimit = 80;
        int limit = Math.min(limitMaybe == null ? defaultLimit : limitMaybe, maxLimit);
        CompletableFuture<Map> matchListFuture = hashtagSearch.invokeAsync(term, startParamsMaybe, limit);
        return matchListFuture.thenCompose(result -> {
            Map nextParams = MastodonApiHelpers.createSearchParams(result);
            List<String> matchList = (List<String>) result.get("matchList");
            return batchHashtagStats.invokeAsync(matchList.stream().distinct().collect(Collectors.toList()))
                                    .thenApply(hashtagToStats -> {
                                        if (hashtagToStats == null) hashtagToStats = new HashMap<>();
                                        List<SimpleEntry<String, ItemStats>> results = new ArrayList<>();
                                        for (Map.Entry<String, ItemStats> entry : hashtagToStats.entrySet()) {
                                            results.add(new SimpleEntry<>(entry.getKey(), entry.getValue()));
                                        }
                                        return new QueryResults<>(results, nextParams == null, nextParams, MastodonApiHelpers.createLinkHeaderParams(nextParams));
                                    });
        });
    }

    public CompletableFuture<Conversation> getConversationFromStatusId(long accountId, StatusPointer pointer) {
        return statusIdToConvoId.selectOneAsync(pointer.authorId, Path.key(pointer.statusId).nullToVal(pointer.statusId))
                                .thenCompose(conversationId -> getConversation.invokeAsync(accountId, conversationId));
    }

    public CompletableFuture<List<AccountWithId>> getWhoToFollowSuggestions(long accountId) {
        return getWhoToFollowSuggestions.invokeAsync(accountId)
                                        .thenApply(ArrayList::new)
                                        .thenCompose(this::getAccountsFromAccountIds);
    }

    public CompletableFuture<AttachmentWithId> getAttachment(String uuid) {
        return uuidToAttachment.selectOneAsync(Path.key(uuid))
                               .thenApply(attachment -> new AttachmentWithId(uuid, (Attachment) attachment));
    }

    public CompletableFuture<QueryResults<StatusWithId, Long>> getScheduledStatuses(Long accountId, StatusPointer offsetMaybe, Integer limitMaybe) {
        long offset = offsetMaybe == null ? -1L : offsetMaybe.statusId;
        int limit = Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT);
        SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
        return accountIdToScheduledStatuses
                .selectAsync(Path.key(accountId)
                                 .sortedMapRangeFrom(offset, options)
                                 .all()
                                 .collectOne(Path.first())
                                 .last()
                                 .key("status"))
                .thenApply((List<Object> results) -> {
                    List<StatusWithId> statuses =
                            results.stream()
                                   .map(result -> {
                                      List<Object> resultList = (List<Object>) result;
                                      Long statusId = (Long) resultList.get(0);
                                      Status status = (Status) resultList.get(1);
                                      return new StatusWithId(statusId, status);
                                   }).collect(Collectors.toList());
                    Long lastId = null;
                    List<SimpleEntry<String, String>> linkHeaderParams = null;
                    if (statuses.size() > 0) {
                        StatusWithId lastStatus = statuses.get(statuses.size()-1);
                        lastId = lastStatus.statusId;
                        linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", MastodonHelpers.serializeStatusPointer(new StatusPointer(lastStatus.status.authorId, lastStatus.statusId))));
                    }
                    return new QueryResults<>(statuses, results.size() < limit, lastId, linkHeaderParams);
                });
    }

    public CompletableFuture<StatusWithId> getScheduledStatus(StatusPointer statusPointer) {
      return accountIdToScheduledStatuses.selectOneAsync(Path.key(statusPointer.authorId, statusPointer.statusId, "status"))
                                         .thenApply(status -> {
                                             if (status == null) return null;
                                             return new StatusWithId(statusPointer.statusId, (Status) status);
                                         });
    }

    public CompletableFuture<StatusWithId> updateScheduledStatus(StatusPointer statusPointer, String scheduledAt) {
        long publishAt = Instant.parse(scheduledAt).toEpochMilli();
        long timestamp = Instant.now().toEpochMilli();
        return scheduledStatusDepot.appendAsync(new EditScheduledStatusPublishTime(statusPointer.authorId, statusPointer.statusId, publishAt, timestamp))
          .thenComposeAsync(result -> {
            return accountIdToScheduledStatuses.selectOneAsync(Path.key(statusPointer.authorId)
                                                                   .must(statusPointer.statusId)
                                                                   .collectOne(Path.key("publishMillis"))
                                                                   .key("status"));
        }).thenApply(result -> {
            List<Object> publishMillisAndStatus = (List<Object>) result;
            Long publishMillis = (Long) publishMillisAndStatus.get(0);
            Status status = (Status) publishMillisAndStatus.get(1);
            status.timestamp = publishMillis;
            return new StatusWithId(statusPointer.statusId, status);
        });
    }

    public CompletableFuture<Void> cancelScheduledStatus(StatusPointer statusPointer) {
        return scheduledStatusDepot.appendAsync(new RemoveStatus(statusPointer.authorId, statusPointer.statusId, Instant.now().toEpochMilli()));
    }

    public CompletableFuture<QueryResults<AccountWithId, Long>> getFollowRequests(long requestAccountId, Long offsetMaybe, Integer limitMaybe) {
        return queryWithPaging(
            (offset, limit) -> {
                SortedRangeFromOptions options = SortedRangeFromOptions.excludeStart().maxAmt(limit);
                CompletableFuture<List<Long>> future = accountIdToFollowRequestsById.selectAsync(Path.key(requestAccountId).sortedMapRangeFrom(offset, options).mapVals().customNav(new com.rpl.mastodon.navs.TField("requesterId")));
                return future.thenCompose(requesterIds -> getAccountsFromAccountIds.invokeAsync(requestAccountId, requesterIds))
                             .thenApply(accountWithIds -> {
                                 Long lastId = null;
                                 List<SimpleEntry<String, String>> linkHeaderParams = null;
                                 if (accountWithIds.size() > 0) {
                                     AccountWithId lastAccount = accountWithIds.get(accountWithIds.size()-1);
                                     lastId = lastAccount.accountId;
                                     linkHeaderParams = Arrays.asList(new SimpleEntry<>("max_id", MastodonHelpers.serializeAccountId(lastAccount.accountId)));
                                 }
                                 return new QueryResults<>(accountWithIds, accountWithIds.size() < limit, lastId, linkHeaderParams);
                             });
            },
            offsetMaybe == null ? -1L : offsetMaybe,
            Math.min(limitMaybe == null ? DEFAULT_LIMIT : limitMaybe, MAX_LIMIT),
            MAX_PAGING_ITERATIONS
        );
    }

    public CompletableFuture<Boolean> acceptFollowRequest(long accountId, long requesterId) {
        return accountIdToFollowRequests.selectOneAsync(Path.key(accountId, requesterId))
                                        .thenCompose(existingRequest -> {
                                            if (existingRequest != null) {
                                                return followAndBlockAccountDepot.appendAsync(new AcceptFollowRequest(accountId, requesterId, System.currentTimeMillis()))
                                                                                 .thenApply(res -> true);
                                            } else return CompletableFuture.completedFuture(false);
                                        });
    }

    public CompletableFuture<Boolean> rejectFollowRequest(long accountId, long requesterId) {
        return accountIdToFollowRequests.selectOneAsync(Path.key(accountId, requesterId))
                                        .thenCompose(existingRequest -> {
                                            if (existingRequest != null) {
                                                return followAndBlockAccountDepot.appendAsync(new RejectFollowRequest(accountId, requesterId))
                                                                                 .thenApply(res -> true);
                                            } else return CompletableFuture.completedFuture(false);
                                        });
    }

    public CompletableFuture<Set<Integer>> getPollVote(long accountId, long statusId) {
        return pollVotes.selectOneAsync(Path.key(statusId, "allVoters", accountId));
    }

    public CompletableFuture<FilterWithId> postFilter(Filter filter) {
        String uuid = UUID.randomUUID().toString();
        AddFilter addFilter = new AddFilter(filter, uuid);
        return filterDepot.appendAsync(addFilter)
                          .thenCompose(res -> postUUIDToGeneratedId.selectOneAsync(Path.key(uuid)))
                          .thenCompose(filterId ->
                              accountIdToFilterIdToFilter.selectOneAsync(Path.key(filter.accountId, filterId))
                                  .thenApply(foundFilter -> new FilterWithId((long) filterId, (Filter) foundFilter)));
    }

    public CompletableFuture<List<FilterWithId>> getFilters(Long requestAccountId) {
        return accountIdToFilterIdToFilter.selectAsync(Path.key(requestAccountId).all())
                                          .thenApply(result -> MastodonHelpers.createFiltersWithIds((List) result));
    }

    public CompletableFuture<FilterWithId> getFilter(Long accountId, Long filterId) {
        return accountIdToFilterIdToFilter.selectOneAsync(Path.key(accountId, filterId))
                                          .thenApply(result -> {
                                              if (result == null) return null;
                                              return new FilterWithId(filterId, (Filter) result);
                                          });
    }

    public CompletableFuture<FilterWithId> putFilter(EditFilter edit) {
        return filterDepot.appendAsync(edit)
                          .thenCompose(res -> accountIdToFilterIdToFilter.selectOneAsync(Path.key(edit.accountId, edit.filterId)))
                          .thenApply(filter -> filter == null? null : new FilterWithId(edit.filterId, (Filter) filter));
    }

    public CompletableFuture<Void> deleteFilter(Long accountId, Long filterId) {
        return filterDepot.appendAsync(new RemoveFilter(filterId, accountId, System.currentTimeMillis()));
    }

    public CompletableFuture<FilterWithId> addStatusToFilter(Long accountId, Long filterId, StatusPointer statusPointer) {
        return filterDepot.appendAsync(new AddStatusToFilter(filterId, accountId, statusPointer))
                          .thenCompose(res ->  accountIdToFilterIdToFilter.selectOneAsync(Path.key(accountId, filterId)))
                          .thenApply(filter -> {
                              if (filter == null) return null;
                              return new FilterWithId(filterId, (Filter) filter);
                          });
    }

    public CompletableFuture<Void> removeStatusFromFilter(Long accountId, Long filterId, StatusPointer statusPointer) {
        return filterDepot.appendAsync(new RemoveStatusFromFilter(filterId, accountId, statusPointer));
    }

    public CompletableFuture<List<String>> getRemoteServerFollowers(long followeeId) {
        return followeeToRemoteServerToFollowers.selectAsync(Path.key(followeeId).sortedMapRangeFrom(0L, 20).mapKeys());
    }
}
