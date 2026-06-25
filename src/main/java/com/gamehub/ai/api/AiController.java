package com.gamehub.ai.api;

import com.gamehub.ai.application.AiOrchestratorService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiOrchestratorService aiOrchestratorService;

    @PostMapping("/decide")
    public AiDtos.AiDecisionResponse decide(@Valid @RequestBody AiDtos.AiDecisionRequest request) {
        return aiOrchestratorService.decide(request);
    }

    @GetMapping("/memory/{scopeId}")
    public List<AiDtos.AiMemoryResponse> memory(@PathVariable UUID scopeId) {
        return aiOrchestratorService.memory(scopeId);
    }
}
