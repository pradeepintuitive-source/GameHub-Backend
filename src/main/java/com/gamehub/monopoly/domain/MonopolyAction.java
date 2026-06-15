package com.gamehub.monopoly.domain;

import com.gamehub.shared.domain.GameAction;
import java.util.Map;
import java.util.UUID;

public record MonopolyAction(
        UUID actorPlayerId,
        MonopolyActionType type,
        Integer tilePosition,
        UUID targetPlayerId,
        Integer amount,
        Map<String, String> metadata) implements GameAction {

    @Override
    public String actionType() {
        return type.name();
    }
}
