package com.gamehub.mafia.domain;

import java.util.UUID;

public record NightAction(
        UUID actorPlayerId,
        MafiaActionType type,
        UUID targetPlayerId) {
}
