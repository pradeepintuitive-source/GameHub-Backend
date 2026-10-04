package com.gamehub.monopoly.infrastructure;

import com.gamehub.monopoly.application.AuctionService;
import com.gamehub.monopoly.application.AuctionService.AuctionException;
import com.gamehub.room.application.RoomService;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.shared.application.GameSessionService;
import com.gamehub.websocket.application.ActionAckService;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/**
 * STOMP controller for the server-authoritative auction.
 *
 * <h3>Destination</h3>
 * Client sends to {@code /app/games/{sessionId}/auction}.
 *
 * <h3>Identity</h3>
 * The actor is <em>always</em> derived from the authenticated JWT principal's
 * {@code userId} (which equals the JWT "sub" claim) and then resolved to a
 * player-seat UUID via {@link GameSessionService#resolveActorPlayerId}.
 * Client-supplied player IDs in the message body are <strong>never</strong> trusted.
 *
 * <h3>Error codes sent to {@code /user/queue/acks}</h3>
 * <ul>
 *   <li>AUTH_REQUIRED – unauthenticated connection</li>
 *   <li>INVALID_REQUEST – missing required field</li>
 *   <li>AUCTION_NOT_ACTIVE, AUCTION_NOT_YOUR_BID, AUCTION_AMOUNT_NOT_MULTIPLE_OF_100,
 *       AUCTION_BID_TOO_LOW, AUCTION_INSUFFICIENT_CASH, AUCTION_INVALID_TILE – service errors</li>
 *   <li>SERVER_ERROR – unexpected exception</li>
 * </ul>
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AuctionStompController {

    private final AuctionService auctionService;
    private final GameSessionService gameSessionService;
    private final RoomService roomService;
    private final ActionAckService actionAckService;

    /** Inbound STOMP message shape from the frontend. */
    public record AuctionMessage(String requestId, String action, Integer tilePosition, Integer amount) {}

    @MessageMapping("/games/{sessionId}/auction")
    public void handle(@DestinationVariable UUID sessionId, AuctionMessage msg, Principal principal) {
        if (msg == null || msg.action() == null) {
            return;
        }

        String act = msg.action();

        // ----------------------------------------------------------------
        // Resolve authenticated user — must be present for all actions.
        // ----------------------------------------------------------------
        GameHubUserPrincipal userPrincipal = resolveUserPrincipal(principal);
        if (userPrincipal == null) {
            actionAckService.sendAck(null, msg.requestId(), act, false,
                    "AUTH_REQUIRED", "Authentication required", Map.of());
            return;
        }

        UUID userId = userPrincipal.userId();   // auth user id == JWT sub

        try {
            // Resolve player-seat UUID from JWT userId — never trust a client-supplied id.
            var session = gameSessionService.requireSession(sessionId);
            UUID actorPlayerId = gameSessionService.resolveActorPlayerId(session.getRoomId(), userId);

            switch (act) {
                case "START" -> {
                    if (msg.tilePosition() == null) {
                        actionAckService.sendAck(userId, msg.requestId(), act, false,
                                "INVALID_REQUEST", "tilePosition is required for START", Map.of());
                        return;
                    }
                    auctionService.startAuction(sessionId, actorPlayerId, msg.tilePosition());
                    actionAckService.sendAck(userId, msg.requestId(), act, true, null, null, Map.of());
                }

                case "PLACE_BID" -> {
                    actorPlayerId = localAuctionActor(session.getRoomId(), userId, actorPlayerId, sessionId);
                    if (msg.amount() == null) {
                        actionAckService.sendAck(userId, msg.requestId(), act, false,
                                "INVALID_REQUEST", "amount is required for PLACE_BID", Map.of());
                        return;
                    }
                    auctionService.placeBid(sessionId, actorPlayerId, msg.amount());
                    actionAckService.sendAck(userId, msg.requestId(), act, true, null, null, Map.of());
                }

                case "PASS" -> {
                    actorPlayerId = localAuctionActor(session.getRoomId(), userId, actorPlayerId, sessionId);
                    auctionService.passBid(sessionId, actorPlayerId);
                    actionAckService.sendAck(userId, msg.requestId(), act, true, null, null, Map.of());
                }

                default -> actionAckService.sendAck(userId, msg.requestId(), act, false,
                        "INVALID_REQUEST", "Unsupported auction action: " + act, Map.of());
            }

        } catch (AuctionException ex) {
            // Send the structured auction error code; do NOT fall through to generic GAME_TURN_ERROR.
            log.debug("Auction validation error: userId={} action={} code={} msg={}",
                    userId, act, ex.getErrorCode(), ex.getMessage());
            actionAckService.sendAck(userId, msg.requestId(), act, false,
                    ex.getErrorCode(), ex.getMessage(), Map.of());

        } catch (Exception ex) {
            log.error("Auction handler error: userId={} action={} session={} error={}",
                    userId, act, sessionId, ex.getMessage(), ex);
            actionAckService.sendAck(userId, msg.requestId(), act, false,
                    "SERVER_ERROR", ex.getMessage(), Map.of());
        }
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private UUID localAuctionActor(UUID roomId, UUID userId, UUID actorPlayerId, UUID sessionId) {
        if (!roomService.isLocalHost(roomId, userId)) {
            return actorPlayerId;
        }
        UUID bidderId = auctionService.currentBidderId(sessionId);
        return bidderId != null ? bidderId : actorPlayerId;
    }

    private GameHubUserPrincipal resolveUserPrincipal(Principal principal) {
        if (principal instanceof Authentication auth
                && auth.getPrincipal() instanceof GameHubUserPrincipal up) {
            return up;
        }
        return null;
    }

    /** Expose userId from principal for callers that tolerate null. */
    private UUID resolveUserId(Principal principal) {
        GameHubUserPrincipal up = resolveUserPrincipal(principal);
        return up == null ? null : up.userId();
    }
}
