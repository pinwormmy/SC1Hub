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
                "/boards/promotionboard/movePost", "/boards/videoLinkBoard/movePost",
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
                "/boards/videolinkboard/submitPost", "/boards/funboard/submitPost"}) {
            var request = new MockHttpServletRequest("POST", path);
            ServletRequestPathUtils.parseAndCache(request);
            for (Object candidate : registry.values()) {
                if (candidate instanceof MappedInterceptor mapped && mapped.getInterceptor() == admin) {
                    assertFalse(mapped.matches(request), path + " must stay open to logged-in members");
                }
            }
        }
    }

    @Test
    void percentEncodedBoardWriteRoutesStayProtected() throws Exception {
        // 핸들러 매핑은 디코딩된 경로("submitPost")로 열리므로, 마지막 글자 하나만 인코딩한 요청도
        // 관리자·로그인 인터셉터가 똑같이 잡아야 한다. 예전 "/**/submitPost" 패턴은 파서가 거부해
        // 원본 URI 기준 AntPathMatcher 로 후퇴했고 이 요청을 놓쳤다.
        var admin = new AdminInterceptor(new ContentApiTokenAuthenticator(""));
        var boardLv = new BoardLvInterceptor();
        var config = new WebConfig(mock(VisitorCountInterceptor.class), mock(CanonicalInterceptor.class),
                boardLv, admin, new MemberLoginInterceptor());
        var registry = new ExposedRegistry();
        config.addInterceptors(registry);
        for (String path : new String[]{"/boards/noticeboard/submit%50ost", "/boards/noticeboard/%73ubmitPost",
                "/boards/tipboard/writePos%74", "/boards/tipboard/modifyPos%74", "/boards/tipboard/modifyPost",
                "/boards/tipboard/submitModifyPos%74", "/boards/tipboard/deletePos%74", "/boards/tipboard/deletePost",
                "/boards/pvstboard/move%50ost", "/boards/tipboard/submitPost;x=y"}) {
            var request = new MockHttpServletRequest("POST", path);
            ServletRequestPathUtils.parseAndCache(request);
            assertTrue(matches(registry, admin, request), path + " must stay admin-protected");
        }
        for (String path : new String[]{"/boards/promotionboard/writePos%74", "/boards/promotionboard/modifyPos%74",
                "/boards/promotionboard/deletePos%74", "/boards/promotionboard/deletePost"}) {
            var request = new MockHttpServletRequest("POST", path);
            ServletRequestPathUtils.parseAndCache(request);
            assertTrue(matches(registry, boardLv, request), path + " must still require login");
            assertFalse(matches(registry, admin, request), path + " stays open to members");
        }
    }

    private static boolean matches(ExposedRegistry registry, HandlerInterceptor interceptor, MockHttpServletRequest request) {
        for (Object candidate : registry.values()) {
            if (candidate instanceof MappedInterceptor mapped && mapped.getInterceptor() == interceptor
                    && mapped.matches(request)) {
                return true;
            }
        }
        return false;
    }

    private static class ExposedRegistry extends InterceptorRegistry {
        List<Object> values() { return getInterceptors(); }
    }
}
