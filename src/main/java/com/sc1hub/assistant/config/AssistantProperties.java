package com.sc1hub.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sc1hub.assistant")
public class AssistantProperties {
    private boolean enabled = true;
    private boolean requireLogin = false;
    private int maxRelatedPosts = 3;
    private int contextPosts = 3;
    private int perBoardLimit = 5;
    private int maxPostSnippetChars = 800;
    private int maxPromptChars = 12000;

    private int anonymousDailyLimit = 3;
    private int memberDailyLimit = 10;
    private boolean adminUnlimited = true;
    private String adminId = "admin";
    private int adminGrade = 3;
}
