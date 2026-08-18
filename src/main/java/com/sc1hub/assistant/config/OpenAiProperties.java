package com.sc1hub.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sc1hub.openai")
public class OpenAiProperties {

    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1/responses";
    private boolean allowLiveCalls = false;
}
