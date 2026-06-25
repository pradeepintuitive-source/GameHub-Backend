package com.gamehub.monopoly.api;

import com.gamehub.monopoly.application.MonopolyGameService;
import com.gamehub.room.application.RoomService;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.shared.application.GameSessionService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monopoly")
@RequiredArgsConstructor
public class MonopolyController {

    private final MonopolyGameService monopolyGameService;
    private final GameSessionService gameSessionService;
    private final RoomService roomService;

    @GetMapping("/{sessionId}")
    public MonopolyDtos.MonopolyStateResponse getState(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal GameHubUserPrincipal principal) {
        var session = gameSessionService.requireSession(sessionId);
        gameSessionService.getSession(principal, sessionId);
        return monopolyGameService.toResponse(monopolyGameService.getState(session));
    }

    @PostMapping("/{sessionId}/action")
    public MonopolyDtos.MonopolyStateResponse action(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal GameHubUserPrincipal principal,
            @Valid @RequestBody MonopolyDtos.MonopolyActionRequest request) {
        var session = gameSessionService.requireSession(sessionId);
        UUID actorPlayerId = gameSessionService.resolveActorPlayerId(session.getRoomId(), principal.userId());
        return monopolyGameService.toResponse(
                monopolyGameService.processAction(session.getRoomId(), session, principal, actorPlayerId, request));
    }
}
