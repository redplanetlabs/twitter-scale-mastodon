package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.data.AccountRelationshipQueryResult;

import java.util.List;

public class GetRelationship {
    public String id;
    public boolean following;
    public boolean showing_reblogs;
    public boolean notifying;
    public List<String> languages;
    public boolean followed_by;
    public boolean blocking;
    public boolean blocked_by;
    public boolean muting;
    public boolean muting_notifications;
    public boolean requested;
    public boolean domain_blocking;
    public boolean endorsed;
    public String note;

    public GetRelationship() { }

    public GetRelationship(String accountId, AccountRelationshipQueryResult result) {
        this.id = accountId;
        this.following = result.following;
        this.showing_reblogs = result.showingBoosts;
        this.notifying = result.notifying;
        this.languages = result.languages;
        this.followed_by = result.followedBy;
        this.blocking = result.blocking;
        this.blocked_by = result.blockedBy;
        this.muting = result.muting;
        this.muting_notifications = result.mutingNotifications;
        this.requested = result.requested;
        this.domain_blocking = result.domainBlocking;
        this.endorsed = result.endorsed;
        this.note = result.note;
    }
}
