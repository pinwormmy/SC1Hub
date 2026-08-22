package com.sc1hub.common.interceptor;

import com.sc1hub.member.dto.MemberDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.HttpServletResponse;

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
    void adminInterceptor_allowsContentApiBearerTokenWithoutSession() throws Exception {
        AdminInterceptor interceptor = new AdminInterceptor();
        ReflectionTestUtils.setField(interceptor, "contentApiToken", "test-content-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/content/images");
        request.addHeader("Authorization", "Bearer test-content-token");

        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(proceed);
        assertNull(request.getSession(false));
    }

    @Test
    void adminInterceptor_doesNotAllowContentTokenForOtherAdminApis() throws Exception {
        AdminInterceptor interceptor = new AdminInterceptor();
        ReflectionTestUtils.setField(interceptor, "contentApiToken", "test-content-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/chat/sanctions");
        request.addHeader("Authorization", "Bearer test-content-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertFalse(proceed);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
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
