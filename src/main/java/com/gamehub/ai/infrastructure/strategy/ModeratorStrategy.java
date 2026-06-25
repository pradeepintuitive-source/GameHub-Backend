package com.gamehub.ai.infrastructure.strategy;

import com.gamehub.ai.domain.AiDecision;
import com.gamehub.ai.domain.AiDecisionContext;
import com.gamehub.ai.domain.AiDifficulty;
import com.gamehub.ai.domain.AiStrategy;
import com.gamehub.ai.domain.AiType;
import org.springframework.stereotype.Component;

@Component
public class ModeratorStrategy implements AiStrategy {

    @Override
    public AiType supports() {
        return AiType.AI_MODERATOR;
    }

    @Override
    public AiDecision decide(AiDecisionContext context, AiDifficulty difficulty) {
        String phase = String.valueOf(context.state().getOrDefault("phase", "UNKNOWN"));
        return new AiDecision(
                supports(),
                difficulty,
                "MODERATE_" + phase,
                "Moderator coordinates phases, announcements, and voting flow");
    }
}
