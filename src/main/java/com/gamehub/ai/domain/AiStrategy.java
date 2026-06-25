package com.gamehub.ai.domain;

public interface AiStrategy {

    AiType supports();

    AiDecision decide(AiDecisionContext context, AiDifficulty difficulty);
}
