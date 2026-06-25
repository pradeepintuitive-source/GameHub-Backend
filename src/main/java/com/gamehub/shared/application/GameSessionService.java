package com.gamehub.shared.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamehub.audit.application.AuditService;
import com.gamehub.audit.domain.AuditType;
import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.mafia.application.MafiaGameService;
import com.gamehub.monopoly.application.MonopolyGameService;
import com.gamehub.player.infrastructure.PlayerRepository;
import com.gamehub.room.domain.RoomState;
import com.gamehub.room.infrastructure.RoomEntity;
import com.gamehub.room.infrastructure.RoomRepository;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.shared.api.GameDtos;
import com.gamehub.shared.domain.GameEventType;
import com.gamehub.shared.domain.SessionStatus;
import com.gamehub.shared.infrastructure.GameSessionEntity;
import com.gamehub.shared.infrastructure.GameSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameSessionService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GameSessionRepository gameSessionRepository;
    private final MonopolyGameService monopolyGameService;
    private final MafiaGameService mafiaGameService;
    private final GameEventService gameEventService;
    private final AuditService auditService;
    private final SaveGameService saveGameService;
    private final ObjectMapper objectMapper;

    @Transactional
    public GameDtos.GameSessionResponse startGame(GameHubUserPrincipal principal, GameDtos.StartGameRequest request) {
        RoomEntity room = requireRoom(request.roomId());
        assertHost(room, principal.userId());
        if (room.getState() == RoomState.IN_PROGRESS) {
            throw new BusinessRuleViolationException("A game is already in progress for this room");
        }
        List<UUID> playerIds = playerRepository.findByRoomIdOrderBySeatOrder(room.getId())
                .stream()
                .map(entity -> entity.getId())
                .toList();
        if (playerIds.size() < 2) {
            throw new BusinessRuleViolationException("At least 2 players are required");
        }

        GameSessionEntity session = new GameSessionEntity();
        session.setId(UUID.randomUUID());
        session.setRoomId(room.getId());
        session.setGameType(room.getGameType());
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStatePayload("{}");
        session.setSaveVersion(0);
        session.setStartedAt(Instant.now());
        session = gameSessionRepository.save(session);

        switch (room.getGameType()) {
            case MONOPOLY -> monopolyGameService.startSession(session, room.getId(), playerIds, principal.userId());
            case MAFIA -> mafiaGameService.startSession(session, room.getId(), playerIds, principal.userId());
        }

        room.setCurrentSessionId(session.getId());
        room.setState(RoomState.IN_PROGRESS);
        roomRepository.save(room);

        auditService.record(AuditType.GAME_ACTION, room.getId(), session.getId(), principal.userId(), "Game started", room.getGameType().name());
        gameEventService.record(room.getId(), session.getId(), GameEventType.GAME_STARTED, principal.userId(), room.getGameType().name());
        return responseFor(session);
    }

    @Transactional(readOnly = true)
    public GameDtos.GameSessionResponse getSession(GameHubUserPrincipal principal, UUID sessionId) {
        GameSessionEntity session = requireSession(sessionId);
        ensureRoomMembership(session.getRoomId(), principal.userId());
        return responseFor(session);
    }

    @Transactional
    public GameDtos.GameSessionResponse save(GameHubUserPrincipal principal, GameDtos.SaveGameRequest request) {
        GameSessionEntity session = requireSession(request.sessionId());
        ensureRoomMembership(session.getRoomId(), principal.userId());
        saveGameService.manualSave(session.getId(), principal.userId(), request.reason());
        return responseFor(session);
    }

    @Transactional
    public GameDtos.GameSessionResponse pause(GameHubUserPrincipal principal, UUID sessionId) {
        GameSessionEntity session = requireSession(sessionId);
        RoomEntity room = requireRoom(session.getRoomId());
        assertHost(room, principal.userId());
        session.setStatus(SessionStatus.PAUSED);
        room.setState(RoomState.PAUSED);
        switch (session.getGameType()) {
            case MONOPOLY -> monopolyGameService.pauseSession(room.getId(), session, principal.userId());
            case MAFIA -> mafiaGameService.pauseSession(room.getId(), session, principal.userId());
        }
        gameEventService.record(room.getId(), sessionId, GameEventType.GAME_PAUSED, principal.userId(), session.getGameType().name());
        return responseFor(session);
    }

    @Transactional
    public GameDtos.GameSessionResponse resume(GameHubUserPrincipal principal, UUID sessionId) {
        GameSessionEntity session = requireSession(sessionId);
        RoomEntity room = requireRoom(session.getRoomId());
        assertHost(room, principal.userId());
        session.setStatus(SessionStatus.IN_PROGRESS);
        room.setState(RoomState.IN_PROGRESS);
        switch (session.getGameType()) {
            case MONOPOLY -> monopolyGameService.resumeSession(room.getId(), session, principal.userId());
            case MAFIA -> mafiaGameService.resumeSession(room.getId(), session, principal.userId());
        }
        gameEventService.record(room.getId(), sessionId, GameEventType.GAME_RESUMED, principal.userId(), session.getGameType().name());
        return responseFor(session);
    }

    @Transactional(readOnly = true)
    public GameSessionEntity requireSession(UUID sessionId) {
        return gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessRuleViolationException("Game session not found"));
    }

    @Transactional(readOnly = true)
    public UUID resolveActorPlayerId(UUID roomId, UUID userId) {
        return playerRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessRuleViolationException("Player is not part of room"))
                .getId();
    }

    @Transactional(readOnly = true)
    public void assertHostForSession(GameSessionEntity session, UUID userId) {
        RoomEntity room = requireRoom(session.getRoomId());
        assertHost(room, userId);
    }

    private GameDtos.GameSessionResponse responseFor(GameSessionEntity session) {
        try {
            JsonNode state = objectMapper.readTree(session.getStatePayload());
            return new GameDtos.GameSessionResponse(
                    session.getId(),
                    session.getRoomId(),
                    session.getGameType(),
                    session.getStatus(),
                    session.getSaveVersion(),
                    state);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleViolationException("Unable to read persisted game session");
        }
    }

    private RoomEntity requireRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessRuleViolationException("Room not found"));
    }

    private void ensureRoomMembership(UUID roomId, UUID userId) {
        playerRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessRuleViolationException("Player is not part of room"));
    }

    private void assertHost(RoomEntity room, UUID userId) {
        if (!room.getHostUserId().equals(userId)) {
            throw new BusinessRuleViolationException("Only the host can perform this action");
        }
    }
}
