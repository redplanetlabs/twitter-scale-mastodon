package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.MastodonConfig;
import com.rpl.mastodonapi.MastodonApiConfig;

public class GetApplication {
    public String name = "Red Planet Labs Mastodon App";
    public String redirect_uri;
    public String client_id = MastodonApiConfig.OAUTH_CLIENT_ID;
    public String client_secret;
}
