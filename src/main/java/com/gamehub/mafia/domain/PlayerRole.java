package com.gamehub.mafia.domain;

import java.util.UUID;

public record PlayerRole(
        UUID playerId,
        Role role,
        boolean alive,
        boolean revealed) {
}
