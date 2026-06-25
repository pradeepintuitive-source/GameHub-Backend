package com.gamehub.mafia.domain;

import com.gamehub.shared.domain.GameAction;
import java.util.UUID;

public record MafiaAction(
        UUID actorPlayerId,
        MafiaActionType type,
        UUID targetPlayerId) implements GameAction {

    @Override
    public String actionType() {
        return type.name();
    }
}
