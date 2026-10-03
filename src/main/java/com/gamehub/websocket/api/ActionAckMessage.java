package com.gamehub.websocket.api;

import java.time.Instant;
import java.util.Map;

public record ActionAckMessage(
        String type,
        String requestId,
        String action,
        boolean success,
        Instant timestamp,
        String errorCode,
        String message,
        Map<String, Object> metadata) {
}
