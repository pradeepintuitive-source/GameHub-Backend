# Banker Ollama Integration with MonopolyEngine

This document explains how the Ollama-powered Banker AI is integrated with the Monopoly game engine.

## Overview

The MonopolyEngine now uses the BankerOllamaService to provide intelligent, contextual decisions for key Monopoly transactions. When players perform financial actions, the Banker AI evaluates the game state and provides recommendations.

## Integration Points

### 1. Property Purchase (`buyProperty`)

**When:** A player lands on an unowned property and attempts to purchase it.

**Banker Role:**
- Evaluates if the purchase is strategically sound
- Considers player's cash position
- Assesses property value relative to game progress

**Impact:**
- Can approve or deny purchase with reasoning
- Denial blocks the purchase action
- Game log includes banker's reasoning

**Example Flow:**
```
Player lands on Park Place ($350)
  ↓
buyProperty() called
  ↓
getBankerApproval() → BankerOllamaService
  ↓
Ollama evaluates: cash=$800, turn=15, properties=3
  ↓
Response: "APPROVED - Strong investment, good cash position"
  ↓
Purchase proceeds, logged with banker comment
```

### 2. Mortgage Operation (`mortgage`)

**When:** A player mortgages a property for immediate cash.

**Banker Role:**
- Provides advice on the mortgage decision
- Considers financial necessity vs. long-term strategy
- Evaluates property importance to portfolio

**Impact:**
- Advice is logged but doesn't block action
- Player sees banker's perspective in logs
- Helps players make informed decisions

**Example:**
```
Player mortgages Vermont Avenue for $55 cash
  ↓
getBankerPropertyAdvice() called
  ↓
Banker response: "Mortgaging early - consider impact on color sets"
  ↓
Logged: "Mortgage placed on Vermont Avenue (Banker: Mortgaging early...)"
```

### 3. Unmortgage Operation (`unmortgage`)

**When:** A player pays to restore a mortgaged property.

**Banker Role:**
- Evaluates timing and financial implications
- Assesses if restoration improves position
- Considers opportunity costs

**Impact:**
- Banker approval required (like purchase)
- Can advise against risky unmortgage moves
- Logged with banker reasoning

### 4. House Building (`buildHouse`)

**When:** A player builds a house on a property.

**Banker Role:**
- Evaluates development strategy
- Considers cash position for development
- Assesses property importance for rent generation

**Impact:**
- Advice provided to guide decisions
- Logged with banker perspective
- Helps players optimize development

### 5. Hotel Building (`buildHotel`)

**When:** A player upgrades to a hotel (4 houses → 1 hotel).

**Banker Role:**
- Evaluates final development investment
- Considers revenue potential
- Assesses competitive position

## Architecture

### Class Structure

```
MonopolyEngine (Game Engine)
  ├─ Injects: BankerOllamaService
  ├─ Methods with Banker Integration:
  │  ├─ buyProperty() → getBankerApproval()
  │  ├─ mortgage() → getBankerPropertyAdvice()
  │  ├─ unmortgage() → getBankerApproval()
  │  ├─ buildHouse() → getBankerPropertyAdvice()
  │  └─ buildHotel() → getBankerPropertyAdvice()
  │
  └─ Helper Methods:
     ├─ getBankerApproval(state, action, amount, type, property)
     ├─ getBankerPropertyAdvice(state, property, amount, isPurchase)
     └─ estimateGameDifficulty(state)
```

### Data Flow

```
MonopolyAction (Player Action)
  ↓
processAction(state, action)
  ↓
Action-specific method (buyProperty, mortgage, etc.)
  ↓
Banker Integration Point
  ├─ Get decision from BankerOllamaService
  ├─ Evaluate response
  └─ Update game state with banker comment
  ↓
MonopolyGameState (Updated)
```

## Banker Decision Logic

### Approval vs. Advice

**Approval-Based Actions** (Blocks if denied):
- `BUY_PROPERTY` - Can deny purchase
- `UNMORTGAGE` - Can advise against
- `PAY_RENT` - Validates financial capability

**Advice-Only Actions** (Never blocks):
- `MORTGAGE` - Provides strategic guidance
- `BUILD_HOUSE` - Recommends or cautions
- `BUILD_HOTEL` - Evaluates investment

### Difficulty Scaling

The banker adjusts behavior based on game difficulty:

```java
private int estimateGameDifficulty(MonopolyGameState state) {
    // Base: 1-5 based on turn progress
    int baseDifficulty = Math.min(5, (state.currentTurn() / 5) + 1);
    
    // Adjusted by property distribution
    long ownerCount = state.owners().values().stream().distinct().count();
    if (ownerCount > 2) {
        baseDifficulty = Math.min(5, baseDifficulty + 1);
    }
    
    return baseDifficulty;
}
```

**Difficulty Levels:**
- **1 (Easy):** Banker is very permissive, helps weaker players
- **3 (Medium):** Balanced decisions, fair gameplay
- **5 (Hard):** Strict banker, punishes poor decisions

## Game Log Integration

All banker decisions are logged for game history:

```
Game Log Examples:
├─ "Player player-123 rolled 8 and landed on Park Place"
├─ "Property purchased: Park Place (Banker: Strong investment...)"
├─ "Mortgage placed on Vermont Avenue (Banker: Consider impact...)"
├─ "Mortgage cleared on Vermont Avenue (Banker: Good recovery...)"
├─ "House built on Park Place (Banker: Excellent strategy...)"
└─ "Hotel built on Boardwalk (Banker: Dominant position secured...)"
```

