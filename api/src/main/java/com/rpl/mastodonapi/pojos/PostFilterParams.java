package com.rpl.mastodonapi.pojos;

import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.rpl.mastodon.data.*;

public class PostFilterParams {
    public String title;
    public List<String> context;
    public String filter_action;
    public JsonNode expires_in; // soapbox sends this as a string for some ungodly reason

    public static class KeywordAttributes {
        public String keyword;
        public Boolean whole_word;
        public String id;
        public Boolean _destroy;

        public KeywordAttributes(String keyword, Boolean whole_word, String id, Boolean _destroy) {
            this.keyword = keyword;
            this.whole_word = whole_word;
            this.id = id;
            this._destroy = _destroy;
        }

        public KeywordAttributes() {}
    }

    public List<KeywordAttributes> keywords_attributes = new ArrayList<KeywordAttributes>();


    public HashSet<FilterContext> parseContexts() {
        HashSet<FilterContext> contexts = new HashSet<FilterContext>();
        for (String context : this.context) {
            if (context.equals("home")) contexts.add(FilterContext.Home);
            else if (context.equals("notifications")) contexts.add(FilterContext.Notifications);
            else if (context.equals("public")) contexts.add(FilterContext.Public);
            else if (context.equals("thread")) contexts.add(FilterContext.Thread);
            else if (context.equals("account")) contexts.add(FilterContext.Account);
            else throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return contexts;
    }

    public List<KeywordFilter> parseKeywordsAsCreates() {
        List<KeywordFilter> keywords = new ArrayList();
        for (PostFilterParams.KeywordAttributes keyword : this.keywords_attributes) {
            KeywordFilter parsedKeyword = new KeywordFilter();
            parsedKeyword.setWord(keyword.keyword);
            parsedKeyword.setWholeWord(keyword.whole_word);
            keywords.add(parsedKeyword);
        }
        return keywords;
    }

    public List<EditFilterKeyword> parseKeywordsAsUpdates() {
        List<EditFilterKeyword> keywords = new ArrayList();
        for (PostFilterParams.KeywordAttributes keyword : this.keywords_attributes) {
            if (keyword._destroy != null && keyword._destroy) {
                keywords.add(EditFilterKeyword.destroyKeyword(GetFilter.FilterKeyword.currentWordFromKeywordId(keyword.id)));
            } else if (keyword.id != null) {
                if (keyword.keyword == null || keyword.keyword.trim().isEmpty()) continue; // ignore empty keywords
                UpdateKeyword update = new UpdateKeyword(GetFilter.FilterKeyword.currentWordFromKeywordId(keyword.id), keyword.keyword, keyword.whole_word != null && keyword.whole_word);
                keywords.add(EditFilterKeyword.updateKeyword(update));
            } else {
                if (keyword.keyword == null || keyword.keyword.trim().isEmpty()) continue; // ignore empty keywords
                KeywordFilter addKeyword = new KeywordFilter(keyword.keyword, keyword.whole_word != null && keyword.whole_word);
                keywords.add(EditFilterKeyword.addKeyword(addKeyword));
            }
        }
        return keywords;
    }

    public FilterAction parseAction() {
        if (this.filter_action.equals("warn")) return FilterAction.Warn;
        else if (this.filter_action.equals("hide")) return FilterAction.Hide;
        else throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
