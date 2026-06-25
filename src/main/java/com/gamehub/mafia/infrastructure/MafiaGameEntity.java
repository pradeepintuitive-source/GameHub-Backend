package com.gamehub.mafia.infrastructure;

import com.gamehub.mafia.domain.MafiaPhase;
import com.gamehub.persistence.infrastructure.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "mafia_games")
public class MafiaGameEntity extends BaseEntity {

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MafiaPhase phase;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "state_payload", nullable = false, columnDefinition = "TEXT")
    private String statePayload;
}
