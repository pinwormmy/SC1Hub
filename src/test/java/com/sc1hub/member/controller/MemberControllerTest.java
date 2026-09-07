package com.sc1hub.member.controller;

import com.sc1hub.common.security.AttackContentDetector;
import com.sc1hub.common.security.OffenderTracker;
import com.sc1hub.common.security.WriteRateLimiter;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.service.LoginAttemptGuard;
import com.sc1hub.member.service.MemberService;
import com.sc1hub.member.service.MemberSessionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    @Mock
    private MemberService memberService;

    @Mock
    private LoginAttemptGuard loginAttemptGuard;

    @Mock
    private MemberSessionRegistry sessionRegistry;

    @Mock
    private OffenderTracker offenderTracker;

    @Spy
    private AttackContentDetector attackContentDetector = new AttackContentDetector();

    @Mock
    private WriteRateLimiter rateLimiter;

    @InjectMocks
    private MemberController controller;

    @Test
    void submitSignUp_rejectsHoneypotFilledRequestsWithoutTouchingTheService() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/submitSignUp");
        request.setParameter(MemberController.SIGNUP_HONEYPOT_FIELD, "http://spam.example");
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.submitSignUp(request, new MemberDTO(), new MockHttpSession(), model);

        assertEquals("alert", view);
        verifyNoInteractions(memberService);
        verify(offenderTracker).strike(any(), anyBoolean(), isNull(), isNull(), anyString());
    }

    @Test
    void submitSignUp_rejectsInjectedProfileFieldsAndSanctionsTheAddress() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/submitSignUp");
        request.addHeader("X-Forwarded-For", "203.0.113.88");
        MemberDTO signup = new MemberDTO();
        signup.setId("newbie1");
        signup.setNickName("<script>alert(1)</script>");
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.submitSignUp(request, signup, new MockHttpSession(), model);

        assertEquals("alert", view);
        verifyNoInteractions(memberService);
        verify(offenderTracker).attackDetected(org.mockito.ArgumentMatchers.eq("203.0.113.88"), anyBoolean(), isNull(), isNull(),
                org.mockito.ArgumentMatchers.argThat(AttackContentDetector.Verdict::isHigh));
    }

    @Test
    void login_rejectsExternalReturnUrl() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "https://evil.example/phishing");

        controller.login(request);

        assertEquals("/", request.getSession().getAttribute("pageBeforeLogin"));
    }

    @Test
    void login_keepsCanonicalSitePathAndQuery() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "https://sc1hub.com/boards/tipboard?recentPage=2");

        controller.login(request);

        assertEquals("/boards/tipboard?recentPage=2",
                request.getSession().getAttribute("pageBeforeLogin"));
    }

    @Test
    void submitLogin_defaultsToHomeWhenReturnPathIsMissing() throws Exception {
        MemberDTO credentials = new MemberDTO();
        credentials.setId("user");
        MemberDTO loggedIn = new MemberDTO();
        loggedIn.setId("user");
        when(memberService.checkLoginData(credentials)).thenReturn(loggedIn);
        MockHttpSession session = new MockHttpSession();

        String view = controller.submitLogin(new MockHttpServletRequest(), session, credentials,
                new ExtendedModelMap());

        assertEquals("redirect:/", view);
        assertEquals(loggedIn, session.getAttribute("member"));
        verify(loginAttemptGuard).reset("user");
    }

    @Test
    void submitLogin_rotatesSessionIdAndRegistersSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        String beforeId = session.getId();
        MemberDTO credentials = new MemberDTO();
        credentials.setId("user");
        MemberDTO loggedIn = new MemberDTO();
        loggedIn.setId("user");
        when(memberService.checkLoginData(credentials)).thenReturn(loggedIn);

        controller.submitLogin(request, session, credentials, new ExtendedModelMap());

        assertNotEquals(beforeId, request.getSession().getId(), "세션 고정 방지를 위해 로그인 시 세션 ID를 교체해야 한다");
        verify(sessionRegistry).register("user", session);
    }

    @Test
    void submitLogin_recordsFailureOnWrongCredentials() throws Exception {
        MemberDTO credentials = new MemberDTO();
        credentials.setId("user");
        when(memberService.checkLoginData(credentials)).thenReturn(null);

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.submitLogin(new MockHttpServletRequest(), new MockHttpSession(),
                credentials, model);

        assertEquals("login", view);
        verify(loginAttemptGuard).recordFailure("user");
    }

    @Test
    void submitLogin_refusesLockedAccountWithoutCheckingCredentials() throws Exception {
        MemberDTO credentials = new MemberDTO();
        credentials.setId("user");
        when(loginAttemptGuard.isBlocked("user")).thenReturn(true);

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.submitLogin(new MockHttpServletRequest(), new MockHttpSession(),
                credentials, model);

        assertEquals("login", view);
        assertEquals("로그인 시도가 너무 많습니다. 5분 후 다시 시도해 주세요.", model.getAttribute("message"));
        verifyNoInteractions(memberService);
    }

    @Test
    void checkUniqueId_rejectsBlankIdWithoutQueryingDatabase() throws Exception {
        ResponseEntity<String> response = controller.checkUniqueId(" ", new MockHttpServletRequest());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("", response.getBody());
        verifyNoInteractions(memberService);
    }

    @Test
    void extendLogin_rejectsExpiredSessionWithoutCreatingNewSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/extendLogin");

        ResponseEntity<Void> response = controller.extendLogin(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(request.getSession(false));
    }

    @Test
    void extendLogin_refreshesAuthenticatedSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/extendLogin");
        MockHttpSession session = new MockHttpSession();
        MemberDTO member = new MemberDTO();
        member.setId("user");
        session.setAttribute("member", member);
        session.setMaxInactiveInterval(60);
        request.setSession(session);

        ResponseEntity<Void> response = controller.extendLogin(request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(30 * 60, session.getMaxInactiveInterval());
    }

    @Test
    void submitModifyMyInfo_usesAuthenticatedMemberId() throws Exception {
        MemberDTO authenticatedMember = new MemberDTO();
        authenticatedMember.setId("owner");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("member", authenticatedMember);
        MemberDTO submitted = new MemberDTO();
        submitted.setId("another-member");
        submitted.setPw("new-password");
        MemberDTO refreshed = new MemberDTO();
        refreshed.setId("owner");
        when(memberService.checkLoginData(any(MemberDTO.class))).thenReturn(refreshed);

        String view = controller.submitModifyMyInfo(submitted, "current-password", session, new ExtendedModelMap());

        assertEquals("myPage", view);
        assertEquals("owner", submitted.getId());
        assertEquals(refreshed, session.getAttribute("member"));
        verify(memberService).submitModifyMyInfo(submitted);
    }

    @Test
    void submitModifyMyInfo_rejectsWrongOrMissingCurrentPassword() throws Exception {
        MemberDTO authenticatedMember = new MemberDTO();
        authenticatedMember.setId("owner");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("member", authenticatedMember);
        MemberDTO submitted = new MemberDTO();
        submitted.setPw("new-password");
        when(memberService.checkLoginData(any(MemberDTO.class))).thenReturn(null);

        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("alert", controller.submitModifyMyInfo(submitted, "wrong", session, model));
        assertEquals(MemberController.CURRENT_PASSWORD_MISMATCH_MESSAGE, model.get("msg"));
        verify(loginAttemptGuard).recordFailure("owner");

        assertEquals("alert", controller.submitModifyMyInfo(submitted, null, session, new ExtendedModelMap()));
        verify(memberService, never()).submitModifyMyInfo(any(MemberDTO.class));
        assertEquals(authenticatedMember, session.getAttribute("member"));
    }

    @Test
    void deleteMyAccount_requiresCurrentPassword() throws Exception {
        MemberDTO authenticatedMember = new MemberDTO();
        authenticatedMember.setId("owner");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("member", authenticatedMember);
        when(memberService.checkLoginData(any(MemberDTO.class))).thenReturn(null);

        String body = controller.deleteMyAccount(request, "wrong");

        assertTrue(body.contains("\"success\": false"));
        verify(memberService, never()).deleteMember(anyString());
        verify(sessionRegistry, never()).invalidateMember(anyString());
    }

    @Test
    void logout_ignoresCrossSiteNavigation() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("member", new MemberDTO());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/logout");
        request.addHeader("Sec-Fetch-Site", "cross-site");

        assertEquals("redirect:/", controller.logout(request, session));
        assertFalse(session.isInvalid());

        // Sec-Fetch-Site 가 없는 옛 브라우저는 Referer 로 판단한다.
        MockHttpServletRequest legacy = new MockHttpServletRequest("GET", "/logout");
        legacy.addHeader("Referer", "https://evil.example/trap");
        assertEquals("redirect:/", controller.logout(legacy, session));
        assertFalse(session.isInvalid());
    }

    @Test
    void logout_invalidatesSessionForSameOriginAndDirectNavigation() {
        MockHttpSession fromSite = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/logout");
        request.addHeader("Sec-Fetch-Site", "same-origin");
        assertEquals("redirect:/", controller.logout(request, fromSite));
        assertTrue(fromSite.isInvalid());

        MockHttpSession typed = new MockHttpSession();
        assertEquals("redirect:/", controller.logout(new MockHttpServletRequest("GET", "/logout"), typed));
        assertTrue(typed.isInvalid());

        MockHttpSession referred = new MockHttpSession();
        MockHttpServletRequest legacy = new MockHttpServletRequest("GET", "/logout");
        legacy.addHeader("Referer", "https://sc1hub.com/boards/funboard");
        assertEquals("redirect:/", controller.logout(legacy, referred));
        assertTrue(referred.isInvalid());
    }

    @Test
    void checkUniqueId_rejectsMalformedIdsAndThrottlesRepeatedLookups() throws Exception {
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.checkUniqueId("Admin'--", new MockHttpServletRequest()).getStatusCode());
        verifyNoInteractions(memberService);

        MockHttpServletRequest proxied = new MockHttpServletRequest();
        proxied.addHeader("X-Forwarded-For", "203.0.113.9");
        when(rateLimiter.allow(eq("lookup-ip:203.0.113.9"), eq(MemberController.LOOKUPS_PER_IP_PER_10_MINUTES), anyLong()))
                .thenReturn(true, false);
        when(memberService.isUniqueId("validid")).thenReturn("0");

        assertEquals(HttpStatus.OK, controller.checkUniqueId("validid", proxied).getStatusCode());
        ResponseEntity<String> throttled = controller.checkUniqueId("validid", proxied);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, throttled.getStatusCode());
        verify(offenderTracker).strike(eq("203.0.113.9"), eq(true), isNull(), isNull(), anyString());
        verify(memberService, times(1)).isUniqueId("validid");
    }
}
