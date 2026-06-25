package com.gamehub.player.domain;

import com.gamehub.ai.domain.AiDifficulty;
import com.gamehub.ai.domain.AiType;
import java.util.UUID;

public record Player(
        UUID id,
        UUID roomId,
        UUID userId,
        String displayName,
        boolean connected,
        boolean aiControlled,
        AiType aiType,
        AiDifficulty aiDifficulty,
        int seatOrder) {
}
