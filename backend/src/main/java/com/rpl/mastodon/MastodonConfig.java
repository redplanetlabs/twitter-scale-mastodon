package com.rpl.mastodon;

public class MastodonConfig {
    public static String API_URL = System.getProperty("mastodon.api.url", "http://localhost:8080");
    public static String API_WEB_SOCKET_URL = System.getProperty("mastodon.api.web.socket.url", "ws://localhost:8080");
    public static String API_DOMAIN = System.getProperty("mastodon.api.domain", "localhost");
    public static String FRONTEND_URL = System.getProperty("mastodon.frontend.url", "http://localhost:8000");
}
