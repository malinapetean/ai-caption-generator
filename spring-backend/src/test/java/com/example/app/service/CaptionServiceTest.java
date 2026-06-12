package com.example.app.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.example.app.config.FastApiProperties;
import com.example.app.dto.caption.CaptionDto;
import com.example.app.dto.caption.CaptionGenerateResponse;
import com.example.app.exception.BadRequestException;
import com.example.app.model.AppUser;
import com.example.app.model.Caption;
import com.example.app.model.ImageRecord;
import com.example.app.repository.AppUserRepository;
import com.example.app.repository.CaptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptionServiceTest {

    @Mock
    private CaptionRepository captionRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private StorageService storageService;

    @Mock
    private FastApiClientService fastApiClientService;

    private FastApiProperties fastApiProperties;

    @InjectMocks
    private CaptionService captionService;

    @BeforeEach
    void setUp() {
        fastApiProperties = new FastApiProperties();
        fastApiProperties.setCaptionsPerRequest(4);
        fastApiProperties.setDefaultStyle("minimalist");
        captionService = new CaptionService(
                captionRepository,
                appUserRepository,
                imageService,
                storageService,
                fastApiClientService,
                fastApiProperties
        );
    }

    @Test
    void generateCaptions_withImageIdAndBlankStyle_usesDefaultStyleAndPersistsCaptions() {
        AppUser user = user(5L, "");
        ImageRecord imageRecord = imageRecord(11L, user, "user-5/existing.png");
        Path imagePath = Path.of("uploads", "user-5", "existing.png");
        AtomicLong generatedIds = new AtomicLong(100L);

        when(imageService.getOwnedImage(11L, 5L)).thenReturn(imageRecord);
        when(storageService.resolve("user-5/existing.png")).thenReturn(imagePath);
        when(fastApiClientService.generateCaptions(imagePath, "minimalist", 4))
                .thenReturn(new FastApiClientService.FastApiBatchResponse(
                        List.of("lake", "sunset"),
                        "Prompt text",
                        List.of("caption one", "caption two")
                ));
        when(captionRepository.save(any(Caption.class))).thenAnswer(invocation -> {
            Caption caption = invocation.getArgument(0);
            caption.setId(generatedIds.incrementAndGet());
            return caption;
        });
        when(appUserRepository.save(user)).thenReturn(user);

        CaptionGenerateResponse response = captionService.generateCaptions(
                user,
                null,
                11L,
                "  ",
                null
        );

        assertThat(response.imageId()).isEqualTo(11L);
        assertThat(response.style()).isEqualTo("minimalist");
        assertThat(response.concepts()).containsExactly("lake", "sunset");
        assertThat(response.prompt()).isEqualTo("Prompt text");
        assertThat(response.captions()).hasSize(2);
        assertThat(response.captions())
                .extracting(CaptionDto::text)
                .containsExactly("caption one", "caption two");
        assertThat(response.captions())
                .extracting(CaptionDto::style)
                .containsOnly("minimalist");
        assertThat(response.captions())
                .extracting(CaptionDto::generationBatchId)
                .doesNotContainNull()
                .containsOnly(response.captions().get(0).generationBatchId());

        ArgumentCaptor<Caption> captionCaptor = ArgumentCaptor.forClass(Caption.class);
        verify(captionRepository, org.mockito.Mockito.times(2)).save(captionCaptor.capture());
        assertThat(captionCaptor.getAllValues())
                .allSatisfy(caption -> {
                    assertThat(caption.getImage()).isEqualTo(imageRecord);
                    assertThat(caption.getStyle()).isEqualTo("minimalist");
                    assertThat(caption.isSelected()).isFalse();
                });

        assertThat(user.getPreferredStyle()).isEqualTo("minimalist");
        verify(appUserRepository).save(user);
    }

    @Test
    void generateCaptions_whenImageSelectionIsInvalid_throwsBadRequest() {
        AppUser user = user(5L, "casual");
        MockMultipartFile image = new MockMultipartFile("image", "test.png", "image/png", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> captionService.generateCaptions(user, null, null, "poetic", 3))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Provide either an image file or an imageId.");

        assertThatThrownBy(() -> captionService.generateCaptions(user, image, 7L, "poetic", 3))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Provide either an image file or an imageId.");

        verify(captionRepository, never()).save(any(Caption.class));
    }

    @Test
    void selectCaption_unselectsOnlyCaptionsFromSameGenerationBatch() {
        AppUser user = user(2L, "travel");
        ImageRecord imageRecord = imageRecord(8L, user, "user-2/image.png");
        Caption target = caption(20L, imageRecord, "travel", "batch-2", false, "Target");
        Caption sibling = caption(21L, imageRecord, "travel", "batch-2", true, "Sibling");

        when(captionRepository.findByIdAndImageUserId(20L, 2L)).thenReturn(Optional.of(target));
        when(captionRepository.findByImageIdAndGenerationBatchId(8L, "batch-2")).thenReturn(List.of(target, sibling));
        when(captionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CaptionDto response = captionService.selectCaption(user, 20L);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.selected()).isTrue();
        assertThat(target.isSelected()).isTrue();
        assertThat(sibling.isSelected()).isFalse();
        verify(captionRepository).saveAll(List.of(target, sibling));
    }

    @Test
    void getHistory_mapsRepositoryCaptionsToDtos() {
        AppUser user = user(9L, "casual");
        ImageRecord imageRecord = imageRecord(15L, user, "user-9/pic.png");
        Caption first = caption(30L, imageRecord, "poetic", "batch-1", false, "First");
        Caption second = caption(31L, imageRecord, "travel", "batch-2", true, "Second");

        when(captionRepository.findByImageUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(second, first));

        List<CaptionDto> history = captionService.getHistory(user);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).id()).isEqualTo(31L);
        assertThat(history.get(0).text()).isEqualTo("Second");
        assertThat(history.get(0).selected()).isTrue();
        assertThat(history.get(1).id()).isEqualTo(30L);
        assertThat(history.get(1).style()).isEqualTo("poetic");
    }

    private AppUser user(Long id, String preferredStyle) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setPreferredStyle(preferredStyle);
        return user;
    }

    private ImageRecord imageRecord(Long id, AppUser user, String path) {
        ImageRecord imageRecord = new ImageRecord();
        imageRecord.setId(id);
        imageRecord.setUser(user);
        imageRecord.setPath(path);
        return imageRecord;
    }

    private Caption caption(
            Long id,
            ImageRecord imageRecord,
            String style,
            String generationBatchId,
            boolean selected,
            String text
    ) {
        Caption caption = new Caption();
        caption.setId(id);
        caption.setImage(imageRecord);
        caption.setStyle(style);
        caption.setGenerationBatchId(generationBatchId);
        caption.setSelected(selected);
        caption.setText(text);
        return caption;
    }
}
