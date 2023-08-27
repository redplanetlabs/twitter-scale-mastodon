package com.rpl.mastodonapi.pojos;

import com.rpl.mastodonapi.MastodonApiMetrics;

import java.time.LocalDateTime;
import java.util.Map;

public class GetMetrics {
    public Map<LocalDateTime, MastodonApiMetrics.Metrics> hourly;

    public GetMetrics(Map<LocalDateTime, MastodonApiMetrics.Metrics> hourly) {
        this.hourly = hourly;
    }
}
