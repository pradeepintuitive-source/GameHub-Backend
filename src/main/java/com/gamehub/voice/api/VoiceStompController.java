package com.gamehub.voice.api;

import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.voice.application.VoiceSignalingService;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/**
 * STOMP controller for WebRTC voice chat signaling.
 *
 * <p>All endpoints require an authenticated WebSocket principal (JWT validated
 * during the STOMP CONNECT handshake -- see WebSocketConfig).
 *
 * <p>Client destinations (prefix /app applied by broker):
 * <pre>
 *   SEND /app/voice/{roomId}/join    -- join voice room
 *   SEND /app/voice/{roomId}/leave   -- leave voice room gracefully
 *   SEND /app/voice/{roomId}/signal  -- relay OFFER / ANSWER / ICE_CANDIDATE / HANG_UP
 *   SEND /app/voice/{roomId}/mute    -- broadcast mute/unmute state change
 * </pre>
 *
 * <p>Server push destinations:
 * <pre>
 *   /user/queue/voice                -- unicast signals to a specific peer
 *   /topic/rooms/{roomId}/voice      -- room broadcast (VOICE_PRESENCE / MUTE_STATE)
 * </pre>
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class VoiceStompController {

    private final VoiceSignalingService signalingService;

    @MessageMapping("/voice/{roomId}/join")
    public void join(@DestinationVariable UUID roomId, Principal principal) {
        UUID userId = extractUserId(principal);
        if (userId == null) return;
        Set<UUID> participants = signalingService.joinVoiceRoom(roomId, userId);
        log.info("VOICE JOIN: roomId={} userId={} participants={}", roomId, userId, participants.size());
    }

    @MessageMapping("/voice/{roomId}/leave")
    public void leave(@DestinationVariable UUID roomId, Principal principal) {
        UUID userId = extractUserId(principal);
        if (userId == null) return;
        signalingService.leaveVoiceRoom(roomId, userId);
        log.info("VOICE LEAVE: roomId={} userId={}", roomId, userId);
    }

    @MessageMapping("/voice/{roomId}/signal")
    public void signal(
            @DestinationVariable UUID roomId,
            @Payload VoiceSignalMessage message,
            Principal principal) {
        UUID userId = extractUserId(principal);
        if (userId == null) return;
        signalingService.relaySignal(roomId, userId, message);
    }

    @MessageMapping("/voice/{roomId}/mute")
    public void mute(
            @DestinationVariable UUID roomId,
            @Payload VoiceMuteRequest request,
            Principal principal) {
        UUID userId = extractUserId(principal);
        if (userId == null) return;
        signalingService.updateMuteState(roomId, userId, request.muted());
        log.info("VOICE MUTE: roomId={} userId={} muted={}", roomId, userId, request.muted());
    }

    private UUID extractUserId(Principal principal) {
        if (principal instanceof Authentication auth
                && auth.getPrincipal() instanceof GameHubUserPrincipal userPrincipal) {
            return userPrincipal.userId();
        }
        log.warn("Voice action rejected: unauthenticated principal={}", principal);
        return null;
    }

    /** Inbound payload for mute/unmute requests. */
    public record VoiceMuteRequest(boolean muted) {}
}
