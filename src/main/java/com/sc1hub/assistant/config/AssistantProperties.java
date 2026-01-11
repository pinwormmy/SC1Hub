package com.sc1hub.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private List<String> excludedBoards = new ArrayList<>();
    private List<String> factBoards = new ArrayList<>(Collections.singletonList("tipBoard"));
    private int boardListCacheSeconds = 60;
    private int searchLogLimit = 5;
    private int searchLogRetentionDays = 7;
    private List<String> blockedWords = new ArrayList<>();

    private int anonymousDailyLimit = 3;
    private int memberDailyLimit = 10;
    private boolean adminUnlimited = true;
    private String adminId = "admin";
    private int adminGrade = 3;
}
