package com.rpl.mastodonapi.pojos;

import java.util.List;

public class GetFamiliarFollowers {
    public String id;
    public List<GetAccount> accounts;

    public GetFamiliarFollowers () { }

    public GetFamiliarFollowers(String id, List<GetAccount> accounts) {
        this.id = id;
        this.accounts = accounts;
    }
}
