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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
                    assertThat(caption.isSelected()).isFalse();
                });

        AppUser refreshedUser = appUserRepository.findById(appUser.getId()).orElseThrow();
        assertThat(refreshedUser.getPreferredStyle()).isEqualTo("poetic");
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

    private AppUser saveUser(String preferredStyle) {
        AppUser appUser = new AppUser();
        appUser.setEmail("tester-" + UUID.randomUUID() + "@example.com");
        appUser.setPasswordHash("password-hash");
        appUser.setPreferredStyle(preferredStyle);
        return appUserRepository.save(appUser);
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
