package com.rpl.mastodonapi.pojos;

import java.util.List;

public class GetFilterV1 {
    public String id;
    public String text;
    public List<String> context;
    public String expires_at;
    public boolean irreversible;
    public boolean whole_word;

    public GetFilterV1() { }
}
