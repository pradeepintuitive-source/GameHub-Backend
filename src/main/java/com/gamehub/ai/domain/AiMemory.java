package com.gamehub.ai.domain;

import java.util.UUID;

public record AiMemory(
        UUID id,
        AiType aiType,
        UUID scopeId,
        String memoryKey,
        String memoryValue) {
}
