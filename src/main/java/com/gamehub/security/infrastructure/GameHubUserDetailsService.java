package com.gamehub.security.infrastructure;

import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.player.infrastructure.UserEntity;
import com.gamehub.player.infrastructure.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GameHubUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public GameHubUserPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity userEntity = userRepository.findByEmail(username)
                .or(() -> userRepository.findByUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toPrincipal(userEntity);
    }

    public GameHubUserPrincipal loadUserById(UUID userId) {
        return userRepository.findById(userId)
                .map(this::toPrincipal)
                .orElseThrow(() -> new BusinessRuleViolationException("User not found"));
    }

    private GameHubUserPrincipal toPrincipal(UserEntity userEntity) {
        return new GameHubUserPrincipal(
                userEntity.getId(),
                userEntity.getUsername(),
                userEntity.getPasswordHash(),
                userEntity.isGuest(),
                userEntity.roleSet());
    }
}
