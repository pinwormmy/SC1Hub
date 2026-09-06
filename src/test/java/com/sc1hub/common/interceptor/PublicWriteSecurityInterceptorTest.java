package com.sc1hub.common.interceptor;

import com.sc1hub.member.dto.MemberDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class PublicWriteSecurityInterceptorTest {
    private final ContentApiTokenAuthenticator auth = new ContentApiTokenAuthenticator("test-token");

    @Test
    void containmentBlocksGuestAndMemberWritesButKeepsReadsAndLogin() throws Exception {
        var guard = new PublicWriteSecurityInterceptor(auth, false);
        for (String path : new String[]{"/api/chat/messages", "/boards/funboard/submitPost",
                "/boards/tipboard/addComment", "/imageUpload", "/submitSignUp", "/strategy-tips"}) {
            var request = new MockHttpServletRequest("POST", path);
            var response = new MockHttpServletResponse();
            assertFalse(guard.preHandle(request, response, null), path);
            assertEquals(503, response.getStatus());
            MemberDTO member = new MemberDTO();
            member.setGrade(1);
            request.getSession().setAttribute("member", member);
            request.addHeader("Origin", "http://localhost");
            assertFalse(guard.preHandle(request, new MockHttpServletResponse(), null));
        }
        for (String path : new String[]{"/", "/api/chat/messages", "/boards/funboard/readPost"}) {
            assertTrue(guard.preHandle(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), null));
        }
        assertTrue(guard.preHandle(new MockHttpServletRequest("POST", "/submitLogin"), new MockHttpServletResponse(), null));
        assertTrue(guard.preHandle(new MockHttpServletRequest("POST", "/boards/funboard/showCommentList"), new MockHttpServletResponse(), null));
    }

    @Test
    void rejectsCrossOriginAdminRequestsAndMissingOriginSessionRequests() throws Exception {
        var guard = new PublicWriteSecurityInterceptor(auth, false);
        var request = new MockHttpServletRequest("POST", "/submitModifyMemberByAdmin");
        MemberDTO admin = new MemberDTO();
        admin.setGrade(3);
        request.getSession().setAttribute("member", admin);
        assertFalse(guard.preHandle(request, new MockHttpServletResponse(), null));
        request.addHeader("Origin", "https://evil.example");
        assertFalse(guard.preHandle(request, new MockHttpServletResponse(), null));
        request.removeHeader("Origin");
        request.addHeader("Origin", "http://localhost");
        assertTrue(guard.preHandle(request, new MockHttpServletResponse(), null));
    }

    @Test
    void permitsAuthenticatedIncidentMaintenanceApi() throws Exception {
        var guard = new PublicWriteSecurityInterceptor(auth, false);
        var request = new MockHttpServletRequest("DELETE", "/api/admin/chat/messages/1");
        request.addHeader("Authorization", "Bearer test-token");
        assertTrue(guard.preHandle(request, new MockHttpServletResponse(), null));
    }

    @Test
    void limitsMembersAcrossChangingSessionsAndSpoofedIpHeaders() throws Exception {
        var guard = new PublicWriteSecurityInterceptor(auth, true);
        for (int i = 0; i < 11; i++) {
            var request = new MockHttpServletRequest("POST", "/api/chat/messages");
            request.setRemoteAddr("192.0.2.1");
            request.addHeader("X-Forwarded-For", "192.0.2." + i);
            request.addHeader("Origin", "http://localhost");
            MemberDTO member = new MemberDTO();
            member.setId("same-member");
            member.setGrade(1);
            request.getSession().setAttribute("member", member);
            var response = new MockHttpServletResponse();
            assertEquals(i < 10, guard.preHandle(request, response, null));
            if (i == 10) assertEquals(429, response.getStatus());
        }
    }
}
