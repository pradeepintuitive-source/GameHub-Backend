package com.gamehub.websocket.infrastructure;

import com.gamehub.security.infrastructure.GameHubAuthenticationToken;
import com.gamehub.security.infrastructure.GameHubUserDetailsService;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Slf4j
public class WebSocketPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    private final GameHubUserDetailsService userDetailsService;

    public WebSocketPrincipalHandshakeHandler(GameHubUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Object userId = attributes.get("userId");
        if (!(userId instanceof String userIdValue)) {
            return null;
        }
        GameHubUserPrincipal userPrincipal = userDetailsService.loadUserById(UUID.fromString(userIdValue));
        GameHubAuthenticationToken authentication = new GameHubAuthenticationToken(userPrincipal);
        log.info(
                "WS HANDSHAKE resolved userId={} username={} principalName={}",
                userIdValue,
                userPrincipal.getUsername(),
                authentication.getName());
        // Must register under userId so convertAndSendToUser(userId, "/queue/acks") matches SimpUserRegistry.
        return authentication;
    }
}
