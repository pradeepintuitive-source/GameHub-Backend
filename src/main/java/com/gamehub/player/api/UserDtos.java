package com.gamehub.player.api;

import com.gamehub.security.domain.UserRole;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(
            UUID id,
            String username,
            String email,
            boolean guest,
            Set<UserRole> roles,
            ProfileResponse profile,
            StatisticsResponse statistics) {
    }

    public record ProfileResponse(
            String displayName,
            String avatarUrl,
            String locale) {
    }

    public record StatisticsResponse(
            int gamesPlayed,
            int wins,
            int losses,
            long playTimeSeconds) {
    }
}
