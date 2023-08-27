package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.*;
import com.rpl.mastodon.data.*;
import com.rpl.mastodonapi.*;
import org.apache.commons.text.StringEscapeUtils;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class GetStatus {
    public String id;
    public String uri;
    public String created_at;
    public GetAccount account;
    public String content;
    public String visibility;
    public boolean sensitive;
    public String spoiler_text;
    public List<GetAttachment> media_attachments;
    public static class Mention {
        public String id;
        public String username;
        public String url;
        public String acct;

        public Mention() { }

        public Mention(AccountWithId accountWithId) {
            this.id = MastodonHelpers.serializeAccountId(accountWithId.accountId);
            this.username = accountWithId.account.name;
            this.url = MastodonConfig.API_URL + "/@" + accountWithId.account.name;
            this.acct = accountWithId.account.name;
        }
    }
    public List<Mention> mentions;
    public List<GetTag> tags;
    public List<GetCustomEmoji> emojis;
    public int reblogs_count;
    public int favourites_count;
    public int replies_count;
    public String url;
    public String in_reply_to_id;
    public String in_reply_to_account_id;
    public GetStatus reblog;
    public GetPoll poll;
    public GetPreviewCard card;
    public String language;
    public String text;
    public String edited_at;
    public boolean favourited;
    public boolean reblogged;
    public boolean muted;
    public boolean bookmarked;
    public boolean pinned;
    public static class FilterResult {
        public GetFilter filter;
        public List<String> keyword_matches;
        public String status_matches;

        public FilterResult() { }

        public FilterResult(GetFilter filter, List<String> keyword_matches) {
            this.filter = filter;
            this.keyword_matches = keyword_matches;
        }
    }
    public List<FilterResult> filtered;

    public GetStatus() { }

    public GetStatus(StatusQueryResult statusQueryResult) {
        this(statusQueryResult.result, statusQueryResult.mentions);
    }

    public GetStatus(StatusResultWithId result, Map<String, AccountWithId> mentions) {
        this(new StatusPointer(result.status.author.accountId, result.statusId), result.status, mentions);
    }

    public GetStatus(StatusPointer statusPointer, StatusResult status, Map<String, AccountWithId> mentions) {
        this.id = MastodonHelpers.serializeStatusPointer(statusPointer);
        this.account = new GetAccount(status.author);
        this.favourited = status.metadata.favorited;
        this.reblogged = status.metadata.boosted;
        this.muted = status.metadata.muted;
        this.bookmarked = status.metadata.bookmarked;
        this.pinned = status.metadata.pinned;
        this.favourites_count = status.metadata.favoriteCount;
        this.reblogs_count = status.metadata.boostCount;
        this.replies_count = status.metadata.replyCount;
        this.visibility = MastodonApiHelpers.createStatusVisibility(MastodonHelpers.getStatusResultVisibility(status));
        this.spoiler_text = "";
        this.media_attachments = new ArrayList<>();
        this.mentions = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.emojis = new ArrayList<>();

        // `mentions` can contain accounts from other statuses that were
        // in the same query result, so we need to filter them down
        Map<String, AccountWithId> localMentions = new HashMap<>();

        if (status.content.isSetNormal()) {
            NormalStatusContent content = status.content.getNormal();
            if (status.isSetPollInfo() && content.isSetPollContent()) this.poll = new GetPoll(this.id, status.pollInfo, content.pollContent);
        } else if (status.content.isSetReply()) {
            ReplyStatusContent content = status.content.getReply();
            if (status.isSetPollInfo() && content.isSetPollContent()) this.poll = new GetPoll(this.id, status.pollInfo, content.pollContent);
            this.in_reply_to_account_id = MastodonHelpers.serializeAccountId(content.parent.authorId);
            this.in_reply_to_id = MastodonHelpers.serializeStatusPointer(content.parent);
            // add parent author to mentions
            mentions.values().stream().filter(awid -> awid.accountId == content.parent.authorId).findFirst().ifPresent(awid -> localMentions.put(awid.account.name, awid));
        } else if (status.content.isSetBoost()) {
            BoostStatusResultContent content = status.content.getBoost();
            this.reblog = new GetStatus(new StatusPointer(content.status.author.accountId, content.statusId), content.status, mentions);
        }

        if (status.content.isSetBoost()) this.filtered = this.reblog.filtered;
        else {
            ArrayList<FilterResult> filterResults = new ArrayList<>();
            for (MatchingFilter matchingFilter : status.metadata.filters) {
                List<String> keywordMatches = matchingFilter.keywordMatches.stream().map(match -> match.word).collect(Collectors.toList());
                filterResults.add(new FilterResult(new GetFilter(matchingFilter.filterId, matchingFilter.filter), keywordMatches));
            }
            this.filtered = filterResults;
        }

        String originalContent = "";

        if (status.content.isSetNormal()) {
            NormalStatusContent content = status.content.getNormal();
            originalContent = content.text;
            if (content.isSetAttachments()) this.media_attachments = content.attachments.stream().map(GetAttachment::new).collect(Collectors.toList());
            if (content.isSetSensitiveWarning()) {
                this.sensitive = true;
                this.spoiler_text = content.sensitiveWarning;
            }
        } else if (status.content.isSetReply()) {
            ReplyStatusContent content = status.content.getReply();
            originalContent = content.text;
            if (content.isSetAttachments()) this.media_attachments = content.attachments.stream().map(GetAttachment::new).collect(Collectors.toList());
            if (content.isSetSensitiveWarning()) {
                this.sensitive = true;
                this.spoiler_text = content.sensitiveWarning;
            }
        }

        this.content = "";
        for (Token token : Token.parseTokens(originalContent)) {
            String tokenContent = StringEscapeUtils.escapeHtml4(token.content);
            switch (token.kind) {
                case BOUNDARY:
                case WORD:
                    this.content += tokenContent;
                    break;
                case LINK:
                    this.content += String.format("<a href=\"%s\">%s</a>", tokenContent, tokenContent);
                    break;
                case HASHTAG:
                    this.content += String.format("<a href=\"%s/tags/%s\">%s</a>", MastodonConfig.FRONTEND_URL, tokenContent, tokenContent);
                    this.tags.add(new GetTag(tokenContent));
                    break;
                case MENTION:
                    if (mentions.containsKey(tokenContent)) {
                        this.content += String.format("<a href=\"%s/@%s\">%s</a>", MastodonConfig.FRONTEND_URL, tokenContent, tokenContent);
                        localMentions.put(tokenContent, mentions.get(tokenContent));
                    } else this.content += tokenContent;
                    break;
                case REMOTE_MENTION:
                    String[] parts = tokenContent.split("@");
                    if (mentions.containsKey(tokenContent) && parts.length == 2) {
                        this.content += String.format("<a href=\"%s/@%s\">%s</a>", MastodonConfig.FRONTEND_URL, tokenContent, tokenContent);
                        localMentions.put(tokenContent, mentions.get(tokenContent));
                    } else this.content += tokenContent;
                    break;
            }
        }

        for (AccountWithId awid : localMentions.values()) this.mentions.add(new Mention(awid));

        this.created_at = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(status.timestamp));
        if (status.isSetEditTimestamp()) this.edited_at = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(status.editTimestamp));
        this.url = MastodonConfig.FRONTEND_URL + "/@" + status.author.account.name + "/" + this.id;
        this.uri = MastodonConfig.API_URL + "/users/" + status.author.account.name + "/statuses/" + this.id;
        this.language = "en";
    }
}
