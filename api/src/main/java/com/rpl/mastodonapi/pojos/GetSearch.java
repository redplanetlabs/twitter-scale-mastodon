package com.rpl.mastodonapi.pojos;

import java.util.List;

public class GetSearch {
    public List<GetAccount> accounts;
    public List<GetStatus> statuses;
    public List<GetTag> hashtags;

    public GetSearch() { }

    public GetSearch(List<GetAccount> accounts, List<GetStatus> statuses, List<GetTag> hashtags) {
        this.accounts = accounts;
        this.statuses = statuses;
        this.hashtags = hashtags;
    }
}
