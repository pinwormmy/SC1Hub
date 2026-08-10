package com.sc1hub.strategytip.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StrategyTipAiStatusDTO {
    private boolean enabled;
    private String model;
    private int dailyDraftLimit;
    private int maxPendingDrafts;
    private int pendingCount;
    private int generatedToday;
    private int apiCallCount;
    private String lastStatus;
    private String lastError;
    private LocalDateTime lastAttemptAt;
    private int inputTokens;
    private int outputTokens;
    private int searchQueryCount;
}
