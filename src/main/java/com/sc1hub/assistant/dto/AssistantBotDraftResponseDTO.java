package com.sc1hub.assistant.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class AssistantBotDraftResponseDTO {
    private Long historyId;
    private String personaName;
    private String boardTitle;
    private String mode;
    private Integer targetPostNum;
    private String status;
    private Integer attemptCount;
    private Integer recentPostCount;
    private Integer recentCommentCount;
    private Integer recentHistoryCount;
    private JsonNode result;
    private String error;
}
