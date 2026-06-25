package com.gamehub.ai.infrastructure.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class OllamaClient {

    private final OllamaConfig ollamaConfig;
    @Qualifier("ollamaRestTemplate")
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Send a chat request to Ollama and get a response
     */
    public OllamaDtos.OllamaChatResponse chat(String userMessage) {
        if (!ollamaConfig.isEnabled()) {
            log.warn("Ollama is disabled, returning null");
            return null;
        }

        try {
            OllamaDtos.OllamaMessage userMsg = new OllamaDtos.OllamaMessage("user", userMessage);
            OllamaDtos.OllamaChatRequest request = new OllamaDtos.OllamaChatRequest(
                    ollamaConfig.getModel(),
                    List.of(userMsg),
                    false
            );

            log.debug("Sending request to Ollama: {}", ollamaConfig.getChatEndpoint());
            OllamaDtos.OllamaChatResponse response = restTemplate.postForObject(
                    ollamaConfig.getChatEndpoint(),
                    request,
                    OllamaDtos.OllamaChatResponse.class
            );

            log.debug("Received response from Ollama: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Error communicating with Ollama at {}", ollamaConfig.getBaseUrl(), e);
            throw new OllamaException("Failed to get response from Ollama", e);
        }
    }

    /**
     * Send a chat request with multiple messages for context
     */
    public OllamaDtos.OllamaChatResponse chatWithHistory(List<OllamaDtos.OllamaMessage> messages) {
        if (!ollamaConfig.isEnabled()) {
            log.warn("Ollama is disabled");
            return null;
        }

        try {
            OllamaDtos.OllamaChatRequest request = new OllamaDtos.OllamaChatRequest(
                    ollamaConfig.getModel(),
                    messages,
                    false
            );

            log.debug("Sending chat request with {} messages to Ollama", messages.size());
            OllamaDtos.OllamaChatResponse response = restTemplate.postForObject(
                    ollamaConfig.getChatEndpoint(),
                    request,
                    OllamaDtos.OllamaChatResponse.class
            );

            return response;
        } catch (Exception e) {
            log.error("Error in Ollama chat with history", e);
            throw new OllamaException("Failed to get response from Ollama", e);
        }
    }

    /**
     * Health check - verify Ollama is accessible
     */
    public boolean isHealthy() {
        if (!ollamaConfig.isEnabled()) {
            return false;
        }

        try {
            String response = restTemplate.getForObject(
                    ollamaConfig.getModelsEndpoint(),
                    String.class
            );
            log.debug("Ollama health check passed");
            return response != null;
        } catch (Exception e) {
            log.warn("Ollama health check failed", e);
            return false;
        }
    }

    /**
     * Custom exception for Ollama operations
     */
    public static class OllamaException extends RuntimeException {
        public OllamaException(String message) {
            super(message);
        }

        public OllamaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
