package com.example.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.fastapi")
public class FastApiProperties {

    private String baseUrl;
    private String generatePath = "/generate-caption";
    private int captionsPerRequest = 3;
    private String defaultStyle = "casual";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getGeneratePath() {
        return generatePath;
    }

    public void setGeneratePath(String generatePath) {
        this.generatePath = generatePath;
    }

    public int getCaptionsPerRequest() {
        return captionsPerRequest;
    }

    public void setCaptionsPerRequest(int captionsPerRequest) {
        this.captionsPerRequest = captionsPerRequest;
    }

    public String getDefaultStyle() {
        return defaultStyle;
    }

    public void setDefaultStyle(String defaultStyle) {
        this.defaultStyle = defaultStyle;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
