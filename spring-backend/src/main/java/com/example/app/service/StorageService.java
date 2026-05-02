package com.example.app.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import com.example.app.config.StorageProperties;
import com.example.app.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StorageService {

    private final StorageProperties storageProperties;
    private Path rootPath;

    public StorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @PostConstruct
    void init() {
        this.rootPath = Path.of(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create upload directory.", exception);
        }
    }

    public String store(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Image file is required.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "image" : file.getOriginalFilename());
        String safeFilename = Path.of(StringUtils.hasText(originalFilename) ? originalFilename : "image").getFileName().toString();
        String relativePath = "user-" + userId + "/" + UUID.randomUUID() + "-" + safeFilename;
        Path destination = rootPath.resolve(relativePath).normalize();

        if (!destination.startsWith(rootPath)) {
            throw new BadRequestException("Invalid file path.");
        }

        try {
            Files.createDirectories(destination.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store image.", exception);
        }

        return relativePath.replace("\\", "/");
    }

    public Path resolve(String relativePath) {
        Path resolved = rootPath.resolve(relativePath).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new BadRequestException("Invalid stored file path.");
        }
        return resolved;
    }
}
