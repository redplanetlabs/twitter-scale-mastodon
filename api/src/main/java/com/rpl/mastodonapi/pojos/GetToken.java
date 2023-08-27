package com.rpl.mastodonapi.pojos;

public class GetToken {
    public String access_token;
    public String token_type = "Bearer";
    public String scope;
    public long created_at;

    public GetToken() { }

    public GetToken(String access_token, String scope) {
        this.access_token = access_token;
        this.scope = scope;
        this.created_at = System.currentTimeMillis() / 1000;
    }
}
