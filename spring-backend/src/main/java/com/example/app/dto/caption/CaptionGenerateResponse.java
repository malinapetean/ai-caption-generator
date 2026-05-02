package com.example.app.dto.caption;

import java.util.List;

public record CaptionGenerateResponse(
        Long imageId,
        String style,
        List<String> concepts,
        String prompt,
        List<CaptionDto> captions
) {
}
