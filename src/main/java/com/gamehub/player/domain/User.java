package com.gamehub.player.domain;

import com.gamehub.security.domain.UserRole;
import java.util.Set;
import java.util.UUID;

public record User(
        UUID id,
        String username,
        String email,
        boolean guest,
        Set<UserRole> roles,
        Profile profile,
        Statistics statistics) {
}
