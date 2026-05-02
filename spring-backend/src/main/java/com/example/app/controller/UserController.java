package com.example.app.controller;

import com.example.app.dto.user.UserProfileResponse;
import com.example.app.model.AppUser;
import com.example.app.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse getProfile(@AuthenticationPrincipal AppUser user) {
        return userService.getProfile(user);
    }
}
