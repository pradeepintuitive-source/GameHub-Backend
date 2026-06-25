package com.gamehub.room.api;

import com.gamehub.ai.domain.AiDifficulty;
import com.gamehub.ai.domain.AiType;
import com.gamehub.room.domain.RoomState;
import com.gamehub.room.domain.RoomType;
import com.gamehub.room.domain.RoomVisibility;
import com.gamehub.shared.domain.GameType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public final class RoomDtos {

    private RoomDtos() {
    }

    public record CreateRoomRequest(
            @NotNull GameType gameType,
            @NotNull RoomType roomType,
            @NotNull RoomVisibility visibility,
            @Min(2) @Max(12) int maxPlayers) {
    }

    public record JoinRoomRequest(
            String roomCode,
            UUID roomId) {
    }

    public record AddAiPlayerRequest(
            @NotBlank String displayName,
            @NotNull AiType aiType,
            @NotNull AiDifficulty aiDifficulty) {
    }

    public record RoomResponse(
            UUID id,
            String roomCode,
            UUID hostUserId,
            GameType gameType,
            RoomType roomType,
            RoomVisibility visibility,
            RoomState state,
            int maxPlayers,
            List<PlayerSummary> players,
            UUID currentSessionId) {
    }

    public record PlayerSummary(
            UUID id,
            UUID userId,
            String displayName,
            boolean connected,
            boolean aiControlled,
            AiType aiType,
            AiDifficulty aiDifficulty,
            int seatOrder) {
    }
}
