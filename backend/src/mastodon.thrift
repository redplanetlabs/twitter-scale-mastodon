namespace java com.rpl.mastodon.data

typedef i64 AccountId
typedef i64 StatusId
typedef i64 Index
typedef i64 ListId
typedef i64 FilterId
typedef i64 FilterClauseId
typedef i64 ConversationId
typedef i64 Timestamp

enum FilterContext {
  Home = 1,
  Notifications = 2,
  Public = 3,
  Thread = 4,
  Account = 5
}

enum FilterAction {
  Warn = 1,
  Hide = 2
}

enum StatusVisibility {
  Public = 1,
  Unlisted = 2,
  Private = 3,
  Direct = 4
}

enum AttachmentKind {
  Image = 1,
  Video = 2
}

enum FanoutAction {
  Add = 1,
  Edit = 2,
  Remove = 3,
}

enum GlobalTimeline {
  Public = 1,
  PublicLocal = 2,
  PublicRemote = 3
}

struct KeyValuePair {
  1: required string key;
  2: required string value;
}

struct LocalAccount {
  1: required string privateKey;
}

struct RemoteAccount {
  1: required string mainUrl;
  2: required string inboxUrl;
  3: required string sharedInboxUrl;
}

union AccountContent {
  1: LocalAccount local;
  2: RemoteAccount remote;
}

struct Account {
  1: required string name;
  2: required string email;
  3: required string pwdHash;
  4: required string locale;
  5: required string uuid;
  6: required string publicKey;
  7: required AccountContent content;
  8: required Timestamp timestamp;
  9: optional string displayName;
  10: optional string bio;
  11: optional bool locked;
  12: optional bool bot;
  13: optional bool discoverable;
  14: optional AttachmentWithId header;
  15: optional AttachmentWithId avatar;
  16: optional list<KeyValuePair> fields;
  17: optional map<string, Marker> markers;
  18: optional map<string, string> preferences;
}

struct AccountMetadata {
  1: required i32 statusCount;
  2: required i32 followerCount;
  3: required i32 followeeCount;
  4: required bool isFollowedByRequester;
  5: required bool isFollowingRequester;
  6: optional Timestamp lastStatusTimestamp;
}

struct AccountWithId {
  1: required AccountId accountId;
  2: required Account account;
  3: required AccountMetadata metadata;
}

struct IndexedAccountWithId {
  1: required Index index;
  2: required AccountWithId accountWithId;
}

struct Follower {
  1: required AccountId accountId;
  2: required bool showBoosts;
  3: optional list<string> languages;
  4: optional string sharedInboxUrl;
}

union EditAccountField {
  1: string email;
  2: string pwdHash;
  3: string locale;
  4: string publicKey;
  5: AccountContent content;
  6: string displayName;
  7: string bio;
  8: bool locked;
  9: bool bot;
  10: bool discoverable;
  11: AttachmentWithId header;
  12: AttachmentWithId avatar;
  13: list<KeyValuePair> fields;
  14: map<string, Marker> markers;
  15: map<string, string> preferences;
}

struct EditAccount {
  1: required AccountId accountId;
  2: required list<EditAccountField> edits;
  3: required Timestamp timestamp;
}

struct AddAuthCode {
  1: required string code;
  2: required AccountId accountId;
}

struct RemoveAuthCode {
  1: required string code;
}

struct StatusPointer {
  1: required AccountId authorId;
  2: required StatusId statusId;
  3: optional bool shouldExclude;
}

struct PollVote {
  1: required AccountId accountId;
  2: required StatusPointer target;
  3: required set<i32> choices;
  4: required Timestamp timestamp;
}

struct FollowAccount {
  1: required AccountId accountId;
  2: required AccountId targetId;
  3: required Timestamp timestamp;
  4: optional bool showBoosts;
  5: optional bool notify;
  6: optional list<string> languages;
  7: optional string followerSharedInboxUrl;
}

struct FollowLockedAccount {
  1: required AccountId accountId;
  2: required AccountId requesterId;
  3: required Timestamp timestamp;
  4: optional bool showBoosts;
  5: optional bool notify;
  6: optional list<string> languages;
}

// Note that due to the partitioning used by the followAndBlockAccountDepot
// and the way that follow requests are accessed, `accountId` here refers
// to the *target* of the follow request, not the originator.
struct AcceptFollowRequest {
  1: required AccountId accountId;
  2: required AccountId requesterId;
  3: required Timestamp timestamp;
}

