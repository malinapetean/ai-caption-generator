package com.example.app.dto.auth;

public record AuthResponse(
        Long userId,
        String email,
        String preferredStyle,
        String token
) {
}
