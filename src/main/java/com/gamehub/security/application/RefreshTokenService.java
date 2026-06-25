package com.gamehub.security.application;

import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.security.infrastructure.RefreshTokenEntity;
import com.gamehub.security.infrastructure.RefreshTokenRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${gamehub.security.refresh-token-days:30}")
    private int refreshTokenDays;

    public RefreshTokenEntity createRefreshToken(UUID userId) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS));
        token.setRevoked(false);
        return refreshTokenRepository.save(token);
    }

    public RefreshTokenEntity validateRefreshToken(String refreshToken) {
        return refreshTokenRepository.findByToken(refreshToken)
                .filter(entity -> !entity.isRevoked())
                .filter(entity -> entity.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessRuleViolationException("Refresh token is invalid or expired"));
    }

    public void revokeRefreshToken(RefreshTokenEntity refreshToken) {
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    public void revokeAllTokensForUser(UUID userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }
}
