package com.gamehub.monopoly.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamehub.audit.application.AuditService;
import com.gamehub.audit.domain.AuditType;
import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.monopoly.api.MonopolyDtos;
import com.gamehub.monopoly.domain.MonopolyAction;
import com.gamehub.monopoly.domain.MonopolyGameState;
import com.gamehub.monopoly.domain.MonopolyPhase;
import com.gamehub.monopoly.domain.Property;
import com.gamehub.monopoly.domain.PropertyDevelopment;
import com.gamehub.monopoly.domain.Railroad;
import com.gamehub.monopoly.domain.Tile;
import com.gamehub.monopoly.domain.Utility;
import com.gamehub.monopoly.infrastructure.MonopolyEngine;
import com.gamehub.monopoly.infrastructure.MonopolyGameEntity;
import com.gamehub.monopoly.infrastructure.MonopolyGameRepository;
import com.gamehub.monopoly.infrastructure.MonopolyPropertyEntity;
import com.gamehub.monopoly.infrastructure.MonopolyPropertyRepository;
import com.gamehub.notification.application.NotificationService;
import com.gamehub.notification.domain.NotificationMessage;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.shared.application.GameEventService;
import com.gamehub.shared.application.SaveGameService;
import com.gamehub.shared.domain.GameEventType;
import com.gamehub.shared.infrastructure.GameSessionEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MonopolyGameService {

    private final MonopolyEngine monopolyEngine;
    private final MonopolyGameRepository monopolyGameRepository;
    private final MonopolyPropertyRepository monopolyPropertyRepository;
    private final ObjectMapper objectMapper;
    private final SaveGameService saveGameService;
    private final AuditService auditService;
    private final GameEventService gameEventService;
    private final NotificationService notificationService;

    @Transactional
    public MonopolyGameState startSession(GameSessionEntity session, UUID roomId, List<UUID> playerIds, UUID actorUserId) {
        MonopolyGameState state = monopolyEngine.startGame(session.getId(), playerIds);
        persistState(session, state);

        MonopolyGameEntity monopolyGameEntity = new MonopolyGameEntity();
        monopolyGameEntity.setId(UUID.randomUUID());
        monopolyGameEntity.setSessionId(session.getId());
        monopolyGameEntity.setPhase(state.phase());
        monopolyGameEntity.setCurrentPlayerId(state.currentPlayerId());
        monopolyGameEntity.setTurnCounter(state.currentTurn());
        monopolyGameEntity.setStatePayload(write(state));
        monopolyGameRepository.save(monopolyGameEntity);
        syncProperties(state);

        auditService.record(AuditType.GAME_ACTION, roomId, session.getId(), actorUserId, "Monopoly game started", null);
        gameEventService.record(roomId, session.getId(), GameEventType.GAME_STARTED, actorUserId, "Monopoly");
        notificationService.sendToTopic(
                "/topic/game/" + roomId,
                new NotificationMessage("GAME_STARTED", roomId, session.getId(), toResponse(state), Instant.now()));
        return state;
    }

    @Transactional(readOnly = true)
    public MonopolyGameState getState(GameSessionEntity session) {
        return read(session.getStatePayload());
    }

    @Transactional
    public MonopolyGameState processAction(
            UUID roomId,
            GameSessionEntity session,
            GameHubUserPrincipal principal,
            UUID actorPlayerId,
            MonopolyDtos.MonopolyActionRequest request) {
        MonopolyGameState currentState = read(session.getStatePayload());
        MonopolyAction action = new MonopolyAction(
                actorPlayerId,
                request.type(),
                request.tilePosition(),
                request.targetPlayerId(),
                request.amount(),
                request.metadata() == null ? Map.of() : request.metadata());
        MonopolyGameState updatedState = monopolyEngine.processAction(currentState, action);
        persistState(session, updatedState);
        updateGameRow(session.getId(), updatedState);
        syncProperties(updatedState);

        GameEventType eventType = switch (request.type()) {
            case ROLL_DICE, END_TURN -> GameEventType.PLAYER_MOVED;
            case BUY_PROPERTY, AUCTION -> GameEventType.PROPERTY_PURCHASED;
            default -> GameEventType.ROOM_UPDATED;
        };
        auditService.record(AuditType.GAME_ACTION, roomId, session.getId(), principal.userId(), "Monopoly action", request.type().name());
        gameEventService.record(roomId, session.getId(), eventType, principal.userId(), request.type().name());
        notificationService.sendToTopic(
                "/topic/game/" + roomId,
                new NotificationMessage(request.type().name(), roomId, session.getId(), toResponse(updatedState), Instant.now()));

        if (request.type().name().contains("TURN") || updatedState.phase() == MonopolyPhase.WAITING_FOR_ROLL) {
            saveGameService.autoSave(session.getId(), principal.userId(), "TURN_END");
        } else {
            saveGameService.autoSave(session.getId(), principal.userId(), "ACTION");
        }
        return updatedState;
    }

    @Transactional
    public MonopolyGameState pauseSession(UUID roomId, GameSessionEntity session, UUID actorUserId) {
        MonopolyGameState updatedState = monopolyEngine.pauseGame(read(session.getStatePayload()));
        persistState(session, updatedState);
        updateGameRow(session.getId(), updatedState);
        auditService.record(AuditType.GAME_ACTION, roomId, session.getId(), actorUserId, "Monopoly paused", null);
        return updatedState;
    }

    @Transactional
    public MonopolyGameState resumeSession(UUID roomId, GameSessionEntity session, UUID actorUserId) {
        MonopolyGameState updatedState = monopolyEngine.resumeGame(read(session.getStatePayload()));
        persistState(session, updatedState);
        updateGameRow(session.getId(), updatedState);
        auditService.record(AuditType.GAME_ACTION, roomId, session.getId(), actorUserId, "Monopoly resumed", null);
        return updatedState;
    }

    public MonopolyDtos.MonopolyStateResponse toResponse(MonopolyGameState state) {
        Map<UUID, MonopolyDtos.AssetResponse> assets = new LinkedHashMap<>();
        state.assets().forEach((playerId, asset) -> assets.put(playerId, new MonopolyDtos.AssetResponse(
                asset.playerId(),
                asset.cash(),
                asset.position(),
                asset.inJail(),
                asset.jailTurns(),
                asset.ownedTilePositions())));
        Map<Integer, MonopolyDtos.DevelopmentResponse> developments = new LinkedHashMap<>();
        state.developments().forEach((position, development) -> developments.put(position, new MonopolyDtos.DevelopmentResponse(
                development.houses(),
                development.hotel())));
        return new MonopolyDtos.MonopolyStateResponse(
                state.sessionId(),
                state.phase(),
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                assets,
                state.owners(),
                developments,
                state.mortgagedTiles(),
                state.log());
    }

    private void persistState(GameSessionEntity session, MonopolyGameState state) {
        session.setStatePayload(write(state));
    }

    private void updateGameRow(UUID sessionId, MonopolyGameState state) {
        MonopolyGameEntity gameEntity = monopolyGameRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessRuleViolationException("Monopoly row missing"));
        gameEntity.setPhase(state.phase());
        gameEntity.setCurrentPlayerId(state.currentPlayerId());
        gameEntity.setTurnCounter(state.currentTurn());
        gameEntity.setStatePayload(write(state));
        monopolyGameRepository.save(gameEntity);
    }

    private void syncProperties(MonopolyGameState state) {
        monopolyPropertyRepository.deleteBySessionId(state.sessionId());
        List<Tile> ownableTiles = state.board().tiles().stream()
                .filter(tile -> tile instanceof Property || tile instanceof Railroad || tile instanceof Utility)
                .toList();
        for (Tile tile : ownableTiles) {
            PropertyDevelopment development = state.developments().get(tile.position());
            MonopolyPropertyEntity entity = new MonopolyPropertyEntity();
            entity.setId(UUID.randomUUID());
            entity.setSessionId(state.sessionId());
            entity.setPropertyName(tile.name());
            entity.setOwnerPlayerId(state.owners().get(tile.position()));
            entity.setMortgaged(state.mortgagedTiles().contains(tile.position()));
            entity.setHouses(development == null ? 0 : development.houses());
            entity.setHotel(development != null && development.hotel());
            monopolyPropertyRepository.save(entity);
        }
    }

    private MonopolyGameState read(String payload) {
        try {
            return objectMapper.readValue(payload, MonopolyGameState.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleViolationException("Unable to read Monopoly state");
        }
    }

    private String write(MonopolyGameState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleViolationException("Unable to write Monopoly state");
        }
    }
}
