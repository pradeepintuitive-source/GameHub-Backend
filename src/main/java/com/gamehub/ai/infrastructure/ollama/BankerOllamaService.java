package com.gamehub.ai.infrastructure.ollama;

import com.gamehub.monopoly.domain.MonopolyGameState;
import com.gamehub.monopoly.domain.PlayerAsset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankerOllamaService {

    private final OllamaClient ollamaClient;

    /**
     * Get banker recommendation for a transaction
     */
    public OllamaDtos.BankerDecisionResponse getBankerRecommendation(
            MonopolyGameState gameState,
            String playerAction,
            int transactionAmount,
            String transactionType,
            int difficulty) {

        try {
            PlayerAsset currentPlayerAsset = gameState.assets().get(gameState.currentPlayerId());

            String prompt = buildBankerPrompt(
                    gameState.phase().toString(),
                    playerAction,
                    currentPlayerAsset.cash(),
                    transactionAmount,
                    transactionType,
                    difficulty,
                    gameState.currentTurn()
            );

            log.debug("Banker prompt: {}", prompt);

            OllamaDtos.OllamaChatResponse response = ollamaClient.chat(prompt);

            if (response == null || response.getResponseContent() == null) {
                log.warn("No response from Ollama, returning default decision");
                return getDefaultBankerDecision(currentPlayerAsset.cash() >= transactionAmount);
            }

            return parseBankerResponse(response.getResponseContent(), transactionAmount);

        } catch (Exception e) {
            log.error("Error getting banker recommendation from Ollama", e);
            // Return safe default: deny transaction if player doesn't have cash
            return getDefaultBankerDecision(false);
        }
    }

    /**
     * Get banker suggestion for payment settlement
     */
    public int getBankerPaymentSuggestion(
            MonopolyGameState gameState,
            int minimumAmount,
            int maximumAmount) {

        try {
            PlayerAsset currentPlayerAsset = gameState.assets().get(gameState.currentPlayerId());

            String prompt = buildPaymentPrompt(
                    currentPlayerAsset.cash(),
                    minimumAmount,
                    maximumAmount,
                    currentPlayerAsset.ownedTilePositions().size()
            );

            OllamaDtos.OllamaChatResponse response = ollamaClient.chat(prompt);

            if (response == null || response.getResponseContent() == null) {
                return minimumAmount;
            }

            return parsePaymentAmount(response.getResponseContent(), minimumAmount, maximumAmount);

        } catch (Exception e) {
            log.error("Error getting payment suggestion from Ollama", e);
            return minimumAmount;
        }
    }

    /**
     * Get banker advice for property transaction
     */
    public String getBankerPropertyAdvice(
            MonopolyGameState gameState,
            String propertyName,
            int propertyPrice,
            boolean isForSale) {

        try {
            PlayerAsset currentPlayerAsset = gameState.assets().get(gameState.currentPlayerId());

            String prompt = buildPropertyAdvicePrompt(
                    propertyName,
                    propertyPrice,
                    currentPlayerAsset.cash(),
                    isForSale,
                    currentPlayerAsset.ownedTilePositions().size()
            );

            OllamaDtos.OllamaChatResponse response = ollamaClient.chat(prompt);

            if (response != null && response.getResponseContent() != null) {
                return response.getResponseContent();
            }

            return isForSale ? "Consider your financial position carefully"
                    : "Evaluate if this purchase aligns with your strategy";

        } catch (Exception e) {
            log.error("Error getting property advice from Ollama", e);
            return "Consult your strategy";
        }
    }

    // ============ Helper Methods ============

    private String buildBankerPrompt(
            String gamePhase,
            String playerAction,
            int playerCash,
            int transactionAmount,
            String transactionType,
            int difficulty,
            int currentTurn) {

        return """
                You are a Monopoly Banker AI assistant. Evaluate this transaction:
                
                Game State:
                - Phase: %s
                - Current Turn: %d
                - Player Cash: $%d
                - Difficulty Level: %d (1=Easy, 5=Hard)
                
                Transaction Details:
                - Action: %s
                - Amount: $%d
                - Type: %s
                
                Respond ONLY in this exact JSON format (no extra text):
                {"approved": true/false, "reasoning": "brief reason", "suggestedAmount": %d}
                
                Consider: Can the player afford this? Is it strategically sound?
                """.formatted(
                gamePhase, currentTurn, playerCash, difficulty, 
                playerAction, transactionAmount, transactionType, transactionAmount
        );
    }

    private String buildPaymentPrompt(
            int playerCash,
            int minimumAmount,
            int maximumAmount,
            int propertiesOwned) {

        return """
                As a Monopoly Banker, suggest a fair payment amount.
                
                Player Status:
                - Available Cash: $%d
                - Properties Owned: %d
                - Payment Range: $%d - $%d
                
                Respond ONLY with a single integer representing the suggested payment amount.
                Suggest an amount that is fair and within range. Consider the player's cash position.
                """.formatted(
                playerCash, propertiesOwned, minimumAmount, maximumAmount
        );
    }

    private String buildPropertyAdvicePrompt(
            String propertyName,
            int propertyPrice,
            int playerCash,
            boolean isForSale,
            int propertiesOwned) {

        String action = isForSale ? "selling" : "buying";
        return """
                As a Monopoly Banker, provide brief advice on this property transaction.
                
                Property: %s
                Price: $%d
                Player Cash: $%d
                Player Properties: %d
                Action: %s
                
                Provide concise advice (1-2 sentences) considering the player's financial position.
                """.formatted(propertyName, propertyPrice, playerCash, propertiesOwned, action);
    }

    private OllamaDtos.BankerDecisionResponse parseBankerResponse(
            String responseContent,
            int suggestedAmount) {

        try {
            // Extract JSON from response
            String jsonStr = responseContent;
            if (responseContent.contains("{")) {
                jsonStr = responseContent.substring(responseContent.indexOf("{"));
                if (jsonStr.contains("}")) {
                    jsonStr = jsonStr.substring(0, jsonStr.lastIndexOf("}") + 1);
                }
            }

            // Parse simple JSON manually or use Jackson if you prefer
            boolean approved = jsonStr.contains("\"approved\": true") || jsonStr.contains("\"approved\":true");
            String reasoning = extractJsonValue(jsonStr, "reasoning");
            int amount = suggestedAmount;

            try {
                String amountStr = extractJsonValue(jsonStr, "suggestedAmount");
                amount = Integer.parseInt(amountStr);
            } catch (Exception e) {
                log.debug("Could not parse suggested amount, using provided amount");
            }

            return new OllamaDtos.BankerDecisionResponse(
                    approved ? "APPROVED" : "DENIED",
                    reasoning,
                    amount,
                    approved
            );
        } catch (Exception e) {
            log.error("Error parsing banker response: {}", responseContent, e);
            return getDefaultBankerDecision(false);
        }
    }

    private int parsePaymentAmount(String responseContent, int min, int max) {
        try {
            // Extract number from response
            String cleanResponse = responseContent.replaceAll("[^0-9]", "");
            if (!cleanResponse.isEmpty()) {
                int amount = Integer.parseInt(cleanResponse);
                return Math.max(min, Math.min(max, amount));
            }
        } catch (Exception e) {
            log.debug("Could not parse payment amount");
        }
        return min;
    }

    private String extractJsonValue(String json, String key) {
        String searchStr = "\"" + key + "\"";
        int startIdx = json.indexOf(searchStr);
        if (startIdx == -1) return "";

        startIdx = json.indexOf(":", startIdx) + 1;
        int endIdx = json.indexOf(",", startIdx);
        if (endIdx == -1) {
            endIdx = json.indexOf("}", startIdx);
        }

        String value = json.substring(startIdx, endIdx).trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private OllamaDtos.BankerDecisionResponse getDefaultBankerDecision(boolean approved) {
        return new OllamaDtos.BankerDecisionResponse(
                approved ? "APPROVED" : "DENIED",
                approved ? "Transaction approved" : "Insufficient funds or invalid transaction",
                0,
                approved
        );
    }
}
