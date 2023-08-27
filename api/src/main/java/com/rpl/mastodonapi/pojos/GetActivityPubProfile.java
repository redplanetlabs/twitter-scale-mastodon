package com.rpl.mastodonapi.pojos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.*;

public class GetActivityPubProfile {
    @JsonProperty("@context")
    public Object context;
    public String id;
    public String type;
    public String following;
    public String followers;
    public String inbox;
    public String outbox;
    public String featured;
    public String featuredTags;
    public String preferredUsername;
    public String name;
    public String summary;
    public String url;
    public boolean manuallyApprovesFollowers;
    public boolean discoverable;
    public String published;
    public String devices;
    public static class PublicKey {
        public String id;
        public String owner;
        public String publicKeyPem;

        public PublicKey() { }

        public PublicKey(String id, String owner, String publicKeyPem) {
            this.id = id;
            this.owner = owner;
            this.publicKeyPem = publicKeyPem;
        }
    }
    public PublicKey publicKey;
    public static class Attachment {
        public String type;
        public String name;
        public String value;

        public Attachment() { }

        public Attachment(String type, String name, String value) {
            this.type = type;
            this.name = name;
            this.value = value;
        }
    }
    public List<Attachment> attachment;
    public static class Media {
        public String type;
        public String mediaType;
        public String url;
    }
    public Media icon;
    public Media image;
    public Map<String, String> endpoints;

    public GetActivityPubProfile() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            this.context = objectMapper.readTree(new ClassPathResource("context.json").getFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
