package com.gamehub.room.infrastructure;

import com.gamehub.persistence.infrastructure.BaseEntity;
import com.gamehub.room.domain.RoomState;
import com.gamehub.room.domain.RoomType;
import com.gamehub.room.domain.RoomVisibility;
import com.gamehub.shared.domain.GameType;
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
@Table(name = "rooms")
public class RoomEntity extends BaseEntity {

    @Column(name = "room_code", nullable = false, unique = true, length = 16)
    private String roomCode;

    @Column(name = "host_user_id", nullable = false)
    private UUID hostUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false)
    private GameType gameType;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false)
    private RoomType roomType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomState state;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(name = "current_session_id")
    private UUID currentSessionId;
}
