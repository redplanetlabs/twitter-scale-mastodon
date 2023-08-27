package com.rpl.mastodonapi.pojos;

public class PostFilterStatusParams {
    public String status_id;

    public PostFilterStatusParams() {}

    public PostFilterStatusParams(String statusId) {
        this.status_id = statusId;
    }
}
