package com.sc1hub.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sc1hub.anthropic")
public class AnthropicProperties {

    private String apiKey = "";
    // 비워 두면 SDK 기본 엔드포인트(api.anthropic.com)를 쓴다.
    private String baseUrl = "";
    private boolean allowLiveCalls = false;
    private String searchModel = "claude-haiku-5-5";
    private String searchEffort = "max";
}
