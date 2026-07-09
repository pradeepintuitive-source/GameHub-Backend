package com.gamehub.room.api;

import com.gamehub.common.domain.ApiException;
import com.gamehub.room.application.RoomService;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    private GameHubUserPrincipal requirePrincipal(GameHubUserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required", List.of());
        }
        return principal;
    }

    @PostMapping
    public RoomDtos.RoomResponse create(
            @AuthenticationPrincipal GameHubUserPrincipal principal,
            @Valid @RequestBody RoomDtos.CreateRoomRequest request) {
        return roomService.createRoom(requirePrincipal(principal), request);
    }

    @GetMapping
    public List<RoomDtos.RoomResponse> list() {
        return roomService.listPublicRooms();
    }

    @GetMapping("/{roomId}")
    public RoomDtos.RoomResponse get(@PathVariable UUID roomId) {
        return roomService.getRoom(roomId);
    }

    @PostMapping("/{roomId}/join")
    public RoomDtos.RoomResponse join(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal GameHubUserPrincipal principal) {
        return roomService.joinRoom(requirePrincipal(principal), new RoomDtos.JoinRoomRequest(null, roomId));
    }

    @PostMapping("/join")
    public RoomDtos.RoomResponse joinByCode(
            @AuthenticationPrincipal GameHubUserPrincipal principal,
            @RequestBody RoomDtos.JoinRoomRequest request) {
        return roomService.joinRoom(requirePrincipal(principal), request);
    }

    @PostMapping("/{roomId}/leave")
    public RoomDtos.RoomResponse leave(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal GameHubUserPrincipal principal) {
        return roomService.leaveRoom(requirePrincipal(principal), roomId);
    }

    @PostMapping("/{roomId}/reconnect")
    public RoomDtos.RoomResponse reconnect(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal GameHubUserPrincipal principal) {
        return roomService.reconnect(requirePrincipal(principal), roomId);
    }

    @PostMapping("/{roomId}/close")
    public RoomDtos.RoomResponse close(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal GameHubUserPrincipal principal) {
        return roomService.closeRoom(requirePrincipal(principal), roomId);
    }

    @PostMapping("/{roomId}/ai")
    public RoomDtos.RoomResponse addAi(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal GameHubUserPrincipal principal,
            @Valid @RequestBody RoomDtos.AddAiPlayerRequest request) {
        return roomService.addAiPlayer(requirePrincipal(principal), roomId, request);
    }
}
