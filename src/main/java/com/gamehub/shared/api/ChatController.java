package com.gamehub.shared.api;

import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import com.gamehub.shared.application.ChatService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/rooms/{roomId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ChatDtos.ChatMessageResponse send(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal GameHubUserPrincipal principal,
            @Valid @RequestBody ChatDtos.SendChatMessageRequest request) {
        return chatService.send(roomId, principal, request);
    }

    @GetMapping
    public List<ChatDtos.ChatMessageResponse> history(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal GameHubUserPrincipal principal) {
        return chatService.history(roomId, principal);
    }
}
