package com.sc1hub.buildtimer.controller;

import com.sc1hub.buildtimer.service.BuildTimerService;
import com.sc1hub.seo.SeoMetadataService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class BuildTimerController {

    private final BuildTimerService buildTimerService;
    private final SeoMetadataService seoMetadataService;

    public BuildTimerController(BuildTimerService buildTimerService, SeoMetadataService seoMetadataService) {
        this.buildTimerService = buildTimerService;
        this.seoMetadataService = seoMetadataService;
    }

    @GetMapping("/build-timer")
    public String showBuildTimer(Model model, HttpServletRequest request) {
        model.addAttribute("builds", buildTimerService.getBuilds());
        model.addAttribute("buildsJson", buildTimerService.getBuildsJson());
        seoMetadataService.applyContentPage(
                model,
                request,
                "스타1 빌드오더 연습 타이머 (beta) | 종족전별 정석 빌드 음성 안내",
                "테저전 투배럭 아카, 저테전 투해처리 뮤탈, 프테전 23넥 등 정석 빌드를 인구수 순서대로 음성 안내받으며 연습하는 스타크래프트1 빌드 타이머 베타 버전.");
        return "buildTimer";
    }
}
