package com.rpl.mastodonapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpl.mastodon.data.*;
import com.rpl.mastodonapi.pojos.*;
import com.rpl.rama.*;
import com.rpl.rama.diffs.*;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.*;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.*;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;
import org.springframework.web.server.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.*;

import java.io.IOException;
import java.net.*;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Configuration
public class MastodonApiStreamingConfig {
    private static final String SEC_WEBSOCKET_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static class StreamState {
        WebSocketSession session;
        Long accountId;
        FluxSink<WebSocketMessage> sink;
        String stream;
        List<ProxyState<SortedMap>> proxies;

        public StreamState(WebSocketSession session, Long accountId, FluxSink<WebSocketMessage> sink, String stream, List<ProxyState<SortedMap>> proxies) {
            this.session = session;
            this.accountId = accountId;
            this.sink = sink;
            this.stream = stream;
            this.proxies = proxies;
        }

        void close() throws IOException {
            for (ProxyState<SortedMap> proxy : this.proxies) proxy.close();
        }
    }

    // track the state for each connected streaming client
    public static final ConcurrentHashMap<String, StreamState> SESSION_ID_TO_STATE = new ConcurrentHashMap<>();

    private static String serializeEvent(StatusQueryResult statusQueryResult, String stream) {
        try {
            String statusStr = OBJECT_MAPPER.writeValueAsString(new GetStatus(statusQueryResult));
            GetStreamEvent event = new GetStreamEvent(stream, "update", statusStr);
            return OBJECT_MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String serializeEvent(Conversation conversation, String stream) {
        try {
            String convoStr = OBJECT_MAPPER.writeValueAsString(new GetConversation(conversation));
            GetStreamEvent event = new GetStreamEvent(stream, "conversation", convoStr);
            return OBJECT_MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String serializeEvent(GetNotification.Bundle notificationBundle, String stream) {
        try {
            String statusStr = OBJECT_MAPPER.writeValueAsString(new GetNotification(notificationBundle));
            GetStreamEvent event = new GetStreamEvent(stream, "notification", statusStr);
            return OBJECT_MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static void sendStatusPointer(WebSocketSession session, FluxSink<WebSocketMessage> sink, String stream, Long accountId, StatusPointer statusPointer) {
        // direct streams return a "conversation" event
        if ("direct".equals(stream))
            MastodonApiController.manager.getConversationFromStatusId(accountId, statusPointer)
                                         .thenAccept(conversation -> {
                                             if (conversation == null) return;
                                             String eventStr = serializeEvent(conversation, stream);
                                             if (eventStr != null) sink.next(session.textMessage(eventStr));
                                         });
        // other streams return an "update" event
        else {
            final FilterContext context;
            if ("user".equals(stream)) context = FilterContext.Home;
            else context = FilterContext.Public;
            QueryFilterOptions filterOptions = new QueryFilterOptions(context, true);
            MastodonApiController.manager.getStatus(accountId, statusPointer, filterOptions)
                                 .thenAccept(statusQueryResult -> {
                                     sendStatusQueryResult(session, sink, stream, statusQueryResult);
                                 });
        }
    }

    public static void sendStatusQueryResult(WebSocketSession session, FluxSink<WebSocketMessage> sink, String stream, StatusQueryResult statusQueryResult) {
        if (statusQueryResult == null) return;
        String eventStr = serializeEvent(statusQueryResult, stream);
        if (eventStr != null) sink.next(session.textMessage(eventStr));
    }

    public static void sendNotificationWithId(WebSocketSession session, FluxSink<WebSocketMessage> sink, String stream, long accountId, NotificationWithId nwid) {
        MastodonApiController.manager.getNotification(accountId, nwid)
                                     .thenAccept(bundle -> {
                                         if (bundle == null) return;
                                         String eventStr = serializeEvent(bundle, stream);
                                         if (eventStr != null) sink.next(session.textMessage(eventStr));
                                     });
    }

    // caches of the latest query results of each global timeline
    public static final ConcurrentHashMap<GlobalTimeline, ConcurrentSkipListMap<Long, StatusQueryResult>> GLOBAL_TIMELINE_TO_INDEX_TO_STATUS = new ConcurrentHashMap() {{
        put(GlobalTimeline.Public, new ConcurrentSkipListMap<>());
        put(GlobalTimeline.PublicLocal, new ConcurrentSkipListMap<>());
        put(GlobalTimeline.PublicRemote, new ConcurrentSkipListMap<>());
    }};
    public static final ConcurrentHashMap<GlobalTimeline, ConcurrentHashMap<StatusPointer, Long>> GLOBAL_TIMELINE_TO_STATUS_POINTER_TO_INDEX = new ConcurrentHashMap() {{
        put(GlobalTimeline.Public, new ConcurrentHashMap<>());
        put(GlobalTimeline.PublicLocal, new ConcurrentHashMap<>());
        put(GlobalTimeline.PublicRemote, new ConcurrentHashMap<>());
    }};
    public static final int GLOBAL_TIMELINE_CACHE_SIZE = 4000; // how many statuses to keep in memory for each timeline
    public static final int GLOBAL_TIMELINE_QUERY_LIMIT = 10; // how many statuses to query on each iteration

    public static class StatusPointerDiffProcessor implements Diff.Processor, KeyDiff.Processor {
        public List<StatusPointer> statusPointers = new ArrayList<>();

        @Override
        public void processKeyDiff(KeyDiff diff) {
            NewValueDiff newValueDiff = (NewValueDiff) diff.getValDiff();
            Object val = newValueDiff.getVal();
            StatusPointer statusPointer = (StatusPointer) val;
            statusPointers.add(statusPointer);
        }

        @Override
        public void unhandled() {
            statusPointers = null;
        }
    }

    public static class NotificationWithIdDiffProcessor implements Diff.Processor, KeyDiff.Processor {
        public List<NotificationWithId> nwids = new ArrayList<>();

        @Override
        public void processKeyDiff(KeyDiff diff) {
            long notificationId = (long) diff.getKey();
            NewValueDiff newValueDiff = (NewValueDiff) diff.getValDiff();
            Notification notification = (Notification) newValueDiff.getVal();
            nwids.add(new NotificationWithId(notificationId, notification));
        }

        @Override
        public void unhandled() {
            nwids = null;
        }
    }

    @Bean
    public WebSocketHandler webSocketHandler() {
        return new WebSocketHandler() {
            @Override
            public Mono<Void> handle(WebSocketSession session) {
                final String wsSessionId = session.getId();
                final Long accountId = (Long) session.getAttributes().get("accountId");

                MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri()).build().getQueryParams();

                return session.send(Flux.create(sink -> {
                                        if (SESSION_ID_TO_STATE.containsKey(wsSessionId)) return;
                                        if (!params.containsKey("stream")) return;
                                        String stream = params.get("stream").get(0);

                                        if ("public".equals(stream) || "public:local".equals(stream) || "public:remote".equals(stream)) {
                                            SESSION_ID_TO_STATE.put(wsSessionId, new StreamState(session, accountId, sink, stream, new ArrayList<>()));
                                        } else {
                                            ProxyState.Callback<SortedMap> statusCallback =
                                              (SortedMap newVal, Diff diff, SortedMap oldVal) -> {
                                                StatusPointerDiffProcessor processor = new StatusPointerDiffProcessor();
                                                diff.process(processor);
                                                // no diff was provided (rare)
                                                if (processor.statusPointers == null) {
                                                  if (newVal != null && oldVal != null) {
                                                    for (Object key : newVal.keySet()) {
                                                        long timelineIndex = (long) key;
                                                        if (!oldVal.containsKey(timelineIndex)) {
                                                          StatusPointer statusPointer = (StatusPointer) newVal.get(timelineIndex);
                                                          sendStatusPointer(session, sink, stream, accountId, statusPointer);
                                                        }
                                                    }
                                                  }
                                                } else {
                                                  for (StatusPointer statusPointer : processor.statusPointers) sendStatusPointer(session, sink, stream, accountId, statusPointer);
                                                }
                                              };

                                            if ("hashtag".equals(stream)) {
                                                if (!params.containsKey("tag")) {
                                                    sink.complete();
                                                    return;
                                                }
                                                String tag = params.get("tag").get(0);
                                                MastodonApiController.manager
                                                        .proxyHashtagTimeline(tag, statusCallback)
                                                        .thenAccept(proxy -> SESSION_ID_TO_STATE.put(wsSessionId, new StreamState(session, accountId, sink, stream, Arrays.asList(proxy))));
                                            } else if ("user".equals(stream)) {
                                                if (accountId == null) return; // login required
                                                ProxyState.Callback<SortedMap> notificationCallback =
                                                  (SortedMap newVal, Diff diff, SortedMap oldVal) -> {
                                                    NotificationWithIdDiffProcessor processor = new NotificationWithIdDiffProcessor();
                                                    diff.process(processor);
                                                    // no diff was provided (rare)
                                                    if (processor.nwids == null) {
                                                      if (newVal != null && oldVal != null) {
                                                        for (Object key : newVal.keySet()) {
                                                            long notificationId = (long) key;
                                                            if (!oldVal.containsKey(notificationId)) {
                                                              Notification notification = (Notification) newVal.get(notificationId);
                                                              sendNotificationWithId(session, sink, stream, accountId, new NotificationWithId(notificationId, notification));
                                                            }
                                                        }
                                                      }
                                                    } else {
                                                      for (NotificationWithId nwid : processor.nwids) sendNotificationWithId(session, sink, stream, accountId, nwid);
                                                    }
                                                  };
                                                // make reactive queries for home and notifications timelines
                                                MastodonApiController.manager
                                                        .proxyHomeTimeline(accountId, statusCallback)
                                                        .thenCompose(homeProxy -> {
                                                            // temporarily save home proxy by itself so it will be properly closed
                                                            // if the notifications proxy is not successfully created.
                                                            SESSION_ID_TO_STATE.put(wsSessionId, new StreamState(session, accountId, sink, stream, Arrays.asList(homeProxy)));
                                                            // make reactive query for notifications timeline
                                                            return MastodonApiController.manager
                                                                    .proxyNotificationsTimeline(accountId, notificationCallback)
                                                                    .thenAccept(notificationsProxy -> SESSION_ID_TO_STATE.put(wsSessionId, new StreamState(session, accountId, sink, stream, Arrays.asList(homeProxy, notificationsProxy))));
                                                        });
                                            } else if ("list".equals(stream)) {
                                                if (accountId == null) return; // login required
                                                if (!params.containsKey("list")) {
                                                    sink.complete();
                                                    return;
                                                }
                                                long listId = Long.parseLong(params.get("list").get(0));
                                                MastodonApiController.manager
                                                        .proxyListTimeline(listId, statusCallback)
                                                        .thenAccept(proxy -> SESSION_ID_TO_STATE.put(wsSessionId, new StreamState(session, accountId, sink, stream, Arrays.asList(proxy))));
                                            } else if ("direct".equals(stream)) {
                                                if (accountId == null) return; // login required
                                                MastodonApiController.manager
                                                        .proxyDirectTimeline(accountId, statusCallback)
                                                        .thenAccept(proxy -> SESSION_ID_TO_STATE.put(wsSessionId, new StreamState(session, accountId, sink, stream, Arrays.asList(proxy))));
                                            }
                                        }
                                    }))
                              .and(session.receive()
                                          .doFinally(sig -> {
                                              session.close();
                                              StreamState state = SESSION_ID_TO_STATE.remove(wsSessionId);
                                              if (state != null) {
                                                try {
                                                  state.close();
                                                } catch (IOException e) {
                                                  throw new RuntimeException(e);
                                                }
                                              }
                                          }).then());
            }
        };
    }

    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        WebSocketHandler handler = webSocketHandler();
        map.put("/api/v1/streaming/", handler);
        map.put("/api/v1/streaming", handler);

        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        handlerMapping.setUrlMap(map);
        return handlerMapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter(webSocketService());
    }

    @Bean
    public WebSocketService webSocketService() {
        return new WebSocketService() {
            @Override
            public Mono<Void> handleRequest(ServerWebExchange exchange, WebSocketHandler handler) {
                ServerHttpRequest request = exchange.getRequest();
                HttpMethod method = request.getMethod();
                HttpHeaders headers = request.getHeaders();

                if (HttpMethod.GET != method) {
                    return Mono.error(new MethodNotAllowedException(request.getMethodValue(), Collections.singleton(HttpMethod.GET)));
                }

                if (!"WebSocket".equalsIgnoreCase(headers.getUpgrade())) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid 'Upgrade' header: " + headers));
                }

                List<String> connectionValue = headers.getConnection();
                if (!connectionValue.contains("Upgrade") && !connectionValue.contains("upgrade")) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid 'Connection' header: " + headers));
                }

                String protocol = headers.getFirst(SEC_WEBSOCKET_PROTOCOL_HEADER);

                return initAttributes(exchange).flatMap(attributes ->
                        new ReactorNettyRequestUpgradeStrategy().upgrade(exchange, handler, protocol,
                                () -> createHandshakeInfo(exchange, request, protocol, attributes))
                );
            }

            private Mono<Map<String, Object>> initAttributes(ServerWebExchange exchange) {
                return exchange.getSession().map(session ->
                        session.getAttributes().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            }

            private HandshakeInfo createHandshakeInfo(ServerWebExchange exchange, ServerHttpRequest request,
                                                      String protocol, Map<String, Object> attributes) {

                URI uri = request.getURI();
                // Copy request headers, as they might be pooled and recycled by
                // the server implementation once the handshake HTTP exchange is done.
                HttpHeaders headers = new HttpHeaders();
                headers.addAll(request.getHeaders());
                MultiValueMap<String, org.springframework.http.HttpCookie> cookies = request.getCookies();
                Mono<Principal> principal = exchange.getPrincipal();
                String logPrefix = exchange.getLogPrefix();
                InetSocketAddress remoteAddress = request.getRemoteAddress();
                return new HandshakeInfo(uri, headers, cookies, principal, protocol, remoteAddress, attributes, logPrefix);
            }
        };
    }
}
