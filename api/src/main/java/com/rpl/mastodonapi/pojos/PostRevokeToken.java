package com.rpl.mastodonapi.pojos;

public class PostRevokeToken {
    public String client_id;
    public String client_secret;
    public String token;

    public PostRevokeToken() { }

    public PostRevokeToken(String token) {
        this.token = token;
    }
}
