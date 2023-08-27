package com.rpl.mastodonapi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// a map that acts as a cache, maintaining a fixed number of entries
// and removing the oldest entries as needed.
public class MastodonApiConcurrentFixedMap<K, V> extends LinkedHashMap<K, V> {
    private final int MAX_ENTRIES;
    public MastodonApiConcurrentFixedMap(int size) {
        super(size);
        this.MAX_ENTRIES = size;
    }

    public static <K, V> Map<K, V> init(int size) {
        return Collections.synchronizedMap(new MastodonApiConcurrentFixedMap<>(size));
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > MAX_ENTRIES;
    }
}
