# CORS and WebSocket Configuration FIX - Complete Summary

## 🔴 Problem Identified

**Two conflicting CORS configurations were running simultaneously:**

1. **CorsConfig.java** - Only applied to `/api/**` with specific origins + credentials enabled
2. **DevCorsConfig.java** - Applied to `/**` with wildcard + credentials disabled

When both were active, SockJS probing requests (`/ws/info?t=...`) were being rejected because:
- Frontend sends credentials (cookies/JWT in headers) 
- DevCorsConfig returns `Access-Control-Allow-Credentials: false`
- Browser security model rejects response as invalid

Result: CORS errors on `/ws/info?t=` endpoints preventing SockJS HTTP fallback.

---

## ✅ Fixes Applied

### 1. **CorsConfig.java** - DISABLED
**File:** `src/main/java/com/gamehub/config/infrastructure/CorsConfig.java`
- Removed `@Configuration` annotation
- Converted to documentation-only class
- All functionality moved to DevCorsConfig

**Why:** Having two CORS configuration classes causes Spring to apply both, creating conflicts.

---

### 2. **DevCorsConfig.java** - COMPREHENSIVE REWRITE
**File:** `src/main/java/com/gamehub/config/infrastructure/DevCorsConfig.java`

**Changes:**
- ✅ Now reads `gamehub.websocket.allowed-origins` from environment
- ✅ Reads `gamehub.enable-cors-credentials` configuration flag
- ✅ Applies to ALL endpoints (`/**`) not just `/api/**`
- ✅ Uses specific origin patterns (not wildcard) - parsed from config
- ✅ Conditionally enables credentials based on configuration
- ✅ Added debug logging for troubleshooting

**Key Logic:**
```java
.allowedOriginPatterns(origins)  // Specific origins parsed from config
.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
.allowedHeaders("*")
.exposedHeaders("Authorization", "Content-Type", "X-Requested-With")
.allowCredentials(enableCredentials)  // Controlled via config flag
```

---

### 3. **WebSocketConfig.java** - ALIGNED WITH CORS CONFIG
**File:** `src/main/java/com/gamehub/websocket/infrastructure/WebSocketConfig.java`

**Changes:**
- ✅ Now reads `gamehub.websocket.allowed-origins` from same config as DevCorsConfig
- ✅ Uses same origin patterns for consistency
- ✅ Added detailed inline documentation explaining SockJS behavior
- ✅ Added debug logging showing configured origins
- ✅ Initialized ThreadPoolTaskScheduler properly

**Key Alignment:**
```java
.setAllowedOriginPatterns(origins)  // Same origins as DevCorsConfig
```

---

### 4. **application.yml** - CONFIGURATION CENTRALIZATION
**File:** `src/main/resources/application.yml`

**Changes:**
- ✅ Added `gamehub.enable-cors-credentials` property (default: false)
- ✅ Clarified `gamehub.websocket.allowed-origins` with defaults
- ✅ Added detailed comments explaining each property

**Configuration:**
```yaml
gamehub:
  websocket:
    # Comma-separated list of allowed origins for CORS
    allowed-origins: ${GAMEHUB_ALLOWED_ORIGINS:https://preview--boardgame-verse.lovable.app,http://localhost:5173,http://192.168.31.103:4173}
  # Set to true to enable credentials with CORS (only when using specific origins, NOT wildcards)
  enable-cors-credentials: ${GAMEHUB_ENABLE_CORS_CREDENTIALS:false}
```

---

### 5. **JwtHandshakeInterceptor.java** - ENHANCED LOGGING
**File:** `src/main/java/com/gamehub/websocket/infrastructure/JwtHandshakeInterceptor.java`

**Changes:**
- ✅ Enhanced logging with emojis (✓, ✗, ⚠) for quick visual parsing
- ✅ Logs Origin, Host, and request URI for debugging
- ✅ Better error messages for token validation failures
- ✅ Clear indication of guest vs authenticated connections

**Sample Log Output:**
```
WebSocket handshake attempt - URI: /ws, Origin: https://preview--boardgame-verse.lovable.app, Auth header present: true
✓ WebSocket authenticated with user: john_doe
```

---

## 📋 Environment Variables Needed

Set these in your Docker compose or .env file:

```bash
# Required
GAMEHUB_ALLOWED_ORIGINS=https://preview--boardgame-verse.lovable.app,http://localhost:5173,http://192.168.31.103:4173

# Optional
GAMEHUB_ENABLE_CORS_CREDENTIALS=false
```

---

## 🚀 Rebuild Instructions

### Step 1: Navigate to Infrastructure Directory
```bash
cd \\wsl.localhost\Ubuntu\home\user\projects\gamehub\gamehub-infrastructure
```

### Step 2: Rebuild Backend Container
```bash
wsl docker compose up --build -d backend
```

### Step 3: Verify Container is Running
```bash
wsl docker compose ps
```

