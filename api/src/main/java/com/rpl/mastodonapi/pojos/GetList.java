package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.data.AccountListWithId;

public class GetList {
    public String id;
    public String title;
    public String replies_policy;

    public GetList() { }

    public GetList(AccountListWithId accountListWithId) {
        this.id = accountListWithId.listId+"";
        this.title = accountListWithId.accountList.title;
        this.replies_policy = accountListWithId.accountList.repliesPolicy;
    }
}
