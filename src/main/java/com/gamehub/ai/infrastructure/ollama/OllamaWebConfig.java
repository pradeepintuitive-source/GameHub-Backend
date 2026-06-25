package com.gamehub.ai.infrastructure.ollama;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

@Configuration
public class OllamaWebConfig {

    @Bean(name = "ollamaRestTemplate")
    public RestTemplate ollamaRestTemplate(RestTemplateBuilder builder, OllamaConfig ollamaConfig) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(ollamaConfig.getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(ollamaConfig.getTimeoutSeconds()))
                .build();
    }
}
