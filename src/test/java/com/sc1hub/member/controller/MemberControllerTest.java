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
}
