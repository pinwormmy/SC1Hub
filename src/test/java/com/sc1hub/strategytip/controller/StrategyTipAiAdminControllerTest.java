package com.sc1hub.strategytip.controller;

import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.strategytip.dto.StrategyTipAiDraftDTO;
import com.sc1hub.strategytip.dto.StrategyTipAiStatusDTO;
import com.sc1hub.strategytip.service.StrategyTipAiDraftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyTipAiAdminControllerTest {

    private static final String REDIRECT_VIEW = "redirect:/adminPage/strategy-tips/ai";
    private static final String CSRF_TOKEN = "test-csrf-token";

    @Mock
    private StrategyTipAiDraftService draftService;

    private StrategyTipAiAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new StrategyTipAiAdminController(draftService);
    }

    @Test
    void reviewPage_populatesReviewModelAndReturnsAdminView() {
        List<StrategyTipAiDraftDTO> pending = Collections.singletonList(new StrategyTipAiDraftDTO());
        List<StrategyTipAiDraftDTO> recent = Collections.singletonList(new StrategyTipAiDraftDTO());
        StrategyTipAiStatusDTO status = new StrategyTipAiStatusDTO();
        when(draftService.getPendingDrafts()).thenReturn(pending);
        when(draftService.getRecentDrafts(30)).thenReturn(recent);
        when(draftService.getStatus()).thenReturn(status);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpSession session = new MockHttpSession();

        String view = controller.reviewPage(model, session);

        assertEquals("adminStrategyTipAi", view);
        assertSame(pending, model.get("pendingDrafts"));
        assertSame(recent, model.get("recentDrafts"));
        assertSame(status, model.get("aiStatus"));
        assertNotNull(model.get("csrfToken"));
        assertSame(model.get("csrfToken"), session.getAttribute("strategyTipAiCsrfToken"));
    }

    @Test
    void generate_addsResultMessageAndRedirectsToReviewPage() {
        when(draftService.generateDailyDrafts())
                .thenReturn(StrategyTipAiDraftService.GenerationResult.created(3));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        MockHttpSession session = adminSession("operator");

        String view = controller.generate(CSRF_TOKEN, session, redirect);

        assertEquals(REDIRECT_VIEW, view);
        assertEquals("AI 한줄 공략 초안 3건을 생성했습니다.",
                redirect.getFlashAttributes().get("msg"));
    }

    @Test
    void generate_addsSafeFailureMessageAndStillRedirects() {
        when(draftService.generateDailyDrafts()).thenThrow(new IllegalStateException("내부 근거 부족"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        MockHttpSession session = adminSession("operator");

        String view = controller.generate(CSRF_TOKEN, session, redirect);

        assertEquals(REDIRECT_VIEW, view);
        assertEquals("내부 근거 부족", redirect.getFlashAttributes().get("msg"));
    }

    @Test
    void approve_passesAdminIdAndAddsPublishedTipMessage() {
        MockHttpSession session = adminSession("operator");
        when(draftService.approve(17L, "t_vs_z", "검수 후 확정한 한줄 공략입니다.", "operator"))
                .thenReturn(203);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.approve(17L, "t_vs_z",
                "검수 후 확정한 한줄 공략입니다.", CSRF_TOKEN, session, redirect);

        assertEquals(REDIRECT_VIEW, view);
        assertEquals("AI 초안을 승인해 한줄 공략 #203으로 공개했습니다.",
                redirect.getFlashAttributes().get("msg"));
        verify(draftService).approve(
                17L, "t_vs_z", "검수 후 확정한 한줄 공략입니다.", "operator");
    }

    @Test
    void reject_passesAdminIdAndAddsConfirmationMessage() {
        MockHttpSession session = adminSession("operator");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.reject(18L, CSRF_TOKEN, session, redirect);

        assertEquals(REDIRECT_VIEW, view);
        assertEquals("AI 초안을 반려했습니다.", redirect.getFlashAttributes().get("msg"));
        verify(draftService).reject(18L, "operator");
    }

    @Test
    void approve_doesNotCallServiceForNonAdminSessionAndAddsErrorFlash() {
        MemberDTO member = new MemberDTO();
        member.setId("ordinary-user");
        member.setGrade(2);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("member", member);
        session.setAttribute("strategyTipAiCsrfToken", CSRF_TOKEN);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.approve(19L, "t_vs_z",
                "공개되면 안 되는 내용입니다.", CSRF_TOKEN, session, redirect);

        assertEquals(REDIRECT_VIEW, view);
        assertTrue(String.valueOf(redirect.getFlashAttributes().get("msg")).contains("관리자"));
        verifyNoInteractions(draftService);
    }

    @Test
    void generate_rejectsMissingCsrfTokenWithoutCallingService() {
        MockHttpSession session = adminSession("operator");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.generate("wrong-token", session, redirect);

        assertEquals(REDIRECT_VIEW, view);
        assertTrue(String.valueOf(redirect.getFlashAttributes().get("msg")).contains("토큰"));
        verifyNoInteractions(draftService);
    }

    @Test
    void generate_rejectsNonAdminSessionEvenWithValidCsrfToken() {
        MemberDTO member = new MemberDTO();
        member.setId("ordinary-user");
        member.setGrade(2);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("member", member);
        session.setAttribute("strategyTipAiCsrfToken", CSRF_TOKEN);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.generate(CSRF_TOKEN, session, redirect);

        assertEquals(REDIRECT_VIEW, view);
        assertTrue(String.valueOf(redirect.getFlashAttributes().get("msg")).contains("관리자"));
        verifyNoInteractions(draftService);
    }

    private MockHttpSession adminSession(String id) {
        MemberDTO admin = new MemberDTO();
        admin.setId(id);
        admin.setGrade(3);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("member", admin);
        session.setAttribute("strategyTipAiCsrfToken", CSRF_TOKEN);
        return session;
    }
}
