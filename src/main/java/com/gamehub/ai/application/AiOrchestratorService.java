package com.gamehub.ai.application;

import com.gamehub.ai.api.AiDtos;
import com.gamehub.ai.domain.AiDecisionContext;
import com.gamehub.ai.domain.AiStrategy;
import com.gamehub.ai.infrastructure.AiMemoryEntity;
import com.gamehub.ai.infrastructure.AiMemoryRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiOrchestratorService {

    private final List<AiStrategy> strategies;
    private final AiMemoryRepository aiMemoryRepository;

    @Transactional
    public AiDtos.AiDecisionResponse decide(AiDtos.AiDecisionRequest request) {
        var strategy = strategies.stream()
                .filter(candidate -> candidate.supports() == request.aiType())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No AI strategy registered for " + request.aiType()));
        var decision = strategy.decide(
                new AiDecisionContext(
                        request.scopeId(),
                        request.gameType(),
                        request.state() == null ? Map.of() : request.state(),
                        request.input() == null ? Map.of() : request.input()),
                request.difficulty());
        rememberDecision(request.scopeId(), decision.action(), decision.rationale(), request.aiType());
        return new AiDtos.AiDecisionResponse(decision.type(), decision.difficulty(), decision.action(), decision.rationale());
    }

    @Transactional(readOnly = true)
    public List<AiDtos.AiMemoryResponse> memory(UUID scopeId) {
        return aiMemoryRepository.findByScopeId(scopeId)
                .stream()
                .map(entity -> new AiDtos.AiMemoryResponse(
                        entity.getId(),
                        entity.getAiType(),
                        entity.getScopeId(),
                        entity.getMemoryKey(),
                        entity.getMemoryValue()))
                .toList();
    }

    private void rememberDecision(UUID scopeId, String action, String rationale, com.gamehub.ai.domain.AiType aiType) {
        AiMemoryEntity actionMemory = new AiMemoryEntity();
        actionMemory.setId(UUID.randomUUID());
        actionMemory.setAiType(aiType);
        actionMemory.setScopeId(scopeId);
        actionMemory.setMemoryKey("last_action");
        actionMemory.setMemoryValue(action);
        aiMemoryRepository.save(actionMemory);

        AiMemoryEntity rationaleMemory = new AiMemoryEntity();
        rationaleMemory.setId(UUID.randomUUID());
        rationaleMemory.setAiType(aiType);
        rationaleMemory.setScopeId(scopeId);
        rationaleMemory.setMemoryKey("last_rationale");
        rationaleMemory.setMemoryValue(rationale);
        aiMemoryRepository.save(rationaleMemory);
    }
}
