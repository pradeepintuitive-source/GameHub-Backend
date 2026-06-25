package com.gamehub.ai.domain;

public record AiDecision(
        AiType type,
        AiDifficulty difficulty,
        String action,
        String rationale) {
}
