package com.gamehub.websocket.infrastructure;

import com.gamehub.notification.application.NotificationService;
import com.gamehub.notification.domain.NotificationMessage;
import com.gamehub.room.application.RoomService;
import com.gamehub.voice.application.VoiceSignalingService;
import com.gamehub.voice.domain.VoiceRoomRegistry;
import com.gamehub.websocket.application.PresenceService;
import com.gamehub.websocket.domain.WebSocketConnection;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PresenceService        presenceService;
    private final RoomService            roomService;
    private final NotificationService    notificationService;
    private final VoiceSignalingService  voiceSignalingService;
    private final VoiceRoomRegistry      voiceRoomRegistry;

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal == null) {
            return;
        }
        UUID userId;
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof com.gamehub.security.infrastructure.GameHubUserPrincipal userPrincipal) {
            userId = userPrincipal.userId();
        } else {
            userId = UUID.fromString(principal.getName());
        }
        presenceService.connect(accessor.getSessionId(), userId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        WebSocketConnection connection = presenceService.disconnect(event.getSessionId());
        if (connection == null) {
            return;
        }
        UUID userId = connection.userId();

        // Auto-leave all voice rooms the user was participating in when they disconnect.
        // This ensures remaining peers clean up their RTCPeerConnection properly.
        voiceRoomRegistry.allRoomIds().forEach(roomId ->
                voiceRoomRegistry.find(roomId).ifPresent(state -> {
                    if (state.isParticipant(userId)) {
                        voiceSignalingService.leaveVoiceRoom(roomId, userId);
                    }
                })
        );

        roomService.markDisconnected(userId);
        notificationService.sendToUser(
                userId,
                "/queue/private",
                new NotificationMessage(
                        "PLAYER_DISCONNECTED",
                        null,
                        null,
                        "Connection lost for session " + connection.sessionId(),
                        Instant.now()));
    }
}
