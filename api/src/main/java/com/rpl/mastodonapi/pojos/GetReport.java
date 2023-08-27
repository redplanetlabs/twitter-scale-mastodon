package com.rpl.mastodonapi.pojos;

import java.util.List;

public class GetReport {
    public String id;
    public boolean action_taken;
    public String action_taken_at;
    public String category;
    public String comment;
    public boolean forwarded;
    public String created_at;
    public List<String> status_ids;
    public List<String> rule_ids;
    public GetAccount target_account;
}
