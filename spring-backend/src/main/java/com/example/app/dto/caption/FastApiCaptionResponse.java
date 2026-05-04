package com.example.app.dto.caption;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FastApiCaptionResponse(
        String filename,
        String style,
        List<String> concepts,
        String prompt,
        String caption,
        List<String> captions
) {
    public List<String> safeCaptions() {
        if (captions != null && !captions.isEmpty()) return captions;
        if (caption != null && !caption.isBlank()) return List.of(caption);
        return List.of();
    }
}
