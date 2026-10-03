package com.gamehub.voice.domain;

/**
 * Types of WebRTC signaling messages exchanged between peers.
 *
 * <p>Flow:
 * <ol>
 *   <li>Initiator sends OFFER to target peer via the signaling server.</li>
 *   <li>Target peer sends ANSWER back to initiator via the signaling server.</li>
 *   <li>Both peers exchange ICE_CANDIDATE messages as they discover network paths.</li>
 *   <li>Either peer can send HANG_UP to end the voice connection.</li>
 * </ol>
 */
public enum VoiceSignalType {
    /** SDP offer from the initiating peer. */
    OFFER,

    /** SDP answer from the responding peer. */
    ANSWER,

    /** ICE candidate for NAT traversal. */
    ICE_CANDIDATE,

    /** Graceful teardown of the peer connection. */
    HANG_UP,

    /** Peer muted/unmuted their microphone -- broadcast to room. */
    MUTE_STATE
}
