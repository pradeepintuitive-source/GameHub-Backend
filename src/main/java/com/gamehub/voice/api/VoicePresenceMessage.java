package com.gamehub.voice.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Broadcast to /topic/rooms/{roomId}/voice whenever the participant list changes.
 * Clients use this to render mic icons and know which peers to connect/disconnect with.
 */
public record VoicePresenceMessage(
        String   type,
        UUID     roomId,
        Set<UUID> participants,
        UUID     changedUserId,
        boolean  joined,
        Instant  timestamp) {

    public static VoicePresenceMessage joined(UUID roomId, Set<UUID> participants, UUID userId) {
        return new VoicePresenceMessage("VOICE_PRESENCE", roomId, participants, userId, true, Instant.now());
    }

    public static VoicePresenceMessage left(UUID roomId, Set<UUID> participants, UUID userId) {
        return new VoicePresenceMessage("VOICE_PRESENCE", roomId, participants, userId, false, Instant.now());
    }
}
