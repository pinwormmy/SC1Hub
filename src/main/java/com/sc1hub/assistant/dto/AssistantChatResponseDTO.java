package com.sc1hub.assistant.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssistantChatResponseDTO {
    private String answer;
    private List<AssistantRelatedPostDTO> relatedPosts = new ArrayList<>();
    private String error;

    // Answer-grounded related posts (backward compatible fields)
    private List<String> usedPostIds = new ArrayList<>();
    private String relatedPostsNotice;

    private String usageText;
    private Integer usageUsed;
    private Integer usageLimit;
    private boolean usageUnlimited;
}
