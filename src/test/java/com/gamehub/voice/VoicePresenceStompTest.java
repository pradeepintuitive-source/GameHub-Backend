package com.gamehub.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VoicePresenceStompTest {

    private static final UUID ROOM_ID = UUID.fromString("8daa24c0-64b7-4335-8758-60a53a70d9df");

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bothSocketsReceivePresenceAfterJoinWithNullRequestId() throws Exception {
        AuthUser pradeep = guest("pradeep");
        AuthUser mac = guest("macuser");

        WebSocketStompClient stompClient = stompClient();
        BlockingQueue<Map<String, Object>> pradeepFrames = new LinkedBlockingQueue<>();
        BlockingQueue<Map<String, Object>> macFrames = new LinkedBlockingQueue<>();

        StompSession pradeepSession = connect(stompClient, pradeep.token());
        StompSession macSession = connect(stompClient, mac.token());
        try {
            String topic = "/topic/rooms/" + ROOM_ID + "/voice";
            pradeepSession.subscribe("/user/queue/voice", frameHandler(pradeepFrames));
            pradeepSession.subscribe(topic, frameHandler(pradeepFrames));
            macSession.subscribe("/user/queue/voice", frameHandler(macFrames));
            macSession.subscribe(topic, frameHandler(macFrames));

            String joinBody = """
                    {"roomId":"%s","requestId":null}
                    """.formatted(ROOM_ID);
            pradeepSession.send("/app/voice/" + ROOM_ID + "/join", objectMapper.readTree(joinBody));
            macSession.send("/app/voice/" + ROOM_ID + "/join", objectMapper.readTree(joinBody));
            pradeepSession.send("/app/voice/" + ROOM_ID + "/mute", objectMapper.readTree("{\"muted\":true,\"requestId\":null}"));
            macSession.send("/app/voice/" + ROOM_ID + "/mute", objectMapper.readTree("{\"muted\":true,\"requestId\":null}"));

            Map<String, Object> pradeepPresence = awaitPresenceWithBoth(pradeepFrames, pradeep.userId(), mac.userId());
            Map<String, Object> macPresence = awaitPresenceWithBoth(macFrames, pradeep.userId(), mac.userId());

            assertPresence(pradeepPresence, pradeep.userId(), mac.userId());
            assertPresence(macPresence, pradeep.userId(), mac.userId());
        } finally {
            if (pradeepSession.isConnected()) {
                pradeepSession.disconnect();
            }
            if (macSession.isConnected()) {
                macSession.disconnect();
            }
            stompClient.stop();
        }
    }

    @Test
    void leaveAndDisconnectBroadcastJoinedFalse() throws Exception {
        AuthUser pradeep = guest("pradeep2");
        AuthUser mac = guest("macuser2");
        WebSocketStompClient stompClient = stompClient();
        BlockingQueue<Map<String, Object>> pradeepFrames = new LinkedBlockingQueue<>();
        BlockingQueue<Map<String, Object>> macFrames = new LinkedBlockingQueue<>();
        StompSession pradeepSession = connect(stompClient, pradeep.token());
        StompSession macSession = connect(stompClient, mac.token());
        try {
            String topic = "/topic/rooms/" + ROOM_ID + "/voice";
            pradeepSession.subscribe(topic, frameHandler(pradeepFrames));
            macSession.subscribe(topic, frameHandler(macFrames));
            String joinBody = """
                    {"roomId":"%s","requestId":null}
                    """.formatted(ROOM_ID);
            pradeepSession.send("/app/voice/" + ROOM_ID + "/join", objectMapper.readTree(joinBody));
            macSession.send("/app/voice/" + ROOM_ID + "/join", objectMapper.readTree(joinBody));
            awaitPresenceWithBoth(pradeepFrames, pradeep.userId(), mac.userId());
            awaitPresenceWithBoth(macFrames, pradeep.userId(), mac.userId());

            macSession.send("/app/voice/" + ROOM_ID + "/leave", objectMapper.readTree("{\"requestId\":null}"));
            Map<String, Object> left = awaitJoined(pradeepFrames, false, mac.userId());
            assertThat(participantIds(left)).contains(pradeep.userId().toString()).doesNotContain(mac.userId().toString());

            pradeepSession.disconnect();
            Map<String, Object> disconnected = awaitJoined(macFrames, false, pradeep.userId());
            assertThat(participantIds(disconnected)).doesNotContain(pradeep.userId().toString());
        } finally {
            if (pradeepSession.isConnected()) {
                pradeepSession.disconnect();
            }
            if (macSession.isConnected()) {
                macSession.disconnect();
            }
            stompClient.stop();
        }
    }

    private void assertPresence(Map<String, Object> frame, UUID pradeepId, UUID macId) {
        assertThat(frame.get("type")).isEqualTo("VOICE_PRESENCE");
        assertThat(frame.get("roomId")).isEqualTo(ROOM_ID.toString());
        assertThat(frame.get("joined")).isEqualTo(true);
        assertThat(participantIds(frame)).containsExactlyInAnyOrder(pradeepId.toString(), macId.toString());
        assertThat(frame.get("changedUserId")).isIn(pradeepId.toString(), macId.toString());
    }

    private Map<String, Object> awaitPresenceWithBoth(BlockingQueue<Map<String, Object>> frames, UUID first, UUID second)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            Map<String, Object> frame = frames.poll(500, TimeUnit.MILLISECONDS);
            if (frame == null || !"VOICE_PRESENCE".equals(frame.get("type")) || !Boolean.TRUE.equals(frame.get("joined"))) {
                continue;
            }
            List<String> ids = participantIds(frame);
            if (ids.contains(first.toString()) && ids.contains(second.toString())) {
                return frame;
            }
        }
        throw new AssertionError("No VOICE_PRESENCE containing both auth user ids arrived");
    }

    private Map<String, Object> awaitJoined(BlockingQueue<Map<String, Object>> frames, boolean joined, UUID changedUserId)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            Map<String, Object> frame = frames.poll(500, TimeUnit.MILLISECONDS);
            if (frame == null || !"VOICE_PRESENCE".equals(frame.get("type"))) {
                continue;
            }
            if (Boolean.valueOf(joined).equals(frame.get("joined"))
                    && changedUserId.toString().equals(frame.get("changedUserId"))) {
                return frame;
            }
        }
        throw new AssertionError("No VOICE_PRESENCE joined=" + joined + " for " + changedUserId);
    }

    @SuppressWarnings("unchecked")
    private List<String> participantIds(Map<String, Object> frame) {
        Object raw = frame.get("participants");
        assertThat(raw).isInstanceOf(List.class);
        List<String> ids = new ArrayList<>();
        for (Object value : (List<Object>) raw) {
            ids.add(String.valueOf(value));
        }
        return ids;
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        client.setMessageConverter(converter);
        return client;
    }

    private StompSession connect(WebSocketStompClient client, String token) throws Exception {
        return client.connectAsync(
                        "http://localhost:" + port + "/ws?token=" + token,
                        new StompSessionHandlerAdapter() {})
                .get(8, TimeUnit.SECONDS);
    }

    private StompFrameHandler frameHandler(BlockingQueue<Map<String, Object>> frames) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add((Map<String, Object>) payload);
            }
        };
    }

    private AuthUser guest(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        JsonNode body = json.has("accessToken") || json.has("token") ? json : json.path("data");
        String token = body.path("accessToken").asText(body.path("token").asText(null));
        String userId = body.path("id").asText(body.path("userId").asText(null));
        assertThat(token).isNotBlank();
        assertThat(userId).isNotBlank();
        return new AuthUser(UUID.fromString(userId), token);
    }

    private record AuthUser(UUID userId, String token) {}
}
