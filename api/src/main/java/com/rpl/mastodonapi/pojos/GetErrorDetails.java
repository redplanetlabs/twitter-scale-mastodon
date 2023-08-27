package com.rpl.mastodonapi.pojos;

import java.util.HashMap;

public class GetErrorDetails {
    public String error;

    public static class Error {
        public String error;
        public String description;

        public Error() { }

        public Error(String error, String description) {
            this.error = error;
            this.description = description;
        }
    }
    public HashMap<String, Error> details;

    public GetErrorDetails() { }

    public GetErrorDetails(String error, HashMap<String, Error> details) {
        this.error = error;
        this.details = details;
    }
}
