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
        IndianEvent activeEvent,
        PendingDebt pendingDebt,
        Set<UUID> bankruptPlayerIds,
        List<PendingDebt> debtQueue,
        PendingSale pendingSale) implements GameState {

    public MonopolyGameState {
        bankruptPlayerIds = bankruptPlayerIds == null ? Set.of() : Set.copyOf(bankruptPlayerIds);
        debtQueue = debtQueue == null ? List.of() : List.copyOf(debtQueue);
    }

    /** Backward-compatible constructor for call sites that omit events and debt. */
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
                null,
                null,
                Set.of(),
                List.of(),
                null);
    }

    /** Backward-compatible constructor for call sites that omit debt. */
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
            List<String> log,
            IndianEvent activeEvent) {
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
                activeEvent,
                null,
                Set.of(),
                List.of(),
                null);
    }

    public MonopolyGameState withLog(List<String> nextLog) {
        return new MonopolyGameState(
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
                nextLog,
                activeEvent,
                pendingDebt,
                bankruptPlayerIds,
                debtQueue,
                pendingSale);
    }

    @Override
    public GameType gameType() {
        return GameType.MONOPOLY;
    }
}
