package com.example.app.dto.caption;

import java.util.List;

public record FastApiCaptionResponse(
        String filename,
        String style,
        List<String> concepts,
        String prompt,
        String caption,
        List<String> captions
) {
}
