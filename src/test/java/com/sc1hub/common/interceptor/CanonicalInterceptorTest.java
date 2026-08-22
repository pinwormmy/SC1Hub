package com.sc1hub.common.interceptor;

import com.sc1hub.seo.SeoUrlPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalInterceptorTest {

    private final CanonicalInterceptor interceptor = new CanonicalInterceptor(new SeoUrlPolicy());

    @Test
    void preHandle_redirectsUppercaseBoardPath_forGet() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boards/pVsZBoard/readPost");
        request.setQueryString("postNum=12");
        request.addParameter("postNum", "12");
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
        request.addParameter("postNum", "12");
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

    @Test
    void preHandle_removesSessionPathAndTrailingSlash() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/strategy-tips;jsessionid=ABC123/");
        request.setQueryString("recentPage=1&category=z_vs_t");
        request.addParameter("recentPage", "1");
        request.addParameter("category", "z_vs_t");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertFalse(proceed);
        assertEquals(HttpServletResponse.SC_MOVED_PERMANENTLY, response.getStatus());
        assertEquals("/strategy-tips?recentPage=1&category=z_vs_t", response.getHeader("Location"));
        assertEquals("https://sc1hub.com/strategy-tips?category=z_vs_t",
                request.getAttribute("canonical"));
    }

    @Test
    void preHandle_redirectsWwwHostToCanonicalOrigin() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boards/tvszboard/");
        request.addHeader("Host", "www.sc1hub.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertFalse(proceed);
        assertEquals("https://sc1hub.com/boards/tvszboard", response.getHeader("Location"));
    }

    @Test
    void preHandle_marksInternalSearchAsNoindex() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boards/tipboard");
        request.addParameter("keyword", "973 빌드");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
        assertEquals(SeoUrlPolicy.NOINDEX_FOLLOW, request.getAttribute("robots"));
        assertEquals(SeoUrlPolicy.NOINDEX_FOLLOW, response.getHeader("X-Robots-Tag"));
    }
}
