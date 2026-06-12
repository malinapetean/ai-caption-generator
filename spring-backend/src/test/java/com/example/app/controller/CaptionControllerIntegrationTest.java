package com.example.app.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.example.app.model.AppUser;
import com.example.app.model.Caption;
import com.example.app.model.ImageRecord;
import com.example.app.repository.AppUserRepository;
import com.example.app.repository.CaptionRepository;
import com.example.app.repository.ImageRecordRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CaptionControllerIntegrationTest {

    private static final Path TEST_UPLOAD_DIR = createTempUploadDir();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ImageRecordRepository imageRecordRepository;

    @Autowired
    private CaptionRepository captionRepository;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.upload-dir", () -> TEST_UPLOAD_DIR.toString());
        registry.add("app.fastapi.base-url", () -> "http://fastapi.test/api");
    }

    @AfterAll
    static void cleanUploadDirectory() throws IOException {
        deleteDirectory(TEST_UPLOAD_DIR);
    }

    @BeforeEach
    void setUp() throws IOException {
        captionRepository.deleteAll();
        imageRecordRepository.deleteAll();
        appUserRepository.deleteAll();
        recreateUploadDirectory();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void generateCaptions_withUploadedImageAndSelectedStyle_returnsGeneratedCaptionsAndPersistsResults() throws Exception {
        AppUser appUser = saveUser("casual");
        byte[] imageBytes = "fake-image-content".getBytes(StandardCharsets.UTF_8);
        String fastApiResponse = """
                {
                  "filename": "lake.png",
                  "style": "poetic",
                  "concepts": ["lake", "sunset"],
                  "prompt": "Write a poetic caption about a lake at sunset.",
                  "captions": [
                    "Golden light settles softly over the lake.",
                    "The sunset leaves a hush on the water.",
                    "Evening drifts in amber across the shore."
                  ]
                }
                """;

        mockServer.expect(ExpectedCount.once(), requestTo("http://fastapi.test/api/generate-caption"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Content-Type", org.hamcrest.Matchers.containsString(MediaType.MULTIPART_FORM_DATA_VALUE)))
                .andRespond(withSuccess(fastApiResponse, MediaType.APPLICATION_JSON));

        mockMvc.perform(multipart("/api/captions/generate")
                        .file(new MockMultipartFile("image", "lake.png", MediaType.IMAGE_PNG_VALUE, imageBytes))
                        .param("style", "poetic")
                        .with(user(appUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageId").isNumber())
                .andExpect(jsonPath("$.style").value("poetic"))
                .andExpect(jsonPath("$.concepts[0]").value("lake"))
                .andExpect(jsonPath("$.concepts[1]").value("sunset"))
                .andExpect(jsonPath("$.prompt").value("Write a poetic caption about a lake at sunset."))
                .andExpect(jsonPath("$.captions.length()").value(3))
                .andExpect(jsonPath("$.captions[0].generationBatchId", notNullValue()))
                .andExpect(jsonPath("$.captions[0].text").value("Golden light settles softly over the lake."))
                .andExpect(jsonPath("$.captions[1].text").value("The sunset leaves a hush on the water."))
                .andExpect(jsonPath("$.captions[2].text").value("Evening drifts in amber across the shore."));

        mockServer.verify();

        List<ImageRecord> images = imageRecordRepository.findAll();
        assertThat(images).hasSize(1);
        assertThat(images.get(0).getPath()).contains("user-" + appUser.getId() + "/");
        assertThat(images.get(0).getPath()).endsWith("-lake.png");
        Path storedImagePath = TEST_UPLOAD_DIR.resolve(images.get(0).getPath());
        assertThat(storedImagePath).exists();
        assertThat(Files.readString(storedImagePath, StandardCharsets.UTF_8)).isEqualTo("fake-image-content");

        List<Caption> captions = captionRepository.findAll();
        assertThat(captions).hasSize(3);
        assertThat(captions)
                .extracting(Caption::getText)
                .containsExactlyInAnyOrder(
                        "Golden light settles softly over the lake.",
                        "The sunset leaves a hush on the water.",
                        "Evening drifts in amber across the shore."
                );
        assertThat(captions)
                .extracting(Caption::getStyle)
                .containsOnly("poetic");
        assertThat(captions)
                .allSatisfy(caption -> {
                    assertThat(caption.getImage().getId()).isEqualTo(images.get(0).getId());
                    assertThat(caption.getGenerationBatchId()).isNotBlank();
                    assertThat(caption.isSelected()).isFalse();
                });
        assertThat(captions)
                .extracting(Caption::getGenerationBatchId)
                .containsOnly(captions.get(0).getGenerationBatchId());

        AppUser refreshedUser = appUserRepository.findById(appUser.getId()).orElseThrow();
        assertThat(refreshedUser.getPreferredStyle()).isEqualTo("poetic");
    }

    @Test
    void selectCaption_onlyUpdatesCaptionsFromSameGenerationBatch() throws Exception {
        AppUser appUser = saveUser("casual");
        ImageRecord imageRecord = saveImage(appUser);

        Caption firstBatchSelected = saveCaption(imageRecord, "poetic", "batch-1", true, "Poetic one");
        Caption firstBatchOther = saveCaption(imageRecord, "poetic", "batch-1", false, "Poetic two");
        Caption secondBatchFirst = saveCaption(imageRecord, "travel", "batch-2", false, "Travel one");
        Caption secondBatchTarget = saveCaption(imageRecord, "travel", "batch-2", false, "Travel two");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/captions/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"captionId\":" + secondBatchTarget.getId() + "}")
                        .with(user(appUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(secondBatchTarget.getId()))
                .andExpect(jsonPath("$.generationBatchId").value("batch-2"))
                .andExpect(jsonPath("$.selected").value(true));

        Caption refreshedFirstBatchSelected = captionRepository.findById(firstBatchSelected.getId()).orElseThrow();
        Caption refreshedFirstBatchOther = captionRepository.findById(firstBatchOther.getId()).orElseThrow();
        Caption refreshedSecondBatchFirst = captionRepository.findById(secondBatchFirst.getId()).orElseThrow();
        Caption refreshedSecondBatchTarget = captionRepository.findById(secondBatchTarget.getId()).orElseThrow();

        assertThat(refreshedFirstBatchSelected.isSelected()).isTrue();
        assertThat(refreshedFirstBatchOther.isSelected()).isFalse();
        assertThat(refreshedSecondBatchFirst.isSelected()).isFalse();
        assertThat(refreshedSecondBatchTarget.isSelected()).isTrue();
    }

    @Test
    void generateCaptions_withoutImageOrImageId_returnsBadRequest() throws Exception {
        AppUser appUser = saveUser("casual");

        mockMvc.perform(multipart("/api/captions/generate")
                        .param("style", "poetic")
                        .with(user(appUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Provide either an image file or an imageId."));

        assertThat(imageRecordRepository.findAll()).isEmpty();
        assertThat(captionRepository.findAll()).isEmpty();
    }

    @Test
    void getHistory_returnsOnlyAuthenticatedUsersCaptionsOrderedByNewestFirst() throws Exception {
        AppUser appUser = saveUser("casual");
        AppUser otherUser = saveUser("travel");

        ImageRecord firstImage = saveImage(appUser);
        ImageRecord secondImage = saveImage(appUser);
        ImageRecord otherImage = saveImage(otherUser);

        Caption oldestCaption = saveCaption(firstImage, "poetic", "batch-1", false, "Oldest caption");
        Caption newestCaption = saveCaption(secondImage, "travel", "batch-2", true, "Newest selected caption");
        saveCaption(otherImage, "luxury", "batch-3", true, "Other user's caption");

        mockMvc.perform(get("/api/captions/history").with(user(appUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newestCaption.getId()))
                .andExpect(jsonPath("$[0].imageId").value(secondImage.getId()))
                .andExpect(jsonPath("$[0].text").value("Newest selected caption"))
                .andExpect(jsonPath("$[0].style").value("travel"))
                .andExpect(jsonPath("$[0].selected").value(true))
                .andExpect(jsonPath("$[1].id").value(oldestCaption.getId()))
                .andExpect(jsonPath("$[1].imageId").value(firstImage.getId()))
                .andExpect(jsonPath("$[1].text").value("Oldest caption"))
                .andExpect(jsonPath("$[1].style").value("poetic"))
                .andExpect(jsonPath("$[1].selected").value(false));
    }

    private AppUser saveUser(String preferredStyle) {
        AppUser appUser = new AppUser();
        appUser.setEmail("tester-" + UUID.randomUUID() + "@example.com");
        appUser.setPasswordHash("password-hash");
        appUser.setPreferredStyle(preferredStyle);
        return appUserRepository.save(appUser);
    }

    private ImageRecord saveImage(AppUser user) {
        ImageRecord imageRecord = new ImageRecord();
        imageRecord.setUser(user);
        imageRecord.setPath("user-" + user.getId() + "/existing-image.jpg");
        return imageRecordRepository.save(imageRecord);
    }

    private Caption saveCaption(
            ImageRecord imageRecord,
            String style,
            String generationBatchId,
            boolean selected,
            String text
    ) {
        Caption caption = new Caption();
        caption.setImage(imageRecord);
        caption.setStyle(style);
        caption.setGenerationBatchId(generationBatchId);
        caption.setSelected(selected);
        caption.setText(text);
        return captionRepository.save(caption);
    }

    private static Path createTempUploadDir() {
        try {
            return Files.createTempDirectory("caption-controller-tests-");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create test upload directory.", exception);
        }
    }

    private static void recreateUploadDirectory() throws IOException {
        deleteDirectory(TEST_UPLOAD_DIR);
        Files.createDirectories(TEST_UPLOAD_DIR);
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (directory == null || Files.notExists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException("Could not clean test upload directory.", exception);
                        }
                    });
        }
    }
}
