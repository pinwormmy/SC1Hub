package com.sc1hub.assistant.controller;

import com.sc1hub.assistant.dto.AssistantBotAutoPublishStatusDTO;
import com.sc1hub.assistant.dto.AssistantBotHistoryDTO;
import com.sc1hub.assistant.dto.AssistantBotHistorySummaryDTO;
import com.sc1hub.assistant.service.AssistantBotService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/adminPage/ops")
public class AssistantBotAdminStatusController {

    private final AssistantBotService assistantBotService;

    public AssistantBotAdminStatusController(AssistantBotService assistantBotService) {
        this.assistantBotService = assistantBotService;
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssistantBotAutoPublishStatusDTO> getAutoPublishStatus() {
        return ResponseEntity.ok(assistantBotService.getAutoPublishStatus());
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
