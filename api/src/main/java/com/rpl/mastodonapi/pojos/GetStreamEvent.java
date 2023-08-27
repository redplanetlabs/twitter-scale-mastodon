package com.rpl.mastodonapi.pojos;

import java.util.*;

public class GetStreamEvent {
    public List<String> stream;
    public String event;
    public String payload;

    public GetStreamEvent() { }

    public GetStreamEvent(String stream, String event, String payload) {
        this.stream = Arrays.asList(stream);
        this.event = event;
        this.payload = payload;
    }
}
