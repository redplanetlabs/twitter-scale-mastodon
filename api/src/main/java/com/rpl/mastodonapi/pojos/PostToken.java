package com.rpl.mastodonapi.pojos;

public class PostToken {
    public String grant_type;
    public String client_id;
    public String client_secret;
    public String redirect_uri;
    public String scope;

    // if grant_type is "password"
    public String username;
    public String password;

    // if grant_type is "authorization_code"
    public String code; // obtained via /oauth/authorize

    public PostToken() { }

    public PostToken(String username, String password) {
        this.username = username;
        this.password = password;
        this.grant_type = "password";
    }

    public PostToken(String client_id, String client_secret, String scope, String username, String password) {
        this.client_id = client_id;
        this.client_secret = client_secret;
        this.scope = scope;
        this.username = username;
        this.password = password;
        this.grant_type = "password";
    }

    public static PostToken initWithCode(String client_id, String client_secret, String redirect_uri, String scope, String code) {
        PostToken token = new PostToken();
        token.client_id = client_id;
        token.client_secret = client_secret;
        token.redirect_uri = redirect_uri;
        token.scope = scope;
        token.code = code;
        token.grant_type = "authorization_code";
        return token;
    }
}
