package com.gamehub.room.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gamehub.audit.application.AuditService;
import com.gamehub.notification.application.NotificationService;
import com.gamehub.player.api.UserDtos;
import com.gamehub.player.application.UserService;
import com.gamehub.player.infrastructure.PlayerEntity;
import com.gamehub.player.infrastructure.PlayerRepository;
import com.gamehub.room.api.RoomDtos;
import com.gamehub.room.domain.RoomState;
import com.gamehub.room.infrastructure.RoomMapper;
import com.gamehub.room.infrastructure.RoomRepository;
import com.gamehub.security.domain.UserRole;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.shared.application.GameEventService;
import com.gamehub.shared.application.SaveGameService;
import com.gamehub.shared.domain.GameType;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private UserService userService;
    @Mock
    private GameEventService gameEventService;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SaveGameService saveGameService;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        RoomMapper roomMapper = Mappers.getMapper(RoomMapper.class);
        roomService = new RoomService(
                roomRepository,
                playerRepository,
                roomMapper,
                userService,
                gameEventService,
                auditService,
                notificationService,
                saveGameService);
    }

    @Test
    void createRoomShouldPersistRoomAndHostPlayer() {
        UUID userId = UUID.randomUUID();
        GameHubUserPrincipal principal = new GameHubUserPrincipal(userId, "host", "pwd", false, Set.of(UserRole.PLAYER));
        when(roomRepository.findByRoomCode(any())).thenReturn(java.util.Optional.empty());
        when(userService.getCurrentUser(principal)).thenReturn(new UserDtos.UserResponse(
                userId,
                "host",
                "host@example.com",
                false,
                Set.of(UserRole.PLAYER),
                new UserDtos.ProfileResponse("Host", null, "en_US"),
                new UserDtos.StatisticsResponse(0, 0, 0, 0)));
        when(playerRepository.findByRoomIdOrderBySeatOrder(any())).thenReturn(java.util.List.of(new PlayerEntity()));

        RoomDtos.RoomResponse response = roomService.createRoom(
                principal,
                new RoomDtos.CreateRoomRequest(GameType.MONOPOLY, com.gamehub.room.domain.RoomType.ONLINE, com.gamehub.room.domain.RoomVisibility.PUBLIC, 4));

        assertThat(response.hostUserId()).isEqualTo(userId);
        assertThat(response.gameType()).isEqualTo(GameType.MONOPOLY);
        assertThat(response.state()).isEqualTo(RoomState.WAITING);
    }
}
