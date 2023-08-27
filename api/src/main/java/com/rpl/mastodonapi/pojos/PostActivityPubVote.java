package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.MastodonConfig;

import java.util.UUID;

public class PostActivityPubVote {
    public String id;
    public String type;
    public String name;
    public String attributedTo;
    public String inReplyTo;
    public String to;


    public PostActivityPubVote() { }

    public PostActivityPubVote(String name, String attributedTo, String inReplyTo, String to) {
        this.id = String.format("%s/%s", MastodonConfig.API_URL, UUID.randomUUID());
        this.type = "Note";
        this.name = name;
        this.attributedTo = attributedTo;
        this.inReplyTo = inReplyTo;
        this.to = to;
    }
}
