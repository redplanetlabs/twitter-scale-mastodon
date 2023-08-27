package com.rpl.mastodonapi.pojos;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import com.rpl.mastodon.MastodonHelpers;
import com.rpl.mastodon.data.*;
import com.rpl.mastodonapi.MastodonApiHelpers;

public class GetScheduledStatus {
    public String id;
    public List<GetAttachment> media_attachments;
    public static class StatusParams {
        public String text;
        public List<String> media_ids = new ArrayList<>();
        public static class PollParams {
            public long expires_in;
            public boolean multiple;
            public List<String> options;
        }
        public PollParams poll;
        public String in_reply_to_id;
        public String spoiler_text;
        public Boolean sensitive;
        public String visibility;
        public String language;
        public String scheduled_at;
        public String application_id;
        public String idempotency;

        public StatusParams(Status status) {
            PollContent pollContent;
            List<AttachmentWithId> attachments;
            StatusVisibility visibility;
            if (status.content.isSetNormal()) {
                NormalStatusContent content = status.content.getNormal();
                this.text = content.text;
                attachments = content.attachments;
                pollContent = content.pollContent;
                visibility = content.visibility;
            } else if (status.content.isSetReply()) {
                ReplyStatusContent content = status.content.getReply();
                this.text = content.text;
                attachments = content.attachments;
                pollContent = content.pollContent;
                visibility = content.visibility;
                this.in_reply_to_id = MastodonHelpers.serializeStatusPointer(content.parent);
            } else if (status.content.isSetBoost()) throw new IllegalArgumentException("Cannot schedule a boost");
            else throw new IllegalArgumentException("Unhandeled status union type: " + status.content.toString());

            this.visibility = MastodonApiHelpers.createStatusVisibility(visibility);
            if (pollContent != null) {
                PollParams poll = new PollParams();
                poll.expires_in = pollContent.expirationMillis;
                poll.multiple = pollContent.multipleChoice;
                poll.options = pollContent.choices;

                this.poll = poll;
            }

            if (attachments != null) this.media_ids = attachments.stream().map(attachment -> attachment.uuid).collect(Collectors.toList());
        }

        public StatusParams() { }
    }
    public StatusParams params;
    public String scheduled_at;

    public GetScheduledStatus() { }

    public GetScheduledStatus(String id, ArrayList<GetAttachment> mediaAttachments, StatusParams params, String scheduledAt) {
      this.id = id;
      this.media_attachments = mediaAttachments;
      this.params = params;
      this.scheduled_at = scheduledAt;
    }

    public GetScheduledStatus(StatusWithId scheduledStatus) {
      this.id = MastodonHelpers.serializeStatusPointer(new StatusPointer(scheduledStatus.status.authorId, scheduledStatus.statusId));
      this.scheduled_at = Instant.ofEpochMilli(scheduledStatus.status.timestamp).toString();

      List<AttachmentWithId> attachments = null;
      if (scheduledStatus.status.content.isSetNormal()) attachments = scheduledStatus.status.content.getNormal().attachments;
      else if (scheduledStatus.status.content.isSetReply()) attachments = scheduledStatus.status.content.getReply().attachments;

      if (attachments == null) attachments = new ArrayList<>();
      this.media_attachments = attachments.stream().map(GetAttachment::new).collect(Collectors.toList());

      this.params = new StatusParams(scheduledStatus.status);
    }
}