## Error Handling

### Banker Service Unavailable

If Ollama is not running or disabled:

1. **Graceful Fallback:**
   - `bankerOllamaService` is `@Autowired(required = false)`
   - Engine checks for null before calling
   - Proceeds with default rules if banker unavailable

2. **Logging:**
   - Debug: "Banker Ollama service not available"
   - Warn: "Error getting banker approval/advice"
   - Proceeds without banker input

3. **User Experience:**
   - Game continues normally
   - Actions allowed based on game rules only
   - No banker reasoning in logs

### Example Error Path

```java
try {
    OllamaDtos.BankerDecisionResponse response = 
        bankerOllamaService.getBankerRecommendation(...);
} catch (Exception e) {
    log.warn("Error getting banker approval, proceeding without approval", e);
    return null;  // Fallback to default behavior
}
```

## Integration Checklist

- [x] BankerOllamaService injection in MonopolyEngine
- [x] Helper methods for banker interaction
- [x] Difficulty estimation logic
- [x] Integration in buyProperty()
- [x] Integration in mortgage()
- [x] Integration in unmortgage()
- [x] Integration in buildHouse()
- [x] Integration in buildHotel()
- [x] Game log augmentation
- [x] Error handling and fallback
- [x] Debug logging

## Configuration for MonopolyEngine

The MonopolyEngine respects Ollama configuration:

```yaml
gamehub:
  ollama:
    enabled: true  # Banker will be active
    base-url: http://localhost:11434
    model: mistral
    timeout-seconds: 30
```

If `enabled: false`, banker operations are skipped.

## Testing the Integration

### Unit Test Example

```java
@SpringBootTest
class MonopolyEngineIntegrationTest {
    
    @MockBean
    private BankerOllamaService bankerService;
    
    @Autowired
    private MonopolyEngine monopolyEngine;
    
    @Test
    void testBuyPropertyWithBankerApproval() {
        // Create game state
        MonopolyGameState state = monopolyEngine.startGame(
            sessionId, List.of(player1, player2)
        );
        
        // Mock banker approval
        OllamaDtos.BankerDecisionResponse approval = 
            new OllamaDtos.BankerDecisionResponse(
                "APPROVED",
                "Good investment",
                350,
                true
            );
        when(bankerService.getBankerRecommendation(any(), any(), any(), any(), anyInt()))
            .thenReturn(approval);
        
        // Perform purchase
        MonopolyAction buyAction = new MonopolyAction(...);
        MonopolyGameState result = monopolyEngine.processAction(state, buyAction);
        
        // Verify banker comment in log
        assertTrue(result.log().stream()
            .anyMatch(log -> log.contains("Banker: Good investment")));
    }
}
```

### Integration Test Example

```bash
# 1. Start Ollama
ollama serve

# 2. Start GameHub
mvn spring-boot:run

# 3. Play a game through API
POST /api/games/monopoly/{sessionId}/action
{
    "type": "BUY_PROPERTY",
    "tilePosition": 39,
    "actorPlayerId": "uuid-123"
}

# 4. Check game logs for banker decisions
GET /api/games/monopoly/{sessionId}
# Look for logs with "(Banker: ...)" comments
```

## Performance Considerations

### Latency Impact

Each banker operation adds ~1-2 seconds (Ollama response time):
- Doesn't block future actions
- Async capable if needed
- Can be cached for repeated scenarios

### Optimization Options

1. **Cache Common Decisions**
   ```java
   @Cacheable("bankerDecisions")
   public OllamaDtos.BankerDecisionResponse getBankerRecommendation(...)
   ```

2. **Async Evaluation**
   ```java
   @Async
   public CompletableFuture<OllamaDtos.BankerDecisionResponse> 
       getBankerRecommendationAsync(...)
   ```

3. **Batch Processing**
   - Evaluate multiple decisions together
   - Reduce round-trips to Ollama

## Future Enhancements

1. **Decision Caching** - Cache banker decisions by game state hash
2. **Async Operations** - Non-blocking banker evaluation
3. **Custom Models** - Train models on real Monopoly games
4. **Analytics** - Track banker decision accuracy and impact
5. **Difficulty Variations** - Different banker personalities per difficulty
6. **Streaming Responses** - Real-time decision reasoning
7. **Multi-Model** - Use different models for different decisions
8. **Distributed Ollama** - Multiple Ollama instances for scalability

## Troubleshooting

### Banker Not Responding

```bash
# Check MonopolyEngine logs
grep -i "banker" application.log

# Verify Ollama is running
curl http://localhost:11434/api/tags

# Enable debug logging
export GAMEHUB_LOG_LEVEL=DEBUG
```

### Unexpected Denials

1. Check Ollama model capabilities
2. Review banker prompts in BankerOllamaService
3. Verify game state is being passed correctly
4. Check difficulty estimation logic

### Performance Issues

1. Increase Ollama timeout in config
2. Try faster model (neural-chat)
3. Consider caching mechanism
4. Check server resources (CPU, Memory)

---

**Integration Status:** ✅ Complete  
**Last Updated:** 2026-06-25  
**Version:** 1.0
