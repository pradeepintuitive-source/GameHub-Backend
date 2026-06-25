package com.gamehub.ai.api;

import com.gamehub.ai.domain.AiDifficulty;
import com.gamehub.ai.domain.AiType;
import com.gamehub.shared.domain.GameType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public final class AiDtos {

    private AiDtos() {
    }

    public record AiDecisionRequest(
            @NotNull AiType aiType,
            @NotNull AiDifficulty difficulty,
            @NotNull UUID scopeId,
            @NotNull GameType gameType,
            Map<String, Object> state,
            Map<String, Object> input) {
    }

    public record AiDecisionResponse(
            AiType aiType,
            AiDifficulty difficulty,
            String action,
            String rationale) {
    }

    public record AiMemoryResponse(
            UUID id,
            AiType aiType,
            UUID scopeId,
            @NotBlank String memoryKey,
            @NotBlank String memoryValue) {
    }
}
