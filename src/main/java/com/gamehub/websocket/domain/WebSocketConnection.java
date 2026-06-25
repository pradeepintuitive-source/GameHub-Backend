package com.gamehub.websocket.domain;

import java.time.Instant;
import java.util.UUID;

public record WebSocketConnection(
        String sessionId,
        UUID userId,
        Instant connectedAt) {
}
