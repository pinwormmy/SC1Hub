package com.sc1hub.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sc1hub.gemini")
public class GeminiProperties {
    private String apiKey;
    private String model = "gemini-3.7-flash";
    private String searchModel = "gemini-3.5-flash-lite";
    private String searchThinkingLevel = "minimal";
    private String embeddingModel = "gemini-embedding-001";
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private String apiVersion = "v1beta";
    private int maxOutputTokens = 512;
    private boolean allowLiveCalls = true;
}
