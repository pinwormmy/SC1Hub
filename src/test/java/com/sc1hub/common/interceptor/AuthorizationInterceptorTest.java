package com.sc1hub.common.interceptor;

import com.sc1hub.member.dto.MemberDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationInterceptorTest {

    @Test
    void adminInterceptor_returns403WithoutCreatingSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/adminPage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = new AdminInterceptor().preHandle(request, response, new Object());

        assertFalse(proceed);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertNull(request.getSession(false));
    }

    @Test
    void boardLevelInterceptor_returns403WithoutCreatingSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boards/tipboard/writePost");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = new BoardLvInterceptor().preHandle(request, response, new Object());

        assertFalse(proceed);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertNull(request.getSession(false));
    }

    @Test
    void memberLoginInterceptor_redirectsPageRequestWithoutCreatingSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/myPage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = new MemberLoginInterceptor().preHandle(request, response, new Object());

        assertFalse(proceed);
        assertEquals(HttpServletResponse.SC_FOUND, response.getStatus());
        assertEquals("/login", response.getRedirectedUrl());
        assertNull(request.getSession(false));
    }

    @Test
    void memberLoginInterceptor_returns401ForExpiredFormSubmission() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/submitModifyMyInfo");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = new MemberLoginInterceptor().preHandle(request, response, new Object());

        assertFalse(proceed);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertNull(request.getSession(false));
    }

    @Test
    void memberLoginInterceptor_allowsAuthenticatedMember() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/myPage");
        MemberDTO member = new MemberDTO();
        member.setId("member");
        request.getSession().setAttribute("member", member);

        boolean proceed = new MemberLoginInterceptor().preHandle(
                request, new MockHttpServletResponse(), new Object());

        assertTrue(proceed);
    }
}
