package com.gamehub.shared.domain;

import java.util.UUID;

public interface GameAction {

    UUID actorPlayerId();

    String actionType();
}
