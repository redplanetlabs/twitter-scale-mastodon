package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.MastodonHelpers;
import com.rpl.mastodon.data.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class GetNotification {
    public String id;
    public String type;
    public String created_at;
    public GetAccount account;
    public GetStatus status;
    public GetReport report;

    public static class Bundle {
        NotificationWithId notificationWithId;
        AccountWithId accountWithId;
        StatusQueryResult statusQueryResult;

        public Bundle(NotificationWithId notificationWithId, AccountWithId accountWithId, StatusQueryResult statusQueryResult) {
            this.notificationWithId = notificationWithId;
            this.accountWithId = accountWithId;
            this.statusQueryResult = statusQueryResult;
        }
    }

    public GetNotification() { }

    public GetNotification(Bundle bundle) {
        this.id = MastodonHelpers.serializeNotificationId(bundle.notificationWithId.notificationId, bundle.notificationWithId.notification.timestamp);
        this.type = MastodonHelpers.getTypeFromNotificationContent(bundle.notificationWithId.notification.content);
        this.created_at = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(bundle.notificationWithId.notification.timestamp));
        this.account = new GetAccount(bundle.accountWithId);
        if (bundle.statusQueryResult != null) this.status = new GetStatus(bundle.statusQueryResult);
    }
}
