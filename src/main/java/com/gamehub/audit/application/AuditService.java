package com.gamehub.audit.application;

import com.gamehub.audit.api.AuditDtos;
import com.gamehub.audit.domain.AuditType;
import com.gamehub.audit.infrastructure.AuditEntryEntity;
import com.gamehub.audit.infrastructure.AuditRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRepository auditRepository;

    @Transactional
    public void record(
            AuditType auditType,
            UUID roomId,
            UUID sessionId,
            UUID actorUserId,
            String message,
            String details) {
        AuditEntryEntity entry = new AuditEntryEntity();
        entry.setId(UUID.randomUUID());
        entry.setAuditType(auditType);
        entry.setRoomId(roomId);
        entry.setSessionId(sessionId);
        entry.setActorUserId(actorUserId);
        entry.setMessage(message);
        entry.setDetails(details);
        auditRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditDtos.AuditEntryResponse> roomAudit(UUID roomId) {
        return auditRepository.findTop100ByRoomIdOrderByCreatedAtDesc(roomId)
                .stream()
                .map(entity -> new AuditDtos.AuditEntryResponse(
                        entity.getId(),
                        entity.getRoomId(),
                        entity.getSessionId(),
                        entity.getActorUserId(),
                        entity.getAuditType(),
                        entity.getMessage(),
                        entity.getDetails(),
                        entity.getCreatedAt()))
                .toList();
    }
}
