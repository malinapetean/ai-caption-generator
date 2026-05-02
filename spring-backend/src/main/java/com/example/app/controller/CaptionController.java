package com.example.app.controller;

import java.util.List;

import com.example.app.dto.caption.CaptionDto;
import com.example.app.dto.caption.CaptionGenerateResponse;
import com.example.app.dto.caption.CaptionSelectionRequest;
import com.example.app.model.AppUser;
import com.example.app.service.CaptionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/captions")
public class CaptionController {

    private final CaptionService captionService;

    public CaptionController(CaptionService captionService) {
        this.captionService = captionService;
    }

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CaptionGenerateResponse generateCaptions(
            @AuthenticationPrincipal AppUser user,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "imageId", required = false) Long imageId,
            @RequestParam(value = "style", required = false) String style,
            @RequestParam(value = "count", required = false) Integer count
    ) {
        return captionService.generateCaptions(user, image, imageId, style, count);
    }

    @PostMapping("/select")
    public CaptionDto selectCaption(
            @AuthenticationPrincipal AppUser user,
            @Valid @RequestBody CaptionSelectionRequest request
    ) {
        return captionService.selectCaption(user, request.captionId());
    }

    @GetMapping("/history")
    public List<CaptionDto> getHistory(@AuthenticationPrincipal AppUser user) {
        return captionService.getHistory(user);
    }
}
