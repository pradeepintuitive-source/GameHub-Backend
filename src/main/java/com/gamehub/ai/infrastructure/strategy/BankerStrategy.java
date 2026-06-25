package com.gamehub.ai.infrastructure.strategy;

import com.gamehub.ai.domain.AiDecision;
import com.gamehub.ai.domain.AiDecisionContext;
import com.gamehub.ai.domain.AiDifficulty;
import com.gamehub.ai.domain.AiStrategy;
import com.gamehub.ai.domain.AiType;
import com.gamehub.ai.infrastructure.ollama.BankerOllamaService;
import com.gamehub.ai.infrastructure.ollama.OllamaDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankerStrategy implements AiStrategy {

    private final BankerOllamaService bankerOllamaService;

    @Override
    public AiType supports() {
        return AiType.AI_BANKER;
    }

    @Override
    public AiDecision decide(AiDecisionContext context, AiDifficulty difficulty) {
        log.debug("Banker strategy deciding with difficulty: {}", difficulty);

        try {
            // Get the Monopoly game state from context if available
            Object gameStateObj = context.state().get("gameState");
            Object actionTypeObj = context.input().get("actionType");
            Object amountObj = context.input().get("amount");
            Object transactionTypeObj = context.input().get("transactionType");

            if (gameStateObj == null || actionTypeObj == null) {
                return getDefaultBankerDecision(difficulty);
            }

            // Get Ollama recommendation
            OllamaDtos.BankerDecisionResponse recommendation =
                    bankerOllamaService.getBankerRecommendation(
                            (com.gamehub.monopoly.domain.MonopolyGameState) gameStateObj,
                            actionTypeObj.toString(),
                            amountObj != null ? (int) amountObj : 0,
                            transactionTypeObj != null ? transactionTypeObj.toString() : "UNKNOWN",
                            difficultyToLevel(difficulty)
                    );

            String action = recommendation.approved()
                    ? "APPROVE_" + transactionTypeObj.toString()
                    : "DENY_TRANSACTION";

            return new AiDecision(
                    supports(),
                    difficulty,
                    action,
                    recommendation.reasoning()
            );

        } catch (Exception e) {
            log.error("Error in Banker strategy with Ollama", e);
            return getDefaultBankerDecision(difficulty);
        }
    }

    private AiDecision getDefaultBankerDecision(AiDifficulty difficulty) {
        String action = switch (difficulty) {
            case EASY -> "ANNOUNCE_SIMPLE_TRANSACTION";
            case MEDIUM -> "ANNOUNCE_OPTIMAL_AUCTION_BAND";
            case HARD -> "ANNOUNCE_RISK_AWARE_SETTLEMENT";
        };
        return new AiDecision(
                supports(),
                difficulty,
                action,
                "Banker assists with deterministic bookkeeping"
        );
    }

    private int difficultyToLevel(AiDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 1;
            case MEDIUM -> 3;
            case HARD -> 5;
        };
    }
}
