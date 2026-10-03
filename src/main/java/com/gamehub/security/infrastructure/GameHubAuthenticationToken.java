package com.gamehub.security.infrastructure;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * WebSocket/STOMP session authentication whose {@link #getName()} is the stable user id.
 *
 * <p>{@link UsernamePasswordAuthenticationToken} resolves {@code getName()} via
 * {@link org.springframework.security.core.userdetails.UserDetails#getUsername()}, which would
 * register STOMP users under display names and break {@code convertAndSendToUser(userId, ...)}.
 */
public class GameHubAuthenticationToken extends UsernamePasswordAuthenticationToken {

    private final GameHubUserPrincipal userPrincipal;

    public GameHubAuthenticationToken(GameHubUserPrincipal userPrincipal) {
        super(userPrincipal, null, userPrincipal.getAuthorities());
        this.userPrincipal = userPrincipal;
    }

    @Override
    public String getName() {
        return userPrincipal.userId().toString();
    }

    public GameHubUserPrincipal userPrincipal() {
        return userPrincipal;
    }
}
