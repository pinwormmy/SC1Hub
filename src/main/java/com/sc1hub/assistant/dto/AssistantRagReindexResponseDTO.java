package com.sc1hub.assistant.dto;

import lombok.Data;

@Data
public class AssistantRagReindexResponseDTO {
    private boolean enabled;
    private int indexedPosts;
    private int indexedChunks;
    private int dimension;
    private String indexPath;
    private String error;
}

