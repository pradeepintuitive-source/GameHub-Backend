package com.gamehub.ai.infrastructure.strategy;

import com.gamehub.ai.domain.AiDecision;
import com.gamehub.ai.domain.AiDecisionContext;
import com.gamehub.ai.domain.AiDifficulty;
import com.gamehub.ai.domain.AiStrategy;
import com.gamehub.ai.domain.AiType;
import org.springframework.stereotype.Component;

@Component
public class BankerStrategy implements AiStrategy {

    @Override
    public AiType supports() {
        return AiType.AI_BANKER;
    }

    @Override
    public AiDecision decide(AiDecisionContext context, AiDifficulty difficulty) {
        String action = switch (difficulty) {
            case EASY -> "ANNOUNCE_SIMPLE_TRANSACTION";
            case MEDIUM -> "ANNOUNCE_OPTIMAL_AUCTION_BAND";
            case HARD -> "ANNOUNCE_RISK_AWARE_SETTLEMENT";
        };
        return new AiDecision(supports(), difficulty, action, "Banker assists with deterministic bookkeeping only");
    }
}
