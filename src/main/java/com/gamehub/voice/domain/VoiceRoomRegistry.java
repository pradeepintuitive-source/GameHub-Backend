package com.gamehub.voice.domain;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory registry of active voice rooms.
 * A room is created lazily on first join and pruned when it empties.
 */
@Component
public class VoiceRoomRegistry {

    private final ConcurrentHashMap<UUID, VoiceRoomState> rooms = new ConcurrentHashMap<>();

    public VoiceRoomState getOrCreate(UUID roomId) {
        return rooms.computeIfAbsent(roomId, VoiceRoomState::new);
    }

    public Optional<VoiceRoomState> find(UUID roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    /** Returns all currently tracked room IDs (snapshot). */
    public Set<UUID> allRoomIds() {
        return Set.copyOf(rooms.keySet());
    }

    /** Remove the room entry if it has no participants left. */
    public void pruneIfEmpty(UUID roomId) {
        rooms.computeIfPresent(roomId, (id, state) -> state.size() == 0 ? null : state);
    }
}