struct RejectFollowRequest {
  1: required AccountId accountId;
  2: required AccountId requesterId;
}

struct RemoveFollowAccount {
  1: required AccountId accountId;
  2: required AccountId targetId;
  3: required Timestamp timestamp;
  4: optional string followerSharedInboxUrl;
}

struct BlockAccount {
  1: required AccountId accountId;
  2: required AccountId targetId;
  3: required Timestamp timestamp;
}

struct RemoveBlockAccount {
  1: required AccountId accountId;
  2: required AccountId targetId;
  3: required Timestamp timestamp;
}

struct MuteAccountOptions {
  1: required bool muteNotifications;
  2: optional Timestamp expirationMillis;
}

struct MuteAccount {
  1: required AccountId accountId;
  2: required AccountId targetId;
  3: required MuteAccountOptions options;
  4: required Timestamp timestamp;
}

struct RemoveMuteAccount {
  1: required AccountId accountId;
  2: required AccountId targetId;
  3: required Timestamp timestamp;
}

struct FeatureAccount {
  1: required AccountId accountId;
  2: required AccountId targetId;
  3: required Timestamp timestamp;
}

struct RemoveFeatureAccount {
  1: required AccountId accountId;
  2: required AccountId targetId;
  3: required Timestamp timestamp;
}

struct FeatureHashtag {
  1: required AccountId accountId;
  2: required string hashtag;
  3: required Timestamp timestamp;
}

struct RemoveFeatureHashtag {
  1: required AccountId accountId;
  2: required string hashtag;
  3: required Timestamp timestamp;
}

struct IndexedFeatureHashtag {
  1: required Index index;
  2: required FeatureHashtag featureHashtag;
}

struct Note {
  1: required AccountId accountId;
  2: required AccountId targetId;
  3: required string note;
}

struct PollContent {
  1: required list<string> choices;
  2: required Timestamp expirationMillis;
  3: required bool multipleChoice;
}

struct Attachment {
  1: required AttachmentKind kind;
  2: required string path;
  3: required string description;
}

struct AttachmentWithId {
  1: required string uuid;
  2: required Attachment attachment;
}

struct NormalStatusContent {
  1: required string text;
  2: required StatusVisibility visibility;
  3: optional PollContent pollContent;
  4: optional list<AttachmentWithId> attachments;
  5: optional string sensitiveWarning;
}

struct ReplyStatusContent {
  1: required string text;
  2: required StatusVisibility visibility;
  3: required StatusPointer parent;
  4: optional PollContent pollContent;
  5: optional list<AttachmentWithId> attachments;
  6: optional string sensitiveWarning;
}

struct BoostStatusContent {
  1: required StatusPointer boosted;
}

union StatusContent {
  1: NormalStatusContent normal;
  2: ReplyStatusContent reply;
  3: BoostStatusContent boost;
}

struct Status {
  1: required AccountId authorId;
  2: required StatusContent content;
  3: required Timestamp timestamp;
  4: optional string remoteUrl;
  5: optional string language;
}

struct AddStatus {
  1: required string uuid;
  2: required Status status;
}

struct EditStatus {
  1: required StatusId statusId;
  2: required Status status;
}

struct RemoveStatus {
  1: required AccountId accountId;
  2: required StatusId statusId;
  3: required Timestamp timestamp;
}

struct StatusWithId {
  1: required StatusId statusId;
  2: required Status status;
}

struct RemoveStatusWithId {
  1: required StatusId statusId;
  2: required Status status;
}

struct RemoveFollowSuggestion {
  1: required AccountId accountId;
  2: required AccountId targetId;
}

typedef NormalStatusContent NormalStatusResultContent
typedef ReplyStatusContent ReplyStatusResultContent

struct BoostStatusResultContent {
  1: required StatusId statusId;
  2: required StatusResult status;
}

union StatusResultContent {
  1: NormalStatusResultContent normal;
  2: ReplyStatusResultContent reply;
  3: BoostStatusResultContent boost;
}

struct PollInfo {
  1: required i32 totalVoters;
  2: required set<i32> ownVotes;
  3: required map<i32, i32> voteCounts;
}

struct StatusMetadata {
  1: required list<MatchingFilter> filters;
  2: required bool favorited;
  3: required bool boosted;
  4: required bool muted;
  5: required bool bookmarked;
  6: required bool pinned;
  7: required i32 favoriteCount;
  8: required i32 boostCount;
  9: required i32 replyCount;
}

