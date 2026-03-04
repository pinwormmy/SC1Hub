package com.sc1hub.assistant.dto;

import com.sc1hub.assistant.search.AssistantSearchTermsIndexService;
import lombok.Data;

@Data
public class AssistantSearchTermsStatusResponseDTO {
    private boolean success;
    private AssistantSearchTermsIndexService.Status searchTerms;
    private String error;
}
