package com.gamehub.shared.domain;

import java.time.Instant;
import java.util.UUID;

public record GameEvent(
        UUID id,
        UUID sessionId,
        UUID roomId,
        GameEventType type,
        UUID actorUserId,
        String payload,
        Instant occurredAt) {
}
