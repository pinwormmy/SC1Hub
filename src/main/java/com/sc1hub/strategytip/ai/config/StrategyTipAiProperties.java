package com.sc1hub.strategytip.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sc1hub.strategy-tip.ai")
public class StrategyTipAiProperties {

    private boolean enabled = false;
    private boolean allowLiveCalls = false;
    private String apiKey = "";
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta/interactions";
    private String model = "gemini-3.6-flash";
    private String thinkingLevel = "medium";
    private int maxOutputTokens = 6000;
    private int dailyDraftLimit = 3;
    private int maxPendingDrafts = 3;
    private int maxDailyApiCalls = 2;
    private int staleRunMinutes = 10;
    private int sourcePostsPerCategory = 3;
    private int sourceExcerptChars = 360;
    private int duplicateContextLimit = 20;
    private String schedulerCron = "0 5 * * * *";
    private String schedulerZone = "Asia/Seoul";
    private String writer = "SC1Hub";
}
