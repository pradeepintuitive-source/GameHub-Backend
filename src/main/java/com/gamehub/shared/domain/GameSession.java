package com.gamehub.shared.domain;

import java.time.Instant;
import java.util.UUID;

public record GameSession(
        UUID id,
        UUID roomId,
        GameType gameType,
        SessionStatus status,
        String statePayload,
        long saveVersion,
        Instant startedAt,
        Instant endedAt) {
}
