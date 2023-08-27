package com.rpl.mastodonapi.pojos;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PutList {
    public String title;
    public String replies_policy = "list";

    public PutList() { }

    public PutList(String title, String replies_policy) {
        this.title = title;
        this.replies_policy = replies_policy;
    }

    @JsonProperty("replies_policy")
    public void setRepliesPolicy(String value) {
        if (!PostList.REPLIES_POLICY.contains(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        this.replies_policy = value;
    }
}
