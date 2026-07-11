package com.gamehub.monopoly.application;

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
    }

    public synchronized Auction startAuction(UUID sessionId, int tilePosition) {
        GameSessionEntity session = gameSessionService.requireSession(sessionId);
        List<PlayerEntity> players = playerRepository.findByRoomIdOrderBySeatOrder(session.getRoomId());
        List<UUID> playerIds = players.stream().map(PlayerEntity::getId).toList();
        Auction a = new Auction(tilePosition, playerIds);
        auctions.put(sessionId, a);
        broadcastUpdate(session.getRoomId(), sessionId, a);
        return a;
    }

    public synchronized Auction placeBid(UUID sessionId, UUID actorPlayerId, int amount) {
        Auction a = auctions.get(sessionId);
        if (a == null) throw new IllegalStateException("No active auction");
        a.highestBid = amount;
        a.highestBidder = actorPlayerId;
        // advance bidder index to next
        a.currentBidderIndex = (a.currentBidderIndex + 1) % Math.max(1, a.activePlayerIds.size());
        GameSessionEntity session = gameSessionService.requireSession(sessionId);
        broadcastUpdate(session.getRoomId(), sessionId, a);
        return a;
    }

    public synchronized void passBid(UUID sessionId, UUID actorPlayerId) {
        Auction a = auctions.get(sessionId);
        if (a == null) throw new IllegalStateException("No active auction");
        a.activePlayerIds.remove(actorPlayerId);
        if (a.activePlayerIds.size() <= 1) {
            // resolve auction
            UUID winner = a.highestBidder != null ? a.highestBidder : (a.activePlayerIds.isEmpty() ? null : a.activePlayerIds.get(0));
            int amount = a.highestBid;
            // finalize by invoking monopoly service to apply AUCTION action
            GameSessionEntity session = gameSessionService.requireSession(sessionId);
            if (winner != null) {
                // resolve using monopolyGameService: create action request and process
                var req = new com.gamehub.monopoly.api.MonopolyDtos.MonopolyActionRequest(
                        com.gamehub.monopoly.domain.MonopolyActionType.AUCTION,
                        a.tilePosition,
                        winner,
                        amount,
                        Map.of());
                // determine actor principal info by looking up the current player's user
                var state = monopolyGameService.getState(session);
                UUID currentPlayerId = state.currentPlayerId();
                Optional<PlayerEntity> actorPlayer = playerRepository.findById(currentPlayerId);
                if (actorPlayer.isPresent()) {
                    Optional<UserEntity> user = userRepository.findById(actorPlayer.get().getUserId());
                    UserEntity ue = user.orElse(null);
                    com.gamehub.security.infrastructure.GameHubUserPrincipal principal = ue == null ? null :
                            new com.gamehub.security.infrastructure.GameHubUserPrincipal(
                                    ue.getId(), ue.getUsername(), ue.getPasswordHash(), ue.isGuest(), ue.roleSet());
                    monopolyGameService.processAction(session.getRoomId(), session, principal, currentPlayerId, req);
                }
            }
            auctions.remove(sessionId);
            broadcastUpdate(session.getRoomId(), sessionId, null);
        } else {
            GameSessionEntity session = gameSessionService.requireSession(sessionId);
            broadcastUpdate(session.getRoomId(), sessionId, a);
        }
    }

    private void broadcastUpdate(UUID roomId, UUID sessionId, Auction a) {
        Object payload = a == null ? null : Map.of(
                "tilePosition", a.tilePosition,
                "activePlayerIds", a.activePlayerIds,
                "currentBidderIndex", a.currentBidderIndex,
                "highestBid", a.highestBid,
                "highestBidder", a.highestBidder
        );
        notificationService.sendToTopic("/topic/game/" + roomId, new NotificationMessage("AUCTION_UPDATE", roomId, sessionId, payload, Instant.now()));
    }
}
