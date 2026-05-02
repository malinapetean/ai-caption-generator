package com.example.app.dto.image;

import java.time.Instant;

public record ImageUploadResponse(
        Long id,
        String path,
        Instant createdAt
) {
}
