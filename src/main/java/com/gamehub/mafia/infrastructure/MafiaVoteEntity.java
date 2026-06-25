package com.gamehub.mafia.infrastructure;

import com.gamehub.persistence.infrastructure.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "mafia_votes")
public class MafiaVoteEntity extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "voter_player_id", nullable = false)
    private UUID voterPlayerId;

    @Column(name = "target_player_id", nullable = false)
    private UUID targetPlayerId;

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;
}
