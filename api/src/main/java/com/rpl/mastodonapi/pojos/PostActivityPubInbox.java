package com.rpl.mastodonapi.pojos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rpl.mastodon.MastodonConfig;

import java.util.UUID;

public class PostActivityPubInbox<T> {
    @JsonProperty("@context")
    public Object context;
    public String id;
    public String type;
    public String actor;
    public T object;

    public PostActivityPubInbox() { }

    public PostActivityPubInbox(String type, String actor, T object) {
        this.context = "https://www.w3.org/ns/activitystreams";
        this.id = String.format("%s/%s", MastodonConfig.API_URL, UUID.randomUUID());
        this.type = type;
        this.actor = actor;
        this.object = object;
    }
}
