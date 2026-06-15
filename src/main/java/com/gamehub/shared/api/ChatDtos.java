package com.gamehub.shared.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record SendChatMessageRequest(
            UUID targetUserId,
            @NotBlank @Size(max = 500) String content) {
    }

    public record ChatMessageResponse(
            UUID id,
            UUID roomId,
            UUID senderUserId,
            UUID targetUserId,
            String senderName,
            String content,
            boolean systemMessage,
            boolean aiMessage,
            Instant sentAt) {
    }
}
