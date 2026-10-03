package com.gamehub.voice.domain;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory per-room voice state (participants + mute flags). */
public class VoiceRoomState {

    private final UUID roomId;
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private final Set<UUID> mutedUsers   = ConcurrentHashMap.newKeySet();

    public VoiceRoomState(UUID roomId) { this.roomId = roomId; }

    public UUID roomId()     { return roomId; }
    public int  size()       { return participants.size(); }

    public boolean isParticipant(UUID id) { return participants.contains(id); }
    public boolean isMuted(UUID id)       { return mutedUsers.contains(id); }
    public Set<UUID> participants()       { return Collections.unmodifiableSet(participants); }

    public boolean join(UUID userId)  { return participants.add(userId); }

    public boolean leave(UUID userId) {
        mutedUsers.remove(userId);
        return participants.remove(userId);
    }

    public void setMuted(UUID userId, boolean muted) {
        if (muted) mutedUsers.add(userId); else mutedUsers.remove(userId);
    }
}
