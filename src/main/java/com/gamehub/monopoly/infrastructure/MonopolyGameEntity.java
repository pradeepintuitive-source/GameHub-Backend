package com.gamehub.monopoly.infrastructure;

import com.gamehub.monopoly.domain.MonopolyPhase;
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
@Table(name = "monopoly_games")
public class MonopolyGameEntity extends BaseEntity {

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MonopolyPhase phase;

    @Column(name = "current_player_id")
    private UUID currentPlayerId;

    @Column(name = "turn_counter", nullable = false)
    private int turnCounter;

    @Column(name = "state_payload", nullable = false, columnDefinition = "TEXT")
    private String statePayload;
}
