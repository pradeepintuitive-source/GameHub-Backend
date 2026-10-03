package com.gamehub.voice.api;

import com.gamehub.voice.domain.VoiceSignalType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Envelope for all WebRTC signaling messages routed through the STOMP signaling server.
 *
 * <p>The backend never inspects {@code payload} -- it simply relays it to the target peer.
 *
 * <p>Inbound (client to server):
 *   SEND /app/voice/{roomId}/signal
 *   { "type": "OFFER", "toUserId": "<uuid>", "payload": { "sdp": "..." } }
 *
 * <p>Outbound (server to client):
 *   /user/queue/voice
 *   { "type": "OFFER", "fromUserId": "<uuid>", "roomId": "<uuid>", "payload": {...} }
 */
public record VoiceSignalMessage(

        /** Discriminator that tells the receiver what to do with payload. */
        @NotNull VoiceSignalType type,

        /** UUID of the room this signal belongs to (set by server on relay). */
        UUID roomId,

        /** UUID of the sending peer (set by server on relay, ignored on inbound). */
        UUID fromUserId,

        /**
         * UUID of the target peer.
         * Required for OFFER / ANSWER / ICE_CANDIDATE / HANG_UP.
         * Optional for MUTE_STATE (broadcasts to whole room when absent).
         */
        UUID toUserId,

        /**
         * Opaque payload forwarded as-is.
         *   OFFER / ANSWER  -> { "sdp": "..." }
         *   ICE_CANDIDATE   -> { "candidate": "...", "sdpMid": "...", "sdpMLineIndex": N }
         *   HANG_UP         -> {}
         *   MUTE_STATE      -> { "muted": true }
         */
        Object payload,

        /** Server-assigned timestamp for ordering / debugging. */
        Instant timestamp
) {
    /** Convenience factory used by the relay service when forwarding a signal. */
    public static VoiceSignalMessage relay(
            VoiceSignalType type,
            UUID roomId,
            UUID fromUserId,
            UUID toUserId,
            Object payload) {
        return new VoiceSignalMessage(type, roomId, fromUserId, toUserId, payload, Instant.now());
    }
}
