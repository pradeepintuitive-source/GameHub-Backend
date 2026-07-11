package com.gamehub.monopoly.infrastructure;

import com.gamehub.monopoly.application.AuctionService;
import com.gamehub.shared.application.GameSessionService;
import com.gamehub.player.infrastructure.PlayerRepository;
import com.gamehub.player.infrastructure.UserRepository;
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

    public static record AuctionMessage(String action, Integer tilePosition, Integer amount) {
    }

    @MessageMapping("/games/{sessionId}/auction")
    public void handle(@DestinationVariable UUID sessionId, AuctionMessage msg, Principal principal) {
        if (msg == null || msg.action() == null) return;
        String act = msg.action();
        switch (act) {
            case "START":
                if (msg.tilePosition() == null) return;
                auctionService.startAuction(sessionId, msg.tilePosition());
                return;
            case "PLACE_BID":
                if (msg.amount() == null) return;
                // resolve actor player id from principal
                if (principal instanceof org.springframework.security.core.Authentication authentication
                        && authentication.getPrincipal() instanceof com.gamehub.security.infrastructure.GameHubUserPrincipal userPrincipal) {
                    // resolve player id
                    UUID actorPlayerId = gameSessionService.resolveActorPlayerId(gameSessionService.requireSession(sessionId).getRoomId(), userPrincipal.userId());
                    auctionService.placeBid(sessionId, actorPlayerId, msg.amount());
                }
                return;
            case "PASS":
                if (principal instanceof org.springframework.security.core.Authentication authentication
                        && authentication.getPrincipal() instanceof com.gamehub.security.infrastructure.GameHubUserPrincipal userPrincipal) {
                    UUID actorPlayerId = gameSessionService.resolveActorPlayerId(gameSessionService.requireSession(sessionId).getRoomId(), userPrincipal.userId());
                    auctionService.passBid(sessionId, actorPlayerId);
                }
                return;
            default:
                return;
        }
    }
}
