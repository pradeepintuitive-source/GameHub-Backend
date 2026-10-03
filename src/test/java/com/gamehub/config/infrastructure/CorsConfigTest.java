package com.gamehub.config.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {

    @Test
    void shouldUseConfiguredAllowedOriginPatterns() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins", "http://localhost:5173,https://boardgame-verse.vercel.app");
        ReflectionTestUtils.setField(corsConfig, "enableCorsCredentials", true);

        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        HttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOriginPatterns())
                .containsExactly("http://localhost:5173", "https://boardgame-verse.vercel.app");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    @Test
    void shouldDisableCredentialsWhenWildcardOriginIsConfigured() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins", "*");
        ReflectionTestUtils.setField(corsConfig, "enableCorsCredentials", true);

        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/ws"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowCredentials()).isFalse();
    }
}
