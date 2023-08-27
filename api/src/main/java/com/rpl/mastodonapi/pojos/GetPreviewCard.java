package com.rpl.mastodonapi.pojos;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class GetPreviewCard {
    public String url = "";
    public String title = "";
    public String description = "";
    public String type = "";
    public String author_name = "";
    public String author_url = "";
    public String provider_name = "";
    public String provider_url = "";
    public String html = "";
    public int width;
    public int height;
    public String image = "";
    public String embed_url = "";
    public String blurhash = "";
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

    public GetPreviewCard() { }

    public GetPreviewCard(String url, String type) {
        this.url = url;
        this.type = type;
        try {
            URI uri = new URI(url);
            this.title = uri.getHost() + uri.getPath();
        } catch (URISyntaxException e) {}
    }
}
