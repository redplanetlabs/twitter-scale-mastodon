package com.rpl.mastodonapi.pojos;

import java.util.List;

public class PostPollVote {
    public List<String> choices;

    public PostPollVote() { }

    public PostPollVote(List<String> choices) {
        this.choices = choices;
    }
}
