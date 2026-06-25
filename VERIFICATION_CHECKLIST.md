# Ollama Integration - Verification Checklist

Use this checklist to verify that the Ollama banker integration is working correctly.

## Pre-Integration Checks

### Code Structure
- [x] BankerOllamaService exists at: `src/main/java/com/gamehub/ai/infrastructure/ollama/BankerOllamaService.java`
- [x] OllamaClient exists at: `src/main/java/com/gamehub/ai/infrastructure/ollama/OllamaClient.java`
- [x] MonopolyEngine updated at: `src/main/java/com/gamehub/monopoly/infrastructure/MonopolyEngine.java`
- [x] Helper methods implemented:
  - [x] `getBankerApproval(state, actionType, amount, transactionType, propertyName)`
  - [x] `getBankerPropertyAdvice(state, propertyName, amount, isPurchase)`
  - [x] `estimateGameDifficulty(state)`

### Dependencies
- [x] BankerOllamaService injected in MonopolyEngine:
  ```java
  @Autowired(required = false)
  private BankerOllamaService bankerOllamaService;
  ```
- [x] Required imports added:
  ```java
  import com.gamehub.ai.infrastructure.ollama.BankerOllamaService;
  import com.gamehub.ai.infrastructure.ollama.OllamaDtos;
  ```

### Configuration
- [x] application.yml contains Ollama config:
  ```yaml
  gamehub:
    ollama:
      enabled: true
      base-url: http://localhost:11434
      model: mistral
      timeout-seconds: 30
  ```
- [x] pom.xml includes dependencies:
  - [x] spring-boot-starter-webflux
  - [x] jackson-databind

## Build Verification

### Compile Check
```bash
# From project root
cd /path/to/GameHub-Backend-main
mvn clean compile

# Should complete successfully with no compilation errors
# Expected output: BUILD SUCCESS
```

**If you see errors:**
- Check imports are correct in MonopolyEngine.java
- Verify BankerOllamaService class exists
- Ensure pom.xml has required dependencies

### Test Check
```bash
# Run tests
mvn test

# Should complete successfully (or skip if no tests)
```

## Runtime Verification

### Step 1: Start Ollama Server
```bash
# Terminal 1 - Start Ollama
ollama serve

# Should output:
# Listening on 127.0.0.1:11434

# Terminal 2 - Pull model (if needed)
ollama pull mistral

# Should complete with: success
```

**Verification:**
```bash
# Check Ollama is running
curl http://localhost:11434/api/tags

# Should return JSON with available models:
# {"models":[{"name":"mistral:latest",...}]}
```

### Step 2: Start GameHub
```bash
# Terminal 3 - Start GameHub
cd /path/to/GameHub-Backend-main
mvn spring-boot:run

# Should output:
# Started GameHubApplication in X.XXX seconds
# (Ollama connection successful - if enabled)
```

**Verification:**
```bash
# Check application started
curl http://localhost:8080/actuator/health

# Should return: {"status":"UP"}
```

### Step 3: Verify Banker Integration in Logs
```bash
# Check for Ollama initialization logs
grep -i "ollama\|banker" application.log | head -20

# Should see logs like:
# INFO - Ollama client initialized at http://localhost:11434
# DEBUG - Banker decision: BankerDecisionResponse(...)
```

### Step 4: Test Banker via API

```bash
# 1. Create a Monopoly game
curl -X POST http://localhost:8080/api/games/monopoly/start \
  -H "Content-Type: application/json" \
  -d '{"players": ["player1", "player2"]}'

# Response should include sessionId
# "sessionId": "uuid-xxxx-xxxx-xxxx-xxxx"

# 2. Get game state (to see initial log)
curl http://localhost:8080/api/games/monopoly/{sessionId}

# Should include game log with banker initialization

# 3. Player buys property (triggers banker approval)
curl -X POST http://localhost:8080/api/games/monopoly/{sessionId}/action \
  -H "Content-Type: application/json" \
  -d '{
    "type": "BUY_PROPERTY",
    "tilePosition": 6,
    "playerId": "player1"
  }'

# 4. Check game state for banker comment
curl http://localhost:8080/api/games/monopoly/{sessionId}

# Should see log entry like:
# "Property purchased: Baltic Avenue (Banker: Good investment...)"
```

## Integration Points Verification

### BuyProperty Integration
```bash
# Trigger property purchase
# Check for:
# - "Property purchased: ... (Banker: ...)"
# - Banker comment should reflect decision
# - Action should be blocked if banker denies
```

### Mortgage Integration
```bash
# Trigger mortgage
# Check for:
# - "Mortgage placed on ... (Banker: ...)"
# - Banker advice should be present in log
# - Action should proceed (advice only, not blocking)
```

