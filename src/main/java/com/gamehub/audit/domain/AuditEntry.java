package com.gamehub.audit.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditEntry(
        UUID id,
        UUID roomId,
        UUID sessionId,
        UUID actorUserId,
        AuditType auditType,
        String message,
        String details,
        Instant createdAt) {
}
