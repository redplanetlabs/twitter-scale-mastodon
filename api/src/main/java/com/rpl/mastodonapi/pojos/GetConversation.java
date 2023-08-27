package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.data.Conversation;
import com.rpl.mastodonapi.MastodonApiHelpers;

import java.util.List;

public class GetConversation {
    public String id;
    public boolean unread;
    public List<GetAccount> accounts;
    public GetStatus last_status;

    public GetConversation() { }

    public GetConversation(Conversation convo) {
        this.id = convo.conversationId+"";
        this.unread = convo.unread;
        this.accounts = MastodonApiHelpers.createGetAccounts(convo.accounts);
        if (convo.lastStatus != null) this.last_status = new GetStatus(convo.lastStatus);
    }
}
