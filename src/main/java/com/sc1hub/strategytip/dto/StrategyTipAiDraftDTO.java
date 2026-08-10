package com.sc1hub.strategytip.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StrategyTipAiDraftDTO {
    private long draftId;
    private LocalDate generationDate;
    private int slotNo;
    private String category;
    private String categoryName;
    private String content;
    private String evidenceSummary;
    private String sourceBoard;
    private int sourcePostNum;
    private String sourceTitle;
    private String sourceExcerpt;
    private String externalSourceUrl;
    private String externalSourceTitle;
    private String externalEvidenceSummary;
    private String model;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private Integer publishedTipNum;
}
