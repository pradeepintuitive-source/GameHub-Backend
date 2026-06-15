package com.gamehub.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record NotificationMessage(
        String type,
        UUID roomId,
        UUID sessionId,
        Object payload,
        Instant timestamp) {
}
