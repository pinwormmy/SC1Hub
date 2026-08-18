package com.sc1hub.strategytip.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sc1hub.strategy-tip.ai")
public class StrategyTipAiProperties {

    private boolean enabled = false;
    private boolean allowLiveCalls = false;
    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1/responses";
    private String model = "gpt-5.6-luna";
    private String reasoningEffort = "high";
    private int maxOutputTokens = 6000;
    private int dailyDraftLimit = 1;
    private int maxPendingDrafts = 30;
    private int maxDailyApiCalls = 2;
    private int staleRunMinutes = 10;
    private int duplicateContextLimit = 20;
    private String schedulerCron = "0 5 * * * *";
    private String schedulerZone = "Asia/Seoul";
    private String writer = "SC1Hub";
}
