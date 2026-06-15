package com.gamehub.ai.domain;

import com.gamehub.shared.domain.GameType;
import java.util.Map;
import java.util.UUID;

public record AiDecisionContext(
        UUID scopeId,
        GameType gameType,
        Map<String, Object> state,
        Map<String, Object> input) {
}
