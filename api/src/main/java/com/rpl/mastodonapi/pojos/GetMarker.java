package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.data.Marker;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class GetMarker {
    public String last_read_id;
    public Integer version;
    public String updated_at;

    public GetMarker() { }

    public GetMarker(Marker marker) {
        this.last_read_id = marker.lastReadId;
        this.version = marker.version;
        this.updated_at = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(marker.timestamp));
    }
}
