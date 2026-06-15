package com.gamehub.shared.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamehub.shared.domain.GameType;
import com.gamehub.shared.domain.SessionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class GameDtos {

    private GameDtos() {
    }

    public record StartGameRequest(
            @NotNull UUID roomId) {
    }

    public record SaveGameRequest(
            @NotNull UUID sessionId,
            @NotBlank String reason) {
    }

    public record GameSessionResponse(
            UUID sessionId,
            UUID roomId,
            GameType gameType,
            SessionStatus status,
            long saveVersion,
            JsonNode state) {
    }
}
