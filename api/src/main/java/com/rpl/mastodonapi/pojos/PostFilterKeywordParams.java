package com.rpl.mastodonapi.pojos;

import com.rpl.mastodon.data.KeywordFilter;

public class PostFilterKeywordParams {
    public String keyword;
    public Boolean whole_word;

    public PostFilterKeywordParams() {}

    public PostFilterKeywordParams(String keyword, Boolean wholeWord) {
        this.keyword = keyword;
        this.whole_word = wholeWord == null ? false : wholeWord;
    }

    public KeywordFilter toKeywordFilter() {
        return new KeywordFilter(this.keyword, this.whole_word);
    }
}