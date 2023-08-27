package com.rpl.mastodonapi.pojos;

import java.util.*;

public class GetTag {
    public String name;
    public String url;
    public static class HistoryItem {
        public String day; // unix timestamp
        public String uses; // counted usage
        public String accounts; // number of accounts using the tag

        public HistoryItem() { }

        public HistoryItem(long day, int uses, int accounts) {
            this.day = (day * 60 * 60 * 24) + "";
            this.uses = uses+"";
            this.accounts = accounts+"";
        }
    }
    public List<HistoryItem> history = new ArrayList<>();
    public Boolean following; // optional
    public String id;
    public boolean trendable;
    public boolean usable;
    public boolean requires_review;

    public GetTag() { }

    public GetTag(String name) {
        this.name = name;
        this.url = "/v1/timelines/tag/" + name;
        this.id = name;
    }

    public GetTag(String name, String url, String id) {
        this.name = name;
        this.url = url;
        this.id = id;
    }
}
