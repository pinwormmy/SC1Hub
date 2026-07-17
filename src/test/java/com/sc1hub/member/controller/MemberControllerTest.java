package com.sc1hub.member.controller;

import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.service.EmailService;
import com.sc1hub.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    @Mock
    private MemberService memberService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private MemberController controller;

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

        String view = controller.submitLogin(session, credentials, new ExtendedModelMap());

        assertEquals("redirect:/", view);
        assertEquals(loggedIn, session.getAttribute("member"));
    }

    @Test
    void checkUniqueId_rejectsBlankIdWithoutQueryingDatabase() throws Exception {
        ResponseEntity<String> response = controller.checkUniqueId(" ");

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

        String view = controller.submitModifyMyInfo(submitted, session);

        assertEquals("myPage", view);
        assertEquals("owner", submitted.getId());
        assertEquals(refreshed, session.getAttribute("member"));
        verify(memberService).submitModifyMyInfo(submitted);
    }
}
