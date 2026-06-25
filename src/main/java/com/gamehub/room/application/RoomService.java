package com.gamehub.room.application;

import com.gamehub.ai.domain.AiDifficulty;
import com.gamehub.ai.domain.AiType;
import com.gamehub.audit.application.AuditService;
import com.gamehub.audit.domain.AuditType;
import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.notification.application.NotificationService;
import com.gamehub.notification.domain.NotificationMessage;
import com.gamehub.player.application.UserService;
import com.gamehub.player.infrastructure.PlayerEntity;
import com.gamehub.player.infrastructure.PlayerRepository;
import com.gamehub.room.api.RoomDtos;
import com.gamehub.room.domain.RoomState;
import com.gamehub.room.infrastructure.RoomEntity;
import com.gamehub.room.infrastructure.RoomMapper;
import com.gamehub.room.infrastructure.RoomRepository;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.shared.application.GameEventService;
import com.gamehub.shared.application.SaveGameService;
import com.gamehub.shared.domain.GameEventType;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RoomService {

    private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final RoomMapper roomMapper;
    private final UserService userService;
    private final GameEventService gameEventService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SaveGameService saveGameService;
    private final Random random = new Random();

    public RoomDtos.RoomResponse createRoom(GameHubUserPrincipal principal, RoomDtos.CreateRoomRequest request) {
        RoomEntity roomEntity = new RoomEntity();
        roomEntity.setId(UUID.randomUUID());
        roomEntity.setRoomCode(generateRoomCode());
        roomEntity.setHostUserId(principal.userId());
        roomEntity.setGameType(request.gameType());
        roomEntity.setRoomType(request.roomType());
        roomEntity.setVisibility(request.visibility());
        roomEntity.setState(RoomState.WAITING);
        roomEntity.setMaxPlayers(request.maxPlayers());
        roomRepository.save(roomEntity);

        var user = userService.getCurrentUser(principal);
        PlayerEntity hostPlayer = new PlayerEntity();
        hostPlayer.setId(UUID.randomUUID());
        hostPlayer.setRoomId(roomEntity.getId());
        hostPlayer.setUserId(principal.userId());
        hostPlayer.setDisplayName(user.profile().displayName());
        hostPlayer.setConnected(true);
        hostPlayer.setAiControlled(false);
        hostPlayer.setSeatOrder(1);
        playerRepository.save(hostPlayer);

        auditService.record(AuditType.ROOM_EVENT, roomEntity.getId(), null, principal.userId(), "Room created", roomEntity.getRoomCode());
        gameEventService.record(roomEntity.getId(), null, GameEventType.ROOM_UPDATED, principal.userId(), "Room created");
        notificationService.sendToTopic(
                "/topic/rooms",
                new NotificationMessage("ROOM_UPDATED", roomEntity.getId(), null, roomEntity.getRoomCode(), Instant.now()));
        return toResponse(roomEntity);
    }

    @Transactional(readOnly = true)
    public List<RoomDtos.RoomResponse> listPublicRooms() {
        return roomRepository.findByVisibilityAndStateIn(
                        com.gamehub.room.domain.RoomVisibility.PUBLIC,
                        List.of(RoomState.WAITING, RoomState.STARTING, RoomState.IN_PROGRESS, RoomState.PAUSED))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public RoomDtos.RoomResponse joinRoom(GameHubUserPrincipal principal, RoomDtos.JoinRoomRequest request) {
        RoomEntity room = resolveRoom(request);
        if (playerRepository.countByRoomId(room.getId()) >= room.getMaxPlayers()) {
            throw new BusinessRuleViolationException("Room is full");
        }
        if (playerRepository.findByRoomIdAndUserId(room.getId(), principal.userId()).isPresent()) {
            return reconnect(principal, room.getId());
        }
        var user = userService.getCurrentUser(principal);
        PlayerEntity player = new PlayerEntity();
        player.setId(UUID.randomUUID());
        player.setRoomId(room.getId());
        player.setUserId(principal.userId());
        player.setDisplayName(user.profile().displayName());
        player.setConnected(true);
        player.setAiControlled(false);
        player.setSeatOrder((int) playerRepository.countByRoomId(room.getId()) + 1);
        playerRepository.save(player);

        auditService.record(AuditType.ROOM_EVENT, room.getId(), null, principal.userId(), "Player joined room", user.username());
        gameEventService.record(room.getId(), room.getCurrentSessionId(), GameEventType.PLAYER_JOINED, principal.userId(), user.username());
        return toResponse(room);
    }

    public RoomDtos.RoomResponse addAiPlayer(GameHubUserPrincipal principal, UUID roomId, RoomDtos.AddAiPlayerRequest request) {
        RoomEntity room = requireRoom(roomId);
        assertHost(room, principal.userId());
        if (playerRepository.countByRoomId(roomId) >= room.getMaxPlayers()) {
            throw new BusinessRuleViolationException("Room is full");
        }

        PlayerEntity aiPlayer = new PlayerEntity();
        aiPlayer.setId(UUID.randomUUID());
        aiPlayer.setRoomId(roomId);
        aiPlayer.setUserId(UUID.randomUUID());
        aiPlayer.setDisplayName(request.displayName());
        aiPlayer.setConnected(true);
        aiPlayer.setAiControlled(true);
        aiPlayer.setAiType(request.aiType());
        aiPlayer.setAiDifficulty(request.aiDifficulty());
        aiPlayer.setSeatOrder((int) playerRepository.countByRoomId(roomId) + 1);
        playerRepository.save(aiPlayer);

        auditService.record(
                AuditType.AI_DECISION,
                roomId,
                room.getCurrentSessionId(),
                principal.userId(),
                "AI player added",
                request.displayName() + ":" + request.aiType() + ":" + request.aiDifficulty());
        gameEventService.record(roomId, room.getCurrentSessionId(), GameEventType.PLAYER_JOINED, principal.userId(), request.displayName());
        return toResponse(room);
    }

    public RoomDtos.RoomResponse leaveRoom(GameHubUserPrincipal principal, UUID roomId) {
        RoomEntity room = requireRoom(roomId);
        PlayerEntity player = playerRepository.findByRoomIdAndUserId(roomId, principal.userId())
                .orElseThrow(() -> new BusinessRuleViolationException("Player is not part of room"));
        playerRepository.delete(player);
        auditService.record(AuditType.ROOM_EVENT, roomId, room.getCurrentSessionId(), principal.userId(), "Player left room", player.getDisplayName());
        gameEventService.record(roomId, room.getCurrentSessionId(), GameEventType.PLAYER_LEFT, principal.userId(), player.getDisplayName());

        if (principal.userId().equals(room.getHostUserId())) {
            List<PlayerEntity> remaining = playerRepository.findByRoomIdOrderBySeatOrder(roomId);
            if (remaining.isEmpty()) {
                roomRepository.delete(room);
                return null;
            }
            room.setHostUserId(remaining.getFirst().getUserId());
            roomRepository.save(room);
        }
        return toResponse(room);
    }

    public RoomDtos.RoomResponse reconnect(GameHubUserPrincipal principal, UUID roomId) {
        RoomEntity room = requireRoom(roomId);
        PlayerEntity player = playerRepository.findByRoomIdAndUserId(roomId, principal.userId())
                .orElseThrow(() -> new BusinessRuleViolationException("Player is not part of room"));
        player.setConnected(true);
        playerRepository.save(player);
        auditService.record(AuditType.ROOM_EVENT, roomId, room.getCurrentSessionId(), principal.userId(), "Player reconnected", player.getDisplayName());
        gameEventService.record(roomId, room.getCurrentSessionId(), GameEventType.PLAYER_RECONNECTED, principal.userId(), player.getDisplayName());
        notificationService.sendToUser(
                principal.userId(),
                "/queue/private",
                new NotificationMessage("PLAYER_RECONNECTED", roomId, room.getCurrentSessionId(), toResponse(room), Instant.now()));
        return toResponse(room);
    }

    public RoomDtos.RoomResponse closeRoom(GameHubUserPrincipal principal, UUID roomId) {
        RoomEntity room = requireRoom(roomId);
        assertHost(room, principal.userId());
        playerRepository.findByRoomIdOrderBySeatOrder(roomId).forEach(playerRepository::delete);
        roomRepository.delete(room);
        auditService.record(AuditType.ROOM_EVENT, roomId, room.getCurrentSessionId(), principal.userId(), "Room closed", room.getRoomCode());
        gameEventService.record(roomId, room.getCurrentSessionId(), GameEventType.ROOM_UPDATED, principal.userId(), "Room closed");
        notificationService.sendToTopic(
                "/topic/rooms",
                new NotificationMessage("ROOM_CLOSED", roomId, room.getCurrentSessionId(), room.getRoomCode(), Instant.now()));
        return null;
    }

    public void markDisconnected(UUID userId) {
        List<PlayerEntity> activePlayers = playerRepository.findAll().stream()
                .filter(player -> player.getUserId().equals(userId))
                .toList();
        for (PlayerEntity player : activePlayers) {
            player.setConnected(false);
            playerRepository.save(player);
            gameEventService.record(player.getRoomId(), null, GameEventType.PLAYER_DISCONNECTED, userId, player.getDisplayName());
            saveGameService.autoSaveCurrentSessionForRoom(player.getRoomId(), userId, "PLAYER_DISCONNECT");
        }
    }

    @Transactional(readOnly = true)
    public RoomDtos.RoomResponse getRoom(UUID roomId) {
        return toResponse(requireRoom(roomId));
    }

    private RoomEntity resolveRoom(RoomDtos.JoinRoomRequest request) {
        if (request.roomId() != null) {
            return requireRoom(request.roomId());
        }
        if (request.roomCode() == null || request.roomCode().isBlank()) {
            throw new BusinessRuleViolationException("roomId or roomCode is required");
        }
        return roomRepository.findByRoomCode(request.roomCode())
                .orElseThrow(() -> new BusinessRuleViolationException("Room not found"));
    }

    private RoomEntity requireRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessRuleViolationException("Room not found"));
    }

    private void assertHost(RoomEntity room, UUID userId) {
        if (!room.getHostUserId().equals(userId)) {
            throw new BusinessRuleViolationException("Only the host can perform this action");
        }
    }

    private RoomDtos.RoomResponse toResponse(RoomEntity roomEntity) {
        List<RoomDtos.PlayerSummary> players = playerRepository.findByRoomIdOrderBySeatOrder(roomEntity.getId())
                .stream()
                .map(roomMapper::toPlayerSummary)
                .toList();
        return new RoomDtos.RoomResponse(
                roomEntity.getId(),
                roomEntity.getRoomCode(),
                roomEntity.getHostUserId(),
                roomEntity.getGameType(),
                roomEntity.getRoomType(),
                roomEntity.getVisibility(),
                roomEntity.getState(),
                roomEntity.getMaxPlayers(),
                players,
                roomEntity.getCurrentSessionId());
    }

    private String generateRoomCode() {
        String candidate;
        do {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 6; index++) {
                builder.append(ROOM_CODE_ALPHABET.charAt(random.nextInt(ROOM_CODE_ALPHABET.length())));
            }
            candidate = builder.toString();
        } while (roomRepository.findByRoomCode(candidate).isPresent());
        return candidate;
    }
}
