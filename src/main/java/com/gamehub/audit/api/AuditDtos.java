package com.gamehub.audit.api;

import com.gamehub.audit.domain.AuditType;
import java.time.Instant;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditEntryResponse(
            UUID id,
            UUID roomId,
            UUID sessionId,
            UUID actorUserId,
            AuditType auditType,
            String message,
            String details,
            Instant createdAt) {
    }
}
