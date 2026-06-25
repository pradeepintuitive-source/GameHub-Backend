package com.gamehub.player.infrastructure;

import com.gamehub.ai.domain.AiDifficulty;
import com.gamehub.ai.domain.AiType;
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
@Table(name = "players")
public class PlayerEntity extends BaseEntity {

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false)
    private boolean connected;

    @Column(name = "ai_controlled", nullable = false)
    private boolean aiControlled;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_type")
    private AiType aiType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_difficulty")
    private AiDifficulty aiDifficulty;

    @Column(name = "seat_order", nullable = false)
    private int seatOrder;
}
