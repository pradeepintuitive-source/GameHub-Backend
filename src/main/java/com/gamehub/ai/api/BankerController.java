package com.gamehub.ai.api;

import com.gamehub.ai.infrastructure.ollama.BankerOllamaService;
import com.gamehub.ai.infrastructure.ollama.OllamaDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/banker")
@RequiredArgsConstructor
@Slf4j
public class BankerController {

    private final BankerOllamaService bankerOllamaService;

    /**
     * Get banker decision for a transaction using Ollama
     */
    @PostMapping("/decision")
    public ResponseEntity<OllamaDtos.BankerDecisionResponse> getBankerDecision(
            @RequestBody OllamaDtos.BankerDecisionRequest request) {

        log.info("Banker decision requested: {}", request);

        // Create a mock game state for demonstration
        // In production, this would come from the actual game session
        OllamaDtos.BankerDecisionResponse response =
                new OllamaDtos.BankerDecisionResponse(
                        "APPROVED",
                        "Transaction meets all criteria",
                        request.transactionAmount(),
                        request.transactionAmount() > 0
                );

        return ResponseEntity.ok(response);
    }

    /**
     * Get payment suggestion from banker
     */
    @PostMapping("/payment-suggestion")
    public ResponseEntity<Integer> getPaymentSuggestion(
            @RequestBody PaymentSuggestionRequest request) {

        log.info("Payment suggestion requested: min={}, max={}", request.minimumAmount(), request.maximumAmount());

        // For demonstration, suggest the midpoint
        int suggestion = (request.minimumAmount() + request.maximumAmount()) / 2;

        return ResponseEntity.ok(suggestion);
    }

    /**
     * Get banker property advice
     */
    @PostMapping("/property-advice")
    public ResponseEntity<String> getPropertyAdvice(
            @RequestBody PropertyAdviceRequest request) {

        log.info("Property advice requested for: {}", request.propertyName());

        String advice = "Consider your current cash position and portfolio before making this decision.";

        return ResponseEntity.ok(advice);
    }

    public record PaymentSuggestionRequest(
            int minimumAmount,
            int maximumAmount,
            int playerCash,
            int propertiesOwned) {
    }

    public record PropertyAdviceRequest(
            String propertyName,
            int propertyPrice,
            int playerCash,
            boolean isForSale) {
    }
}
