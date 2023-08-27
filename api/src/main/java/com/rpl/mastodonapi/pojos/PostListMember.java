package com.rpl.mastodonapi.pojos;

import java.util.List;

public class PostListMember {
    public List<String> account_ids;

    public PostListMember() { }

    public PostListMember(List<String> account_ids) {
        this.account_ids = account_ids;
    }
}
