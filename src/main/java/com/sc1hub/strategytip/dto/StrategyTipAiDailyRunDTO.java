package com.sc1hub.strategytip.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StrategyTipAiDailyRunDTO {
    private LocalDate generationDate;
    private int apiCallCount;
    private String lastStatus;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime completedAt;
    private String lastError;
    private int inputTokens;
    private int outputTokens;
    private int searchQueryCount;
}
