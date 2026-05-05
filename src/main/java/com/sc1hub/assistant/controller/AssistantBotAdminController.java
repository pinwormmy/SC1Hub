package com.sc1hub.assistant.controller;

import com.sc1hub.assistant.dto.AssistantBotDraftRequestDTO;
import com.sc1hub.assistant.dto.AssistantBotDraftResponseDTO;
import com.sc1hub.assistant.dto.AssistantBotHistoryDTO;
import com.sc1hub.assistant.dto.AssistantBotHistorySummaryDTO;
import com.sc1hub.assistant.dto.AssistantBotPublishResponseDTO;
import com.sc1hub.assistant.service.AssistantBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/assistant-bot")
@Slf4j
public class AssistantBotAdminController {

    private final AssistantBotService assistantBotService;

    public AssistantBotAdminController(AssistantBotService assistantBotService) {
        this.assistantBotService = assistantBotService;
    }

    @PostMapping(value = "/drafts", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssistantBotDraftResponseDTO> generateDraft(
            @RequestBody(required = false) AssistantBotDraftRequestDTO request) {
        AssistantBotDraftResponseDTO response = assistantBotService.generateDraft(request);
        if (response.getError() != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/drafts/{historyId}/publish", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssistantBotPublishResponseDTO> publishDraft(@PathVariable long historyId) {
        AssistantBotPublishResponseDTO response = assistantBotService.publishDraft(historyId);
        if (response.getError() != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/auto-publish/run", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssistantBotService.AutoPublishResult> runAutoPublish() {
        AssistantBotService.AutoPublishResult result = assistantBotService.autoPublishOnce();
        if (AssistantBotService.AutoPublishResult.OUTCOME_FAILED.equals(result.getOutcome())) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<AssistantBotHistoryDTO>> getHistory(
            @RequestParam(name = "days", defaultValue = "3") int days,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return ResponseEntity.ok(assistantBotService.getRecentHistory(days, limit));
    }

    @GetMapping(value = "/history/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<AssistantBotHistorySummaryDTO>> getHistorySummary(
            @RequestParam(name = "days", defaultValue = "3") int days) {
        return ResponseEntity.ok(assistantBotService.getHistorySummary(days));
    }
}
