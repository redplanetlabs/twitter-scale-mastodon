package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.MastodonConfig;
import com.rpl.mastodonapi.MastodonApiConfig;

import java.util.*;

public class GetInstance {
    public String domain = MastodonConfig.API_DOMAIN;
    public String title = "Red Planet Labs Mastodon";
    public String version = "4.0.0"; // necessary to pretend backend is mastodon
    public String source_url = "";
    public String description = "";
    public List<String> languages = Arrays.asList("en");
    public static class Registrations {
        public boolean enabled;
        public boolean approval_required;
        public String message;

        public Registrations(boolean enabled, boolean approval_required) {
            this.enabled = enabled;
            this.approval_required = approval_required;
        }
    }
    public Registrations registrations = new Registrations(true, false);

    public Map<String, String> urls = new HashMap(){{
        put("streaming_api", MastodonConfig.API_WEB_SOCKET_URL);
    }};
    public int max_toot_chars = MastodonApiConfig.MAX_STATUS_LENGTH;
}
