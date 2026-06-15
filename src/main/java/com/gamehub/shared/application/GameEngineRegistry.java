package com.gamehub.shared.application;

import com.gamehub.shared.domain.GameEngine;
import com.gamehub.shared.domain.GameType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GameEngineRegistry {

    private final Map<GameType, GameEngine<?, ?>> engines = new EnumMap<>(GameType.class);

    public GameEngineRegistry(List<GameEngine<?, ?>> engines) {
        engines.forEach(engine -> this.engines.put(engine.supportedGameType(), engine));
    }

    @SuppressWarnings("unchecked")
    public <S extends com.gamehub.shared.domain.GameState, A extends com.gamehub.shared.domain.GameAction> GameEngine<S, A> get(GameType gameType) {
        GameEngine<?, ?> engine = engines.get(gameType);
        if (engine == null) {
            throw new IllegalArgumentException("No engine registered for " + gameType);
        }
        return (GameEngine<S, A>) engine;
    }
}
