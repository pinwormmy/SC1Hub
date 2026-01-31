package com.sc1hub.assistant.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssistantSearchTermsReindexResponseDTO {
    private boolean success;
    private int boardCount;
    private int scannedPosts;
    private int updatedPosts;
    private int batchSize;
    private List<String> failedBoards = new ArrayList<>();
    private String error;
}
