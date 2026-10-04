package com.gamehub.room.application;

import com.gamehub.ai.domain.AiDifficulty;
import com.gamehub.ai.domain.AiType;
import com.gamehub.audit.application.AuditService;
import com.gamehub.audit.domain.AuditType;
import com.gamehub.common.domain.ApiException;
import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.mafia.infrastructure.MafiaGameRepository;
import com.gamehub.mafia.infrastructure.MafiaRoleRepository;
import com.gamehub.mafia.infrastructure.MafiaVoteRepository;
import com.gamehub.monopoly.infrastructure.MonopolyGameRepository;
import com.gamehub.monopoly.infrastructure.MonopolyPropertyRepository;
import com.gamehub.notification.application.NotificationService;
import com.gamehub.notification.domain.NotificationMessage;
import com.gamehub.player.application.UserService;
import com.gamehub.player.infrastructure.PlayerEntity;
import com.gamehub.player.infrastructure.PlayerRepository;
import com.gamehub.player.infrastructure.UserEntity;
import com.gamehub.player.infrastructure.UserRepository;
import com.gamehub.room.api.RoomDtos;
import com.gamehub.room.domain.PlayMode;
import com.gamehub.room.domain.RoomState;
import com.gamehub.room.domain.RoomVisibility;
import com.gamehub.room.infrastructure.RoomEntity;
import com.gamehub.room.infrastructure.RoomMapper;
import com.gamehub.room.infrastructure.RoomRepository;
import com.gamehub.security.domain.UserRole;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.shared.application.GameEventService;
import com.gamehub.shared.application.SaveGameService;
import com.gamehub.shared.domain.GameEventType;
import com.gamehub.shared.infrastructure.ChatMessageRepository;
import com.gamehub.shared.infrastructure.GameEventRepository;
import com.gamehub.shared.infrastructure.GameSessionEntity;
import com.gamehub.shared.infrastructure.GameSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional
@RequiredArgsConstructor
public class RoomService {

    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);

    private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;
    private final RoomMapper roomMapper;
    private final UserService userService;
    private final GameEventService gameEventService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final com.gamehub.websocket.infrastructure.LobbyBroadcaster lobbyBroadcaster;
    private final SaveGameService saveGameService;
    private final GameSessionRepository gameSessionRepository;
    private final GameEventRepository gameEventRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MonopolyPropertyRepository monopolyPropertyRepository;
    private final MonopolyGameRepository monopolyGameRepository;
    private final MafiaVoteRepository mafiaVoteRepository;
    private final MafiaRoleRepository mafiaRoleRepository;
    private final MafiaGameRepository mafiaGameRepository;
    private final Random random = new Random();

    public RoomDtos.RoomResponse createRoom(GameHubUserPrincipal principal, RoomDtos.CreateRoomRequest request) {
        logger.info("createRoom request from user={} gameType={} roomType={} visibility={} maxPlayers={}",
                principal.userId(), request.gameType(), request.roomType(), request.visibility(), request.maxPlayers());

        RoomEntity roomEntity = new RoomEntity();
        roomEntity.setId(UUID.randomUUID());
        roomEntity.setRoomCode(generateRoomCode());
        roomEntity.setHostUserId(principal.userId());
        roomEntity.setGameType(request.gameType());
        roomEntity.setRoomType(request.roomType());
        roomEntity.setVisibility(request.visibility());
        roomEntity.setState(RoomState.WAITING);
        roomEntity.setMaxPlayers(request.maxPlayers());
        roomEntity.setPlayMode(PlayMode.ONLINE);
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
        broadcastRoomAfterCommit(roomEntity.getId());

        logger.info("Room created id={} code={} hostUserId={}", roomEntity.getId(), roomEntity.getRoomCode(), principal.userId());
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
        logger.info("joinRoom for user={} request={} roomCode={}", principal.userId(), request, request.roomCode());
        RoomEntity room = resolveRoom(request);
        if (room.getPlayMode() == PlayMode.LOCAL && !room.getHostUserId().equals(principal.userId())) {
            throw new BusinessRuleViolationException("This match is played on one device");
        }
        if (playerRepository.findByRoomIdAndUserId(room.getId(), principal.userId()).isPresent()) {
            PlayerEntity existingPlayer = playerRepository.findByRoomIdAndUserId(room.getId(), principal.userId()).get();
            existingPlayer.setConnected(true);
            playerRepository.save(existingPlayer);
            broadcastRoomAfterCommit(room.getId());
            return toResponse(room);
        }
        if (room.getState() != RoomState.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, "Room is not accepting new players", List.of());
        }
        if (playerRepository.countByRoomId(room.getId()) >= room.getMaxPlayers()) {
            throw new BusinessRuleViolationException("Room is full");
        }
        var user = userService.getCurrentUser(principal);
        PlayerEntity player = new PlayerEntity();
        player.setId(UUID.randomUUID());
        player.setRoomId(room.getId());
        player.setUserId(principal.userId());
        player.setDisplayName(user.profile().displayName());
        player.setConnected(true);
        player.setReady(false);
        player.setAiControlled(false);
        player.setSeatOrder((int) playerRepository.countByRoomId(room.getId()) + 1);
        playerRepository.save(player);

        auditService.record(AuditType.ROOM_EVENT, room.getId(), null, principal.userId(), "Player joined room", user.username());
        gameEventService.record(room.getId(), room.getCurrentSessionId(), GameEventType.PLAYER_JOINED, principal.userId(), user.username());
        broadcastRoomAfterCommit(room.getId());
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
        broadcastRoomAfterCommit(roomId);
        return toResponse(room);
    }

    public RoomDtos.RoomResponse addLocalPlayers(
            GameHubUserPrincipal principal, UUID roomId, RoomDtos.AddLocalPlayersRequest request) {
        RoomEntity room = requireRoom(roomId);
        assertHost(room, principal.userId());
        if (room.getState() != RoomState.WAITING) {
            throw new BusinessRuleViolationException("Players can only be named before the match starts");
        }
        List<String> names = request.players().stream()
                .map(name -> name == null ? "" : name.trim())
                .filter(name -> !name.isEmpty())
                .toList();
        if (names.size() < 2 || names.size() > 6) {
            throw new BusinessRuleViolationException("A same-device match needs 2 to 6 players");
        }

        PlayerEntity host = playerRepository.findByRoomIdAndUserId(roomId, principal.userId())
                .orElseThrow(() -> new BusinessRuleViolationException("Player is not part of room"));
        playerRepository.findByRoomIdOrderBySeatOrder(roomId).stream()
                .filter(player -> !player.getId().equals(host.getId()))
                .forEach(playerRepository::delete);
        playerRepository.flush();

        host.setDisplayName(names.getFirst());
        host.setConnected(true);
        host.setReady(true);
        host.setAiControlled(false);
        host.setSeatOrder(1);
        playerRepository.save(host);

        room.setPlayMode(PlayMode.LOCAL);
        room.setVisibility(RoomVisibility.PRIVATE);
        room.setMaxPlayers(names.size());
        roomRepository.save(room);

        for (int index = 1; index < names.size(); index++) {
            UserEntity seatUser = new UserEntity();
            seatUser.setId(UUID.randomUUID());
            seatUser.setUsername("seat-" + seatUser.getId().toString().replace("-", "").substring(0, 12));
            seatUser.setPasswordHash("{noop}local-seat");
            seatUser.setGuest(true);
            seatUser.setRoles(UserRole.GUEST.name());
            userRepository.save(seatUser);

            PlayerEntity seat = new PlayerEntity();
            seat.setId(UUID.randomUUID());
            seat.setRoomId(roomId);
            seat.setUserId(seatUser.getId());
            seat.setDisplayName(names.get(index));
            seat.setConnected(true);
            seat.setReady(true);
            seat.setAiControlled(false);
            seat.setSeatOrder(index + 1);
            playerRepository.save(seat);
        }

        auditService.record(
                AuditType.ROOM_EVENT,
                roomId,
                room.getCurrentSessionId(),
                principal.userId(),
                "Local players named",
                String.join(", ", names));
        gameEventService.record(
                roomId, room.getCurrentSessionId(), GameEventType.PLAYER_JOINED, principal.userId(), String.join(", ", names));
        broadcastRoomAfterCommit(roomId);
        return toResponse(room);
    }

    public boolean isLocalHost(UUID roomId, UUID userId) {
        return roomRepository.findById(roomId)
                .filter(room -> room.getPlayMode() == PlayMode.LOCAL && room.getHostUserId().equals(userId))
                .isPresent();
    }

    public RoomDtos.RoomResponse leaveRoom(GameHubUserPrincipal principal, UUID roomId) {
        RoomEntity room = requireRoom(roomId);
        PlayerEntity player = playerRepository.findByRoomIdAndUserId(roomId, principal.userId())
                .orElseThrow(() -> new BusinessRuleViolationException("Player is not part of room"));
        playerRepository.delete(player);
        auditService.record(AuditType.ROOM_EVENT, roomId, room.getCurrentSessionId(), principal.userId(), "Player left room", player.getDisplayName());
        gameEventService.record(roomId, room.getCurrentSessionId(), GameEventType.PLAYER_LEFT, principal.userId(), player.getDisplayName());

        List<PlayerEntity> remainingHumans = playerRepository.findByRoomIdOrderBySeatOrder(roomId).stream()
                .filter(candidate -> !candidate.isAiControlled())
                .toList();
        if (remainingHumans.isEmpty()) {
            playerRepository.findByRoomIdOrderBySeatOrder(roomId).forEach(playerRepository::delete);
            deleteRoomAndDependencies(room);
            broadcastRoomClosedAfterCommit(roomId);
            return null;
        }

        if (principal.userId().equals(room.getHostUserId())) {
            PlayerEntity nextHost = remainingHumans.stream()
                    .min(java.util.Comparator.comparingInt(PlayerEntity::getSeatOrder).thenComparing(PlayerEntity::getCreatedAt))
                    .orElse(remainingHumans.getFirst());
            room.setHostUserId(nextHost.getUserId());
            roomRepository.save(room);
        }
        broadcastRoomAfterCommit(roomId);
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
        broadcastRoomAfterCommit(roomId);
        return toResponse(room);
    }

    public RoomDtos.RoomResponse closeRoom(GameHubUserPrincipal principal, UUID roomId) {
        RoomEntity room = requireRoom(roomId);
        assertHost(room, principal.userId());
        String roomCode = room.getRoomCode();
        UUID sessionId = room.getCurrentSessionId();
        auditService.record(AuditType.ROOM_EVENT, roomId, sessionId, principal.userId(), "Room closed", roomCode);
        gameEventService.record(roomId, sessionId, GameEventType.ROOM_UPDATED, principal.userId(), "Room closed");
        playerRepository.findByRoomIdOrderBySeatOrder(roomId).forEach(playerRepository::delete);
        deleteRoomAndDependencies(room);
        notificationService.sendToTopic(
            "/topic/rooms",
            new NotificationMessage("ROOM_CLOSED", roomId, sessionId, roomCode, Instant.now()));
        broadcastRoomClosedAfterCommit(roomId);
        return null;
    }

    public void markDisconnected(UUID userId) {
        List<PlayerEntity> activePlayers = playerRepository.findAll().stream()
                .filter(player -> player.getUserId().equals(userId))
                .toList();
        for (PlayerEntity player : activePlayers) {
            RoomEntity room = roomRepository.findById(player.getRoomId()).orElse(null);
            if (room == null) {
                continue;
            }
            player.setConnected(false);
            playerRepository.save(player);
            gameEventService.record(player.getRoomId(), room.getCurrentSessionId(), GameEventType.PLAYER_DISCONNECTED, userId, player.getDisplayName());
            if (room.getCurrentSessionId() != null) {
                saveGameService.autoSaveCurrentSessionForRoom(player.getRoomId(), userId, "PLAYER_DISCONNECT");
            }
            try {
                broadcastRoomAfterCommit(player.getRoomId());
            } catch (Exception e) {
                // ignore missing room during disconnect handling
            }
        }
    }

    public RoomDtos.RoomResponse setReady(GameHubUserPrincipal principal, UUID roomId, boolean ready) {
        RoomEntity room = requireRoom(roomId);
        PlayerEntity player = playerRepository.findByRoomIdAndUserId(roomId, principal.userId())
                .orElseThrow(() -> new BusinessRuleViolationException("Player is not part of room"));
        player.setReady(ready);
        playerRepository.save(player);
        auditService.record(AuditType.ROOM_EVENT, roomId, room.getCurrentSessionId(), principal.userId(), "Player ready toggled", String.valueOf(ready));
        gameEventService.record(roomId, room.getCurrentSessionId(), GameEventType.PLAYER_UPDATED, principal.userId(), player.getDisplayName());
        broadcastRoomAfterCommit(roomId);
        return toResponse(room);
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
        String normalizedCode = request.roomCode().trim();
        return roomRepository.findByRoomCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new BusinessRuleViolationException("Room not found"));
    }

    private RoomEntity requireRoom(UUID roomId) {
        logger.info("requireRoom lookup roomId={}", roomId);
        return roomRepository.findById(roomId)
                .orElseThrow(() -> {
                    logger.warn("Room not found for roomId={}", roomId);
                    return new BusinessRuleViolationException("Room not found");
                });
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
                roomEntity.getCurrentSessionId(),
                roomEntity.getPlayMode() == null ? PlayMode.ONLINE : roomEntity.getPlayMode());
    }

    private void broadcastRoomAfterCommit(UUID roomId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            broadcastRoomSnapshot(roomId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broadcastRoomSnapshot(roomId);
            }
        });
    }

    private void broadcastRoomClosedAfterCommit(UUID roomId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            lobbyBroadcaster.broadcastRoomClosed(roomId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                lobbyBroadcaster.broadcastRoomClosed(roomId);
            }
        });
    }

    private void broadcastRoomSnapshot(UUID roomId) {
        roomRepository.findById(roomId).ifPresent(room -> lobbyBroadcaster.broadcastRoom(roomId, toResponse(room)));
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

    /**
     * Deletes game sessions and dependent rows before removing the room,
     * avoiding PostgreSQL foreign-key violations.
     */
    private void deleteRoomAndDependencies(RoomEntity room) {
        UUID roomId = room.getId();
        List<GameSessionEntity> sessions = gameSessionRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
        for (GameSessionEntity session : sessions) {
            UUID sessionId = session.getId();
            monopolyPropertyRepository.deleteBySessionId(sessionId);
            monopolyGameRepository.deleteBySessionId(sessionId);
            mafiaVoteRepository.deleteBySessionId(sessionId);
            mafiaRoleRepository.deleteBySessionId(sessionId);
            mafiaGameRepository.deleteBySessionId(sessionId);
            gameEventRepository.deleteBySessionId(sessionId);
        }
        gameEventRepository.deleteByRoomId(roomId);
        chatMessageRepository.deleteByRoomId(roomId);
        room.setCurrentSessionId(null);
        roomRepository.save(room);
        gameSessionRepository.deleteByRoomId(roomId);
        roomRepository.delete(room);
        logger.info("Deleted room {} and dependent game data", roomId);
    }
}
