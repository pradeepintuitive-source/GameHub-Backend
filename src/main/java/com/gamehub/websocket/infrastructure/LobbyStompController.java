package com.gamehub.websocket.infrastructure;

import com.gamehub.room.application.RoomService;
import com.gamehub.websocket.infrastructure.LobbyStompController.ReadyMessage;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class LobbyStompController {

    private final RoomService roomService;

    public static record ReadyMessage(boolean ready) {
    }

    @MessageMapping("/rooms/{roomId}/ready")
    public void ready(@DestinationVariable UUID roomId, ReadyMessage message, Principal principal) {
        if (principal == null) {
            return;
        }
        if (principal instanceof org.springframework.security.core.Authentication authentication
                && authentication.getPrincipal() instanceof com.gamehub.security.infrastructure.GameHubUserPrincipal userPrincipal) {
            roomService.setReady(userPrincipal, roomId, message.ready());
        } else {
            // Fallback when principal is a simple string user id
            // convert to GameHubUserPrincipal by loading user via roomService if necessary
        }
    }
}
