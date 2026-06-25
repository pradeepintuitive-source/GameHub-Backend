package com.gamehub.ai.infrastructure.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class OllamaDtos {

    private OllamaDtos() {
    }

    public record OllamaMessage(
            String role,
            String content) {
    }

    public record OllamaChatRequest(
            String model,
            List<OllamaMessage> messages,
            @JsonProperty("stream")
            boolean stream) {
    }

    public record OllamaChoice(
            OllamaMessage message) {
    }

    public record OllamaChatResponse(
            String model,
            List<OllamaChoice> choices,
            @JsonProperty("created_at")
            String createdAt,
            long total_tokens) {

        public String getResponseContent() {
            if (choices != null && !choices.isEmpty()) {
                return choices.get(0).message().content();
            }
            return null;
        }
    }

    public record BankerDecisionRequest(
            String gamePhase,
            String playerAction,
            int playerCash,
            int transactionAmount,
            String transactionType,
            int difficulty) {
    }

    public record BankerDecisionResponse(
            String recommendation,
            String reasoning,
            int suggestedAmount,
            boolean approved) {
    }
}
