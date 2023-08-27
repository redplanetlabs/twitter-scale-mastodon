package com.rpl.mastodonapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpl.mastodonapi.pojos.GetConversation;
import com.rpl.mastodonapi.pojos.GetNotification;
import com.rpl.mastodonapi.pojos.GetStatus;
import com.rpl.mastodonapi.pojos.GetStreamEvent;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestStreamClient {
    private final ConcurrentLinkedQueue<GetStreamEvent> messages = new ConcurrentLinkedQueue<>();
    private final WebSocketClient webSocketClient;

    public TestStreamClient(String url, String authHeader) {
        String sessionId = authHeader.split(" ")[1];
        Map<String, String> headers = new HashMap<>();
        headers.put("Sec-WebSocket-Protocol", sessionId);
        webSocketClient = new WebSocketClient(URI.create(url), headers) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
            }

            @Override
            public void onMessage(String s) {
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    messages.add(objectMapper.readValue(s, GetStreamEvent.class));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onClose(int i, String s, boolean b) {
            }

            @Override
            public void onError(Exception e) {
            }
        };
        // wait for the initial connection
        webSocketClient.connect();
        TestHelpers.attainCondition(() -> webSocketClient.isOpen() && MastodonApiStreamingConfig.SESSION_ID_TO_STATE.size() > 0);
    }

    public GetStreamEvent waitForMessage() {
        TestHelpers.attainCondition(() -> messages.peek() != null);
        return messages.poll();
    }

    public GetStatus waitForStatus() throws JsonProcessingException {
        GetStreamEvent event = waitForMessage();
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(event.payload, GetStatus.class);
    }

    public GetConversation waitForConversation() throws JsonProcessingException {
        GetStreamEvent event = waitForMessage();
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(event.payload, GetConversation.class);
    }

    public GetNotification waitForNotification() throws JsonProcessingException {
        GetStreamEvent event = waitForMessage();
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(event.payload, GetNotification.class);
    }

    public void close() {
        webSocketClient.close();
    }
}
