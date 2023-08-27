package com.rpl.mastodonapi.pojos;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.HashSet;

public class PostList {
    public String title;
    public String replies_policy = "list";

    public PostList() { }

    public PostList(String title, String replies_policy) {
        this.title = title;
        this.replies_policy = replies_policy;
    }

    public static final HashSet<String> REPLIES_POLICY = new HashSet<>(Arrays.asList("followed", "list", "none"));

    @JsonProperty("replies_policy")
    public void setRepliesPolicy(String value) {
        if (!REPLIES_POLICY.contains(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        this.replies_policy = value;
    }
}
