package com.sc1hub.common.interceptor;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlainHttpRedirectFilterTest {

    private final PlainHttpRedirectFilter filter = new PlainHttpRedirectFilter(true, "https://sc1hub.com/", 300);

    /** 카페24 프록시를 거친 평문 HTTP 요청의 모양: 스킴 http, X-Real-IP 있음, X-Forwarded-Proto 없음. */
    private MockHttpServletRequest plainHttpRequest(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setScheme("http");
        request.setSecure(false);
        request.setServerName("sc1hub.com");
        request.addHeader("Host", "sc1hub.com");
        request.addHeader("X-Real-IP", "203.0.113.5");
        request.addHeader("X-Forwarded-For", "203.0.113.5");
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void plainHttpReadIsRedirectedToHttpsWithQueryAndMarkerCookie() throws Exception {
        MockHttpServletRequest request = plainHttpRequest("GET", "/boards/pvstboard");
        request.setQueryString("recentPage=2");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertEquals(301, response.getStatus());
        assertEquals("https://sc1hub.com/boards/pvstboard?recentPage=2", response.getHeader("Location"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        Cookie marker = response.getCookie(PlainHttpRedirectFilter.MARKER_COOKIE);
        assertNotNull(marker);
        assertTrue(marker.getSecure());
        assertTrue(marker.isHttpOnly());
        assertNull(chain.getRequest(), "리다이렉트된 요청은 뒤로 넘기지 않는다");
    }

    @Test
    void tlsTerminatedRequestWithoutRealIpPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setScheme("http");
        request.setServerName("sc1hub.com");
        request.addHeader("Host", "sc1hub.com");
        request.addHeader("X-Forwarded-For", "203.0.113.5"); // HTTPS 경로는 X-Real-IP 가 없다
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void writesAndNonCanonicalHostsAndMarkedRequestsAreNeverRedirected() throws Exception {
        MockHttpServletRequest post = plainHttpRequest("POST", "/submitLogin");
        assertEquals(200, run(post, new MockFilterChain()).getStatus());

        MockHttpServletRequest healthCheck = plainHttpRequest("GET", "/");
        healthCheck.removeHeader("Host");
        healthCheck.addHeader("Host", "127.0.0.1:8645");
        assertEquals(200, run(healthCheck, new MockFilterChain()).getStatus());

        MockHttpServletRequest marked = plainHttpRequest("GET", "/");
        marked.setCookies(new Cookie(PlainHttpRedirectFilter.MARKER_COOKIE, "1"));
        assertEquals(200, run(marked, new MockFilterChain()).getStatus());

        MockHttpServletRequest declaredHttps = plainHttpRequest("GET", "/");
        declaredHttps.addHeader("X-Forwarded-Proto", "https");
        assertEquals(200, run(declaredHttps, new MockFilterChain()).getStatus());
    }

    @Test
    void disabledFilterPassesEverything() throws Exception {
        PlainHttpRedirectFilter disabled = new PlainHttpRedirectFilter(false, "https://sc1hub.com", 300);
        MockHttpServletResponse response = new MockHttpServletResponse();
        disabled.doFilter(plainHttpRequest("GET", "/"), response, new MockFilterChain());
        assertEquals(200, response.getStatus());
    }

    @Test
    void redirectStormTripsTheBreaker() throws Exception {
        PlainHttpRedirectFilter tight = new PlainHttpRedirectFilter(true, "https://sc1hub.com", 3);
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            tight.doFilter(plainHttpRequest("GET", "/"), response, new MockFilterChain());
            assertEquals(301, response.getStatus());
        }
        MockHttpServletResponse fourth = new MockHttpServletResponse();
        tight.doFilter(plainHttpRequest("GET", "/"), fourth, new MockFilterChain());
        assertEquals(200, fourth.getStatus(), "분당 상한을 넘기면 리다이렉트를 멈추고 통과시킨다");
    }
}
