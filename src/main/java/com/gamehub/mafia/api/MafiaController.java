package com.gamehub.mafia.api;

import com.gamehub.mafia.application.MafiaGameService;
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
@RequestMapping("/api/mafia")
@RequiredArgsConstructor
public class MafiaController {

    private final MafiaGameService mafiaGameService;
    private final GameSessionService gameSessionService;

    @GetMapping("/{sessionId}")
    public MafiaDtos.MafiaStateResponse getState(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal GameHubUserPrincipal principal) {
        var session = gameSessionService.requireSession(sessionId);
        gameSessionService.getSession(principal, sessionId);
        return mafiaGameService.toResponse(mafiaGameService.getState(session));
    }

    @PostMapping("/{sessionId}/action")
    public MafiaDtos.MafiaStateResponse action(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal GameHubUserPrincipal principal,
            @Valid @RequestBody MafiaDtos.MafiaActionRequest request) {
        var session = gameSessionService.requireSession(sessionId);
        UUID actorPlayerId = gameSessionService.resolveActorPlayerId(session.getRoomId(), principal.userId());
        return mafiaGameService.toResponse(
                mafiaGameService.processAction(session.getRoomId(), session, principal, actorPlayerId, request));
    }

    @PostMapping("/{sessionId}/advance-phase")
    public MafiaDtos.MafiaStateResponse advancePhase(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal GameHubUserPrincipal principal) {
        var session = gameSessionService.requireSession(sessionId);
        gameSessionService.assertHostForSession(session, principal.userId());
        return mafiaGameService.toResponse(mafiaGameService.advancePhase(session.getRoomId(), session, principal.userId()));
    }
}
