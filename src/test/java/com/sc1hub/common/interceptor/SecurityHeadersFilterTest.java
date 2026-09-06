package com.sc1hub.common.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void setsBaselineSecurityHeadersOnEveryResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        String csp = response.getHeader("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("frame-ancestors 'self'"));
        assertTrue(csp.contains("object-src 'none'"));
        assertNotNull(response.getHeader("Strict-Transport-Security"));
        // 일반 페이지는 캐시 금지 표시를 붙이지 않는다.
        assertNull(response.getHeader("Cache-Control"));
    }

    @Test
    void marksSensitivePagesNoStore() throws Exception {
        for (String uri : new String[]{"/adminPage", "/myPage", "/modifyMyInfo",
                "/modifyMemberByAdmin", "/api/admin/chat/sanctions"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals("no-store", response.getHeader("Cache-Control"), uri);
        }
    }
}
