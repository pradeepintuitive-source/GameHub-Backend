package com.gamehub.security.application;

import com.gamehub.common.domain.ApiException;
import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.player.infrastructure.ProfileEntity;
import com.gamehub.player.infrastructure.ProfileRepository;
import com.gamehub.player.infrastructure.StatisticsEntity;
import com.gamehub.player.infrastructure.StatisticsRepository;
import com.gamehub.player.infrastructure.UserEntity;
import com.gamehub.player.infrastructure.UserRepository;
import com.gamehub.security.api.AuthDtos;
import com.gamehub.security.domain.UserRole;
import com.gamehub.security.infrastructure.GameHubUserDetailsService;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final StatisticsRepository statisticsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final GameHubUserDetailsService userDetailsService;

    public AuthDtos.AuthResponse createGuest(AuthDtos.GuestAuthRequest request) {
        String username = request.username() == null || request.username().isBlank()
                ? "guest-" + UUID.randomUUID().toString().substring(0, 8)
                : request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new BusinessRuleViolationException("Username already exists");
        }
        UserEntity user = createUser(username, null, UUID.randomUUID().toString(), true, EnumSet.of(UserRole.GUEST));
        return responseFor(user);
    }

    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleViolationException("Email already exists");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessRuleViolationException("Username already exists");
        }
        UserEntity user = createUser(
                request.username().trim(),
                request.email().trim().toLowerCase(),
                request.password(),
                false,
                EnumSet.of(UserRole.PLAYER));
        return responseFor(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String identifier = request.identifier() == null ? "" : request.identifier().trim();
        if (identifier.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User not found", List.of());
        }

        var userOpt = identifier.contains("@")
                ? userRepository.findByEmail(identifier.toLowerCase())
                        .or(() -> userRepository.findByUsername(identifier))
                : userRepository.findByUsername(identifier)
                        .or(() -> userRepository.findByEmail(identifier.toLowerCase()));

        if (userOpt.isEmpty()) {
            String message = identifier.contains("@") ? "Email not found" : "User not found";
            throw new ApiException(HttpStatus.UNAUTHORIZED, message, List.of());
        }

        UserEntity user = userOpt.get();
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password() == null ? "" : request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect password", List.of());
        }

        return responseFor(user);
    }

    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshTokenRequest request) {
        var refreshTokenEntity = refreshTokenService.validateRefreshToken(request.refreshToken());
        GameHubUserPrincipal principal = userDetailsService.loadUserById(refreshTokenEntity.getUserId());
        refreshTokenService.revokeRefreshToken(refreshTokenEntity);
        return responseFor(principal);
    }

    public void logout(AuthDtos.LogoutRequest request) {
        if (request.refreshToken() != null && !request.refreshToken().isBlank()) {
            var refreshTokenEntity = refreshTokenService.validateRefreshToken(request.refreshToken());
            refreshTokenService.revokeRefreshToken(refreshTokenEntity);
        }
    }

    public AuthDtos.AuthMeResponse me(GameHubUserPrincipal principal) {
        return new AuthDtos.AuthMeResponse(
                principal.userId(),
                principal.username(),
                principal.roles(),
                principal.guest());
    }

    private UserEntity createUser(
            String username,
            String email,
            String rawPassword,
            boolean guest,
            Set<UserRole> roles) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setGuest(guest);
        user.roleSet(roles);
        userRepository.save(user);

        ProfileEntity profile = new ProfileEntity();
        profile.setId(UUID.randomUUID());
        profile.setUserId(user.getId());
        profile.setDisplayName(username);
        profile.setLocale("en_US");
        profileRepository.save(profile);

        StatisticsEntity statistics = new StatisticsEntity();
        statistics.setId(UUID.randomUUID());
        statistics.setUserId(user.getId());
        statisticsRepository.save(statistics);

        return user;
    }

    private AuthDtos.AuthResponse responseFor(UserEntity user) {
        GameHubUserPrincipal principal = userDetailsService.loadUserById(user.getId());
        return responseFor(principal);
    }

    private AuthDtos.AuthResponse responseFor(GameHubUserPrincipal principal) {
        String token = jwtService.generateToken(principal);
        var refreshToken = refreshTokenService.createRefreshToken(principal.userId());
        return new AuthDtos.AuthResponse(
                principal.userId(),
                principal.username(),
                token,
                refreshToken.getToken(),
                principal.roles(),
                principal.guest());
    }
}