### Unmortgage Integration
```bash
# Trigger unmortgage
# Check for:
# - "Mortgage cleared on ... (Banker: ...)"
# - Banker advice logged
```

### BuildHouse Integration
```bash
# Trigger house building
# Check for:
# - "House built on ... (Banker: ...)"
# - Banker advice about development strategy
```

### BuildHotel Integration
```bash
# Trigger hotel building
# Check for:
# - "Hotel built on ... (Banker: ...)"
# - Banker advice about final investment
```

## Fallback Mode Verification

### Test with Ollama Disabled

```bash
# Start GameHub with Ollama disabled
mvn spring-boot:run -Dspring-boot.run.arguments="--gamehub.ollama.enabled=false"

# Or modify application.yml:
# gamehub.ollama.enabled: false

# Play a game
# Should work normally without banker comments in logs

# Check logs for:
# "Banker Ollama service not available"
```

### Test with Ollama Unavailable

```bash
# Stop Ollama server (Ctrl+C in Ollama terminal)

# Keep GameHub running

# Attempt property purchase
# Should:
# - Process normally (no crash)
# - Log warning: "Error getting banker approval"
# - Proceed with default game rules

# Check logs for:
# "Error getting banker approval, proceeding without approval"
```

## Performance Verification

### Check Latency

```bash
# Monitor logs for timing
grep -i "banker decision" application.log

# Note time before and after for:
# [15:30:45] Banker decision initiated
# [15:30:47] Property purchased

# Typical latency: 1-2 seconds per decision
```

### Check Resource Usage

```bash
# Monitor Java process
jps -l | grep GameHub

# Check CPU/Memory
# Should use < 500MB additional for Ollama operations

# Monitor Ollama
# ollama serve output should show model load/response times
```

## Troubleshooting Checklist

### Build Failures

- [ ] Run `mvn clean` to clear cache
- [ ] Check Java version: `java -version` (should be 21+)
- [ ] Verify imports in MonopolyEngine.java
- [ ] Check pom.xml for typos
- [ ] Run `mvn compile` to see detailed errors

### Runtime Failures

- [ ] Ollama running? `curl http://localhost:11434/api/tags`
- [ ] Model loaded? `ollama list`
- [ ] Port 11434 available? `netstat -an | grep 11434`
- [ ] GameHub logs show errors? `tail -f application.log | grep ERROR`

### No Banker Comments in Logs

- [ ] Ollama enabled in application.yml
- [ ] BankerOllamaService injected successfully
- [ ] Helper methods called (check logs)
- [ ] Ollama model responding (slow network?)

### Banker Decisions Incorrect

- [ ] Check Ollama model: `ollama list`
- [ ] Try different model: `mistral` vs `neural-chat`
- [ ] Review prompt in BankerOllamaService
- [ ] Check game state being passed is complete

## Documentation Location

- **Quick Start**: `OLLAMA_SETUP.md`
- **Architecture & API**: `OLLAMA_INTEGRATION.md`
- **Engine Integration**: `BANKER_ENGINE_INTEGRATION.md`
- **Completion Summary**: `INTEGRATION_COMPLETE.md`
- **This Checklist**: `VERIFICATION_CHECKLIST.md`

## Success Criteria

You've successfully integrated Ollama when:

1. ✅ GameHub builds and starts without errors
2. ✅ Ollama server responds to requests
3. ✅ Games can be created via API
4. ✅ Property purchase includes banker comment in logs
5. ✅ Banker comment reflects actual decision (approve/deny)
6. ✅ Game plays normally when Ollama is unavailable
7. ✅ No crashes or exceptions related to banker operations
8. ✅ Response time is acceptable (< 5 seconds per decision)

## Next Steps After Verification

1. **Play Full Game**
   - Create a game with 2-4 players
   - Play several rounds
   - Observe banker behavior

2. **Test Edge Cases**
   - Very low cash situation
   - Multiple properties owned
   - Late game scenarios

3. **Monitor Performance**
   - Check response times
   - Monitor memory usage
   - Consider optimization if needed

4. **Fine-tune**
   - Adjust prompts if banker decisions seem off
   - Try different models for performance
   - Tweak timeout settings

## Support

If issues persist:

1. Check application.log for errors
2. Review BANKER_ENGINE_INTEGRATION.md troubleshooting section
3. Verify Ollama is running and responsive
4. Ensure all files were modified correctly
5. Try with Ollama disabled to isolate the issue

---

**Verification Status**: Ready to Use  
**Last Updated**: 2026-06-25  
**Expected Time to Verify**: 15-30 minutes
