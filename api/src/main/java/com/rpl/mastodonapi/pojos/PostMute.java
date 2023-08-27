package com.rpl.mastodonapi.pojos;

public class PostMute {
    public boolean notifications = true;
    public Long duration;

    public PostMute() { }

    public PostMute(boolean notifications, Long duration) {
        this.notifications = notifications;
        this.duration = duration;
    }
}
