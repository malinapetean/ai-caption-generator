package com.example.app.dto.caption;

import java.time.Instant;

public record CaptionDto(
        Long id,
        Long imageId,
        String text,
        String style,
        boolean selected,
        Instant createdAt
) {
}
