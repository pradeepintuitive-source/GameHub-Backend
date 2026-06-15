package com.gamehub.shared.api;

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
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameSessionService gameSessionService;

    @PostMapping("/start")
    public GameDtos.GameSessionResponse start(
            @AuthenticationPrincipal GameHubUserPrincipal principal,
            @Valid @RequestBody GameDtos.StartGameRequest request) {
        return gameSessionService.startGame(principal, request);
    }

    @PostMapping("/save")
    public GameDtos.GameSessionResponse save(
            @AuthenticationPrincipal GameHubUserPrincipal principal,
            @Valid @RequestBody GameDtos.SaveGameRequest request) {
        return gameSessionService.save(principal, request);
    }

    @PostMapping("/{sessionId}/pause")
    public GameDtos.GameSessionResponse pause(
            @AuthenticationPrincipal GameHubUserPrincipal principal,
            @PathVariable UUID sessionId) {
        return gameSessionService.pause(principal, sessionId);
    }

    @PostMapping("/{sessionId}/resume")
    public GameDtos.GameSessionResponse resume(
            @AuthenticationPrincipal GameHubUserPrincipal principal,
            @PathVariable UUID sessionId) {
        return gameSessionService.resume(principal, sessionId);
    }

    @GetMapping("/{sessionId}")
    public GameDtos.GameSessionResponse get(
            @AuthenticationPrincipal GameHubUserPrincipal principal,
            @PathVariable UUID sessionId) {
        return gameSessionService.getSession(principal, sessionId);
    }
}
