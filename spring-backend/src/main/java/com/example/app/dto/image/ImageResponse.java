package com.example.app.dto.image;

import java.time.Instant;

public record ImageResponse(
        Long id,
        Long userId,
        String path,
        Instant createdAt
) {
}
