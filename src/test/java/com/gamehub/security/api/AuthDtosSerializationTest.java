package com.gamehub.security.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthDtosSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authResponseShouldExposeFrontendFriendlyJsonFields() throws Exception {
        var response = new AuthDtos.AuthResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "alice",
                "jwt-token",
                "refresh-token",
                Set.of(),
                false);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("id").asText()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(json.path("accessToken").asText()).isEqualTo("jwt-token");
        assertThat(json.path("refreshToken").asText()).isEqualTo("refresh-token");
        assertThat(json.path("isGuest").asBoolean()).isFalse();
        assertThat(json.has("token")).isFalse();
        assertThat(json.has("userId")).isFalse();
    }
}
