package com.sc1hub.strategytip.controller;

import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.strategytip.service.StrategyTipAiDraftService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Controller
@RequestMapping("/adminPage/strategy-tips/ai")
public class StrategyTipAiAdminController {

    private static final String REDIRECT_URL = "redirect:/adminPage/strategy-tips/ai";
    private static final String CSRF_SESSION_ATTRIBUTE = "strategyTipAiCsrfToken";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StrategyTipAiDraftService draftService;

    public StrategyTipAiAdminController(StrategyTipAiDraftService draftService) {
        this.draftService = draftService;
    }

    @GetMapping
    public String reviewPage(Model model, HttpSession session) {
        model.addAttribute("pendingDrafts", draftService.getPendingDrafts());
        model.addAttribute("recentDrafts", draftService.getRecentDrafts(30));
        model.addAttribute("aiStatus", draftService.getStatus());
        model.addAttribute("csrfToken", getOrCreateCsrfToken(session));
        return "adminStrategyTipAi";
    }

    @PostMapping("/generate")
    public String generate(@RequestParam String csrfToken,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        try {
            verifyCsrfToken(session, csrfToken);
            StrategyTipAiDraftService.GenerationResult result = draftService.generateDailyDrafts();
            redirectAttributes.addFlashAttribute("msg", result.getMessage());
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("msg", safeMessage(e, "AI 초안 생성에 실패했습니다."));
        }
        return REDIRECT_URL;
    }

    @PostMapping("/approve")
    public String approve(@RequestParam long draftId,
                          @RequestParam String category,
                          @RequestParam String content,
                          @RequestParam String csrfToken,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        try {
            verifyCsrfToken(session, csrfToken);
            int tipNum = draftService.approve(draftId, category, content, reviewerId(session));
            redirectAttributes.addFlashAttribute("msg",
                    "AI 초안을 승인해 한줄 공략 #" + tipNum + "으로 공개했습니다.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("msg", safeMessage(e, "AI 초안 승인에 실패했습니다."));
        }
        return REDIRECT_URL;
    }

    @PostMapping("/reject")
    public String reject(@RequestParam long draftId,
                         @RequestParam String csrfToken,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        try {
            verifyCsrfToken(session, csrfToken);
            draftService.reject(draftId, reviewerId(session));
            redirectAttributes.addFlashAttribute("msg", "AI 초안을 반려했습니다.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("msg", safeMessage(e, "AI 초안 반려에 실패했습니다."));
        }
        return REDIRECT_URL;
    }

    private String reviewerId(HttpSession session) {
        MemberDTO member = session == null ? null : (MemberDTO) session.getAttribute("member");
        if (member == null || member.getGrade() != 3) {
            throw new IllegalArgumentException("관리자 로그인 정보를 확인해주세요.");
        }
        return member.getId();
    }

    private String getOrCreateCsrfToken(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("관리자 세션을 확인할 수 없습니다.");
        }
        Object existing = session.getAttribute(CSRF_SESSION_ATTRIBUTE);
        if (existing instanceof String && !((String) existing).isEmpty()) {
            return (String) existing;
        }
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        session.setAttribute(CSRF_SESSION_ATTRIBUTE, token);
        return token;
    }

    private void verifyCsrfToken(HttpSession session, String submittedToken) {
        Object expected = session == null ? null : session.getAttribute(CSRF_SESSION_ATTRIBUTE);
        if (!(expected instanceof String) || submittedToken == null
                || !MessageDigest.isEqual(((String) expected).getBytes(StandardCharsets.UTF_8),
                submittedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("요청 검증 토큰이 올바르지 않습니다. 페이지를 새로고침해주세요.");
        }
    }

    private String safeMessage(RuntimeException e, String fallback) {
        return e != null && e.getMessage() != null && !e.getMessage().trim().isEmpty()
                ? e.getMessage() : fallback;
    }
}
