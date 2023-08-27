package com.rpl.mastodonapi.pojos;

public class PutScheduledStatus {
    public String scheduled_at;

    public PutScheduledStatus() { }

    public PutScheduledStatus(String scheduledAt) {
        this.scheduled_at = scheduledAt;
    }
}
