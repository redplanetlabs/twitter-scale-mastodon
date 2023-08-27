package com.rpl.mastodonapi.pojos;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

public class PostStatus {
    public String status;
    public List<String> media_ids = new ArrayList<>();
    public static class Poll {
        public long expires_in;
        public boolean multiple;
        public List<String> options;
    }
    public Poll poll;
    public String in_reply_to_id;
    public String spoiler_text;
    public Boolean sensitive;
    public String visibility;
    public String language;
    public String scheduled_at;

    public PostStatus() { }

    public PostStatus(String status, String in_reply_to_id, String visibility) {
        this.status = status;
        this.in_reply_to_id = in_reply_to_id;
        this.visibility = visibility;
    }

    public PostStatus(String status, String in_reply_to_id, String visibility, String scheduled_at) {
        this.status = status;
        this.in_reply_to_id = in_reply_to_id;
        this.visibility = visibility;
        this.scheduled_at = scheduled_at;
    }

    public static final HashSet<String> VISIBILITY = new HashSet<>(Arrays.asList("public", "unlisted", "private", "direct"));

    @JsonProperty("visibility")
    public void setVisibility(String value) {
        if (!VISIBILITY.contains(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        this.visibility = value;
    }
}
