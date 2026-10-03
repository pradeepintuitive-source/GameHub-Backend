package com.gamehub.voice.application;

import com.gamehub.voice.api.VoicePresenceMessage;
import com.gamehub.voice.api.VoiceSignalMessage;
import com.gamehub.voice.domain.VoiceRoomRegistry;
import com.gamehub.voice.domain.VoiceRoomState;
import com.gamehub.voice.domain.VoiceSignalType;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Core voice signaling service.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Relay WebRTC signals (OFFER / ANSWER / ICE_CANDIDATE / HANG_UP) point-to-point
 *       between authenticated peers via user-private STOMP queues.</li>
 *   <li>Maintain per-room participant lists and broadcast presence updates.</li>
 *   <li>Track mute state and broadcast MUTE_STATE changes to the whole room topic.</li>
 * </ol>
 *
 * <p>The server is media-agnostic -- it never touches SDP or ICE payload bytes.
 *
 * <p>All user identity uses the authenticated user UUID (same as {@code user.id} /
 * {@code player.userId}). Usernames and in-game player ids are not used here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceSignalingService {

    private static final String VOICE_QUEUE     = "/queue/voice";
    private static final String PRESENCE_TOPIC  = "/topic/rooms/%s/voice";

    private final SimpMessagingTemplate messaging;
    private final VoiceRoomRegistry     registry;

    // ── Join / Leave ─────────────────────────────────────────────────────────

    /**
     * Add userId to the voice room and broadcast the updated participant list.
     * Joining twice is idempotent: no second entry is created and no extra broadcast fires.
     *
     * @return the updated participant set (including the new joiner)
     */
    public Set<UUID> joinVoiceRoom(UUID roomId, UUID userId) {
        VoiceRoomState state = registry.getOrCreate(roomId);
        boolean added = state.join(userId);
        if (added) {
            log.info("Voice join: roomId={} userId={} total={}", roomId, userId, state.size());
            broadcastPresence(VoicePresenceMessage.joined(roomId, state.participants(), userId));
        }
        return state.participants();
    }

    /**
     * Remove userId from the voice room, send HANG_UP to all remaining peers,
     * and broadcast the updated participant list.
     */
    public void leaveVoiceRoom(UUID roomId, UUID userId) {
        registry.find(roomId).ifPresent(state -> {
            // Notify remaining peers so they can clean up their RTCPeerConnection
            Set<UUID> snapshot = Set.copyOf(state.participants());
            snapshot.stream()
                    .filter(peerId -> !peerId.equals(userId))
                    .forEach(peerId -> relayToPeer(peerId,
                            VoiceSignalMessage.relay(VoiceSignalType.HANG_UP, roomId, userId, peerId, Map.of())));

            state.leave(userId);
            log.info("Voice leave: roomId={} userId={} remaining={}", roomId, userId, state.size());
            broadcastPresence(VoicePresenceMessage.left(roomId, state.participants(), userId));
            registry.pruneIfEmpty(roomId);
        });
    }

    // ── Signal relay ──────────────────────────────────────────────────────────

    /**
     * Relay a unicast WebRTC signaling message from {@code fromUserId} to the target peer.
     *
     * <p>The sender must be an active participant. The {@code fromUserId} is always stamped
     * from the authenticated principal -- the client-supplied value is ignored.
     *
     * <p>ICE candidates may arrive before both peers have registered; only the sender's
     * membership is enforced so that fast ICE trickle is not dropped.
     */
    public void relaySignal(UUID roomId, UUID fromUserId, VoiceSignalMessage inbound) {
        if (inbound.toUserId() == null) {
            log.warn("Signal relay rejected: toUserId null. roomId={} from={} type={}",
                    roomId, fromUserId, inbound.type());
            return;
        }

        VoiceRoomState state = registry.find(roomId).orElse(null);
        if (state == null || !state.isParticipant(fromUserId)) {
            log.warn("Signal relay rejected: sender not in voice room. roomId={} from={} to={}",
                    roomId, fromUserId, inbound.toUserId());
            return;
        }

        // Stamp fromUserId from the authenticated session; forward type, toUserId, payload unchanged.
        VoiceSignalMessage outbound = VoiceSignalMessage.relay(
                inbound.type(), roomId, fromUserId, inbound.toUserId(), inbound.payload());
        relayToPeer(inbound.toUserId(), outbound);
        log.debug("Signal relayed: type={} roomId={} from={} to={}",
                inbound.type(), roomId, fromUserId, inbound.toUserId());
    }

    // ── Mute state ────────────────────────────────────────────────────────────

    /**
     * Update mute state for userId and broadcast MUTE_STATE to the entire room topic.
     * The {@code fromUserId} is stamped by the server from the authenticated principal.
     */
    public void updateMuteState(UUID roomId, UUID userId, boolean muted) {
        registry.find(roomId).ifPresent(state -> {
            state.setMuted(userId, muted);
            Map<String, Object> payload = Map.of("muted", muted);
            VoiceSignalMessage broadcast = VoiceSignalMessage.relay(
                    VoiceSignalType.MUTE_STATE, roomId, userId, null, payload);
            messaging.convertAndSend(PRESENCE_TOPIC.formatted(roomId), broadcast);
            log.debug("Mute updated: roomId={} userId={} muted={}", roomId, userId, muted);
        });
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void relayToPeer(UUID toUserId, VoiceSignalMessage message) {
        messaging.convertAndSendToUser(toUserId.toString(), VOICE_QUEUE, message);
    }

    private void broadcastPresence(VoicePresenceMessage message) {
        messaging.convertAndSend(PRESENCE_TOPIC.formatted(message.roomId()), message);
    }
}
