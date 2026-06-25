package com.gamehub.mafia.domain;

import java.util.UUID;

public record Vote(
        UUID voterPlayerId,
        UUID targetPlayerId) {
}
