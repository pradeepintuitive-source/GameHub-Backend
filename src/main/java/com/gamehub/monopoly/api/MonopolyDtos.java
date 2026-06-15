package com.gamehub.monopoly.api;

import com.gamehub.monopoly.domain.MonopolyActionType;
import com.gamehub.monopoly.domain.MonopolyPhase;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MonopolyDtos {

    private MonopolyDtos() {
    }

    public record MonopolyActionRequest(
            @NotNull MonopolyActionType type,
            Integer tilePosition,
            UUID targetPlayerId,
            Integer amount,
            Map<String, String> metadata) {
    }

    public record MonopolyStateResponse(
            UUID sessionId,
            MonopolyPhase phase,
            UUID currentPlayerId,
            int currentTurn,
            int lastDiceTotal,
            Map<UUID, AssetResponse> assets,
            Map<Integer, UUID> owners,
            Map<Integer, DevelopmentResponse> developments,
            Set<Integer> mortgagedTiles,
            List<String> log) {
    }

    public record AssetResponse(
            UUID playerId,
            int cash,
            int position,
            boolean inJail,
            int jailTurns,
            Set<Integer> ownedTilePositions) {
    }

    public record DevelopmentResponse(
            int houses,
            boolean hotel) {
    }
}
