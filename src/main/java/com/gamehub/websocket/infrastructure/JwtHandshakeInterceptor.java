package com.gamehub.websocket.infrastructure;

import com.gamehub.security.application.JwtService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Slf4j
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String origin = servletRequest.getServletRequest().getHeader("Origin");
            String host = servletRequest.getServletRequest().getHeader("Host");
            String authHeader = servletRequest.getServletRequest().getHeader(HttpHeaders.AUTHORIZATION);
            String requestUri = servletRequest.getServletRequest().getRequestURI();
            String queryString = servletRequest.getServletRequest().getQueryString();
            
            log.info("WebSocket handshake attempt - URI: {}, Query: {}, Origin: {}, Host: {}, Auth header present: {}", 
                    requestUri, queryString, origin, host, authHeader != null);
            
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            // If no Authorization header, check for ?token= query parameter (used by SockJS)
            if (token == null) {
                String param = servletRequest.getServletRequest().getParameter("token");
                if (param != null && !param.isBlank()) {
                    token = param;
                    log.debug("Found token in query parameter for WebSocket handshake");
                }
            }

            if (token != null) {
                if (jwtService.isValid(token)) {
                    attributes.put("userId", jwtService.extractUserId(token).toString());
                    attributes.put("username", jwtService.extractUsername(token));
                    log.info("✓ WebSocket authenticated with user: {}", jwtService.extractUsername(token));
                } else {
                    log.warn("✗ WebSocket token validation failed for token: {}", token.substring(0, Math.min(20, token.length())) + "...");
                }
            } else {
                log.debug("⚠ WebSocket request without auth header or token param - allowing as unauthenticated (guest connection)");
            }
        }
        return true;  // Allow handshake to proceed (authentication is optional for guests)
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        if (exception != null) {
            log.error("✗ WebSocket handshake failed: {}", exception.getMessage(), exception);
        } else {
            log.debug("✓ WebSocket handshake completed successfully");
        }
    }
}
