package com.gamehub.websocket.infrastructure;

import com.gamehub.room.application.RoomService;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.websocket.application.ActionAckService;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class LobbyStompController {

    private final RoomService roomService;
    private final ActionAckService actionAckService;

    public static record ReadyMessage(String requestId, Boolean ready) {
    }

    @MessageMapping("/rooms/{roomId}/ready")
    public void ready(@DestinationVariable UUID roomId, ReadyMessage message, Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof GameHubUserPrincipal userPrincipal)) {
            actionAckService.sendAck(null, message == null ? null : message.requestId(), "READY", false, "AUTH_REQUIRED", "Authentication required", Map.of());
            return;
        }
        if (message == null || message.ready() == null) {
            actionAckService.sendAck(userPrincipal.userId(), message == null ? null : message.requestId(), "READY", false, "INVALID_REQUEST", "ready is required", Map.of());
            return;
        }
        try {
            roomService.setReady(userPrincipal, roomId, message.ready());
            actionAckService.sendAck(userPrincipal.userId(), message.requestId(), "READY", true, null, null, Map.of());
        } catch (Exception ex) {
            actionAckService.sendAck(userPrincipal.userId(), message.requestId(), "READY", false, "SERVER_ERROR", ex.getMessage(), Map.of());
        }
    }
}
