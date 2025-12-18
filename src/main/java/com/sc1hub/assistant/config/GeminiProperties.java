package com.sc1hub.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sc1hub.gemini")
public class GeminiProperties {
    private String apiKey;
    private String model = "gemini-1.5-flash";
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private String apiVersion = "v1beta";
    private double temperature = 0.2;
    private int maxOutputTokens = 512;
}

