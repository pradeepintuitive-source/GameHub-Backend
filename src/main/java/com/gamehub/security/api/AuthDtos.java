package com.gamehub.security.api;

import com.gamehub.security.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record GuestAuthRequest(
            @Size(min = 3, max = 30) String username) {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 30) String username,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 128) String password) {
    }

    public record LoginRequest(
            @NotBlank String identifier,
            @NotBlank String password) {
    }

    public record RefreshTokenRequest(
            @NotBlank String refreshToken) {
    }

    public record LogoutRequest(
            String refreshToken) {
    }

    public record AuthMeResponse(
            UUID userId,
            String username,
            Set<UserRole> roles,
            boolean guest) {
    }

    public record AuthResponse(
            UUID userId,
            String username,
            String token,
            String refreshToken,
            Set<UserRole> roles,
            boolean guest) {
    }
}
