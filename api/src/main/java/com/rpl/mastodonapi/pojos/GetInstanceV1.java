package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.MastodonConfig;
import com.rpl.mastodonapi.MastodonApiConfig;

import java.util.*;

public class GetInstanceV1 {
    public String uri = MastodonConfig.API_DOMAIN;
    public String title = "Red Planet Labs Mastodon";
    public String short_description = "";
    public String description = "";
    public String email = "";
    public String version = "4.0.0"; // necessary to pretend backend is mastodon
    public Map<String, String> urls = new HashMap(){{
        put("streaming_api", MastodonConfig.API_WEB_SOCKET_URL);
    }};
    public static class Stats {
        public int user_count;
        public int status_count;
        public int domain_count;
    }
    public Stats stats = new Stats();
    public List<String> languages = Arrays.asList("en");
    public boolean registrations = true;
    public boolean approval_required = false;
    public boolean invites_enabled = false;

    public int max_toot_chars = MastodonApiConfig.MAX_STATUS_LENGTH;
}
