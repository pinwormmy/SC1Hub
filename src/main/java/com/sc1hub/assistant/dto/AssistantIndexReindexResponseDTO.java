package com.sc1hub.assistant.dto;

import lombok.Data;

@Data
public class AssistantIndexReindexResponseDTO {
    private boolean success;
    private AssistantRagReindexResponseDTO rag;
    private AssistantSearchTermsReindexResponseDTO searchTerms;
    private String error;
}

