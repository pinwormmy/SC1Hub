package com.sc1hub.assistant.dto;

import com.sc1hub.assistant.rag.AssistantRagSearchService;
import com.sc1hub.assistant.search.AssistantSearchTermsIndexService;
import lombok.Data;

@Data
public class AssistantIndexStatusResponseDTO {
    private boolean success;
    private AssistantRagSearchService.Status rag;
    private AssistantSearchTermsIndexService.Status searchTerms;
    private String error;
}

