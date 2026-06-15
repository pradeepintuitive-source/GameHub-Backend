package com.gamehub.player.api;

import com.gamehub.player.application.UserService;
import com.gamehub.security.infrastructure.GameHubUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserDtos.UserResponse me(@AuthenticationPrincipal GameHubUserPrincipal principal) {
        return userService.getCurrentUser(principal);
    }
}