Expected output:
```
NAME                    STATUS
gamehub-backend        Up (healthy)
gamehub-postgres       Up (healthy)
gamehub-redis          Up
```

### Step 4: View Backend Logs
```bash
wsl docker compose logs backend --tail=50 -f
```

Look for these log lines to confirm configuration loaded:
```
DevCorsConfig: Configured origins = [https://preview--boardgame-verse.lovable.app, http://localhost:5173, ...]
DevCorsConfig: enableCredentials = false
WebSocketConfig: Configured origins for /ws = [https://preview--boardgame-verse.lovable.app, ...]
```

---

## 🧪 Testing Checklist

### Test 1: REST API CORS (Login Endpoint)
```bash
# From browser console at https://preview--boardgame-verse.lovable.app
fetch('https://giuseppina-proctological-malachi.ngrok-free.dev/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'testuser', password: 'Test@123' })
})
```
Expected: No CORS error in console

### Test 2: WebSocket Connection
1. Open browser DevTools → Network tab
2. Filter by "ws" (WebSocket)
3. Refresh the frontend page
4. Look for:
   - ✓ Initial WebSocket handshake: `/ws` or `/ws?...` (101 Switching Protocols)
   - ✓ If WebSocket fails, should see `/ws/info?t=...` (SockJS probe - 200 OK)
   - ✓ Console status should show "CONNECTED" (green)

### Test 3: Cross-Browser Profile Testing
1. **Profile A (Working):**
   - Clear browser cache: Ctrl+Shift+Del
   - Hard refresh: Ctrl+F5
   - Verify login and WebSocket connection

2. **Profile B (Previously Failing):**
   - Open in incognito/private window
   - Try login and verify WebSocket connection
   - Should now work identically to Profile A

### Test 4: Backend Logs Verification
```bash
wsl docker compose logs backend --tail=50
```

Look for (per connection):
```
✓ WebSocket handshake attempt - Origin: https://preview--boardgame-verse.lovable.app
✓ WebSocket authenticated with user: john_doe
```

---

## 🔍 Troubleshooting

### If CORS errors still appear on `/ws/info?t=...`

**Check 1: Environment variable is set**
```bash
wsl docker compose exec backend env | grep GAMEHUB_ALLOWED_ORIGINS
```
Should output:
```
GAMEHUB_ALLOWED_ORIGINS=https://preview--boardgame-verse.lovable.app,http://localhost:5173,...
```

**Check 2: DevCorsConfig is being used**
Look in backend logs:
```bash
wsl docker compose logs backend | grep "DevCorsConfig:"
```
Should show origins were loaded

**Check 3: Response headers**
In browser DevTools, Network tab, check `/ws/info?t=` response headers:
```
Access-Control-Allow-Origin: https://preview--boardgame-verse.lovable.app
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD
Access-Control-Allow-Headers: *
```

### If WebSocket connects but no messages arrive

Check backend logs for:
```
WebSocket authenticated with user: [username]
```

If missing, check auth token validity.

### If CorsConfig errors appear

**Verify CorsConfig.java is disabled:**
- File should NOT have `@Configuration` annotation
- Should look like a documentation file

---

## 📚 Architecture Summary

### Before (Broken)
```
DevCorsConfig (/** with wildcard, no credentials)
                ↓
CorsConfig (/api/** with specific origins, credentials enabled)
                ↓
WebSocketConfig (/ws with wildcard)
                ↓
JwtHandshakeInterceptor
                ↓
RESULT: CONFLICT - SockJS probes fail
```

### After (Fixed)
```
DevCorsConfig (/** with specific origins, credentials configurable)
                ↓
WebSocketConfig (/ws with same origins from DevCorsConfig)
                ↓
JwtHandshakeInterceptor
                ↓
RESULT: CONSISTENT - All endpoints use same origin rules
```

---

## 🎯 Key Principles

1. **Single Source of Truth**: All CORS origins now defined in `gamehub.websocket.allowed-origins`
2. **No Wildcard with Credentials**: Browser security doesn't allow `Access-Control-Allow-Origin: *` with credentials
3. **Specific Origins**: Using specific origin patterns instead of wildcards for production-ready setup
4. **Consistent Configuration**: WebSocket and REST CORS use same origin list
5. **Debug Logging**: Console logs show what's configured and why

---

## ✨ What Should Work Now

✅ Login endpoint from frontend (REST API call)
✅ WebSocket connection establishment
✅ SockJS HTTP fallback (if WebSocket unavailable)
✅ Cross-profile browser access
✅ ngrok tunnel access
✅ JWT authentication over WebSocket
✅ Real-time messaging features

---

## 📞 Need More Help?

If issues persist after rebuild:
1. Check backend logs: `docker compose logs backend`
2. Look for log lines starting with "DevCorsConfig:" or "WebSocketConfig:"
3. Check browser Network tab for exact error messages
4. Verify environment variables are set correctly
