package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.data.AccountWithId;

public class GetSuggestion {
    public String source;
    public GetAccount account;

    public GetSuggestion() { }

    public GetSuggestion(String source, AccountWithId accountWithId) {
        this.source = source;
        this.account = new GetAccount(accountWithId);
    }
}
