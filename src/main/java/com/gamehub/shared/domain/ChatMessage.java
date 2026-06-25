package com.gamehub.shared.domain;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(
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
