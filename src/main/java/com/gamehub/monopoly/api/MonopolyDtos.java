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
            List<String> log,
            IndianEventResponse activeEvent,
            /** Tile whose buy offer was closed (e.g. auction ended unsold); null if none. */
            Integer declinedPurchaseTile,
            /**
             * Live auction state, if an auction is currently in progress for this session.
             * Clients use {@code turnDeadlineAt} (epoch millis) to show the remaining time
             * without resetting to a fresh 20-second window on page refresh.
             * Null when no auction is active.
             */
            Map<String, Object> auction,
            PendingDebtResponse pendingDebt,
            PendingSaleResponse pendingSale,
            UUID winnerId) {
    }

    public record PendingDebtResponse(UUID debtorId, UUID creditorId, int amount, String reason) {
    }

    public record PendingSaleResponse(UUID sellerId, UUID buyerId, int tilePosition, int price) {
    }

    public record IndianEventResponse(
            String id,
            String title,
            String description,
            int expiresOnTurn) {
    }

    public record AssetResponse(
            UUID playerId,
            int cash,
            int position,
            boolean inJail,
            int jailTurns,
            Set<Integer> ownedTilePositions,
            boolean bankrupt) {
    }

    public record DevelopmentResponse(
            int houses,
            boolean hotel) {
    }
}
