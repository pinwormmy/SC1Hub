package com.sc1hub.assistant.dto;

import lombok.Data;

@Data
public class AssistantIndexUpdateResponseDTO {
    private boolean success;
    private AssistantRagUpdateResponseDTO rag;
    private AssistantSearchTermsReindexResponseDTO searchTerms;
    private String error;
}

