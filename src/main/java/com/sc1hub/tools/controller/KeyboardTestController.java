package com.sc1hub.tools.controller;

import com.sc1hub.seo.SeoMetadataService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class KeyboardTestController {

    private final SeoMetadataService seoMetadataService;

    public KeyboardTestController(SeoMetadataService seoMetadataService) {
        this.seoMetadataService = seoMetadataService;
    }

    @GetMapping("/keyboard-test")
    public String showKeyboardTest(Model model, HttpServletRequest request) {
        seoMetadataService.applyContentPage(
                model,
                request,
                "스타1 키보드 동시입력·키 씹힘 테스트 | 롤오버·고스팅 확인",
                "스타크래프트1 부대지정(Shift+1+A), 화면지정(F2+생산) 조합에서 키가 씹히는지 브라우저에서 바로 확인하는 키보드 동시입력(롤오버)·고스팅 테스트기.");
        return "keyboardTest";
    }
}
