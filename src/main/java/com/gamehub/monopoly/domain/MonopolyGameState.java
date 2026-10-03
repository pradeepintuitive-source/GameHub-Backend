package com.gamehub.monopoly.domain;

import com.gamehub.shared.domain.GameState;
import com.gamehub.shared.domain.GameType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record MonopolyGameState(
        UUID sessionId,
        MonopolyPhase phase,
        UUID currentPlayerId,
        int currentTurn,
        int lastDiceTotal,
        Board board,
        Map<UUID, PlayerAsset> assets,
        Map<Integer, UUID> owners,
        Map<Integer, PropertyDevelopment> developments,
        Set<Integer> mortgagedTiles,
        List<String> log,
        IndianEvent activeEvent) implements GameState {

    /** Backward-compatible constructor for call sites that omit events. */
    public MonopolyGameState(
            UUID sessionId,
            MonopolyPhase phase,
            UUID currentPlayerId,
            int currentTurn,
            int lastDiceTotal,
            Board board,
            Map<UUID, PlayerAsset> assets,
            Map<Integer, UUID> owners,
            Map<Integer, PropertyDevelopment> developments,
            Set<Integer> mortgagedTiles,
            List<String> log) {
        this(
                sessionId,
                phase,
                currentPlayerId,
                currentTurn,
                lastDiceTotal,
                board,
                assets,
                owners,
                developments,
                mortgagedTiles,
                log,
                null);
    }

    @Override
    public GameType gameType() {
        return GameType.MONOPOLY;
    }
}
