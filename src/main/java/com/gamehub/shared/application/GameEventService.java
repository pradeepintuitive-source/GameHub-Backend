package com.gamehub.shared.application;

import com.gamehub.notification.application.NotificationService;
import com.gamehub.notification.domain.NotificationMessage;
import com.gamehub.shared.domain.GameEventType;
import com.gamehub.shared.infrastructure.GameEventEntity;
import com.gamehub.shared.infrastructure.GameEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameEventService {

    private final GameEventRepository gameEventRepository;
    private final NotificationService notificationService;

    @Transactional
    public void record(
            UUID roomId,
            UUID sessionId,
            GameEventType type,
            UUID actorUserId,
            String payload) {
        GameEventEntity entity = new GameEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setRoomId(roomId);
        entity.setSessionId(sessionId);
        entity.setEventType(type);
        entity.setActorUserId(actorUserId);
        entity.setPayload(payload);
        entity.setOccurredAt(Instant.now());
        gameEventRepository.save(entity);

        NotificationMessage message = new NotificationMessage(type.name(), roomId, sessionId, payload, Instant.now());
        notificationService.sendToTopic("/topic/lobby", message);
        notificationService.sendToTopic("/topic/rooms", message);
        if (roomId != null) {
            notificationService.sendToTopic("/topic/game/" + roomId, message);
        }
    }
}
