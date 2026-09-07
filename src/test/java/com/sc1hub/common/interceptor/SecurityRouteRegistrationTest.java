package com.sc1hub.common.interceptor;

import com.sc1hub.common.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityRouteRegistrationTest {
    @Test
    void sensitiveSubmissionAndEveryBoardMoveHaveAdminChecks() throws Exception {
        var admin = new AdminInterceptor(new ContentApiTokenAuthenticator(""));
        var config = new WebConfig(mock(VisitorCountInterceptor.class), mock(CanonicalInterceptor.class),
                new BoardLvInterceptor(), admin, new MemberLoginInterceptor());
        var registry = new ExposedRegistry();
        config.addInterceptors(registry);
        for (String path : new String[]{"/submitModifyMemberByAdmin", "/boards/noticeboard/submitPost",
                "/boards/tipboard/submitModifyPost", "/boards/funboard/movePost",
                "/boards/supportboard/movePost", "/boards/videoLinkBoard/movePost",
                "/api/admin/assistant-publisher/drafts/1/publish",
                "/api/admin/assistant-publisher/auto-publish/run-all", "/api/admin/chat/sanctions"}) {
            var request = new MockHttpServletRequest("POST", path);
            ServletRequestPathUtils.parseAndCache(request);
            boolean protectedByAdmin = false;
            for (Object candidate : registry.values()) {
                if (candidate instanceof MappedInterceptor mapped && mapped.getInterceptor() == admin
                        && mapped.matches(request)) {
                    protectedByAdmin = true;
                    var response = new MockHttpServletResponse();
                    assertFalse(mapped.preHandle(request, response, null));
                    assertEquals(403, response.getStatus());
                }
            }
            assertTrue(protectedByAdmin, path);
        }
    }

    @Test
    void memberWritableBoardsAreNotAdminProtectedUnderTheirCanonicalLowercaseUrls() throws Exception {
        var admin = new AdminInterceptor(new ContentApiTokenAuthenticator(""));
        var config = new WebConfig(mock(VisitorCountInterceptor.class), mock(CanonicalInterceptor.class),
                new BoardLvInterceptor(), admin, new MemberLoginInterceptor());
        var registry = new ExposedRegistry();
        config.addInterceptors(registry);
        for (String path : new String[]{"/boards/promotionboard/writePost", "/boards/promotionboard/submitPost",
                "/boards/supportboard/submitPost", "/boards/videolinkboard/submitPost", "/boards/funboard/submitPost"}) {
            var request = new MockHttpServletRequest("POST", path);
            ServletRequestPathUtils.parseAndCache(request);
            for (Object candidate : registry.values()) {
                if (candidate instanceof MappedInterceptor mapped && mapped.getInterceptor() == admin) {
                    assertFalse(mapped.matches(request), path + " must stay open to logged-in members");
                }
            }
        }
    }

    private static class ExposedRegistry extends InterceptorRegistry {
        List<Object> values() { return getInterceptors(); }
    }
}
