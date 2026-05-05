package com.sc1hub.assistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssistantBotHistorySummaryDTO {
    private String personaName;
    private String boardTitle;
    private String generationMode;
    private String status;
    private int count;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime latestCreatedAt;
}
