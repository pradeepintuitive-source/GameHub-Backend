package com.gamehub.websocket.infrastructure;

import com.gamehub.security.application.JwtService;
import com.gamehub.security.infrastructure.GameHubAuthenticationToken;
import com.gamehub.security.infrastructure.GameHubUserDetailsService;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.MimeTypeUtils;
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
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final JwtService jwtService;
    private final GameHubUserDetailsService userDetailsService;

    @Value("${gamehub.websocket.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * Wire a shared Jackson ObjectMapper into the STOMP message converter so that:
     * <ul>
     *   <li>{@code Instant} fields serialize as ISO-8601 strings, not numeric arrays.</li>
     *   <li>STOMP messages use the same serialization settings as the REST API.</li>
     * </ul>
     * Returns {@code false} so Spring keeps its default converters
     * (e.g. {@code StringMessageConverter}) alongside this one.
     */
    @Override
    public boolean configureMessageConverters(List<MessageConverter> converters) {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(mapper);
        converter.setContentTypeResolver(resolver);
        converters.add(converter);
        return false;
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
                    log.info(
                            "CONNECT already authenticated: principalClass={} principalName={} sessionId={}",
                            existingPrincipal.getClass().getName(),
                            existingPrincipal.getName(),
                            accessor.getSessionId());
                    return message;
                }

                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                if (sessionAttributes != null && sessionAttributes.get("userId") != null) {
                    UUID userId = UUID.fromString(sessionAttributes.get("userId").toString());
                    GameHubUserPrincipal userPrincipal = userDetailsService.loadUserById(userId);
                    accessor.setUser(new GameHubAuthenticationToken(userPrincipal));
                    log.info(
                            "CONNECT setUser from sessionAttributes: principalName={} sessionId={}",
                            accessor.getUser().getName(),
                            accessor.getSessionId());
                    return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
                }

                String token = resolveToken(accessor);
                if (token != null && jwtService.isValid(token)) {
                    GameHubUserPrincipal userPrincipal =
                            userDetailsService.loadUserById(jwtService.extractUserId(token));
                    accessor.setUser(new GameHubAuthenticationToken(userPrincipal));
                    log.info(
                            "CONNECT setUser from token: principalName={} sessionId={}",
                            accessor.getUser().getName(),
                            accessor.getSessionId());
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

    /**
     * STOMP inbound/outbound executor. The heartbeat scheduler is also an {@code Executor},
     * which suppresses Boot's default task executor; without this bean Boot runs voice
     * broadcasts on that two-thread scheduler.
     */
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("stomp-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
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
    ChannelInterceptor stompInboundLoggingInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null) {
                    log.info("STOMP INBOUND: command={} destination={} sessionId={} principal={} principalName={}",
                            accessor.getCommand(),
                            accessor.getDestination(),
                            accessor.getSessionId(),
                            accessor.getUser(),
                            accessor.getUser() != null ? accessor.getUser().getName() : null);
                }
                return message;
            }
        };
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompInboundLoggingInterceptor(), stompAuthChannelInterceptor());
    }

    @Bean
    ChannelInterceptor stompOutboundLoggingInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && accessor.getCommand() == StompCommand.MESSAGE) {
                    log.info("STOMP OUTBOUND MESSAGE: destination={} sessionId={} principal={} principalName={} command={}",
                            accessor.getDestination(),
                            accessor.getSessionId(),
                            accessor.getUser(),
                            accessor.getUser() != null ? accessor.getUser().getName() : null,
                            accessor.getCommand());
                }
                return message;
            }
        };
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompOutboundLoggingInterceptor());
    }

    @Bean
    WebSocketPrincipalHandshakeHandler webSocketPrincipalHandshakeHandler() {
        return new WebSocketPrincipalHandshakeHandler(userDetailsService);
    }
}
