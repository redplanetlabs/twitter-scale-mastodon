package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.MastodonHelpers;
import com.rpl.mastodon.data.*;
import com.rpl.mastodonapi.MastodonApiHelpers;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class GetFilter {
    public String id;
    public String title;
    public List<String> context;
    public String expires_at;
    public String filter_action;

    public static class FilterKeyword {
        public String id;
        public String keyword;
        public boolean whole_word;

        public FilterKeyword() { }

        public static Long filterIdFromKeywordId(String keywordId) {
            return Long.parseLong(keywordId.split("-")[0]);
        }

        public static String currentWordFromKeywordId(String keywordId) {
            try {
                return URLDecoder.decode(keywordId.substring(keywordId.indexOf("-")+1), StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        public FilterKeyword(Long filterId, String keyword, boolean wholeWord) {
            try {
                this.id = "" + filterId + "-" + URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                // this should be unreachable
                throw new RuntimeException(e);
            }
            this.keyword = keyword;
            this.whole_word = wholeWord;
        }
    }

    public List<FilterKeyword> keywords;
    public static class FilterStatus {
        public String id;
        public String status_id;
        FilterStatus(long filterId, StatusPointer statusPointer) {
            String comboId = MastodonHelpers.serializeStatusPointer(statusPointer);
            this.id = filterId + "-" + comboId;
            this.status_id = comboId;
        }

        public FilterStatus() { }
    }
    public List<FilterStatus> statuses;

    public GetFilter() { }

    public GetFilter(long filterId, Filter filter) {
        this.id = filterId+"";
        this.title = filter.title;
        this.context = filter.contexts.stream().map(MastodonApiHelpers::createFilterContext).collect(Collectors.toList());
        this.filter_action = MastodonApiHelpers.createFilterAction(filter.action);
        this.keywords = filter.keywords.stream().map(keyword -> new FilterKeyword(filterId, keyword.word, keyword.wholeWord)).collect(Collectors.toList());
        this.statuses = filter.statuses.stream().map(statusPointer -> new FilterStatus(filterId, statusPointer)).collect(Collectors.toList());
        if (filter.isSetExpirationMillis()) this.expires_at = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(filter.expirationMillis));
    }

    public GetFilter(FilterWithId filterWithId) {
        this(filterWithId.filterId, filterWithId.filter);
    }

    String formatStatusFilterId(String statusId) {
        return this.id + "-" + statusId;
    }
}
