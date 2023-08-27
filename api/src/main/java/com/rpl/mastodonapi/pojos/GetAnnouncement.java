package com.rpl.mastodonapi.pojos;

import java.util.List;

public class GetAnnouncement {
    public String id;
    public String content;
    public String starts_at;
    public String ends_at;
    public boolean published;
    public boolean all_day;
    public String published_at;
    public String updated_at;
    public boolean read;
    public static class Account {
        public String id;
        public String username;
        public String url;
        public String acct;

        public Account() { }

        public Account(String id, String username, String url, String acct) {
            this.id = id;
            this.username = username;
            this.url = url;
            this.acct = acct;
        }
    }
    public List<Account> mentions;
    public static class Status {
        public String id;
        public String url;

        public Status() { }

        public Status(String id, String url) {
            this.id = id;
            this.url = url;
        }
    }
    public List<Status> statuses;
    public List<GetTag> tags;
    public List<GetCustomEmoji> emojis;
    public List<GetReaction> reactions;

    public GetAnnouncement() { }
}
