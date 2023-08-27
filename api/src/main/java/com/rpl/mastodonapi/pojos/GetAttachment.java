package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.MastodonConfig;
import com.rpl.mastodon.data.*;
import com.rpl.mastodonapi.MastodonApiConfig;

import java.util.Map;

public class GetAttachment {
    public String id;
    public String type; // unknown, image, gifv, video, audio
    public String url;
    public String preview_url;
    public String remote_url; // nullable
    public Map meta;
    public String description; // nullable
    public String blurhash;

    public GetAttachment() { }

    public GetAttachment(AttachmentWithId attachmentWithId) {
        Attachment attachment = attachmentWithId.attachment;
        this.id = attachmentWithId.uuid;
        switch (attachment.kind) {
            case Image:
                this.type = "image";
                break;
            case Video:
                this.type = "video";
                break;
        }
        if (MastodonApiConfig.S3_OPTIONS != null) {
            this.url = String.format("%s/%s",
                    MastodonApiConfig.S3_OPTIONS.url,
                    attachment.path
            );
        } else {
            this.url = String.format("%s/%s/%s",
                    MastodonConfig.API_URL,
                    MastodonApiConfig.STATIC_FILE_URL_PATH_NAME,
                    attachment.path
            );
        }
        this.preview_url = this.url;
        this.description = attachment.description;
    }
}
