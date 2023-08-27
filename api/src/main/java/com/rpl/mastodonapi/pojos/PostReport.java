package com.rpl.mastodonapi.pojos;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

public class PostReport {
    public String account_id;
    public List<String> status_ids;
    public String comment;
    public boolean forward;
    public String category;
    public List<Integer> rule_ids;

    public PostReport() { }

    public PostReport(String account_id, List<String> status_ids, String comment, String category) {
        this.account_id = account_id;
        this.status_ids = status_ids;
        this.comment = comment;
        this.category = category;
    }

    public static final HashSet<String> CATEGORY = new HashSet<>(Arrays.asList("spam", "violation", "other"));

    @JsonProperty("category")
    public void setCategory(String value) {
        if (!CATEGORY.contains(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        this.category = value;
    }

    @Override
    public String toString() {
        return "PostReport{" + "account_id='" + account_id + '\'' + ", status_ids=" + status_ids +
                ", comment='" + comment + '\'' + ", forward=" + forward + ", category='" + category + '\'' +
                ", rule_ids=" + rule_ids + '}';
    }
}
