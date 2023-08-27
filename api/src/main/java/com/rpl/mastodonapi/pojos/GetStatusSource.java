package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.MastodonHelpers;
import com.rpl.mastodon.data.*;
import com.rpl.mastodonapi.MastodonApiHelpers;

public class GetStatusSource {
    public String id;
    public String text;
    public String spoiler_text;

    public GetStatusSource() { }

    public GetStatusSource(StatusQueryResult statusQueryResult) {
        this.id = MastodonHelpers.serializeStatusPointer(new StatusPointer(statusQueryResult.result.status.author.accountId, statusQueryResult.result.statusId));
        this.text = MastodonApiHelpers.getStatusResultContentText(statusQueryResult.result.status.content);
        this.spoiler_text = "";
    }
}
