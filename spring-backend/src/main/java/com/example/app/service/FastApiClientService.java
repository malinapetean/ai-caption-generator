package com.example.app.service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.example.app.config.FastApiProperties;
import com.example.app.dto.caption.FastApiCaptionResponse;
import com.example.app.exception.ExternalServiceException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class FastApiClientService {

    private final RestTemplate restTemplate;
    private final FastApiProperties fastApiProperties;

    public FastApiClientService(RestTemplate restTemplate, FastApiProperties fastApiProperties) {
        this.restTemplate = restTemplate;
        this.fastApiProperties = fastApiProperties;
    }

    public FastApiBatchResponse generateCaptions(Path imagePath, String style, int count) {
        int requested = Math.max(1, count);
        int maxAttempts = Math.max(requested, requested * 2);
        Set<String> uniqueCaptions = new LinkedHashSet<>();
        List<String> concepts = List.of();
        String prompt = null;

        for (int attempt = 0; attempt < maxAttempts && uniqueCaptions.size() < requested; attempt++) {
            FastApiCaptionResponse response = callFastApi(imagePath, style);
            if (response == null) {
                throw new ExternalServiceException("FastAPI service returned an empty response.");
            }

            if (response.concepts() != null && !response.concepts().isEmpty()) {
                concepts = response.concepts();
            }
            if (response.prompt() != null && !response.prompt().isBlank()) {
                prompt = response.prompt();
            }

            List<String> responseCaptions = response.safeCaptions();

            responseCaptions.stream()
                    .map(String::trim)
                    .filter(text -> !text.isBlank())
                    .forEach(uniqueCaptions::add);
        }

        if (uniqueCaptions.isEmpty()) {
            throw new ExternalServiceException("FastAPI service did not return any captions.");
        }

        return new FastApiBatchResponse(
                concepts,
                prompt,
                new ArrayList<>(uniqueCaptions).subList(0, Math.min(requested, uniqueCaptions.size()))
        );
    }

    private FastApiCaptionResponse callFastApi(Path imagePath, String style) {

        byte[] imageBytes;
        try {
            imageBytes = Files.readAllBytes(imagePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read stored image.", exception);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return imagePath.getFileName().toString();
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", imageResource);
        body.add("style", style);

        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

        try {
            ResponseEntity<FastApiCaptionResponse> response =
                    restTemplate.postForEntity(
                            fastApiProperties.getBaseUrl() + fastApiProperties.getGeneratePath(),
                            requestEntity,
                            FastApiCaptionResponse.class
                    );

            return response.getBody();

        } catch (ResourceAccessException exception) {
            throw new ExternalServiceException(describeResourceAccessException(exception), exception);
        } catch (HttpStatusCodeException exception) {
            throw new ExternalServiceException(describeHttpStatusException(exception), exception);
        } catch (RestClientException exception) {
            throw new ExternalServiceException("Failed to call FastAPI caption service.", exception);
        }
    }

    private String describeResourceAccessException(ResourceAccessException exception) {
        String message = exception.getMessage();
        Throwable rootCause = exception.getMostSpecificCause();

        if (rootCause instanceof SocketTimeoutException || containsIgnoreCase(message, "timed out")) {
            return "FastAPI caption request timed out after "
                    + fastApiProperties.getReadTimeoutMs()
                    + " ms. The first generation can take longer while the model warms up.";
        }

        if (containsIgnoreCase(message, "connection refused") || containsIgnoreCase(message, "failed to connect")) {
            return "Could not connect to the FastAPI caption service.";
        }

        return "Failed to reach the FastAPI caption service.";
    }

    private String describeHttpStatusException(HttpStatusCodeException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (StringUtils.hasText(responseBody)) {
            return "FastAPI caption service returned HTTP "
                    + exception.getStatusCode().value()
                    + ": "
                    + responseBody;
        }

        return "FastAPI caption service returned HTTP " + exception.getStatusCode().value() + ".";
    }

    private boolean containsIgnoreCase(String message, String fragment) {
        return message != null && message.toLowerCase().contains(fragment.toLowerCase());
    }

    public record FastApiBatchResponse(
            List<String> concepts,
            String prompt,
            List<String> captions
    ) {
    }
}
