package com.sc1hub.assistant.dto;

import lombok.Data;

@Data
public class AssistantRagUpdateResponseDTO {
    private boolean enabled;
    private boolean ready;
    private int updatedPosts;
    private int updatedChunks;
    private int dimension;
    private String indexPath;
    private String error;
}

