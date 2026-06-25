package com.gamehub.shared.domain;

import java.util.List;
import java.util.UUID;

public interface GameEngine<S extends GameState, A extends GameAction> {

    GameType supportedGameType();

    Class<S> stateType();

    S startGame(UUID sessionId, List<UUID> playerIds);

    S endGame(S state);

    S pauseGame(S state);

    S resumeGame(S state);

    void validateAction(S state, A action);

    S processAction(S state, A action);
}
