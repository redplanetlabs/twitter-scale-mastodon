package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.MastodonConfig;
import com.rpl.mastodon.MastodonHelpers;
import com.rpl.mastodon.data.*;
import com.rpl.mastodonapi.MastodonApiConfig;
import com.rpl.mastodonapi.MastodonApiHelpers;
import org.apache.commons.text.StringEscapeUtils;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class GetAccount {
    public String id;
    public String username;
    public String acct;
    public String display_name;
    public String note;
    public boolean locked;
    public boolean bot;
    public boolean discoverable;
    public boolean group;
    public String url;
    public String avatar;
    public String avatar_static;
    public String header;
    public String header_static;
    public static class Field {
        public String name;
        public String value;
        public String verified_at;

        public Field() { }

        public Field(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
    public List<Field> fields;
    public List<GetCustomEmoji> emojis;
    public static class Source {
        public String note = "";
        public List<Field> fields;
        public String privacy = "public";
        public boolean sensitive = false;
        public String language = "";
        public int follow_requests_count = 0;
    }
    public Source source;
    public String created_at;
    public String last_status_at;
    public int statuses_count;
    public int followers_count;
    public int following_count;

    public GetAccount() { }

    public GetAccount(AccountWithId accountWithId) {
        this(accountWithId.accountId, accountWithId.account, accountWithId.metadata);
    }

    public GetAccount(long accountId, Account account, AccountMetadata metadata) {
        this.id = MastodonHelpers.serializeAccountId(accountId);
        this.username = account.name;
        this.acct = account.name;
        this.display_name = account.isSetDisplayName() ? StringEscapeUtils.escapeHtml4(account.displayName) : "";
        this.note = account.isSetBio() ? StringEscapeUtils.escapeHtml4(account.bio) : "";
        this.locked = account.locked;
        this.bot = account.bot;
        this.discoverable = account.discoverable;

        if (account.content.isSetRemote()) this.url = account.content.getRemote().mainUrl;
        else this.url = MastodonConfig.FRONTEND_URL + "/@" + account.name;

        if (account.isSetFields()) this.fields = account.fields.stream().map(field -> new Field(StringEscapeUtils.escapeHtml4(field.key), StringEscapeUtils.escapeHtml4(field.value))).collect(Collectors.toList());
        else this.fields = new ArrayList<>();

        this.emojis = new ArrayList<>();
        this.source = new Source();
        this.source.note = this.note;
        this.source.fields = this.fields;

        if (MastodonApiConfig.S3_OPTIONS != null) {
            if (account.isSetAvatar() && !account.avatar.attachment.path.isEmpty()) {
                if (MastodonApiHelpers.isValidURL(account.avatar.attachment.path)) this.avatar = account.avatar.attachment.path;
                else this.avatar = String.format("%s/%s", MastodonApiConfig.S3_OPTIONS.url, account.avatar.attachment.path);

                this.avatar_static = this.avatar;
            }
            if (account.isSetHeader() && !account.header.attachment.path.isEmpty()) {
                if (MastodonApiHelpers.isValidURL(account.header.attachment.path)) this.header = account.header.attachment.path;
                else this.header = String.format("%s/%s", MastodonApiConfig.S3_OPTIONS.url, account.header.attachment.path);

                this.header_static = this.header;
            }
        } else {
            if (account.isSetAvatar() && !account.avatar.attachment.path.isEmpty()) {
                if (MastodonApiHelpers.isValidURL(account.avatar.attachment.path)) this.avatar = account.avatar.attachment.path;
                else this.avatar = String.format("%s/%s/%s", MastodonConfig.API_URL, MastodonApiConfig.STATIC_FILE_URL_PATH_NAME, account.avatar.attachment.path);

                this.avatar_static = this.avatar;
            }
            if (account.isSetHeader() && !account.header.attachment.path.isEmpty()) {
                if (MastodonApiHelpers.isValidURL(account.header.attachment.path)) this.header = account.header.attachment.path;
                else this.header = String.format("%s/%s/%s", MastodonConfig.API_URL, MastodonApiConfig.STATIC_FILE_URL_PATH_NAME, account.header.attachment.path);

                this.header_static = this.header;
            }
        }

        // default images
        if (this.header == null) {
            this.header = MastodonConfig.API_URL + "/missing_header.png";
            this.header_static = MastodonConfig.API_URL + "/missing_header.png";
        }
        if (this.avatar == null) {
            this.avatar = MastodonConfig.API_URL + "/missing_avatar.png";
            this.avatar_static = MastodonConfig.API_URL + "/missing_avatar.png";
        }

        this.created_at = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(account.timestamp));

        if (metadata != null) {
            if (metadata.isSetLastStatusTimestamp()) this.last_status_at = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(metadata.lastStatusTimestamp));
            this.statuses_count = metadata.statusCount;
            this.followers_count = metadata.followerCount;
            this.following_count = metadata.followeeCount;
        }
    }
}
