package com.sc1hub.assistant.dto;

import lombok.Data;

@Data
public class AssistantBotDraftRequestDTO {
    private String mode;
    private String boardTitle;
    private Integer targetPostNum;
    private Integer recentPostLimit;
    private Integer recentCommentLimit;
}
