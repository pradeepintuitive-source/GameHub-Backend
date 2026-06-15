package com.gamehub.room.domain;

import com.gamehub.player.domain.Player;
import com.gamehub.shared.domain.GameType;
import java.util.List;
import java.util.UUID;

public record Room(
        UUID id,
        String roomCode,
        UUID hostUserId,
        GameType gameType,
        RoomType roomType,
        RoomVisibility visibility,
        RoomState state,
        int maxPlayers,
        List<Player> players,
        UUID currentSessionId) {
}
