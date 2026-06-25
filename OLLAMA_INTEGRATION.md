# Ollama Integration for GameHub Banker Operations

This document explains the Ollama integration for handling intelligent Banker operations in the GameHub backend.

## Overview

The Banker AI now uses **Ollama**, an open-source large language model framework, to provide intelligent recommendations for:
- Transaction approvals/denials
- Payment settlement suggestions
- Property transaction advice
- Game strategy recommendations

## Architecture

### Components

1. **OllamaConfig** - Configuration properties for Ollama connection
2. **OllamaClient** - REST client for communicating with Ollama API
3. **BankerOllamaService** - Business logic for banker operations using Ollama
4. **BankerStrategy** - AI Strategy that uses BankerOllamaService
5. **BankerController** - REST endpoints for banker operations
6. **OllamaDtos** - Data transfer objects for requests/responses

### Flow

```
BankerStrategy (AI Strategy)
    ↓
BankerOllamaService (Business Logic)
    ↓
OllamaClient (HTTP REST Client)
    ↓
Ollama Server (LLM)
```

## Setup Instructions

### 1. Install Ollama

Download and install Ollama from: https://ollama.ai

### 2. Download a Model

```bash
# Download mistral model (recommended, ~7GB)
ollama pull mistral

# Alternative models:
ollama pull neural-chat  # Faster, ~4GB
ollama pull llama2        # More capable, ~7GB
ollama pull openchat      # Lightweight, ~4GB
```

### 3. Start Ollama Server

```bash
ollama serve
# Server will run at http://localhost:11434
```

### 4. Configure GameHub

Update `application.yml`:

```yaml
gamehub:
  ollama:
    enabled: true
    base-url: http://localhost:11434
    model: mistral
    timeout-seconds: 30
```

Or use environment variables:

```bash
export GAMEHUB_OLLAMA_ENABLED=true
export GAMEHUB_OLLAMA_BASE_URL=http://localhost:11434
export GAMEHUB_OLLAMA_MODEL=mistral
export GAMEHUB_OLLAMA_TIMEOUT=30
```

## API Endpoints

### Get Banker Decision

**POST** `/api/banker/decision`

Request:
```json
{
  "gamePhase": "WAITING_FOR_DECISION",
  "playerAction": "BUY_PROPERTY",
  "playerCash": 1500,
  "transactionAmount": 200,
  "transactionType": "PURCHASE",
  "difficulty": 3
}
```

Response:
```json
{
  "recommendation": "APPROVED",
  "reasoning": "Player has sufficient funds and strategic position is strong",
  "suggestedAmount": 200,
  "approved": true
}
```

### Get Payment Suggestion

**POST** `/api/banker/payment-suggestion`

Request:
```json
{
  "minimumAmount": 100,
  "maximumAmount": 500,
  "playerCash": 2000,
  "propertiesOwned": 3
}
```

Response: `300` (suggested payment amount)

### Get Property Advice

**POST** `/api/banker/property-advice`

Request:
```json
{
  "propertyName": "Park Place",
  "propertyPrice": 350,
  "playerCash": 400,
  "isForSale": false
}
```

Response: `"This property is strategically valuable. Consider securing it if possible."`

## Configuration

### application.yml

```yaml
gamehub:
  ollama:
    enabled: ${GAMEHUB_OLLAMA_ENABLED:true}
    base-url: ${GAMEHUB_OLLAMA_BASE_URL:http://localhost:11434}
    model: ${GAMEHUB_OLLAMA_MODEL:mistral}
    timeout-seconds: ${GAMEHUB_OLLAMA_TIMEOUT:30}
```

### Properties

| Property | Default | Description |
|----------|---------|-------------|
| `gamehub.ollama.enabled` | `true` | Enable/disable Ollama integration |
| `gamehub.ollama.base-url` | `http://localhost:11434` | Ollama server URL |
| `gamehub.ollama.model` | `mistral` | Model name to use |
| `gamehub.ollama.timeout-seconds` | `30` | HTTP request timeout |

## Usage in Monopoly Game

When a player performs a banker action:

1. **BankerStrategy.decide()** is invoked with the game context
2. **BankerOllamaService** analyzes the game state
3. **OllamaClient** sends a prompt to the Ollama server
4. The LLM provides intelligent recommendations
5. **BankerDecision** is returned to the game engine

### Example Integration

