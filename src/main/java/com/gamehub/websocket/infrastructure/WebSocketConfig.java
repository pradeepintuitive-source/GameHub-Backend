package com.gamehub.websocket.infrastructure;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration with STOMP and SockJS support.
 * 
 * IMPORTANT: CORS headers for WebSocket are handled by:
 * 1. Spring's WebSocket origin validation (setAllowedOriginPatterns below)
 * 2. DevCorsConfig for REST endpoint CORS (which also covers SockJS HTTP polling)
 * 
 * SockJS HTTP fallback requests like /ws/info?t=... are REST-like requests
 * that go through Spring's DispatcherServlet and are subject to CORS rules
 * defined in DevCorsConfig. The WebSocket origin patterns below only apply to
 * the initial WebSocket handshake, not to SockJS probing requests.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Value("${gamehub.websocket.allowed-origins:https://boardgame-verse.vercel.app,https://preview--boardgame-verse.lovable.app,https://*.vercel.app,https://*.lovable.app,http://localhost:5173,http://localhost:4173}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartBeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Parse allowed origins from configuration
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        System.out.println("WebSocketConfig: Configured origins for /ws = " + Arrays.toString(origins));

        registry.addEndpoint("/ws")
                // Use the same origins as DevCorsConfig to maintain consistency
                // These are used for WebSocket handshake validation
                .setAllowedOriginPatterns(origins)
                // JWT validation during WebSocket handshake
                .addInterceptors(jwtHandshakeInterceptor)
                // Custom principal handler
                .setHandshakeHandler(webSocketPrincipalHandshakeHandler())
                // Enable SockJS fallback for browsers that don't support WebSocket
                .withSockJS()
                // SockJS configuration
                .setHeartbeatTime(10000)
                .setDisconnectDelay(5000)
                .setHttpMessageCacheSize(1024)
                .setWebSocketEnabled(true)
                .setStreamBytesLimit(512 * 1024);
    }

    @Bean
    ThreadPoolTaskScheduler heartBeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    WebSocketPrincipalHandshakeHandler webSocketPrincipalHandshakeHandler() {
        return new WebSocketPrincipalHandshakeHandler();
    }
}
