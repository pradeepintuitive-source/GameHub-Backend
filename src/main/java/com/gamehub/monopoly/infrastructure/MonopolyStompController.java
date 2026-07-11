package com.gamehub.monopoly.infrastructure;

import com.gamehub.monopoly.api.MonopolyDtos;
import com.gamehub.monopoly.application.MonopolyGameService;
import com.gamehub.monopoly.domain.MonopolyActionType;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.shared.application.GameSessionService;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class MonopolyStompController {

    private final MonopolyGameService monopolyGameService;
    private final GameSessionService gameSessionService;

    public static record MonopolyActionMessage(
            String type,
            Integer tilePosition,
            String targetPlayerId,
            Integer amount,
            Map<String, String> metadata) {
    }

    @MessageMapping("/games/{sessionId}/action")
    public void handleAction(@DestinationVariable UUID sessionId, MonopolyActionMessage msg, Principal principal) {
        if (msg == null || msg.type() == null) return;
        if (!(principal instanceof org.springframework.security.core.Authentication authentication
                && authentication.getPrincipal() instanceof GameHubUserPrincipal userPrincipal)) {
            return;
        }

        var session = gameSessionService.requireSession(sessionId);
        UUID actorPlayerId = gameSessionService.resolveActorPlayerId(session.getRoomId(), userPrincipal.userId());
        UUID targetPlayerId = null;
        if (msg.targetPlayerId() != null) {
            try {
                targetPlayerId = UUID.fromString(msg.targetPlayerId());
            } catch (IllegalArgumentException e) {
                // ignore invalid UUID payload
            }
        }

        MonopolyDtos.MonopolyActionRequest request = new MonopolyDtos.MonopolyActionRequest(
                MonopolyActionType.valueOf(msg.type()),
                msg.tilePosition(),
                targetPlayerId,
                msg.amount(),
                msg.metadata());

        monopolyGameService.processAction(session.getRoomId(), session, userPrincipal, actorPlayerId, request);
    }
}
