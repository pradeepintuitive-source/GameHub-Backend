# Ollama Integration - Quick Setup Guide

This guide will help you quickly set up Ollama for the GameHub Banker operations.

## 📋 Prerequisites

- Java 21+
- Maven 3.8+
- Docker or direct Ollama installation

## 🚀 Quick Start (5 minutes)

### Step 1: Install Ollama

**Option A: Direct Installation**
```bash
# macOS/Linux
curl https://ollama.ai/install.sh | sh

# Windows: Download installer from https://ollama.ai
```

**Option B: Docker**
```bash
docker run -d --name ollama \
  -p 11434:11434 \
  ollama/ollama
```

### Step 2: Download a Model

```bash
# The recommended model for GameHub
ollama pull mistral

# Alternative (faster, smaller):
ollama pull neural-chat

# Alternative (more capable):
ollama pull llama2
```

**Check download progress:**
```bash
ollama list
```

### Step 3: Start Ollama Server

```bash
# Ollama will be available at http://localhost:11434
ollama serve
```

### Step 4: Verify Ollama is Running

```bash
# In a new terminal
curl http://localhost:11434/api/tags

# You should see your downloaded models
```

### Step 5: Configure GameHub

Add to `src/main/resources/application.yml`:

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

### Step 6: Build and Run GameHub

```bash
# Build
mvn clean package

# Run
mvn spring-boot:run
```

## ✅ Verification

### Check Ollama Connection

```bash
# Verify Ollama is running
curl -X GET http://localhost:11434/api/tags

# Expected response: JSON with available models
```

### Test Banker Endpoints

```bash
# Test banker decision endpoint
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

# Expected: Banker decision response
```

### Check Application Logs

```bash
# Enable debug logging
export GAMEHUB_LOG_LEVEL=DEBUG

# Look for logs like:
# "Sending request to Ollama: http://localhost:11434/api/chat"
# "Received response from Ollama"
```

## 🛠️ Troubleshooting

### Ollama Not Responding

```bash
# Check if process is running
ps aux | grep ollama

# Restart
ollama serve

# Check logs
# macOS/Linux: ~/.ollama/logs
```

### Model Not Found

```bash
# List available models
ollama list

# Download model
ollama pull mistral

# Check space (models are large, ~7GB for mistral)
df -h
```

### Slow Response Times

1. Check model: `mistral` is balanced, `neural-chat` is faster
2. Check hardware: Models run slower on CPU
3. Increase timeout: `gamehub.ollama.timeout-seconds: 60`
4. Monitor: `ollama ps` to see current load

### Docker Issues

```bash
# Allocate more resources
docker update --cpus 2 --memory 4g ollama

# Check container logs
docker logs ollama

# Restart container
docker restart ollama
```

## 📚 Model Recommendations

| Model | Size | Speed | Quality | Recommended For |
|-------|------|-------|---------|-----------------|
| neural-chat | 4GB | Fast | Good | Real-time decisions |
| mistral | 7GB | Medium | Very Good | **Best overall** |
| llama2 | 7GB | Medium | Excellent | Strategic advice |
| openchat | 4GB | Fast | Good | Lightweight setup |

## 🔧 Advanced Configuration

### Using Different Model

```yaml
gamehub:
  ollama:
    model: llama2  # Change model name
```

### Increase Timeout (for slower hardware)

```yaml
gamehub:
  ollama:
    timeout-seconds: 60  # Increase from 30
```

### Disable Ollama (fallback mode)

```yaml
gamehub:
  ollama:
    enabled: false  # Uses hardcoded banker logic
```

### Remote Ollama Server

```yaml
gamehub:
  ollama:
    base-url: http://ollama-server.example.com:11434
```

## 📖 Files Created/Modified

### New Files
- `src/main/java/com/gamehub/ai/infrastructure/ollama/OllamaDtos.java`
- `src/main/java/com/gamehub/ai/infrastructure/ollama/OllamaConfig.java`
- `src/main/java/com/gamehub/ai/infrastructure/ollama/OllamaClient.java`
- `src/main/java/com/gamehub/ai/infrastructure/ollama/OllamaWebConfig.java`
- `src/main/java/com/gamehub/ai/infrastructure/ollama/BankerOllamaService.java`
- `src/main/java/com/gamehub/ai/api/BankerController.java`
- `OLLAMA_INTEGRATION.md` (Comprehensive documentation)

### Modified Files
- `pom.xml` (Added webflux and jackson-databind dependencies)
- `src/main/resources/application.yml` (Added Ollama configuration)
- `src/main/java/com/gamehub/ai/infrastructure/strategy/BankerStrategy.java` (Now uses Ollama)

## 🎯 Next Steps

1. **Integrate with Monopoly Game**: Update MonopolyEngine to use BankerStrategy
2. **Add Caching**: Cache common banker decisions for performance
3. **Fine-tune**: Create game-specific prompts for better decisions
4. **Monitor**: Add metrics to track banker decision quality
5. **Scale**: Set up multiple Ollama instances for production

## 📞 Support

For detailed information, see: [OLLAMA_INTEGRATION.md](./OLLAMA_INTEGRATION.md)

---

**Quick Test:**
```bash
# Terminal 1: Run Ollama
ollama serve

# Terminal 2: Run GameHub
mvn spring-boot:run

# Terminal 3: Test endpoint
curl -X POST http://localhost:8080/api/banker/decision \
  -H "Content-Type: application/json" \
  -d '{"gamePhase":"WAITING_FOR_DECISION","playerAction":"BUY_PROPERTY","playerCash":1500,"transactionAmount":200,"transactionType":"PURCHASE","difficulty":3}'
```

Enjoy your Ollama-powered GameHub Banker! 🎮🤖
