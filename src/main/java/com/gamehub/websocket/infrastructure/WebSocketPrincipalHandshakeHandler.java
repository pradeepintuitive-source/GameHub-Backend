package com.gamehub.websocket.infrastructure;

import com.gamehub.security.infrastructure.GameHubUserDetailsService;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

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
        GameHubUserPrincipal userPrincipal = userDetailsService.loadUserById(java.util.UUID.fromString(userIdValue));
        return new UsernamePasswordAuthenticationToken(
                userPrincipal,
                null,
                userPrincipal.getAuthorities());
    }
}
