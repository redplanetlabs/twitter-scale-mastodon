package com.rpl.mastodonapi.pojos;

public class GetFeatureHashtag {
    public String id;
    public String name;
    public String url;
    public int statuses_count;
    public String last_status_at;

    public GetFeatureHashtag() { }

    public GetFeatureHashtag(String name, int statusCount, long lastStatusAt) {
        this.id = name;
        this.name = name;
        this.statuses_count = statusCount;
        this.last_status_at = "" + lastStatusAt;
    }
}
