package com.example.app.service;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.example.app.config.FastApiProperties;
import com.example.app.dto.caption.FastApiCaptionResponse;
import com.example.app.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FastApiClientServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private FastApiClientService fastApiClientService;

    @TempDir
    Path tempDir;

    @Test
    void generateCaptions_whenFastApiTimesOut_returnsHelpfulTimeoutMessage() throws Exception {
        FastApiProperties properties = properties();
        fastApiClientService = new FastApiClientService(restTemplate, properties);
        Path imagePath = Files.writeString(tempDir.resolve("image.png"), "image", StandardCharsets.UTF_8);

        when(restTemplate.postForEntity(
                eq("http://localhost:8000/api/generate-caption"),
                any(HttpEntity.class),
                eq(FastApiCaptionResponse.class)
        )).thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> fastApiClientService.generateCaptions(imagePath, "travel", 1))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("FastAPI caption request timed out after 120000 ms. The first generation can take longer while the model warms up.");
    }

    @Test
    void generateCaptions_whenFastApiReturnsHttpError_includesStatusAndBody() throws Exception {
        FastApiProperties properties = properties();
        fastApiClientService = new FastApiClientService(restTemplate, properties);
        Path imagePath = Files.writeString(tempDir.resolve("image.png"), "image", StandardCharsets.UTF_8);

        when(restTemplate.postForEntity(
                eq("http://localhost:8000/api/generate-caption"),
                any(HttpEntity.class),
                eq(FastApiCaptionResponse.class)
        )).thenThrow(HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY,
                "Bad Gateway",
                org.springframework.http.HttpHeaders.EMPTY,
                "{\"detail\":\"Ollama unavailable\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        ));

        assertThatThrownBy(() -> fastApiClientService.generateCaptions(imagePath, "travel", 1))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("FastAPI caption service returned HTTP 502: {\"detail\":\"Ollama unavailable\"}");
    }

    @Test
    void generateCaptions_collectsUniqueCaptionsUntilRequestedCount() throws Exception {
        FastApiProperties properties = properties();
        fastApiClientService = new FastApiClientService(restTemplate, properties);
        Path imagePath = Files.writeString(tempDir.resolve("image.png"), "image", StandardCharsets.UTF_8);

        when(restTemplate.postForEntity(
                eq("http://localhost:8000/api/generate-caption"),
                any(HttpEntity.class),
                eq(FastApiCaptionResponse.class)
        ))
                .thenReturn(org.springframework.http.ResponseEntity.ok(
                        new FastApiCaptionResponse(
                                "image.png",
                                "travel",
                                java.util.List.of("sea", "mountains"),
                                "prompt 1",
                                null,
                                java.util.List.of("caption one", "caption one")
                        )
                ))
                .thenReturn(org.springframework.http.ResponseEntity.ok(
                        new FastApiCaptionResponse(
                                "image.png",
                                "travel",
                                java.util.List.of("sea", "mountains"),
                                "prompt 2",
                                null,
                                java.util.List.of("caption two")
                        )
                ));

        FastApiClientService.FastApiBatchResponse response =
                fastApiClientService.generateCaptions(imagePath, "travel", 2);

        assertThat(response.concepts()).containsExactly("sea", "mountains");
        assertThat(response.prompt()).isEqualTo("prompt 2");
        assertThat(response.captions()).containsExactly("caption one", "caption two");
    }

    private FastApiProperties properties() {
        FastApiProperties properties = new FastApiProperties();
        properties.setBaseUrl("http://localhost:8000/api");
        properties.setGeneratePath("/generate-caption");
        properties.setReadTimeoutMs(120000);
        properties.setConnectTimeoutMs(5000);
        return properties;
    }
}
