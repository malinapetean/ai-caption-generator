package com.example.app.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    private final FastApiProperties fastApiProperties;

    public RestTemplateConfig(FastApiProperties fastApiProperties) {
        this.fastApiProperties = fastApiProperties;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(java.time.Duration.ofMillis(fastApiProperties.getConnectTimeoutMs()))
                .setReadTimeout(java.time.Duration.ofMillis(fastApiProperties.getReadTimeoutMs()))
                .build();
    }
}
