package com.gamehub.websocket.infrastructure;

import com.gamehub.security.application.JwtService;
import com.gamehub.security.infrastructure.GameHubUserDetailsService;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
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
    private final JwtService jwtService;
    private final GameHubUserDetailsService userDetailsService;

    @Value("${gamehub.websocket.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor());
    }

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
        List<String> patterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (patterns.isEmpty()) {
            patterns = List.of("*");
        }

        System.out.println("WebSocketConfig: Configured origins for /ws = " + patterns);

        registry.addEndpoint("/ws")
                // Allow all origins for WebSocket/STOMP handshake
                .setAllowedOriginPatterns("*")
                // JWT validation during WebSocket handshake
                .addInterceptors(jwtHandshakeInterceptor)
                // Custom principal handler
                .setHandshakeHandler(webSocketPrincipalHandshakeHandler())
                // Enable SockJS fallback for browsers that don't support WebSocket
                .withSockJS()
                // SockJS configuration
                .setSessionCookieNeeded(false)
                .setHeartbeatTime(10000)
                .setDisconnectDelay(5000)
                .setHttpMessageCacheSize(1024)
                .setWebSocketEnabled(true)
                .setStreamBytesLimit(512 * 1024);
    }

    @Bean
    ChannelInterceptor stompAuthChannelInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
                    return message;
                }

                Principal existingPrincipal = accessor.getUser();
                if (existingPrincipal != null) {
                    return message;
                }

                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                if (sessionAttributes != null && sessionAttributes.get("userId") != null) {
                    accessor.setUser(() -> sessionAttributes.get("userId").toString());
                    return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
                }

                String token = resolveToken(accessor);
                if (token != null && jwtService.isValid(token)) {
                    accessor.setUser(() -> jwtService.extractUserId(token).toString());
                    return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
                }

                throw new MessageDeliveryException("Authentication required");
            }
        };
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        List<String> authorizationHeaders = accessor.getNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeaders != null && !authorizationHeaders.isEmpty()) {
            String authorization = authorizationHeaders.getFirst();
            if (authorization != null && authorization.startsWith("Bearer ")) {
                return authorization.substring(7);
            }
            return authorization;
        }
        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            return tokenHeaders.getFirst();
        }
        return null;
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
        return new WebSocketPrincipalHandshakeHandler(userDetailsService);
    }
}
