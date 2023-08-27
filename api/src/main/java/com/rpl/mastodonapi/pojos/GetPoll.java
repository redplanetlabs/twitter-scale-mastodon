package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.data.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class GetPoll {
    public String id;
    public String expires_at;
    public boolean expired;
    public boolean multiple;
    public int votes_count;
    public Integer voters_count; // nullable
    public static class Option {
        public String title;
        public Integer votes_count; // nullable

        public Option() { }

        public Option(String title, Integer votes_count) {
            this.title = title;
            this.votes_count = votes_count;
        }
    }
    public List<Option> options;
    public List<GetCustomEmoji> emojis;
    public boolean voted;
    public Set<Integer> own_votes;

    public GetPoll() { }

    public GetPoll(String id, PollInfo info, PollContent poll) {
        this.id = id;
        this.votes_count = info.voteCounts.values().stream().reduce(0, Integer::sum);
        this.voters_count = info.totalVoters;
        this.voted = info.ownVotes.size() > 0;
        this.own_votes = info.ownVotes;
        this.options = new ArrayList<>();
        for (int i = 0; i < poll.choices.size(); i++) {
            this.options.add(new GetPoll.Option(poll.choices.get(i), info.voteCounts.getOrDefault(i, 0)));
        }
        this.expires_at = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(poll.expirationMillis));
        this.expired = System.currentTimeMillis() > poll.expirationMillis;
        this.multiple = poll.multipleChoice;
        this.emojis = new ArrayList<>();
    }
}
