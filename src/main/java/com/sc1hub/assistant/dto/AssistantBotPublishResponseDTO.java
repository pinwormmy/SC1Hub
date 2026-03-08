package com.sc1hub.assistant.dto;

import lombok.Data;

@Data
public class AssistantBotPublishResponseDTO {
    private Long historyId;
    private String boardTitle;
    private String mode;
    private String status;
    private Integer targetPostNum;
    private Integer publishedPostNum;
    private String redirectUrl;
    private String error;
}
