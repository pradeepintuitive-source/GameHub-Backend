package com.gamehub.monopoly.infrastructure;

import com.gamehub.monopoly.application.AuctionService;
import com.gamehub.shared.application.GameSessionService;
import com.gamehub.player.infrastructure.PlayerRepository;
import com.gamehub.player.infrastructure.UserRepository;
import com.gamehub.websocket.application.ActionAckService;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class AuctionStompController {

    private final AuctionService auctionService;
    private final GameSessionService gameSessionService;
    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;
    private final ActionAckService actionAckService;

    public static record AuctionMessage(String requestId, String action, Integer tilePosition, Integer amount) {
    }

    @MessageMapping("/games/{sessionId}/auction")
    public void handle(@DestinationVariable UUID sessionId, AuctionMessage msg, Principal principal) {
        if (msg == null || msg.action() == null) {
            return;
        }
        String act = msg.action();
        try {
            switch (act) {
                case "START":
                    if (msg.tilePosition() == null) {
                        actionAckService.sendAck(resolveUserId(principal), msg.requestId(), act, false, "INVALID_REQUEST", "tilePosition is required", Map.of());
                        return;
                    }
                    auctionService.startAuction(sessionId, msg.tilePosition());
                    actionAckService.sendAck(resolveUserId(principal), msg.requestId(), act, true, null, null, Map.of());
                    return;
                case "PLACE_BID":
                    if (msg.amount() == null) {
                        actionAckService.sendAck(resolveUserId(principal), msg.requestId(), act, false, "INVALID_REQUEST", "amount is required", Map.of());
                        return;
                    }
                    if (principal instanceof org.springframework.security.core.Authentication authentication
                            && authentication.getPrincipal() instanceof com.gamehub.security.infrastructure.GameHubUserPrincipal userPrincipal) {
                        UUID actorPlayerId = gameSessionService.resolveActorPlayerId(gameSessionService.requireSession(sessionId).getRoomId(), userPrincipal.userId());
                        auctionService.placeBid(sessionId, actorPlayerId, msg.amount());
                        actionAckService.sendAck(userPrincipal.userId(), msg.requestId(), act, true, null, null, Map.of());
                    } else {
                        actionAckService.sendAck(null, msg.requestId(), act, false, "AUTH_REQUIRED", "Authentication required", Map.of());
                    }
                    return;
                case "PASS":
                    if (principal instanceof org.springframework.security.core.Authentication authentication
                            && authentication.getPrincipal() instanceof com.gamehub.security.infrastructure.GameHubUserPrincipal userPrincipal) {
                        UUID actorPlayerId = gameSessionService.resolveActorPlayerId(gameSessionService.requireSession(sessionId).getRoomId(), userPrincipal.userId());
                        auctionService.passBid(sessionId, actorPlayerId);
                        actionAckService.sendAck(userPrincipal.userId(), msg.requestId(), act, true, null, null, Map.of());
                    } else {
                        actionAckService.sendAck(null, msg.requestId(), act, false, "AUTH_REQUIRED", "Authentication required", Map.of());
                    }
                    return;
                default:
                    actionAckService.sendAck(resolveUserId(principal), msg.requestId(), act, false, "INVALID_REQUEST", "Unsupported auction action", Map.of());
                    return;
            }
        } catch (Exception ex) {
            actionAckService.sendAck(resolveUserId(principal), msg.requestId(), act, false, "SERVER_ERROR", ex.getMessage(), Map.of());
        }
    }

    private UUID resolveUserId(Principal principal) {
        if (principal instanceof org.springframework.security.core.Authentication authentication
                && authentication.getPrincipal() instanceof com.gamehub.security.infrastructure.GameHubUserPrincipal userPrincipal) {
            return userPrincipal.userId();
        }
        return null;
    }
}