struct StatusResult {
  1: required AccountWithId author;
  2: required StatusResultContent content;
  3: required StatusMetadata metadata;
  4: required Timestamp timestamp;
  5: optional Timestamp editTimestamp;
  6: optional PollInfo pollInfo; // kept here because normal statuses, replies, and boosts can all have polls
  7: optional string remoteUrl;
}

struct ReviewItem {
  1: required string item;
  2: required Timestamp timestamp;
}

struct RemoveReviewItem {
  1: required string item;
  2: required Timestamp timestamp;
}

struct StatusResultWithId {
  1: required StatusId statusId;
  2: required StatusResult status;
}

struct IndexedStatusResultWithId {
  1: required Index index;
  2: required StatusResultWithId statusResultWithId;
}

struct StatusQueryResults {
  1: required list<StatusResultWithId> results;
  2: required map<string, AccountWithId> mentions;
  3: required bool reachedEnd;
  4: required bool refreshed;
  // not necessarily the last one in `results` since it could've been excluded
  5: optional StatusPointer lastStatusPointer;
}

struct StatusQueryResult {
  1: required StatusResultWithId result;
  2: required map<string, AccountWithId> mentions;
}

struct Conversation {
  1: required ConversationId conversationId;
  2: required bool unread;
  3: required list<AccountWithId> accounts;
  4: optional StatusQueryResult lastStatus;
}

struct IndexedConversation {
  1: required Index index;
  2: required Conversation conversation;
}

struct EditConversation {
  1: required AccountId accountId;
  2: required ConversationId conversationId;
  3: required bool unread;
}

struct RemoveConversation {
  1: required AccountId accountId;
  2: required ConversationId conversationId;
}

struct FollowHashtag {
  1: required AccountId accountId;
  2: required string token;
  3: required Timestamp timestamp;
}

struct RemoveFollowHashtag {
  1: required AccountId accountId;
  2: required string token;
  3: required Timestamp timestamp;
}

struct FollowerFanout {
  1: required AccountId authorId;
  2: required i64 nextIndex;
  3: required FanoutAction fanoutAction;
  4: required Status status;
  5: required i32 task;
}

struct ListFanout {
  1: required AccountId authorId;
  2: required i64 nextIndex;
  3: required Status status;
}

struct HashtagFanout {
  1: required AccountId authorId;
  2: required StatusId statusId;
  3: required string hashtag;
}

struct AccountList {
  1: required AccountId authorId;
  2: required string title;
  3: required string repliesPolicy;
  4: required Timestamp timestamp;
}

struct AccountListWithId {
  1: required ListId listId;
  2: required AccountList accountList;
}

struct RemoveAccountList {
  1: required ListId listId;
  2: required Timestamp timestamp;
}

struct AccountListMember {
  1: required ListId listId;
  2: required AccountId memberId;
  3: required Timestamp timestamp;
}

struct RemoveAccountListMember {
  1: required ListId listId;
  2: required AccountId memberId;
  3: required Timestamp timestamp;
}

struct Filter {
  1: required AccountId accountId;
  2: required string title;
  3: required set<FilterContext> contexts;
  4: required list<KeywordFilter> keywords;
  5: required set<StatusPointer> statuses;
  6: required FilterAction action;
  7: required Timestamp timestamp;
  8: optional i64 expirationMillis;
}

struct MatchingFilter {
  1: required FilterId filterId;
  2: required Filter filter;
  3: required list<KeywordFilter> keywordMatches;
  4: required bool statusFilterMatch;
}

struct KeywordFilter {
  1: required string word;
  2: required bool wholeWord;
}

struct FilterWithId {
  1: required FilterId filterId;
  2: required Filter filter;
}

struct AddFilter {
  1: required Filter filter;
  2: required string uuid;
}

struct RemoveFilter {
  1: required FilterId filterId;
  2: required AccountId accountId;
  3: required Timestamp timestamp;
}

struct UpdateKeyword {
  1: required string currentWord; // this is a compound id of filterid + keyword
  2: required string newWord;
  3: required bool wholeWord;
}

union EditFilterKeyword {
  1: UpdateKeyword updateKeyword;
  2: string destroyKeyword; // keyword id to remove
  3: KeywordFilter addKeyword;
}

