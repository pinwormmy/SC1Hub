package com.sc1hub.common.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalInterceptorTest {

    private final CanonicalInterceptor interceptor = new CanonicalInterceptor();

    @Test
    void preHandle_redirectsUppercaseBoardPath_forGet() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boards/pVsZBoard/readPost");
        request.setQueryString("postNum=12");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertFalse(proceed);
        assertEquals(HttpServletResponse.SC_MOVED_PERMANENTLY, response.getStatus());
        assertEquals("/boards/pvszboard/readPost?postNum=12", response.getHeader("Location"));
    }

    @Test
    void preHandle_setsCanonical_whenAlreadyNormalized() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boards/pvszboard/readPost");
        request.setQueryString("postNum=12");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
        assertEquals("https://sc1hub.com/boards/pvszboard/readPost?postNum=12", request.getAttribute("canonical"));
    }

    @Test
    void preHandle_doesNotRedirectReservedBoardEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boards/boardList");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
        assertEquals("https://sc1hub.com/boards/boardList", request.getAttribute("canonical"));
    }

    @Test
    void preHandle_doesNotRedirectForPostRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/boards/pVsZBoard/submitPost");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
        assertEquals("https://sc1hub.com/boards/pvszboard/submitPost", request.getAttribute("canonical"));
    }
}
