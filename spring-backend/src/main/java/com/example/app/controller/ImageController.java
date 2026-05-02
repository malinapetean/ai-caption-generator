package com.example.app.controller;

import java.util.List;

import com.example.app.dto.image.ImageResponse;
import com.example.app.dto.image.ImageUploadResponse;
import com.example.app.model.AppUser;
import com.example.app.service.ImageService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageUploadResponse uploadImage(
            @AuthenticationPrincipal AppUser user,
            @RequestPart("image") MultipartFile image
    ) {
        return imageService.uploadImage(user, image);
    }

    @GetMapping("/user/{id}")
    public List<ImageResponse> getUserImages(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUser user
    ) {
        return imageService.getImagesForUser(id, user);
    }
}
