package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.data.*;
import com.rpl.mastodonapi.MastodonApiHelpers;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GetStatusEdit {
    public String content;
    public String spoiler_text;
    public boolean sensitive;
    public String created_at;
    public GetAccount account;
    public GetPoll poll;
    public List<GetAttachment> media_attachments;
    public List<GetCustomEmoji> emojis;

    public GetStatusEdit() { }

    public GetStatusEdit(AccountWithId accountWithId, Status status) {
        this.content = MastodonApiHelpers.getStatusContentText(status.content);
        this.spoiler_text = "";
        this.created_at = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(status.timestamp));
        this.account = new GetAccount(accountWithId);
        this.media_attachments = new ArrayList<>();
        this.emojis = new ArrayList<>();

        if (status.content.isSetNormal()) {
            NormalStatusContent content = status.content.getNormal();
            if (content.isSetAttachments()) this.media_attachments = content.attachments.stream().map(GetAttachment::new).collect(Collectors.toList());
            if (content.isSetSensitiveWarning()) {
                this.sensitive = true;
                this.spoiler_text = content.sensitiveWarning;
            }
        } else if (status.content.isSetReply()) {
            ReplyStatusContent content = status.content.getReply();
            if (content.isSetAttachments()) this.media_attachments = content.attachments.stream().map(GetAttachment::new).collect(Collectors.toList());
            if (content.isSetSensitiveWarning()) {
                this.sensitive = true;
                this.spoiler_text = content.sensitiveWarning;
            }
        }
    }
}
