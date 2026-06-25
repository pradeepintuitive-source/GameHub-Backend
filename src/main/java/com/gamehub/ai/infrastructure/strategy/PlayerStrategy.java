package com.gamehub.ai.infrastructure.strategy;

import com.gamehub.ai.domain.AiDecision;
import com.gamehub.ai.domain.AiDecisionContext;
import com.gamehub.ai.domain.AiDifficulty;
import com.gamehub.ai.domain.AiStrategy;
import com.gamehub.ai.domain.AiType;
import org.springframework.stereotype.Component;

@Component
public class PlayerStrategy implements AiStrategy {

    @Override
    public AiType supports() {
        return AiType.AI_PLAYER;
    }

    @Override
    public AiDecision decide(AiDecisionContext context, AiDifficulty difficulty) {
        String action = switch (difficulty) {
            case EASY -> "SAFE_PLAY";
            case MEDIUM -> "BALANCED_PLAY";
            case HARD -> "AGGRESSIVE_PLAY";
        };
        return new AiDecision(supports(), difficulty, action, "Strategy pattern selected an AI player behavior");
    }
}
