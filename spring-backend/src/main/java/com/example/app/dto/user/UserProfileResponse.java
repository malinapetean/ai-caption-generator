package com.example.app.dto.user;

public record UserProfileResponse(
        Long id,
        String email,
        String preferredStyle
) {
}
