package com.gamehub.websocket.application;

import com.gamehub.websocket.domain.WebSocketConnection;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class PresenceService {

    private final Map<String, WebSocketConnection> sessions = new ConcurrentHashMap<>();

    public void connect(String sessionId, UUID userId) {
        sessions.put(sessionId, new WebSocketConnection(sessionId, userId, Instant.now()));
    }

    public WebSocketConnection disconnect(String sessionId) {
        return sessions.remove(sessionId);
    }

    public long activeConnections(UUID userId) {
        return sessions.values().stream()
                .filter(connection -> connection.userId().equals(userId))
                .count();
    }
}
