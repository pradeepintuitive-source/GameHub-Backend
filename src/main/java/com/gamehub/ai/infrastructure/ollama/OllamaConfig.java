package com.gamehub.ai.infrastructure.ollama;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gamehub.ollama")
@Data
public class OllamaConfig {

    private boolean enabled = true;
    private String baseUrl = "http://localhost:11434";
    private String model = "mistral";
    private int timeoutSeconds = 30;

    public String getChatEndpoint() {
        return baseUrl + "/api/chat";
    }

    public String getGenerateEndpoint() {
        return baseUrl + "/api/generate";
    }

    public String getModelsEndpoint() {
        return baseUrl + "/api/tags";
    }
}
