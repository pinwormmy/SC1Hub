package com.sc1hub.assistant.controller;

import com.sc1hub.assistant.service.AssistantBotService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/adminPage/ops")
public class AssistantBotAdminPageController {

    private final AssistantBotService assistantBotService;

    public AssistantBotAdminPageController(AssistantBotService assistantBotService) {
        this.assistantBotService = assistantBotService;
    }

    @GetMapping
    public String statusPage(@RequestParam(name = "days", defaultValue = "14") int days,
                             @RequestParam(name = "limit", defaultValue = "20") int limit,
                             Model model) {
        model.addAttribute("autoPublishStatus", assistantBotService.getAutoPublishStatus());
        model.addAttribute("historySummary", assistantBotService.getHistorySummary(days));
        model.addAttribute("history", assistantBotService.getRecentHistory(days, limit));
        model.addAttribute("days", days);
        model.addAttribute("limit", limit);
        return "adminOps";
    }
}
