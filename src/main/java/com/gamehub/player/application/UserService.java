package com.gamehub.player.application;

import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.player.api.UserDtos;
import com.gamehub.player.infrastructure.ProfileEntity;
import com.gamehub.player.infrastructure.ProfileRepository;
import com.gamehub.player.infrastructure.StatisticsEntity;
import com.gamehub.player.infrastructure.StatisticsRepository;
import com.gamehub.player.infrastructure.UserEntity;
import com.gamehub.player.infrastructure.UserMapper;
import com.gamehub.player.infrastructure.UserRepository;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final StatisticsRepository statisticsRepository;
    private final UserMapper userMapper;

    public UserDtos.UserResponse getCurrentUser(GameHubUserPrincipal principal) {
        return getUser(principal.userId());
    }

    public UserDtos.UserResponse getUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleViolationException("User not found"));
        ProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleViolationException("Profile not found"));
        StatisticsEntity statistics = statisticsRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleViolationException("Statistics not found"));
        return new UserDtos.UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isGuest(),
                user.roleSet(),
                userMapper.toProfileResponse(profile),
                userMapper.toStatisticsResponse(statistics));
    }
}
