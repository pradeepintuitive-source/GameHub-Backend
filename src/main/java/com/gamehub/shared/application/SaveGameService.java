package com.gamehub.shared.application;

import com.gamehub.audit.application.AuditService;
import com.gamehub.audit.domain.AuditType;
import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.room.infrastructure.RoomRepository;
import com.gamehub.shared.infrastructure.GameSessionEntity;
import com.gamehub.shared.infrastructure.GameSessionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaveGameService {

    private final GameSessionRepository gameSessionRepository;
    private final RoomRepository roomRepository;
    private final AuditService auditService;

    @Transactional
    public void autoSave(UUID sessionId, UUID actorUserId, String reason) {
        GameSessionEntity session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessRuleViolationException("Game session not found"));
        session.setSaveVersion(session.getSaveVersion() + 1);
        UUID roomId = roomRepository.findById(session.getRoomId())
                .orElseThrow(() -> new BusinessRuleViolationException("Room not found"))
                .getId();
        auditService.record(AuditType.GAME_ACTION, roomId, sessionId, actorUserId, "Auto save", reason);
    }

    @Transactional
    public void manualSave(UUID sessionId, UUID actorUserId, String reason) {
        GameSessionEntity session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessRuleViolationException("Game session not found"));
        session.setSaveVersion(session.getSaveVersion() + 1);
        auditService.record(AuditType.GAME_ACTION, session.getRoomId(), sessionId, actorUserId, "Manual save", reason);
    }

    @Transactional
    public void autoSaveCurrentSessionForRoom(UUID roomId, UUID actorUserId, String reason) {
        var room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessRuleViolationException("Room not found"));
        if (room.getCurrentSessionId() == null) {
            return;
        }
        autoSave(room.getCurrentSessionId(), actorUserId, reason);
    }
}
