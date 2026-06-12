package com.example.app.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.example.app.model.AppUser;
import com.example.app.model.ImageRecord;
import com.example.app.repository.AppUserRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ImageControllerIntegrationTest {

    private static final Path TEST_UPLOAD_DIR = createTempUploadDir();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ImageRecordRepository imageRecordRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.upload-dir", () -> TEST_UPLOAD_DIR.toString());
    }

    @AfterAll
    static void cleanUploadDirectory() throws IOException {
        deleteDirectory(TEST_UPLOAD_DIR);
    }

    @BeforeEach
    void setUp() throws IOException {
        imageRecordRepository.deleteAll();
        appUserRepository.deleteAll();
        recreateUploadDirectory();
    }

    @Test
    void uploadImage_persistsRecordAndStoresFile() throws Exception {
        AppUser appUser = saveUser();
        byte[] imageBytes = "uploaded-image-content".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/images/upload")
                        .file(new MockMultipartFile("image", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, imageBytes))
                        .with(user(appUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.path").isString())
                .andExpect(jsonPath("$.createdAt").isString());

        List<ImageRecord> images = imageRecordRepository.findAll();
        assertThat(images).hasSize(1);
        assertThat(images.get(0).getPath()).contains("user-" + appUser.getId() + "/");
        assertThat(images.get(0).getPath()).endsWith("-photo.jpg");

        Path storedImagePath = TEST_UPLOAD_DIR.resolve(images.get(0).getPath());
        assertThat(storedImagePath).exists();
        assertThat(Files.readString(storedImagePath, StandardCharsets.UTF_8)).isEqualTo("uploaded-image-content");
    }

    @Test
    void uploadImage_withoutFile_returnsBadRequest() throws Exception {
        AppUser appUser = saveUser();

        mockMvc.perform(multipart("/api/images/upload").with(user(appUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required part 'image' is not present."));
    }

    private AppUser saveUser() {
        AppUser appUser = new AppUser();
        appUser.setEmail("image-" + UUID.randomUUID() + "@example.com");
        appUser.setPasswordHash("password-hash");
        appUser.setPreferredStyle("casual");
        return appUserRepository.save(appUser);
    }

    private static Path createTempUploadDir() {
        try {
            return Files.createTempDirectory("image-controller-tests-");
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
