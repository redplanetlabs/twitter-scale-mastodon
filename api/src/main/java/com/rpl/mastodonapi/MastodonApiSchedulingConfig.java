package com.rpl.mastodonapi;

import com.rpl.mastodon.data.*;
import com.rpl.rama.ProxyState;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static com.rpl.mastodonapi.MastodonApiStreamingConfig.*;

@Configuration
@Profile("!end-to-end-test") // don't run scheduled methods in e2e test
@EnableScheduling
public class MastodonApiSchedulingConfig {
    @Scheduled(fixedDelay = 5000)
    public static void refreshHomeTimelineProxies() {
        if (MastodonApiController.manager == null) return; // exit early if the manager hasn't been initialized yet
        List<MastodonApiManager.HomeTimelineProxyState> proxies = new ArrayList();
        for(StreamState ss: SESSION_ID_TO_STATE.values()) {
            for (ProxyState<SortedMap> proxy : ss.proxies) {
                if (proxy instanceof MastodonApiManager.HomeTimelineProxyState) proxies.add((MastodonApiManager.HomeTimelineProxyState) proxy);
            }
        }
        if(!proxies.isEmpty()) MastodonApiController.manager.refreshHomeTimelineProxies(proxies);
    }

    // for each global timeline, run the query and send the new results to any connected clients
    @Scheduled(fixedDelay = 1000)
    public static void refreshGlobalTimelines() {
        refreshGlobalTimelines(GLOBAL_TIMELINE_CACHE_SIZE, GLOBAL_TIMELINE_QUERY_LIMIT, false);
    }

    public static void refreshGlobalTimelines(int cacheSize, int queryLimit, boolean fullRefresh) {
        if (MastodonApiController.manager == null) return; // exit early if the manager hasn't been initialized yet
        for (Map.Entry<GlobalTimeline, ConcurrentSkipListMap<Long, StatusQueryResult>> streamEntry : GLOBAL_TIMELINE_TO_INDEX_TO_STATUS.entrySet()) {
            GlobalTimeline timeline = streamEntry.getKey();
            String stream;
            switch (timeline) {
                case Public:
                    stream = "public";
                    break;
                case PublicLocal:
                    stream = "public:local";
                    break;
                case PublicRemote:
                    stream = "public:remote";
                    break;
                default:
                    continue;
            }
            ConcurrentSkipListMap<Long, StatusQueryResult> indexToStatusOld = streamEntry.getValue();
            // find any new results since last time we queried
            int limit = indexToStatusOld.size() == 0 ? cacheSize : queryLimit;
            SortedMap<Long, StatusQueryResult> indexToStatusNew = MastodonApiController.manager.getGlobalTimeline(timeline, limit);
            SortedMap<Long, StatusQueryResult> indexToStatusDiff = new TreeMap<>();
            for (Map.Entry<Long, StatusQueryResult> entry : indexToStatusNew.entrySet()) {
                long timelineIndex = entry.getKey();
                if (!indexToStatusOld.containsKey(timelineIndex)) indexToStatusDiff.put(timelineIndex, entry.getValue());
            }
            if (fullRefresh || indexToStatusDiff.size() > 0) {
                // update the cache and remove older items if it is above the size limit
                List<StatusPointer> removedStatusPointers = new ArrayList<>();
                GLOBAL_TIMELINE_TO_INDEX_TO_STATUS.computeIfPresent(timeline, (GlobalTimeline key, ConcurrentSkipListMap<Long, StatusQueryResult> indexToStatus) -> {
                    indexToStatus.putAll(indexToStatusDiff);
                    int removeCount = indexToStatus.size() - cacheSize;
                    for (int i = 0; i < removeCount; i++) {
                        StatusQueryResult removedStatus = indexToStatus.remove(indexToStatus.lastKey());
                        removedStatusPointers.add(new StatusPointer(removedStatus.result.status.author.accountId, removedStatus.result.statusId));
                    }
                    return indexToStatus;
                });
                GLOBAL_TIMELINE_TO_STATUS_POINTER_TO_INDEX.computeIfPresent(timeline, (GlobalTimeline key, ConcurrentHashMap<StatusPointer, Long> indexToStatus) -> {
                    for (Map.Entry<Long, StatusQueryResult> entry : indexToStatusDiff.entrySet()) indexToStatus.put(new StatusPointer(entry.getValue().result.status.author.accountId, entry.getValue().result.statusId), entry.getKey());
                    for (StatusPointer removedStatusPointer : removedStatusPointers) indexToStatus.remove(removedStatusPointer);
                    return indexToStatus;
                });
                // send the new results to every necessary client
                for (StatusQueryResult statusQueryResult : indexToStatusDiff.values()) {
                    // for each web socket connection
                    for (StreamState state : SESSION_ID_TO_STATE.values()) {
                        if (state.stream.equals(stream)) {
                            // if they aren't logged in, just send the status we already queried
                            if (state.accountId == null) sendStatusQueryResult(state.session, state.sink, state.stream, statusQueryResult);
                            // otherwise, query the status with this account id so it takes blocks/mutes into account
                            else sendStatusPointer(state.session, state.sink, state.stream, state.accountId, new StatusPointer(statusQueryResult.result.status.author.accountId, statusQueryResult.result.statusId));
                        }
                    }
                }
            }
        }
    }
}
