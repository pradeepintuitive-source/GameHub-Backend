package com.gamehub.shared.infrastructure;

import com.gamehub.persistence.infrastructure.BaseEntity;
import com.gamehub.shared.domain.GameEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "game_events")
public class GameEventEntity extends BaseEntity {

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private GameEventType eventType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
