package com.gamehub.security.application;

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
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
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
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.identifier(), request.password()));
        GameHubUserPrincipal principal = userDetailsService.loadUserByUsername(request.identifier());
        String token = jwtService.generateToken(principal);
        return new AuthDtos.AuthResponse(
                principal.userId(),
                principal.username(),
                token,
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
        String token = jwtService.generateToken(principal);
        return new AuthDtos.AuthResponse(
                user.getId(),
                user.getUsername(),
                token,
                user.roleSet(),
                user.isGuest());
    }
}
