package com.sc1hub.assistant.dto;

import lombok.Data;

import java.util.Date;

@Data
public class AssistantRagReindexResponseDTO {
    private boolean enabled;
    private boolean accepted;
    private boolean running;
    private Date startedAt;
    private Date finishedAt;
    private int indexedPosts;
    private int indexedChunks;
    private int dimension;
    private String indexPath;
    private String error;
    private String lastError;
}
