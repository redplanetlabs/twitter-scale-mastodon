package com.rpl.mastodon;

import com.rpl.mastodon.data.*;
import com.rpl.mastodon.ops.*;
import com.rpl.rama.*;
import com.rpl.rama.ops.*;
import org.apache.thrift.*;

import java.lang.reflect.Field;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MastodonHelpers {

  public static StatusPointer parseStatusPointer(String id) {
    if (id == null) return null;
    String[] parts = id.split("-");
    if ("sa".equals(parts[parts.length-1]) && parts.length == 3) {
      long statusId = Long.parseLong(parts[0]);
      long authorId = Long.parseLong(parts[1]);
      return new StatusPointer(authorId, statusId);
    } else throw new RuntimeException("Not a status pointer: " + id);
  }

  public static Long parseNotificationId(String id) {
    if (id == null) return null;
    String[] parts = id.split("-");
    if ("tn".equals(parts[parts.length-1]) && parts.length == 3) return Long.parseLong(parts[1]);
    else throw new RuntimeException("Not a notification id: " + id);
  }

  public static Long parseAccountId(String id) {
    if (id == null) return null;
    String[] parts = id.split("-");
    if ("a".equals(parts[parts.length-1]) && parts.length == 2) return Long.parseLong(parts[0]);
    else throw new RuntimeException("Not an account id: " + id);
  }

  public static String serializeStatusPointer(StatusPointer statusPointer) {
    // the id is a statusId-authorId combo. the author id is necessary so
    // we don't have to query for the author separately when requesting the status.
    // they are padded with zeroes to ensure that sorting works correctly.
    return String.format("%019d", statusPointer.statusId) + "-" + String.format("%019d", statusPointer.authorId) + "-sa";
  }

  public static String serializeNotificationId(long notificationId, long timestamp) {
    return String.format("%019d", timestamp) + "-" + String.format("%019d", notificationId) + "-tn";
  }

  public static String serializeAccountId(long accountId) {
    return String.format("%019d", accountId) + "-a";
  }

  public static Long getFilterIdFromStatusFilterId(String statusFilterId) {
      return Long.parseLong(statusFilterId.substring(0, statusFilterId.indexOf("-")));
  }

  public static StatusPointer getStatusPointerFromStatusFilterId(String statusFilterId) {
      return MastodonHelpers.parseStatusPointer(statusFilterId.substring(statusFilterId.indexOf("-")+1));
  }

  public static final int BULK_FETCH_SIZE = 1000;
  public static final ConcurrentHashMap<Class, Map<String, TFieldIdEnum>> TFIELD_CACHE = new ConcurrentHashMap<>();

  public static Map<String, TFieldIdEnum> getTFieldCache(Class thriftClass) {
    Map<String, TFieldIdEnum> ret = TFIELD_CACHE.get(thriftClass);
    if(ret==null) {
      try {
        Field f = thriftClass.getField("metaDataMap");
        Map<TFieldIdEnum, Object> m = (Map) f.get(thriftClass);
        ret = new HashMap<>();
        for(TFieldIdEnum e: m.keySet()) ret.put(e.getFieldName(), e);
        TFIELD_CACHE.put(thriftClass, ret);
      } catch(Exception e) {
        throw new RuntimeException(e);
      }
    }
    return ret;
  }

  public static boolean hasTField(TBase obj, String fieldName) {
    TFieldIdEnum field = getTFieldCache(obj.getClass()).get(fieldName);
    return field != null;
  }

  public static Object getTFieldByName(TBase obj, String fieldName) {
    TFieldIdEnum field = getTFieldCache(obj.getClass()).get(fieldName);
    if(field==null) throw new RuntimeException("Field " + fieldName + " does not exist on " + obj.getClass());

    Object ret = null;
    if(obj.isSet(field)) ret = obj.getFieldValue(field);
    if(ret instanceof TUnion) ret = ((TUnion) ret).getFieldValue();
    return ret;
  }

  public static void setTFieldByName(TBase obj, String fieldName, Object val) {
    TFieldIdEnum field = getTFieldCache(obj.getClass()).get(fieldName);
    if(field==null) throw new RuntimeException("Field " + fieldName + " does not exist on " + obj.getClass());
    obj.setFieldValue(field, val);
  }

  public static Block extractFields(Object from, String... fieldVars) {
    Block.Impl ret = Block.create();
    for(String f: fieldVars) {
      String name;
      if(Helpers.isGeneratedVar(f)) name = Helpers.getGeneratedVarPrefix(f);
      else name = f.substring(1);
      ret = ret.each(new ExtractField(name), from).out(f);
    }
    return ret;
  }

  public static class ExtractTargetAuthorId implements RamaFunction1<Object, Long> {
    @Override
    public Long invoke(Object o) {
      StatusPointer target = (StatusPointer) getTFieldByName((TBase) o, "target");
      return target.authorId;
    }
  }

  public static class ExtractAccountId extends ExtractField {
    public ExtractAccountId() { super("accountId"); }
  }

  public static class ExtractListId implements RamaFunction1<Object, Long> {
    @Override
    public Long invoke(Object o) {
      if (o instanceof AccountList) return ((AccountList) o).getAuthorId();
      else return (Long) getTFieldByName((TBase) o, "listId");
    }
  }

  public static class ExtractFilterAccountId implements RamaFunction1<Object, Long> {
    @Override
    public Long invoke(Object o) {
      if (o instanceof AddFilter) return ((AddFilter) o).getFilter().getAccountId();
      if (o instanceof Filter) return ((Filter) o).getAccountId();
      if (o instanceof FilterWithId) return ((FilterWithId) o).getFilter().getAccountId();
      else return (Long) getTFieldByName((TBase) o, "accountId");
    }
  }

  public static class ExtractStatusId extends ExtractField {
    public ExtractStatusId() { super("statusId"); }
  }

  public static class ExtractUuid extends ExtractField {
    public ExtractUuid() { super("uuid"); }
  }

  public static class ExtractName extends ExtractField {
    public ExtractName() { super("name"); }
  }

  public static class ExtractItem extends ExtractField {
    public ExtractItem() { super("item"); }
  }

  public static class ExtractCode extends ExtractField {
    public ExtractCode() { super("code"); }
  }

  public static class ExtractToken extends ExtractField {
    public ExtractToken() { super("token"); }
  }

  public static class SublistStart implements RamaFunction1<List<Long>, Long> {
    long offset;

    public SublistStart(long offset) {
      this.offset = offset;
    }

    @Override
    public Long invoke(List<Long> list) {
      return Math.min(list.size(), this.offset);
    }
  }

  public static class SublistEnd implements RamaFunction2<List<Long>, Long, Long> {
    long limit;

    public SublistEnd(long limit) {
      this.limit = limit;
    }

    @Override
    public Long invoke(List<Long> list, Long startIndex) {
      return Math.min(list.size(), startIndex + this.limit);
    }
  }

  public static StatusResult normalStatusResult(Status status, StatusMetadata metadata, Account author) {
    StatusResultContent content = StatusResultContent.normal(status.content.getNormal());
    StatusResult result = new StatusResult(new AccountWithId(status.authorId, author, new AccountMetadata()), content, metadata, status.timestamp);
    if (status.isSetRemoteUrl()) result.setRemoteUrl(status.remoteUrl);
    return result;
  }

  public static StatusResult replyStatusResult(Status status, StatusMetadata metadata, Account author) {
    StatusResultContent content = StatusResultContent.reply(status.content.getReply());
    StatusResult result = new StatusResult(new AccountWithId(status.authorId, author, new AccountMetadata()), content, metadata, status.timestamp);
    if (status.isSetRemoteUrl()) result.setRemoteUrl(status.remoteUrl);
    return result;
  }

  public static StatusResult boostStatusResult(Status status, StatusMetadata metadata, Account author, Status boostedStatus, StatusMetadata boostedMetadata, Account boostedAuthor) {
    StatusResultContent boostedContent;
    if (boostedStatus.content.isSetNormal()) boostedContent = StatusResultContent.normal(boostedStatus.content.getNormal());
    else if (boostedStatus.content.isSetReply()) boostedContent = StatusResultContent.reply(boostedStatus.content.getReply());
    else return null; // You can't boost a boost
    StatusResult innerResult = new StatusResult(new AccountWithId(boostedStatus.authorId, boostedAuthor, new AccountMetadata()), boostedContent, boostedMetadata, boostedStatus.timestamp);
    StatusResultContent content = StatusResultContent.boost(new BoostStatusResultContent(status.content.getBoost().boosted.statusId, innerResult));
    StatusResult result = new StatusResult(new AccountWithId(status.authorId, author, new AccountMetadata()), content, metadata, status.timestamp);
    if (status.isSetRemoteUrl()) result.setRemoteUrl(status.remoteUrl);
    return result;
  }

  public static List<FilterWithId> createFiltersWithIds(List<List> filterIdAndFilters) {
    ArrayList<FilterWithId> filters = new ArrayList<>();
    for (List filterIdAndFilter : filterIdAndFilters) {
      filters.add(new FilterWithId((Long) filterIdAndFilter.get(0), (Filter) filterIdAndFilter.get(1)));
    }
    return filters;
  }

  public static Block resolveMetadata(String requestAccountIdVar, String filtersVar, String filterContextValueVar, String authorIdVar, String statusIdVar, String statusVar, String outVar) {
    String favoriteIndexVar = Helpers.genVar("favoriteIndex");
    String boostIndexVar = Helpers.genVar("boostIndex");
    String muteVar = Helpers.genVar("mute");
    String bookmarkVar = Helpers.genVar("bookmark");
    String pinIndexVar = Helpers.genVar("pinIndex");
    String favoriteCountVar = Helpers.genVar("favoriteCount");
    String boostCountVar = Helpers.genVar("boostCount");
    String replyCountVar = Helpers.genVar("replyCount");
    String textVar = Helpers.genVar("text");
    String matchingFiltersVar = Helpers.genVar("matchingFilters");
    String shouldHideVar = Helpers.genVar("shouldHide");
    String metaVar = Helpers.genVar("meta");
    String countsVar = Helpers.genVar("counts");
    return Block.ifTrue(new Expr(Ops.IS_NULL, requestAccountIdVar),
                   Block.each(Ops.IDENTITY, null).out(favoriteIndexVar)
                        .each(Ops.IDENTITY, null).out(boostIndexVar)
                        .each(Ops.IDENTITY, false).out(muteVar)
                        .each(Ops.IDENTITY, false).out(bookmarkVar)
                        .each(Ops.IDENTITY, null).out(pinIndexVar),
                   Block.localSelect("$$statusIdToFavoriters", Path.key(statusIdVar, requestAccountIdVar)).out(favoriteIndexVar)
                        .localSelect("$$statusIdToBoosters", Path.key(statusIdVar, requestAccountIdVar)).out(boostIndexVar)
                        .localSelect("$$statusIdToBookmarkers", Path.key(statusIdVar).view(Ops.CONTAINS, requestAccountIdVar)).out(bookmarkVar)
                        .localSelect("$$statusIdToMuters", Path.key(statusIdVar).view(Ops.CONTAINS, requestAccountIdVar)).out(muteVar)
                        // since statuses can only be pinned by creating user, it's correct to query for this on this partition, as this
                        // can only be true for the author of the status (when authorId = requestAccountIdVar)
                        .localSelect("$$pinnerToStatusIdsReverse", Path.key(requestAccountIdVar, statusIdVar)).out(pinIndexVar))
                .localSelect("$$statusIdToFavoriters", Path.key(statusIdVar).view(Ops.SIZE)).out(favoriteCountVar)
                .localSelect("$$statusIdToBoosters", Path.key(statusIdVar).view(Ops.SIZE)).out(boostCountVar)
                .localSelect("$$statusIdToReplies", Path.key(statusIdVar).view(Ops.SIZE)).out(replyCountVar)
                // look for matching filters
                .each(MastodonHelpers::getStatusText, statusVar).out(textVar)
                .each(MastodonHelpers::getMatchingFilters, statusIdVar, textVar, filtersVar, new Expr(FilterContext::findByValue, filterContextValueVar)).out(matchingFiltersVar)
                .each((List<MatchingFilter> matchingFilters) -> matchingFilters.stream().anyMatch(matchingFilter -> matchingFilter.filter.action == FilterAction.Hide), matchingFiltersVar).out(shouldHideVar)
                .keepTrue(new Expr(Ops.NOT, shouldHideVar))
                // create status metadata
                .each(Ops.TUPLE, favoriteIndexVar, boostIndexVar, muteVar, bookmarkVar, pinIndexVar).out(metaVar)
                .each(Ops.TUPLE, favoriteCountVar, boostCountVar, replyCountVar).out(countsVar)
                .each((List<MatchingFilter> matchingFilters, List meta,
                       List<Integer> counts) -> new StatusMetadata(matchingFilters, meta.get(0) != null,
                        meta.get(1) != null, (boolean) meta.get(2), (boolean) meta.get(3),
                        meta.get(4) != null, counts.get(0), counts.get(1), counts.get(2)),
                    matchingFiltersVar, metaVar, countsVar).out(outVar);
  }

  public static Block resolveStatusResult(String requestAccountIdVar, String filtersVar, String filterContextValueVar, String authorIdVar, String statusIdVar, String statusVar, String contentVar, String outVar) {
    String authorVar = Helpers.genVar("author");
    String metadataVar = Helpers.genVar("metadata");
    String boostedVar = Helpers.genVar("boosted");
    String boostedStatusIdVar = Helpers.genVar("boostedStatusId");
    String boostedAuthorIdVar = Helpers.genVar("boostedAuthorId");
    String boostedStatusVar = Helpers.genVar("boostedStatus");
    String boostedAuthorVar = Helpers.genVar("boostedAuthor");
    String boostedMetadataVar = Helpers.genVar("boostedMetadata");
    return Block.localSelect("$$accountIdToAccount", Path.must(authorIdVar)).out(authorVar)
                .macro(resolveMetadata(requestAccountIdVar, filtersVar, filterContextValueVar, authorIdVar, statusIdVar, statusVar, metadataVar))
                .subSource(contentVar,
                      SubSource.create(NormalStatusContent.class)
                               .each(MastodonHelpers::normalStatusResult, statusVar, metadataVar, authorVar).out(outVar),
                      SubSource.create(ReplyStatusContent.class)
                               .each(MastodonHelpers::replyStatusResult, statusVar, metadataVar, authorVar).out(outVar),
                      SubSource.create(BoostStatusContent.class)
                               .macro(MastodonHelpers.extractFields(contentVar, boostedVar))
                               .each((StatusPointer boosted) -> boosted.authorId, boostedVar).out(boostedAuthorIdVar)
                               .each((StatusPointer boosted) -> boosted.statusId, boostedVar).out(boostedStatusIdVar)
                               .hashPartition(boostedAuthorIdVar)
                               .localSelect("$$accountIdToStatuses", Path.must(boostedAuthorIdVar, boostedStatusIdVar).first()).out(boostedStatusVar)
                               .localSelect("$$accountIdToAccount", Path.must(boostedAuthorIdVar)).out(boostedAuthorVar)
                               .macro(resolveMetadata(requestAccountIdVar, filtersVar, filterContextValueVar, boostedAuthorIdVar, boostedStatusIdVar, boostedStatusVar, boostedMetadataVar))
                               .each(MastodonHelpers::boostStatusResult, statusVar, metadataVar, authorVar, boostedStatusVar, boostedMetadataVar, boostedAuthorVar).out(outVar))
                               .hashPartition(authorIdVar);
  }

  public static long origStatusId(long statusId, Status status) {
    if(status.content.isSetBoost()) return status.content.getBoost().boosted.statusId;
    else return statusId;
  }

  public static long origAuthorId(long authorId, Status status) {
    if(status.content.isSetBoost()) return status.content.getBoost().boosted.authorId;
    else return authorId;
  }

  public static List<Long> getAuthorIds(StatusResult status) {
    List<Long> authorIds = new ArrayList<>();
    authorIds.add(status.author.accountId);
    if(status.content.isSetBoost()) authorIds.add(status.content.getBoost().status.author.accountId);
    return authorIds;
  }

  public static void updateAccountMetadata(StatusResult status, Map<Long, AccountMetadata> accountIdToMetadata) {
    AccountMetadata authorMeta = accountIdToMetadata.get(status.author.accountId);
    if(authorMeta != null) status.author.metadata = authorMeta;
    if(status.content.isSetBoost()) {
      AccountMetadata boostAuthorMeta = accountIdToMetadata.get(status.content.getBoost().status.author.accountId);
      if(boostAuthorMeta != null) status.content.getBoost().status.author.metadata = boostAuthorMeta;
    }
  }

  public static String getStatusText(Status status) {
    if (status.content.isSetNormal()) return status.content.getNormal().text;
    else if (status.content.isSetReply()) return status.content.getReply().text;
    return "";
  }

  public static String getStatusResultText(StatusResult statusResult) {
    if (statusResult.content.isSetNormal()) return statusResult.content.getNormal().text;
    else if (statusResult.content.isSetReply()) return statusResult.content.getReply().text;
    else if (statusResult.content.isSetBoost()) return getStatusResultText(statusResult.content.getBoost().status);
    return "";
  }

  public static StatusVisibility getStatusResultVisibility(StatusResult statusResult) {
    if (statusResult.getContent().isSetNormal()) return statusResult.getContent().getNormal().visibility;
    else if (statusResult.getContent().isSetReply()) return statusResult.getContent().getReply().visibility;
    else if (statusResult.getContent().isSetBoost()) return getStatusResultVisibility(statusResult.getContent().getBoost().status);
    else throw new RuntimeException("Unexpected status content " + statusResult.getContent().getFieldValue().getClass());
  }

  public static StatusVisibility getStatusVisibility(Status status) {
    Object content = status.getContent().getFieldValue();
    if(content instanceof BoostStatusContent) return StatusVisibility.Public;
    else if(content instanceof ReplyStatusContent) return ((ReplyStatusContent) content).getVisibility();
    else if(content instanceof NormalStatusContent) return ((NormalStatusContent) content).getVisibility();
    else throw new RuntimeException("Unexpected status content " + content.getClass());
  }

  public static String getTypeFromNotificationContent(NotificationContent content) {
    if (content.isSetMention()) return "mention";
    else if (content.isSetBoost()) return "reblog";
    else if (content.isSetFollow()) return "follow";
    else if (content.isSetFollowRequest()) return "follow_request";
    else if (content.isSetFavorite()) return "favourite";
    else if (content.isSetPollComplete()) return "poll";
    else if (content.isSetBoostedUpdate()) return "update";
    else if (content.isSetFolloweeStatus()) return "status";
    else throw new RuntimeException("Unexpected notification content: " + content);
  }

  public static long getAccountIdFromNotificationContent(NotificationContent content) {
    if (content.isSetMention()) return content.getMention().authorId;
    else if (content.isSetBoost()) return content.getBoost().responderAccountId;
    else if (content.isSetFollow()) return content.getFollow();
    else if (content.isSetFollowRequest()) return content.getFollowRequest();
    else if (content.isSetFavorite()) return content.getFavorite().responderAccountId;
    else if (content.isSetPollComplete()) return content.getPollComplete().authorId;
    else if (content.isSetBoostedUpdate()) return content.getBoostedUpdate().authorId;
    else if (content.isSetFolloweeStatus()) return content.getFolloweeStatus().authorId;
    else throw new RuntimeException("Unexpected notification content: " + content);
  }

  public static StatusPointer getStatusPointerFromNotificationContent(NotificationContent content) {
    if (content.isSetMention()) return content.getMention();
    else if (content.isSetBoost()) return content.getBoost().target;
    else if (content.isSetFollow()) return null;
    else if (content.isSetFollowRequest()) return null;
    else if (content.isSetFavorite()) return content.getFavorite().target;
    else if (content.isSetPollComplete()) return content.getPollComplete();
    else if (content.isSetBoostedUpdate()) return content.getBoostedUpdate();
    else if (content.isSetFolloweeStatus()) return content.getFolloweeStatus();
    else throw new RuntimeException("Unexpected notification content: " + content);
  }

  public static ArrayList<MatchingFilter> getMatchingFilters(Long statusId, String text, List<FilterWithId> filters, FilterContext context) {
    ArrayList<MatchingFilter> matchingFilters = new ArrayList<>();
    for (FilterWithId filterWithId : filters) {
      if (filterWithId.filter.contexts.contains(context)) {
        String content = text.toLowerCase();
        Set<String> words = Token.parseTokens(content).stream().filter(token -> token.kind != Token.TokenKind.BOUNDARY).map(token -> token.content).collect(Collectors.toSet());
        List<KeywordFilter> relevantMatches = filterWithId.filter.keywords.stream()
                .filter(keyword -> keyword.word.trim().length() > 0) // ignore empty keywords
                .filter(keyword -> keyword.wholeWord ? words.contains(keyword.word.toLowerCase()) : content.contains(keyword.word.toLowerCase()))
                .collect(Collectors.toList());
        boolean isStatusFilterMatch = filterWithId.filter.statuses.stream().anyMatch(statusPointer -> statusId == statusPointer.statusId);
        if (relevantMatches.size() > 0 || isStatusFilterMatch) {
          matchingFilters.add(new MatchingFilter(filterWithId.filterId, filterWithId.filter, relevantMatches, isStatusFilterMatch));
        }
      }
    }
    return matchingFilters;
  }

  public static Set<String> getRemoteMentionsFromStatus(Status status) {
    Set<String> mentions = new HashSet<>();
    String text = getStatusText(status);
    for (Token token : Token.parseTokens(text)) {
      if (token.kind == Token.TokenKind.REMOTE_MENTION) mentions.add(token.content);
    }
    return mentions;
  }

  public static Set<String> getMentionsFromStatusResult(StatusResultWithId statusResultWithId) {
    Set<String> mentions = new HashSet<>();
    String text = getStatusResultText(statusResultWithId.status);
    for (Token token : Token.parseTokens(text)) {
      if (token.kind == Token.TokenKind.MENTION || token.kind == Token.TokenKind.REMOTE_MENTION) mentions.add(token.content);
    }
    return mentions;
  }

  public static Set<String> getMentionsFromStatusResults(List<StatusResultWithId> statusResults) {
    Set<String> mentions = new HashSet<>();
    for (StatusResultWithId statusResultWithId : statusResults) mentions.addAll(getMentionsFromStatusResult(statusResultWithId));
    return mentions;
  }

  public static Set<Long> getParentAccountIdsFromStatusResults(List<StatusResultWithId> statusResults) {
    Set<Long> accountIds = new HashSet<>();
    for (StatusResultWithId result : statusResults) {
      if (result.status.content.isSetBoost()) {
        if (result.status.content.getBoost().status.content.isSetReply()) {
          ReplyStatusContent content = result.status.content.getBoost().status.content.getReply();
          accountIds.add(content.parent.authorId);
        }
      } else if (result.status.content.isSetReply()) {
        ReplyStatusContent content = result.status.content.getReply();
        accountIds.add(content.parent.authorId);
      }
    }
    return accountIds;
  }

  public static String normalizeURL(String url) throws URISyntaxException {
    String norm = new URI(url).normalize().toString();
    if(norm.endsWith("/")) norm = norm.substring(0, norm.length() - 1);
    return norm;
  }

  public static boolean statusResultHasPoll(StatusResult status) {
    if (status.content.isSetNormal()) return status.content.getNormal().getPollContent() != null;
    else if (status.content.isSetReply()) return status.content.getReply().getPollContent() != null;
    else if (status.content.isSetBoost()) return statusResultHasPoll(status.content.getBoost().status);
    else throw new RuntimeException("Unexpected status result type");
  }

  public static PollContent extractPollContent(Status status) {
    Object content = status.getContent().getFieldValue();
    if(content instanceof NormalStatusContent) return ((NormalStatusContent) content).getPollContent();
    else if(content instanceof ReplyStatusContent) return ((ReplyStatusContent) content).getPollContent();
    else return null;
  }

  public static List<NotificationWithId> createNotificationWithIds(List<List> timelineIndexAndNotifications) {
    return timelineIndexAndNotifications.stream().map((List indexAndNotification) -> {
      Long index = (Long) indexAndNotification.get(0);
      Notification notification = (Notification) indexAndNotification.get(1);
      return new NotificationWithId(index, notification);
    }).collect(Collectors.toList());
  }

  public static StatusQueryResults updateStatusQueryResults(StatusQueryResults statusQueryResults, List<StatusPointer> statusPointers, int limit, boolean refreshed) {
    statusQueryResults.reachedEnd = statusPointers.size() < limit;
    statusQueryResults.setRefreshed(refreshed);
    if (statusPointers.size() > 0) statusQueryResults.setLastStatusPointer(statusPointers.get(statusPointers.size()-1));
    return statusQueryResults;
  }

  public static AddStatus createAddStatusFromBoost(BoostStatus boostStatus) {
    StatusContent content = StatusContent.boost(new BoostStatusContent(new StatusPointer(boostStatus.target.authorId, boostStatus.target.statusId)));
    Status status = new Status(boostStatus.accountId, content, boostStatus.timestamp);
    if (boostStatus.remoteUrl != null) status.setRemoteUrl(boostStatus.remoteUrl);
    return new AddStatus(boostStatus.uuid, status);
  }
}