struct EditFilter {
  1: required FilterId filterId;
  2: required AccountId accountId;
  3: required Timestamp timestamp;
  4: required list<EditFilterKeyword> keywords;
  5: optional string title;
  6: optional set<FilterContext> context;
  7: optional FilterAction action;
  8: optional i64 expirationMillis;
}

struct AddStatusToFilter {
  1: required FilterId filterId;
  2: required AccountId accountId;
  3: required StatusPointer target;
}

struct RemoveStatusFromFilter {
  1: required FilterId filterId;
  2: required AccountId accountId;
  3: required StatusPointer target;
}

struct BoostStatus {
  1: required string uuid;
  2: required AccountId accountId;
  3: required StatusPointer target;
  4: required Timestamp timestamp;
  5: optional string remoteUrl;
}

struct RemoveBoostStatus {
  1: required AccountId accountId;
  2: required StatusPointer target;
  3: required Timestamp timestamp;
}

struct FavoriteStatus {
  1: required AccountId accountId;
  2: required StatusPointer target;
  3: required Timestamp timestamp;
}

struct RemoveFavoriteStatus {
  1: required AccountId accountId;
  2: required StatusPointer target;
  3: required Timestamp timestamp;
}

struct BookmarkStatus {
  1: required AccountId accountId;
  2: required StatusPointer target;
  3: required Timestamp timestamp;
}

struct RemoveBookmarkStatus {
  1: required AccountId accountId;
  2: required StatusPointer target;
  3: required Timestamp timestamp;
}

struct MuteStatus {
  1: required AccountId accountId;
  2: required StatusPointer target;
  3: required Timestamp timestamp;
}

struct RemoveMuteStatus {
  1: required AccountId accountId;
  2: required StatusPointer target;
  3: required Timestamp timestamp;
}

struct PinStatus {
  1: required AccountId accountId;
  2: required StatusId statusId;
  3: required Timestamp timestamp;
}

struct RemovePinStatus {
  1: required AccountId accountId;
  2: required StatusId statusId;
  3: required Timestamp timestamp;
}

struct ProfileSearchRecord {
  1: set<string> otherProfileTerms;
  2: string username;
}

struct StatusSearchRecord {
  1: set<string> terms;
  2: AccountId accountId;
  3: StatusId statusId;
}

struct AccountRelationshipQueryResult {
  1: required AccountId accountId;
  2: required bool following;
  3: required bool showingBoosts;
  4: required bool notifying;
  5: required list<string> languages;
  6: required bool followedBy;
  7: required bool blocking;
  8: required bool blockedBy;
  9: required bool muting;
  10: required bool mutingNotifications;
  11: required bool requested;
  12: required bool domainBlocking;
  13: required bool endorsed;
  14: required string note;
}

struct QueryFilterOptions {
  1: required FilterContext filterContext;
  2: required bool excludeBlockedAndMuted;
}

struct DayBucket {
  1: required i32 uses;
  2: required i32 accounts;
}

struct ItemStats {
  1: required map<i64, DayBucket> dayBuckets;
  2: required i64 statusCount;
  3: required Timestamp latestStatusMillis;
  4: required bool isReviewed;
}

struct StatusResponseNotificationContent {
  1: required AccountId responderAccountId;
  2: required StatusPointer target;
}

union NotificationContent {
  1: StatusPointer mention;
  2: AccountId follow;
  3: AccountId followRequest;
  4: StatusResponseNotificationContent favorite;
  5: StatusResponseNotificationContent boost;
  6: StatusPointer pollComplete;
  7: StatusPointer boostedUpdate;
  8: StatusPointer followeeStatus;
}

struct Notification {
  1: required NotificationContent content;
  2: required Timestamp timestamp;
}

struct NotificationWithId {
  1: required i64 notificationId;
  2: required Notification notification;
}

struct DismissNotification {
  1: required AccountId accountId;
  2: optional i64 notificationId; // null for "dismiss all"
}

struct FeaturedHashtagInfo {
  1: required string hashtag;
  2: required i32 numStatuses;
  3: required Timestamp timestamp;
}

struct AddScheduledStatus {
  1: required string uuid;
  2: required Status status;
  3: required Timestamp publishMillis;
}

struct EditScheduledStatusPublishTime {
  1: required AccountId accountId;
  2: required i64 id;
  3: required Timestamp publishMillis;
  4: required Timestamp timestamp;
}

struct Marker {
  1: required string lastReadId; // must store the stringified id because that is how the client must receive it later
  2: required i32 version;
  3: required i64 timestamp;
}
