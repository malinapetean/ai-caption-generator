package com.example.app.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.example.app.dto.image.ImageResponse;
import com.example.app.dto.image.ImageUploadResponse;
import com.example.app.exception.BadRequestException;
import com.example.app.exception.ResourceNotFoundException;
import com.example.app.model.AppUser;
import com.example.app.service.ImageService;
import com.example.app.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
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
    private final StorageService storageService;

    public ImageController(ImageService imageService, StorageService storageService) {
        this.imageService = imageService;
        this.storageService = storageService;
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

    @GetMapping("/{*filename}")
    public ResponseEntity<Resource> serveImage(@PathVariable String filename) {
        String cleanedPath = normalizePublicPath(filename);
        Path resolvedPath = storageService.resolve(cleanedPath);

        if (!Files.exists(resolvedPath) || !Files.isReadable(resolvedPath)) {
            throw new ResourceNotFoundException("Image not found.");
        }

        Resource resource;
        try {
            resource = new UrlResource(resolvedPath.toUri());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not open stored image.", exception);
        }

        String contentType;
        try {
            contentType = Files.probeContentType(resolvedPath);
        } catch (IOException exception) {
            contentType = null;
        }

        MediaType mediaType = StringUtils.hasText(contentType)
                ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource);
    }

    private String normalizePublicPath(String filename) {
        String cleanedPath = StringUtils.cleanPath(filename == null ? "" : filename).replace("\\", "/");

        while (cleanedPath.startsWith("/")) {
            cleanedPath = cleanedPath.substring(1);
        }

        if (!StringUtils.hasText(cleanedPath) || cleanedPath.contains("..")) {
            throw new BadRequestException("Invalid image path.");
        }

        return cleanedPath;
    }
}
