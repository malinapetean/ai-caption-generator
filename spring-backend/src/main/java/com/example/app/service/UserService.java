package com.example.app.service;

import com.example.app.dto.user.UserProfileResponse;
import com.example.app.model.AppUser;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    public UserProfileResponse getProfile(AppUser user) {
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getPreferredStyle());
    }
}
