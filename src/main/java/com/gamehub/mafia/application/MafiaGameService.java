package com.gamehub.mafia.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamehub.audit.application.AuditService;
import com.gamehub.audit.domain.AuditType;
import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.mafia.api.MafiaDtos;
import com.gamehub.mafia.domain.MafiaAction;
import com.gamehub.mafia.domain.MafiaGameState;
import com.gamehub.mafia.infrastructure.MafiaEngine;
import com.gamehub.mafia.infrastructure.MafiaGameEntity;
import com.gamehub.mafia.infrastructure.MafiaGameRepository;
import com.gamehub.mafia.infrastructure.MafiaRoleEntity;
import com.gamehub.mafia.infrastructure.MafiaRoleRepository;
import com.gamehub.mafia.infrastructure.MafiaVoteEntity;
import com.gamehub.mafia.infrastructure.MafiaVoteRepository;
import com.gamehub.notification.application.NotificationService;
import com.gamehub.notification.domain.NotificationMessage;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.shared.application.GameEventService;
import com.gamehub.shared.application.SaveGameService;
import com.gamehub.shared.domain.GameEventType;
import com.gamehub.shared.infrastructure.GameSessionEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MafiaGameService {

    private final MafiaEngine mafiaEngine;
    private final MafiaGameRepository mafiaGameRepository;
    private final MafiaRoleRepository mafiaRoleRepository;
    private final MafiaVoteRepository mafiaVoteRepository;
    private final ObjectMapper objectMapper;
    private final SaveGameService saveGameService;
    private final AuditService auditService;
    private final GameEventService gameEventService;
    private final NotificationService notificationService;

    @Transactional
    public MafiaGameState startSession(GameSessionEntity session, UUID roomId, List<UUID> playerIds, UUID actorUserId) {
        MafiaGameState state = mafiaEngine.startGame(session.getId(), playerIds);
        session.setStatePayload(write(state));

        MafiaGameEntity entity = new MafiaGameEntity();
        entity.setId(UUID.randomUUID());
        entity.setSessionId(session.getId());
        entity.setPhase(state.phase());
        entity.setDayNumber(state.currentCycle().cycleNumber());
        entity.setStatePayload(write(state));
        mafiaGameRepository.save(entity);
        syncRolesAndVotes(state);

        auditService.record(AuditType.GAME_ACTION, roomId, session.getId(), actorUserId, "Mafia game started", null);
        gameEventService.record(roomId, session.getId(), GameEventType.GAME_STARTED, actorUserId, "Mafia");
        notificationService.sendToTopic(
                "/topic/game/" + roomId,
                new NotificationMessage("GAME_STARTED", roomId, session.getId(), toResponse(state), Instant.now()));
        return state;
    }

    @Transactional(readOnly = true)
    public MafiaGameState getState(GameSessionEntity session) {
        return read(session.getStatePayload());
    }

    @Transactional
    public MafiaGameState processAction(
            UUID roomId,
            GameSessionEntity session,
            GameHubUserPrincipal principal,
            UUID actorPlayerId,
            MafiaDtos.MafiaActionRequest request) {
        MafiaGameState currentState = read(session.getStatePayload());
        MafiaGameState updatedState = mafiaEngine.processAction(
                currentState,
                new MafiaAction(actorPlayerId, request.type(), request.targetPlayerId()));
        persist(roomId, session, principal.userId(), updatedState, request.type().name(), GameEventType.ROOM_UPDATED);
        saveGameService.autoSave(session.getId(), principal.userId(), "PHASE_CHANGE");
        return updatedState;
    }

    @Transactional
    public MafiaGameState advancePhase(UUID roomId, GameSessionEntity session, UUID actorUserId) {
        MafiaGameState currentState = read(session.getStatePayload());
        MafiaGameState updatedState = mafiaEngine.advancePhase(currentState);
        persist(roomId, session, actorUserId, updatedState, "Phase advanced", GameEventType.ROOM_UPDATED);
        return updatedState;
    }

    @Transactional
    public MafiaGameState pauseSession(UUID roomId, GameSessionEntity session, UUID actorUserId) {
        MafiaGameState updatedState = mafiaEngine.pauseGame(read(session.getStatePayload()));
        persist(roomId, session, actorUserId, updatedState, "Mafia paused", GameEventType.GAME_PAUSED);
        return updatedState;
    }

    @Transactional
    public MafiaGameState resumeSession(UUID roomId, GameSessionEntity session, UUID actorUserId) {
        MafiaGameState updatedState = mafiaEngine.resumeGame(read(session.getStatePayload()));
        persist(roomId, session, actorUserId, updatedState, "Mafia resumed", GameEventType.GAME_RESUMED);
        return updatedState;
    }

    public MafiaDtos.MafiaStateResponse toResponse(MafiaGameState state) {
        Map<UUID, MafiaDtos.PlayerRoleResponse> roles = new LinkedHashMap<>();
        state.roles().forEach((playerId, role) -> roles.put(playerId, new MafiaDtos.PlayerRoleResponse(
                role.playerId(),
                role.role(),
                role.alive(),
                role.revealed())));
        List<MafiaDtos.NightActionResponse> nightActions = state.nightActions().stream()
                .map(action -> new MafiaDtos.NightActionResponse(action.actorPlayerId(), action.type(), action.targetPlayerId()))
                .toList();
        List<MafiaDtos.VoteResponse> votes = state.currentCycle().votes().stream()
                .map(vote -> new MafiaDtos.VoteResponse(vote.voterPlayerId(), vote.targetPlayerId()))
                .toList();
        return new MafiaDtos.MafiaStateResponse(
                state.sessionId(),
                state.phase(),
                roles,
                nightActions,
                new MafiaDtos.DayCycleResponse(state.currentCycle().cycleNumber(), votes),
                state.announcements(),
                state.protectedPlayers(),
                state.investigatedPlayers());
    }

    private void persist(
            UUID roomId,
            GameSessionEntity session,
            UUID actorUserId,
            MafiaGameState updatedState,
            String auditMessage,
            GameEventType eventType) {
        session.setStatePayload(write(updatedState));
        MafiaGameEntity gameEntity = mafiaGameRepository.findBySessionId(session.getId())
                .orElseThrow(() -> new BusinessRuleViolationException("Mafia row missing"));
        gameEntity.setPhase(updatedState.phase());
        gameEntity.setDayNumber(updatedState.currentCycle().cycleNumber());
        gameEntity.setStatePayload(write(updatedState));
        mafiaGameRepository.save(gameEntity);
        syncRolesAndVotes(updatedState);
        auditService.record(AuditType.GAME_ACTION, roomId, session.getId(), actorUserId, auditMessage, updatedState.phase().name());
        gameEventService.record(roomId, session.getId(), eventType, actorUserId, updatedState.phase().name());
        notificationService.sendToTopic(
                "/topic/game/" + roomId,
                new NotificationMessage(eventType.name(), roomId, session.getId(), toResponse(updatedState), Instant.now()));
    }

    private void syncRolesAndVotes(MafiaGameState state) {
        mafiaRoleRepository.deleteBySessionId(state.sessionId());
        mafiaVoteRepository.deleteBySessionId(state.sessionId());
        state.roles().values().forEach(playerRole -> {
            MafiaRoleEntity entity = new MafiaRoleEntity();
            entity.setId(UUID.randomUUID());
            entity.setSessionId(state.sessionId());
            entity.setPlayerId(playerRole.playerId());
            entity.setRoleName(playerRole.role());
            entity.setAlive(playerRole.alive());
            entity.setRevealed(playerRole.revealed());
            mafiaRoleRepository.save(entity);
        });
        state.currentCycle().votes().forEach(vote -> {
            MafiaVoteEntity entity = new MafiaVoteEntity();
            entity.setId(UUID.randomUUID());
            entity.setSessionId(state.sessionId());
            entity.setVoterPlayerId(vote.voterPlayerId());
            entity.setTargetPlayerId(vote.targetPlayerId());
            entity.setCycleNumber(state.currentCycle().cycleNumber());
            mafiaVoteRepository.save(entity);
        });
    }

    private MafiaGameState read(String payload) {
        try {
            return objectMapper.readValue(payload, MafiaGameState.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleViolationException("Unable to read Mafia state");
        }
    }

    private String write(MafiaGameState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleViolationException("Unable to write Mafia state");
        }
    }
}
