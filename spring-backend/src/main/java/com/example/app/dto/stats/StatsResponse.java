package com.example.app.dto.stats;

import java.util.List;

public record StatsResponse(
        long totalCaptions,
        long totalSelectedCaptions,
        long totalUploadedImages,
        String mostUsedStyle,
        String mostSelectedStyle,
        String preferredStyle,
        List<StyleStatDto> generatedByStyle,
        List<StyleStatDto> selectedByStyle,
        List<SelectionRateStatDto> selectionRateByStyle
) {

    public record SelectionRateStatDto(
            String style,
            long generatedCount,
            long selectedCount,
            double selectionRate
    ) {
    }
}
