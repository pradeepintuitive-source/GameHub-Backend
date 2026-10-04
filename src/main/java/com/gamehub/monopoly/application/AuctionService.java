package com.gamehub.monopoly.application;

import com.gamehub.monopoly.api.MonopolyDtos;
import com.gamehub.monopoly.domain.MonopolyActionType;
import com.gamehub.monopoly.domain.MonopolyGameState;
import com.gamehub.monopoly.domain.PlayerAsset;
import com.gamehub.notification.application.NotificationService;
import com.gamehub.notification.domain.NotificationMessage;
import com.gamehub.player.infrastructure.PlayerEntity;
import com.gamehub.player.infrastructure.PlayerRepository;
import com.gamehub.player.infrastructure.UserEntity;
import com.gamehub.player.infrastructure.UserRepository;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.shared.application.GameSessionService;
import com.gamehub.shared.infrastructure.GameSessionEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

/**
 * Server-authoritative auction service.
 *
 * <h3>Identity model</h3>
 * All player IDs in this service use the <em>PlayerEntity seat ID</em> (the same UUID stored in
 * {@code MonopolyGameState.assets} keys and {@code currentPlayerId}).  The controller resolves
 * the JWT/STOMP principal's {@code userId} → {@code playerId} via
 * {@link GameSessionService#resolveActorPlayerId} before calling into this service.
 *
 * <h3>Timer</h3>
 * Each bidder gets exactly 20 seconds.  The deadline is stored as
 * {@code turnDeadlineAt} (epoch millis) so that refreshing clients can compute
 * remaining time without resetting to a fresh 20 s window.  The timer is
 * cancelled and rescheduled on every START / PLACE_BID / PASS.
 *
 * <h3>Error codes</h3>
 * All validation errors carry a machine-readable error code (never a generic
 * "It is not this player's turn" from the main turn engine):
 * <ul>
 *   <li>AUCTION_NOT_ACTIVE</li>
 *   <li>AUCTION_NOT_YOUR_BID</li>
 *   <li>AUCTION_AMOUNT_NOT_MULTIPLE_OF_100</li>
 *   <li>AUCTION_BID_TOO_LOW</li>
 *   <li>AUCTION_INSUFFICIENT_CASH</li>
 *   <li>AUCTION_INVALID_TILE</li>
 * </ul>
 *
 * <h3>Start order</h3>
 * {@code activePlayerIds} is rotated so the declining player (currentPlayerId) bids first,
 * then continues in seat order.  This is documented and kept stable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionService {

    /** Auction duration per bidder in milliseconds. */
    private static final long TURN_DURATION_MS = 20_000L;

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------

    private final GameSessionService gameSessionService;
    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;
    private final MonopolyGameService monopolyGameService;
    private final NotificationService notificationService;
    /** Bean defined in WebSocketConfig as heartBeatScheduler. */
    private final ThreadPoolTaskScheduler heartBeatScheduler;

    // -----------------------------------------------------------------------
    // In-memory auction state  (sessionId → Auction)
    // -----------------------------------------------------------------------

    private final ConcurrentHashMap<UUID, Auction> auctions = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Public inner model
    // -----------------------------------------------------------------------

    /**
     * Mutable auction snapshot kept in memory.
     * All mutations must be performed under {@code synchronized(AuctionService.this)}.
     */
    public static class Auction {
        public final UUID sessionId;
        public final UUID roomId;
        public int tilePosition;
        /** Ordered list of player-seat UUIDs still in the auction. */
        public List<UUID> activePlayerIds = new ArrayList<>();
        public int currentBidderIndex = 0;
        public int highestBid = 0;
        public UUID highestBidderId = null;
        /** Ordered bid history for broadcast. */
        public final List<BidEntry> bids = new ArrayList<>();
        /** Epoch millis when this bidder's window started. */
        public long startedAt;
        /** Epoch millis when this bidder's window expires. */
        public long turnDeadlineAt;
        /** Scheduled timeout — cancelled on every transition. Not serialized. */
        public transient ScheduledFuture<?> timeoutFuture;

        public Auction(UUID sessionId, UUID roomId, int tilePosition, List<UUID> players) {
            this.sessionId = sessionId;
            this.roomId = roomId;
            this.tilePosition = tilePosition;
            this.activePlayerIds = new ArrayList<>(players);
            resetTimer();
        }

        /** Returns the seat UUID whose turn it currently is, or {@code null} if empty. */
        public UUID currentBidderId() {
            if (activePlayerIds.isEmpty()) {
                return null;
            }
            return activePlayerIds.get(Math.floorMod(currentBidderIndex, activePlayerIds.size()));
        }

        /** Restarts the 20-second window; call after every START / PLACE_BID / PASS. */
        public void resetTimer() {
            long now = Instant.now().toEpochMilli();
            this.startedAt = now;
            this.turnDeadlineAt = now + TURN_DURATION_MS;
        }
    }

    /** A single bid record for the broadcast history. */
    public record BidEntry(UUID playerId, int amount) {}

    // -----------------------------------------------------------------------
    // Public API — controller resolves userId→playerId before calling here
    // -----------------------------------------------------------------------

    /**
     * START: only the monopoly-turn's current player may start the auction.
     *
     * @param sessionId     game session UUID
     * @param actorPlayerId resolved player-seat UUID from JWT principal
     * @param tilePosition  tile the current player just declined to buy
     */
    public synchronized Auction startAuction(UUID sessionId, UUID actorPlayerId, int tilePosition) {
        GameSessionEntity session = gameSessionService.requireSession(sessionId);
        MonopolyGameState state = monopolyGameService.getState(session);

        // Only the Monopoly-turn current player may initiate.
        if (!state.currentPlayerId().equals(actorPlayerId)) {
            throw new AuctionException("AUCTION_NOT_YOUR_BID",
                    "Only the current Monopoly-turn player can start an auction");
        }

        validateAuctionTile(state, tilePosition);

        // Build active-player list: non-AI, non-bankrupt players in seat order,
        // rotated so the declining player bids first.
        List<PlayerEntity> players = playerRepository.findByRoomIdOrderBySeatOrder(session.getRoomId());
        List<UUID> ordered = buildActivePlayerIds(state, players, actorPlayerId);

        if (ordered.isEmpty()) {
            throw new AuctionException("AUCTION_NOT_ACTIVE", "No eligible players for auction");
        }

        cancelExistingAuction(sessionId);

        Auction a = new Auction(sessionId, session.getRoomId(), tilePosition, ordered);
        auctions.put(sessionId, a);
        scheduleTimeout(a);
        broadcastUpdate(a);
        return a;
    }

    /**
     * PLACE_BID: only {@code activePlayerIds[currentBidderIndex]} may bid.
     *
     * @param sessionId     game session UUID
     * @param actorPlayerId resolved player-seat UUID from JWT principal
     * @param amount        bid amount
     */
    public synchronized Auction placeBid(UUID sessionId, UUID actorPlayerId, int amount) {
        Auction a = requireAuction(sessionId);
        assertCurrentBidder(a, actorPlayerId);

        if (amount % 100 != 0) {
            throw new AuctionException("AUCTION_AMOUNT_NOT_MULTIPLE_OF_100",
                    "Bid amount must be a multiple of \u20b9100");
        }

        int minBid = (a.highestBid == 0) ? 100 : a.highestBid + 100;
        if (amount < minBid) {
            throw new AuctionException("AUCTION_BID_TOO_LOW",
                    "Minimum bid is \u20b9" + minBid + " (current high: \u20b9" + a.highestBid + ")");
        }

        GameSessionEntity session = gameSessionService.requireSession(sessionId);
        MonopolyGameState state = monopolyGameService.getState(session);
        PlayerAsset asset = resolveAsset(state, actorPlayerId);
        if (asset == null || asset.cash() < amount) {
            throw new AuctionException("AUCTION_INSUFFICIENT_CASH",
                    "Insufficient cash to place this bid");
        }

        a.bids.add(new BidEntry(actorPlayerId, amount));
        a.highestBid = amount;
        a.highestBidderId = actorPlayerId;
        a.currentBidderIndex = (a.currentBidderIndex + 1) % Math.max(1, a.activePlayerIds.size());

        cancelTimeout(a);
        a.resetTimer();
        scheduleTimeout(a);

        broadcastUpdate(a);
        return a;
    }

    /**
     * PASS: remove the current bidder from the auction.
     *
     * @param sessionId       game session UUID
     * @param actorPlayerId   resolved player-seat UUID (or current bidder's UUID when called by timeout)
     * @param isTimeoutPass   {@code true} when the server fires this on behalf of an inactive bidder;
     *                        skips the current-bidder ownership assertion so the check is not re-run
     *                        inside the synchronized block that already owns the monitor.
     */
    public synchronized void passBid(UUID sessionId, UUID actorPlayerId, boolean isTimeoutPass) {
        Auction a = auctions.get(sessionId);
        if (a == null) {
            // Already settled — duplicate or stale PASS; safe to ignore.
            log.debug("passBid: no active auction for session {}, ignoring stale pass", sessionId);
            return;
        }

        if (!isTimeoutPass) {
            assertCurrentBidder(a, actorPlayerId);
        }

        // Always remove the current bidder (voluntary or timeout).
        UUID removedPlayer = a.currentBidderId();
        if (removedPlayer == null) {
            settle(a);
            return;
        }

        int removedIdx = a.activePlayerIds.indexOf(removedPlayer);
        if (removedIdx < 0) {
            settle(a);
            return;
        }

        a.activePlayerIds.remove(removedIdx);

        if (a.currentBidderIndex > removedIdx) {
            a.currentBidderIndex--;
        }
        if (!a.activePlayerIds.isEmpty()) {
            a.currentBidderIndex = Math.floorMod(a.currentBidderIndex, a.activePlayerIds.size());
        }

        if (a.activePlayerIds.size() <= 1) {
            settle(a);
        } else {
            cancelTimeout(a);
            a.resetTimer();
            scheduleTimeout(a);
            broadcastUpdate(a);
        }
    }

    // -----------------------------------------------------------------------
    // Convenience overload for client-initiated PASS (not timeout)
    // -----------------------------------------------------------------------

    public void passBid(UUID sessionId, UUID actorPlayerId) {
        passBid(sessionId, actorPlayerId, false);
    }

    // -----------------------------------------------------------------------
    // Lookup helpers (used by controller and snapshot builder)
    // -----------------------------------------------------------------------

    /** Returns the live auction for a session, or {@code empty} if none. */
    public UUID currentBidderId(UUID sessionId) {
        Auction auction = auctions.get(sessionId);
        return auction == null ? null : auction.currentBidderId();
    }

    public Optional<Auction> findAuction(UUID sessionId) {
        return Optional.ofNullable(auctions.get(sessionId));
    }

    /** Returns {@code true} if there is an active auction for the session. */
    public boolean hasActiveAuction(UUID sessionId) {
        return auctions.containsKey(sessionId);
    }

    // -----------------------------------------------------------------------
    // Payload builder — used for broadcasts and full snapshot hydration
    // -----------------------------------------------------------------------

    /**
     * Builds the auction payload map for inclusion in {@code AUCTION_UPDATE} messages
     * and full game-state snapshots.  Returns {@code null} if {@code a} is {@code null}.
     */
    public Map<String, Object> toPayload(Auction a) {
        if (a == null) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tilePosition", a.tilePosition);
        body.put("bids", a.bids.stream().map(b -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("playerId", b.playerId());
            entry.put("amount", b.amount());
            return entry;
        }).toList());
        body.put("currentBidderIndex", a.currentBidderIndex);
        body.put("activePlayerIds", a.activePlayerIds);
        body.put("highestBid", a.highestBid);
        body.put("highestBidderId", a.highestBidderId);
        body.put("startedAt", a.startedAt);
        body.put("turnDeadlineAt", a.turnDeadlineAt);
        return body;
    }

    // -----------------------------------------------------------------------
    // Settle
    // -----------------------------------------------------------------------

    private void settle(Auction a) {
        cancelTimeout(a);
        UUID sessionId = a.sessionId;
        // Drop the live auction before the settlement broadcast, so that
        // snapshot does not paint the auction panel back open.
        auctions.remove(sessionId);

        try {
            GameSessionEntity session = gameSessionService.requireSession(sessionId);

            if (a.highestBidderId != null && a.highestBid >= 100) {
                MonopolyGameState state = monopolyGameService.getState(session);
                UUID currentPlayerId = state.currentPlayerId();

                MonopolyDtos.MonopolyActionRequest req = new MonopolyDtos.MonopolyActionRequest(
                        MonopolyActionType.AUCTION,
                        a.tilePosition,
                        a.highestBidderId,
                        a.highestBid,
                        Map.of());

                GameHubUserPrincipal principal = buildPrincipalForPlayer(currentPlayerId);
                monopolyGameService.processAction(session.getRoomId(), session, principal, currentPlayerId, req);
                log.info("Auction settled: winner={} amount={} tile={}", a.highestBidderId, a.highestBid, a.tilePosition);

            } else {
                monopolyGameService.recordAuctionUnsold(session.getRoomId(), session, a.tilePosition);
                log.info("Auction unsold: tile={}", a.tilePosition);
            }
        } catch (Exception ex) {
            log.error("Auction settlement failed for session {}: {}", sessionId, ex.getMessage(), ex);
        } finally {
            broadcastAuctionNull(a.roomId, sessionId);
        }
    }

    // -----------------------------------------------------------------------
    // Timer management
    // -----------------------------------------------------------------------

    private void scheduleTimeout(Auction a) {
        UUID sessionId = a.sessionId;
        UUID currentBidder = a.currentBidderId();
        long deadlineAt = a.turnDeadlineAt;   // capture for stale-timer guard

        ScheduledFuture<?> future = heartBeatScheduler.schedule(() -> {
            log.info("Auction timeout: session={} bidder={}", sessionId, currentBidder);
            // Re-acquire lock; the synchronized passBid will check if auction is still live.
            try {
                synchronized (AuctionService.this) {
                    Auction live = auctions.get(sessionId);
                    if (live == null) {
                        return;   // already settled
                    }
                    // Stale-timer guard: only act if the deadline hasn't been extended by a bid/pass.
                    if (live.turnDeadlineAt != deadlineAt) {
                        log.debug("Stale auction timer ignored: session={}", sessionId);
                        return;
                    }
                    passBid(sessionId, currentBidder, true);
                }
            } catch (Exception ex) {
                log.error("Auction timeout handler failed: session={} error={}", sessionId, ex.getMessage(), ex);
            }
        }, Instant.ofEpochMilli(deadlineAt));

        a.timeoutFuture = future;
    }

    private void cancelTimeout(Auction a) {
        if (a.timeoutFuture != null && !a.timeoutFuture.isDone()) {
            a.timeoutFuture.cancel(false);
            a.timeoutFuture = null;
        }
    }

    private void cancelExistingAuction(UUID sessionId) {
        Auction old = auctions.remove(sessionId);
        if (old != null) {
            cancelTimeout(old);
        }
    }

    // -----------------------------------------------------------------------
    // Broadcast helpers
    // -----------------------------------------------------------------------

    private void broadcastUpdate(Auction a) {
        // NotificationMessage already has a payload field. Nesting another
        // payload here made clients read an empty wrapper (GO, no bidders).
        notificationService.sendToTopic(
                "/topic/game/" + a.roomId,
                new NotificationMessage("AUCTION_UPDATE", a.roomId, a.sessionId, toPayload(a), Instant.now()));
    }

    private void broadcastAuctionNull(UUID roomId, UUID sessionId) {
        notificationService.sendToTopic(
                "/topic/game/" + roomId,
                new NotificationMessage("AUCTION_UPDATE", roomId, sessionId, null, Instant.now()));
    }

    // -----------------------------------------------------------------------
    // Validation helpers
    // -----------------------------------------------------------------------

    private void validateAuctionTile(MonopolyGameState state, int tilePosition) {
        try {
            var tile = state.board().tileAt(tilePosition);
            if (tile == null) {
                throw new AuctionException("AUCTION_INVALID_TILE", "No tile at position " + tilePosition);
            }
        } catch (AuctionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuctionException("AUCTION_INVALID_TILE", "Invalid tile position: " + tilePosition);
        }
        if (state.owners().containsKey(tilePosition)) {
            throw new AuctionException("AUCTION_INVALID_TILE", "Tile at position " + tilePosition + " is already owned");
        }
    }

    private Auction requireAuction(UUID sessionId) {
        Auction a = auctions.get(sessionId);
        if (a == null) {
            throw new AuctionException("AUCTION_NOT_ACTIVE", "No active auction for this session");
        }
        return a;
    }

    private void assertCurrentBidder(Auction a, UUID actorPlayerId) {
        UUID expected = a.currentBidderId();
        if (expected == null || !expected.equals(actorPlayerId)) {
            throw new AuctionException("AUCTION_NOT_YOUR_BID",
                    "It is not your turn to bid (current bidder: " + expected + ")");
        }
    }

    /**
     * Builds the active-player list.
     * Seat order is the stable canonical order; we rotate so the declining player bids first.
     * Non-AI and players present in the game-state assets are included.
     */
    private List<UUID> buildActivePlayerIds(MonopolyGameState state, List<PlayerEntity> players,
            UUID startingPlayerId) {
        List<UUID> eligible = new ArrayList<>();
        for (PlayerEntity p : players) {
            if (p.isAiControlled()) {
                continue;
            }
            if (resolveAsset(state, p.getId()) == null) {
                continue;
            }
            eligible.add(p.getId());
        }

        int startIdx = eligible.indexOf(startingPlayerId);
        if (startIdx > 0) {
            List<UUID> rotated = new ArrayList<>();
            rotated.addAll(eligible.subList(startIdx, eligible.size()));
            rotated.addAll(eligible.subList(0, startIdx));
            return rotated;
        }
        return eligible;
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
        return null;
    }

    // -----------------------------------------------------------------------
    // Principal builder for engine passthrough
    // -----------------------------------------------------------------------

    private GameHubUserPrincipal buildPrincipalForPlayer(UUID playerId) {
        return playerRepository.findById(playerId)
                .flatMap(p -> userRepository.findById(p.getUserId()))
                .map(u -> new GameHubUserPrincipal(
                        u.getId(), u.getUsername(), u.getPasswordHash(), u.isGuest(), u.roleSet()))
                .orElse(null);
    }

    // -----------------------------------------------------------------------
    // Structured exception
    // -----------------------------------------------------------------------

    /**
     * Auction-specific validation exception with a machine-readable {@code errorCode}.
     * The controller catches this to send the right code via {@code /user/queue/acks}.
     */
    public static class AuctionException extends RuntimeException {
        private final String errorCode;

        public AuctionException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
