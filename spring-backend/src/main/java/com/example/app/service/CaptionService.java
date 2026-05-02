package com.example.app.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.app.config.FastApiProperties;
import com.example.app.dto.caption.CaptionDto;
import com.example.app.dto.caption.CaptionGenerateResponse;
import com.example.app.exception.BadRequestException;
import com.example.app.exception.ResourceNotFoundException;
import com.example.app.model.AppUser;
import com.example.app.model.Caption;
import com.example.app.model.ImageRecord;
import com.example.app.repository.AppUserRepository;
import com.example.app.repository.CaptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CaptionService {

    private final CaptionRepository captionRepository;
    private final AppUserRepository appUserRepository;
    private final ImageService imageService;
    private final StorageService storageService;
    private final FastApiClientService fastApiClientService;
    private final FastApiProperties fastApiProperties;

    public CaptionService(
            CaptionRepository captionRepository,
            AppUserRepository appUserRepository,
            ImageService imageService,
            StorageService storageService,
            FastApiClientService fastApiClientService,
            FastApiProperties fastApiProperties
    ) {
        this.captionRepository = captionRepository;
        this.appUserRepository = appUserRepository;
        this.imageService = imageService;
        this.storageService = storageService;
        this.fastApiClientService = fastApiClientService;
        this.fastApiProperties = fastApiProperties;
    }

    @Transactional
    public CaptionGenerateResponse generateCaptions(
            AppUser user,
            MultipartFile imageFile,
            Long imageId,
            String style,
            Integer requestedCount
    ) {
        boolean hasImageFile = imageFile != null && !imageFile.isEmpty();
        boolean hasImageId = imageId != null;

        if (hasImageFile == hasImageId) {
            throw new BadRequestException("Provide either an image file or an imageId.");
        }

        String resolvedStyle = resolveStyle(user, style);
        ImageRecord imageRecord = hasImageFile
                ? imageService.createImageRecord(user, imageFile)
                : imageService.getOwnedImage(imageId, user.getId());

        Path imagePath = storageService.resolve(imageRecord.getPath());
        int count = requestedCount == null ? fastApiProperties.getCaptionsPerRequest() : requestedCount;
        FastApiClientService.FastApiBatchResponse batchResponse =
                fastApiClientService.generateCaptions(imagePath, resolvedStyle, count);

        List<Caption> savedCaptions = new ArrayList<>();
        for (String generatedText : batchResponse.captions()) {
            Caption caption = new Caption();
            caption.setImage(imageRecord);
            caption.setText(generatedText);
            caption.setStyle(resolvedStyle);
            caption.setSelected(false);
            savedCaptions.add(captionRepository.save(caption));
        }

        if (!resolvedStyle.equals(user.getPreferredStyle())) {
            user.setPreferredStyle(resolvedStyle);
            appUserRepository.save(user);
        }

        return new CaptionGenerateResponse(
                imageRecord.getId(),
                resolvedStyle,
                batchResponse.concepts(),
                batchResponse.prompt(),
                savedCaptions.stream().map(this::toDto).toList()
        );
    }

    @Transactional
    public CaptionDto selectCaption(AppUser user, Long captionId) {
        Caption selectedCaption = captionRepository.findByIdAndImageUserId(captionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Caption not found."));

        List<Caption> imageCaptions = captionRepository.findByImageId(selectedCaption.getImage().getId());
        imageCaptions.forEach(caption -> caption.setSelected(false));
        selectedCaption.setSelected(true);
        captionRepository.saveAll(imageCaptions);
        return toDto(selectedCaption);
    }

    @Transactional(readOnly = true)
    public List<CaptionDto> getHistory(AppUser user) {
        return captionRepository.findByImageUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    private String resolveStyle(AppUser user, String style) {
        if (StringUtils.hasText(style)) {
            return style.trim();
        }
        if (StringUtils.hasText(user.getPreferredStyle())) {
            return user.getPreferredStyle();
        }
        return fastApiProperties.getDefaultStyle();
    }

    private CaptionDto toDto(Caption caption) {
        return new CaptionDto(
                caption.getId(),
                caption.getImage().getId(),
                caption.getText(),
                caption.getStyle(),
                caption.isSelected(),
                caption.getCreatedAt()
        );
    }
}
