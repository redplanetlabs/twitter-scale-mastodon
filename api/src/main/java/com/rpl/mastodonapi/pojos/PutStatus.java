package com.rpl.mastodonapi.pojos;

import java.util.ArrayList;
import java.util.List;

public class PutStatus {
    public String status;
    public List<String> media_ids = new ArrayList<>();
    public PostStatus.Poll poll;
    public String spoiler_text;
    public Boolean sensitive;
    public String language;

    public PutStatus() { }

    public PutStatus(String status) {
        this.status = status;
    }
}
