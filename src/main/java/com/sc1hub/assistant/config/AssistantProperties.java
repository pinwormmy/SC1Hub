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
    // Answer-grounded related posts: candidate pool size for selecting high-quality supporting links.
    private int relatedCandidatePoolSize = 40;
    // Minimum score threshold to show supporting posts (0~1-ish). Evidence post still shows when available.
    private double relatedPostThreshold = 0.35;
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

    // Optional PR4: low-cost LLM-assisted routing/reranking
    private boolean llmMatchupClassificationEnabled = false;
    private double llmMatchupClassificationMaxParserConfidence = 0.55;
    private double llmMatchupClassificationMinConfidence = 0.6;
    private int llmMatchupClassificationCacheSeconds = 3600;
    private int llmMatchupClassificationRateLimitPerMinute = 10;

    private boolean llmRerankEnabled = false;
    private int llmRerankTopN = 20;
    private int llmRerankExcerptChars = 140;
    private int llmRerankCacheSeconds = 600;
    private int llmRerankRateLimitPerMinute = 5;

    // Optional: LLM-based evidence/supporting selection for related posts
    private boolean llmRelatedPostsEnabled = false;
    private int llmRelatedPostsTopN = 15;
    private int llmRelatedPostsExcerptChars = 200;
    private int llmRelatedPostsCacheSeconds = 600;
    private int llmRelatedPostsRateLimitPerMinute = 5;
}
