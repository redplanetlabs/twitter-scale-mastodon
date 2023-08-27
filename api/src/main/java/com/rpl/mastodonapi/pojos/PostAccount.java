package com.rpl.mastodonapi.pojos;

public class PostAccount {
    public String username;
    public String email;
    public String password;
    public Boolean agreement;
    public String locale;
    public String reason;

    public PostAccount() { }

    public PostAccount(String username, String email, String password, Boolean agreement, String locale, String reason) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.agreement = agreement;
        this.locale = locale;
        this.reason = reason;
    }
}
