package com.sc1hub.common.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
