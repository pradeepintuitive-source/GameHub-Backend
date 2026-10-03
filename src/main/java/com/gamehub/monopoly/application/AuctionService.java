package com.gamehub.monopoly.application;

import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.monopoly.domain.MonopolyGameState;
import com.gamehub.monopoly.domain.PlayerAsset;
import com.gamehub.notification.application.NotificationService;
import com.gamehub.notification.domain.NotificationMessage;
import com.gamehub.player.infrastructure.PlayerEntity;
import com.gamehub.player.infrastructure.PlayerRepository;
import com.gamehub.player.infrastructure.UserEntity;
import com.gamehub.player.infrastructure.UserRepository;
import com.gamehub.shared.application.GameSessionService;
import com.gamehub.shared.infrastructure.GameSessionEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private final Map<UUID, Auction> auctions = new HashMap<>();
    private final GameSessionService gameSessionService;
    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;
    private final MonopolyGameService monopolyGameService;
    private final NotificationService notificationService;

    public static class Auction {
        public int tilePosition;
        public List<UUID> activePlayerIds = new ArrayList<>();
        public int currentBidderIndex = 0;
        public int highestBid = 0;
        public UUID highestBidder = null;

        public Auction(int tilePosition, List<UUID> players) {
            this.tilePosition = tilePosition;
            this.activePlayerIds = new ArrayList<>(players);
        }

        public UUID currentBidderId() {
            if (activePlayerIds.isEmpty()) {
                return null;
            }
            int idx = Math.floorMod(currentBidderIndex, activePlayerIds.size());
            return activePlayerIds.get(idx);
        }
    }

    public Optional<Auction> findAuction(UUID sessionId) {
        return Optional.ofNullable(auctions.get(sessionId));
    }

    public Map<String, Object> toPayload(Auction a) {
        if (a == null) {
            return null;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("tilePosition", a.tilePosition);
        body.put("tileIndex", a.tilePosition);
        body.put("activePlayerIds", a.activePlayerIds);
        body.put("currentBidderIndex", a.currentBidderIndex);
        body.put("highestBid", a.highestBid);
        body.put("highestBidder", a.highestBidder);
        body.put("highestBidderId", a.highestBidder);
        return body;
    }

    public synchronized Auction startAuction(UUID sessionId, int tilePosition) {
        GameSessionEntity session = gameSessionService.requireSession(sessionId);
        MonopolyGameState state = monopolyGameService.getState(session);
        List<PlayerEntity> players = playerRepository.findByRoomIdOrderBySeatOrder(session.getRoomId());

        // Humans only — AI seats would freeze the auction on "Waiting for bids".
        List<UUID> playerIds = players.stream()
                .filter(player -> !player.isAiControlled())
                .map(PlayerEntity::getId)
                .filter(id -> {
                    PlayerAsset asset = resolveAsset(state, id);
                    return asset != null;
                })
                .toList();
        if (playerIds.isEmpty()) {
            throw new BusinessRuleViolationException("No eligible players for auction");
        }

        List<UUID> ordered = new ArrayList<>(playerIds);
        UUID current = state.currentPlayerId();
        int startAt = ordered.indexOf(current);
        if (startAt > 0) {
            List<UUID> rotated = new ArrayList<>();
            rotated.addAll(ordered.subList(startAt, ordered.size()));
            rotated.addAll(ordered.subList(0, startAt));
            ordered = rotated;
        }

        Auction a = new Auction(tilePosition, ordered);
        a.currentBidderIndex = 0;
        auctions.put(sessionId, a);
        broadcastUpdate(session.getRoomId(), sessionId, a);
        return a;
    }

    public synchronized Auction placeBid(UUID sessionId, UUID actorPlayerId, int amount) {
        Auction a = auctions.get(sessionId);
        if (a == null) {
            throw new BusinessRuleViolationException("No active auction");
        }
        UUID expected = a.currentBidderId();
        if (expected == null || !expected.equals(actorPlayerId)) {
            throw new BusinessRuleViolationException("It is not your turn to bid");
        }
        if (amount <= a.highestBid) {
            throw new BusinessRuleViolationException("Bid must be higher than the current highest bid");
        }
        GameSessionEntity session = gameSessionService.requireSession(sessionId);
        MonopolyGameState state = monopolyGameService.getState(session);
        PlayerAsset asset = resolveAsset(state, actorPlayerId);
        if (asset == null || asset.cash() < amount) {
            throw new BusinessRuleViolationException("Insufficient funds to place this bid");
        }
        a.highestBid = amount;
        a.highestBidder = actorPlayerId;
        a.currentBidderIndex = (a.currentBidderIndex + 1) % Math.max(1, a.activePlayerIds.size());
        broadcastUpdate(session.getRoomId(), sessionId, a);
        return a;
    }

    public synchronized void passBid(UUID sessionId, UUID actorPlayerId) {
        Auction a = auctions.get(sessionId);
        if (a == null) {
            throw new BusinessRuleViolationException("No active auction");
        }
        UUID expected = a.currentBidderId();
        if (expected != null && !expected.equals(actorPlayerId)) {
            throw new BusinessRuleViolationException("It is not your turn to pass");
        }

        int removedIdx = a.activePlayerIds.indexOf(actorPlayerId);
        if (removedIdx < 0) {
            throw new BusinessRuleViolationException("You are not in this auction");
        }
        a.activePlayerIds.remove(removedIdx);
        if (a.currentBidderIndex > removedIdx) {
            a.currentBidderIndex--;
        }
        if (!a.activePlayerIds.isEmpty()) {
            a.currentBidderIndex = Math.floorMod(a.currentBidderIndex, a.activePlayerIds.size());
        }

        if (a.activePlayerIds.size() <= 1) {
            GameSessionEntity session = gameSessionService.requireSession(sessionId);
            // Only award when someone actually bid. Passing last must NOT gift the property.
            if (a.highestBidder != null && a.highestBid > 0) {
                var req = new com.gamehub.monopoly.api.MonopolyDtos.MonopolyActionRequest(
                        com.gamehub.monopoly.domain.MonopolyActionType.AUCTION,
                        a.tilePosition,
                        a.highestBidder,
                        a.highestBid,
                        Map.of());
                var state = monopolyGameService.getState(session);
                UUID currentPlayerId = state.currentPlayerId();
                Optional<PlayerEntity> actorPlayer = playerRepository.findById(currentPlayerId);
                if (actorPlayer.isPresent()) {
                    Optional<UserEntity> user = userRepository.findById(actorPlayer.get().getUserId());
                    UserEntity ue = user.orElse(null);
                    com.gamehub.security.infrastructure.GameHubUserPrincipal principal = ue == null
                            ? null
                            : new com.gamehub.security.infrastructure.GameHubUserPrincipal(
                                    ue.getId(), ue.getUsername(), ue.getPasswordHash(), ue.isGuest(), ue.roleSet());
                    monopolyGameService.processAction(session.getRoomId(), session, principal, currentPlayerId, req);
                }
            } else {
                // No bids — property stays with the bank; notify clients and record a log line.
                monopolyGameService.recordAuctionUnsold(session.getRoomId(), session, a.tilePosition);
            }
            auctions.remove(sessionId);
            broadcastUpdate(session.getRoomId(), sessionId, null);
        } else {
            GameSessionEntity session = gameSessionService.requireSession(sessionId);
            broadcastUpdate(session.getRoomId(), sessionId, a);
        }
    }

    private PlayerAsset resolveAsset(MonopolyGameState state, UUID playerId) {
        if (playerId == null || state.assets() == null) {
            return null;
        }
        PlayerAsset direct = state.assets().get(playerId);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<UUID, PlayerAsset> entry : state.assets().entrySet()) {
            if (playerId.equals(entry.getKey())
                    || (entry.getValue() != null && playerId.equals(entry.getValue().playerId()))) {
                return entry.getValue();
            }
        }
        for (PlayerAsset asset : state.assets().values()) {
            if (asset != null && playerId.equals(asset.playerId())) {
                return asset;
            }
        }
        return null;
    }

    private void broadcastUpdate(UUID roomId, UUID sessionId, Auction a) {
        notificationService.sendToTopic(
                "/topic/game/" + roomId,
                new NotificationMessage("AUCTION_UPDATE", roomId, sessionId, toPayload(a), Instant.now()));
    }
}
