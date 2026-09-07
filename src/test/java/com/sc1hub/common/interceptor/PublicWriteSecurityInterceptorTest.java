package com.sc1hub.common.interceptor;

import com.sc1hub.chat.service.ChatModerationService;
import com.sc1hub.common.security.OffenderTracker;
import com.sc1hub.common.security.SecuritySwitches;
import com.sc1hub.common.security.WriteRateLimiter;
import com.sc1hub.member.dto.MemberDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.pattern.PathPatternParser;

import java.net.URI;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PublicWriteSecurityInterceptorTest {
    private final ContentApiTokenAuthenticator auth = new ContentApiTokenAuthenticator("test-token");
    private final ChatModerationService moderation = mock(ChatModerationService.class);
    private final OffenderTracker tracker = mock(OffenderTracker.class);

    private PublicWriteSecurityInterceptor guard(boolean publicWrites, boolean guestWrites) {
        return new PublicWriteSecurityInterceptor(auth, new SecuritySwitches(publicWrites, guestWrites),
                new WriteRateLimiter(), moderation, tracker);
    }

    private MemberDTO member(String id, int grade) {
        MemberDTO member = new MemberDTO();
        member.setId(id);
        member.setGrade(grade);
        return member;
    }

    private MockHttpServletRequest memberRequest(String path, MemberDTO member) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.getSession().setAttribute("member", member);
        request.addHeader("Origin", "http://localhost");
        return request;
    }

    @Test
    void containmentBlocksGuestAndMemberWritesButKeepsReadsAndLogin() throws Exception {
        var guard = guard(false, false);
        for (String path : new String[]{"/api/chat/messages", "/boards/funboard/submitPost",
                "/boards/tipboard/addComment", "/imageUpload", "/submitSignUp", "/strategy-tips",
                "/submitModifyMyInfo"}) {
            var request = new MockHttpServletRequest("POST", path);
            var response = new MockHttpServletResponse();
            assertFalse(guard.preHandle(request, response, null), path);
            assertEquals(503, response.getStatus());
            request.getSession().setAttribute("member", member("m", 1));
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
        var guard = guard(false, false);
        var request = new MockHttpServletRequest("POST", "/submitModifyMemberByAdmin");
        request.getSession().setAttribute("member", member("admin", 3));
        assertFalse(guard.preHandle(request, new MockHttpServletResponse(), null));
        request.addHeader("Origin", "https://evil.example");
        assertFalse(guard.preHandle(request, new MockHttpServletResponse(), null));
        request.removeHeader("Origin");
        request.addHeader("Origin", "http://localhost");
        assertTrue(guard.preHandle(request, new MockHttpServletResponse(), null));
    }

    @Test
    void permitsAuthenticatedIncidentMaintenanceApi() throws Exception {
        var guard = guard(false, false);
        var request = new MockHttpServletRequest("DELETE", "/api/admin/chat/messages/1");
        request.addHeader("Authorization", "Bearer test-token");
        assertTrue(guard.preHandle(request, new MockHttpServletResponse(), null));
    }

    @Test
    void limitsMembersAcrossChangingSessionsAndSpoofedIpHeaders() throws Exception {
        var guard = guard(true, false);
        for (int i = 0; i < 11; i++) {
            var request = memberRequest("/api/chat/messages", member("same-member", 1));
            request.setRemoteAddr("192.0.2.1");
            request.addHeader("X-Forwarded-For", "192.0.2." + i);
            var response = new MockHttpServletResponse();
            assertEquals(i < 10, guard.preHandle(request, response, null));
            if (i == 10) assertEquals(429, response.getStatus());
        }
    }

    @Test
    void containmentUsesActualMappedRouteForMatrixParametersAndEncodedPaths() throws Exception {
        var guard = guard(false, false);
        var mvc = standaloneSetup(new ContainmentProbeController())
                .setPatternParser(new PathPatternParser())
                .addInterceptors(guard)
                .build();
        for (String path : new String[]{"/api/chat;probe=1/messages", "/%61pi/chat/messages",
                "/submitSignUp;probe=1", "/submitSign%55p", "/imageUpload;probe=1",
                "/boards;probe=1/funboard/submitPost", "/%62oards/funboard/submitPost"}) {
            mvc.perform(post(URI.create(path))).andExpect(status().isServiceUnavailable());
        }
    }

    @Test
    void guestsMustLogInUnlessGuestWritesAreSwitchedOn() throws Exception {
        var response = new MockHttpServletResponse();
        assertFalse(guard(true, false).preHandle(new MockHttpServletRequest("POST", "/api/chat/messages"), response, null));
        assertEquals(401, response.getStatus());

        assertTrue(guard(true, true).preHandle(new MockHttpServletRequest("POST", "/api/chat/messages"),
                new MockHttpServletResponse(), null));
        // 가입은 비회원 쓰기 스위치와 무관하게 허용된다.
        assertTrue(guard(true, false).preHandle(new MockHttpServletRequest("POST", "/submitSignUp"),
                new MockHttpServletResponse(), null));
    }

    @Test
    void plainHttpWritesAndLoginsAreRefused() throws Exception {
        var guard = guard(true, false);
        var write = memberRequest("/boards/funboard/submitPost", member("m", 1));
        write.addHeader("X-Real-IP", "203.0.113.9");
        var response = new MockHttpServletResponse();
        assertFalse(guard.preHandle(write, response, null));
        assertEquals(403, response.getStatus());

        var login = new MockHttpServletRequest("POST", "/submitLogin");
        login.addHeader("X-Real-IP", "203.0.113.9");
        assertFalse(guard.preHandle(login, new MockHttpServletResponse(), null));
    }

    @Test
    void sanctionedMembersAndAddressesAreRefusedEverywhere() throws Exception {
        when(moderation.checkRestricted(eq("banned"), isNull())).thenReturn("이용이 제한되었습니다.");
        when(moderation.checkRestricted(isNull(), eq("203.0.113.9"))).thenReturn("이용이 제한되었습니다.");
        var guard = guard(true, false);

        var mutedWrite = memberRequest("/boards/funboard/addComment", member("banned", 1));
        var response = new MockHttpServletResponse();
        assertFalse(guard.preHandle(mutedWrite, response, null));
        assertEquals(403, response.getStatus());

        var blockedSignup = new MockHttpServletRequest("POST", "/submitSignUp");
        blockedSignup.addHeader("X-Forwarded-For", "203.0.113.9");
        assertFalse(guard.preHandle(blockedSignup, new MockHttpServletResponse(), null));

        var blockedLogin = new MockHttpServletRequest("POST", "/submitLogin");
        blockedLogin.addHeader("X-Forwarded-For", "203.0.113.9");
        assertFalse(guard.preHandle(blockedLogin, new MockHttpServletResponse(), null));
    }

    @Test
    void newAccountsGetTighterPostLimits() throws Exception {
        var guard = guard(true, false);
        MemberDTO fresh = member("fresh", 1);
        fresh.setRegDate(new Date());
        for (int i = 0; i < PublicWriteSecurityInterceptor.NEW_MEMBER_POSTS_PER_10_MINUTES; i++) {
            assertTrue(guard.preHandle(memberRequest("/boards/funboard/submitPost", fresh), new MockHttpServletResponse(), null));
        }
        var response = new MockHttpServletResponse();
        assertFalse(guard.preHandle(memberRequest("/boards/funboard/submitPost", fresh), response, null));
        assertEquals(429, response.getStatus());
    }

    @Test
    void signupIsRateLimitedPerClientAddress() throws Exception {
        var guard = guard(true, false);
        for (int i = 0; i < PublicWriteSecurityInterceptor.SIGNUPS_PER_IP_PER_HOUR; i++) {
            var request = new MockHttpServletRequest("POST", "/submitSignUp");
            request.addHeader("X-Forwarded-For", "203.0.113.7");
            assertTrue(guard.preHandle(request, new MockHttpServletResponse(), null));
        }
        var request = new MockHttpServletRequest("POST", "/submitSignUp");
        request.addHeader("X-Forwarded-For", "203.0.113.7");
        var response = new MockHttpServletResponse();
        assertFalse(guard.preHandle(request, response, null));
        assertEquals(429, response.getStatus());
    }

    @RestController
    static class ContainmentProbeController {
        @PostMapping({"/api/chat/messages", "/submitSignUp", "/imageUpload", "/boards/{boardTitle}/submitPost"})
        String write() {
            throw new AssertionError("A contained write must not reach its controller");
        }
    }
}
