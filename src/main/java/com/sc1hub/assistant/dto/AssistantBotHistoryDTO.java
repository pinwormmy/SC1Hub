package com.sc1hub.assistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssistantBotHistoryDTO {
    private Long id;
    private String personaName;
    private String boardTitle;
    private String generationMode;
    private Integer targetPostNum;
    private String topic;
    private String draftTitle;
    private String draftBody;
    private String rawJson;
    private String status;
    private Integer publishedPostNum;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