```java
// In MonopolyEngine or MonopolyGameService
AiDecisionContext context = new AiDecisionContext(
    UUID.randomUUID(),
    gameState,
    Map.of(
        "gameState", gameState,
        "actionType", "BUY_PROPERTY",
        "amount", 200,
        "transactionType", "PURCHASE"
    )
);

AiDecision decision = bankerStrategy.decide(context, AiDifficulty.MEDIUM);
```

## Difficulty Levels

The Banker adjusts behavior based on difficulty:

| Difficulty | Level | Behavior |
|-----------|-------|----------|
| EASY | 1 | Conservative, helps players |
| MEDIUM | 3 | Balanced, fair decisions |
| HARD | 5 | Strict, challenges players |

## Error Handling

If Ollama is unavailable:
- Fallback to default banker decisions
- Logs warnings but doesn't crash
- Gracefully degrades to hardcoded logic
- Health check available at `/api/banker/health`

## Performance Considerations

### Timeout Settings

- Default timeout: 30 seconds
- Adjust based on model and hardware
- Faster models: 10-15 seconds
- Larger models: 30+ seconds

### Model Selection

**Recommended:**
- `mistral` - Good balance of speed and quality
- `neural-chat` - Fast, suitable for real-time

**For better responses:**
- `llama2` - More capable, slower
- `openchat` - Lightweight, decent quality

### Optimization Tips

1. **Run Ollama on GPU**
   ```bash
   # CUDA support (NVIDIA)
   ollama serve --gpu true
   ```

2. **Increase context window** in prompts for better decisions

3. **Cache responses** for common banker decisions

4. **Batch requests** during non-critical game phases

## Testing

### Unit Tests

```java
@SpringBootTest
class BankerOllamaServiceTest {
    
    @MockBean
    private OllamaClient ollamaClient;
    
    @Autowired
    private BankerOllamaService bankerService;
    
    @Test
    void testBankerDecision() {
        // Mock Ollama response
        OllamaDtos.OllamaChatResponse mockResponse = 
            new OllamaDtos.OllamaChatResponse(...);
        
        when(ollamaClient.chat(any())).thenReturn(mockResponse);
        
        // Test banker decision
        OllamaDtos.BankerDecisionResponse response = 
            bankerService.getBankerRecommendation(...);
        
        assertTrue(response.approved());
    }
}
```

### Integration Testing

1. Start Ollama: `ollama serve`
2. Run tests: `mvn test`
3. Check logs for Ollama communication

### Manual Testing

```bash
# Test Ollama connection
curl http://localhost:11434/api/tags

# Test banker endpoint
curl -X POST http://localhost:8080/api/banker/decision \
  -H "Content-Type: application/json" \
  -d '{
    "gamePhase": "WAITING_FOR_DECISION",
    "playerAction": "BUY_PROPERTY",
    "playerCash": 1500,
    "transactionAmount": 200,
    "transactionType": "PURCHASE",
    "difficulty": 3
  }'
```

## Troubleshooting

### Ollama Not Responding

```bash
# Check if Ollama is running
curl http://localhost:11434/api/tags

# Restart Ollama
ollama serve

# Check logs
# Look for: "Ollama health check failed"
```

### Model Not Found

```bash
# List available models
ollama list

# Pull missing model
ollama pull mistral
```

### Slow Responses

1. Check CPU/GPU usage
2. Try faster model (neural-chat)
3. Increase timeout in config
4. Run on machine with more resources

### Memory Issues

1. Use lighter model: `neural-chat` or `openchat`
2. Reduce context window in prompts
3. Allocate more RAM to container/VM
4. Disable quantization if using full precision

## Future Enhancements

1. **Response Caching** - Cache common banker decisions
2. **Fine-tuning** - Train models on game strategies
3. **Multi-model Support** - Use different models for different scenarios
4. **Streaming Responses** - Real-time decision explanations
5. **Analytics** - Track banker decision outcomes
6. **Custom Prompts** - Game-specific prompt engineering
7. **Fallback Models** - Automatic model switching on failures
8. **Distributed Ollama** - Multiple instances for scalability

## References

- [Ollama Documentation](https://github.com/ollama/ollama)
- [OpenAI API Format](https://platform.openai.com/docs/api-reference/chat)
- [Model Library](https://ollama.ai/library)
- [Prompt Engineering Guide](https://platform.openai.com/docs/guides/prompt-engineering)

## Support

For issues or questions:
1. Check application logs: `GAMEHUB_LOG_LEVEL=DEBUG`
2. Verify Ollama is running and accessible
3. Test with curl commands
4. Enable debug logging for OllamaClient
5. Check Ollama model compatibility

---

**Version:** 1.0  
**Last Updated:** 2026-06-25
