package com.rpl.mastodonapi.pojos;

import java.util.List;

public class GetContext {
    public List<GetStatus> ancestors;
    public List<GetStatus> descendants;

    public GetContext() { }

    public GetContext(List<GetStatus> ancestors, List<GetStatus> descendants) {
        this.ancestors = ancestors;
        this.descendants = descendants;
    }
}
