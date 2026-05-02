package com.example.app.dto.caption;

import jakarta.validation.constraints.NotNull;

public record CaptionSelectionRequest(@NotNull Long captionId) {
}
