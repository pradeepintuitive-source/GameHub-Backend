package com.gamehub.shared.infrastructure;

import com.gamehub.persistence.infrastructure.BaseEntity;
import com.gamehub.shared.domain.GameType;
import com.gamehub.shared.domain.SessionStatus;
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
@Table(name = "game_sessions")
public class GameSessionEntity extends BaseEntity {

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false)
    private GameType gameType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "state_payload", nullable = false, columnDefinition = "TEXT")
    private String statePayload;

    @Column(name = "save_version", nullable = false)
    private long saveVersion;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;
}
