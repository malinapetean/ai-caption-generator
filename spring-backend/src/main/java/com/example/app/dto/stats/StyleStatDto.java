package com.example.app.dto.stats;

public record StyleStatDto(
        String style,
        long count,
        double percentage
) {
}
