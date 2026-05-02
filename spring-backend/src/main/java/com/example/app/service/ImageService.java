package com.example.app.service;

import java.util.List;

import com.example.app.dto.image.ImageResponse;
import com.example.app.dto.image.ImageUploadResponse;
import com.example.app.exception.ResourceNotFoundException;
import com.example.app.model.AppUser;
import com.example.app.model.ImageRecord;
import com.example.app.repository.ImageRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageService {

    private final ImageRecordRepository imageRecordRepository;
    private final StorageService storageService;

    public ImageService(ImageRecordRepository imageRecordRepository, StorageService storageService) {
        this.imageRecordRepository = imageRecordRepository;
        this.storageService = storageService;
    }

    @Transactional
    public ImageUploadResponse uploadImage(AppUser user, MultipartFile file) {
        ImageRecord imageRecord = createImageRecord(user, file);
        return toUploadResponse(imageRecord);
    }

    @Transactional
    public ImageRecord createImageRecord(AppUser user, MultipartFile file) {
        String storedPath = storageService.store(user.getId(), file);

        ImageRecord imageRecord = new ImageRecord();
        imageRecord.setUser(user);
        imageRecord.setPath(storedPath);
        return imageRecordRepository.save(imageRecord);
    }

    @Transactional(readOnly = true)
    public ImageRecord getOwnedImage(Long imageId, Long userId) {
        return imageRecordRepository.findByIdAndUserId(imageId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found."));
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> getImagesForUser(Long requestedUserId, AppUser currentUser) {
        if (!requestedUserId.equals(currentUser.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot access another user's images.");
        }

        return imageRecordRepository.findByUserIdOrderByCreatedAtDesc(requestedUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ImageResponse toResponse(ImageRecord imageRecord) {
        return new ImageResponse(
                imageRecord.getId(),
                imageRecord.getUser().getId(),
                imageRecord.getPath(),
                imageRecord.getCreatedAt()
        );
    }

    private ImageUploadResponse toUploadResponse(ImageRecord imageRecord) {
        return new ImageUploadResponse(
                imageRecord.getId(),
                imageRecord.getPath(),
                imageRecord.getCreatedAt()
        );
    }
}
