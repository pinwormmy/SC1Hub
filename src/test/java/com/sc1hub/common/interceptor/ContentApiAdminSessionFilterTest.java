package com.sc1hub.common.interceptor;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.mapper.MemberMapper;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentApiAdminSessionFilterTest {

    private final MemberMapper memberMapper = mock(MemberMapper.class);
    private final AssistantProperties assistantProperties = new AssistantProperties();
    private final ContentApiAdminSessionFilter filter = new ContentApiAdminSessionFilter(
            new ContentApiTokenAuthenticator("secret-token"), memberMapper, assistantProperties);

    @Test
    void validToken_attachesAdminMemberForTheRequestAndCleansUp() throws Exception {
        MemberDTO admin = new MemberDTO();
        admin.setId("admin");
        admin.setNickName("SC1Hub");
        admin.setPw("hash");
        admin.setGrade(3);
        when(memberMapper.getMemberInfo("admin")).thenReturn(admin);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/assistant/index/reindex");
        request.addHeader("Authorization", "Bearer secret-token");
        AtomicReference<MemberDTO> seen = new AtomicReference<>();
        AtomicReference<HttpSession> sessionSeen = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            HttpSession session = ((MockHttpServletRequest) req).getSession(false);
            sessionSeen.set(session);
            seen.set((MemberDTO) session.getAttribute("member"));
        });

        assertEquals("SC1Hub", seen.get().getNickName());
        assertEquals(3, seen.get().getGrade());
        assertNull(seen.get().getPw());
        assertTrue(((MockHttpSession) sessionSeen.get()).isInvalid());
    }

    @Test
    void validToken_fallsBackToSyntheticAdminWhenAccountIsMissing() throws Exception {
        when(memberMapper.getMemberInfo("admin")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/adminPage");
        request.addHeader("Authorization", "Bearer secret-token");
        AtomicReference<MemberDTO> seen = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                seen.set((MemberDTO) ((MockHttpServletRequest) req).getSession(false).getAttribute("member")));

        assertEquals("admin", seen.get().getId());
        assertEquals("운영자", seen.get().getNickName());
        assertEquals(assistantProperties.getAdminGrade(), seen.get().getGrade());
    }

    @Test
    void wrongOrMissingToken_leavesRequestUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/assistant/index/reindex");
        request.addHeader("Authorization", "Bearer wrong");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertNull(((MockHttpServletRequest) req).getSession(false)));

        assertNull(request.getSession(false));
    }

    @Test
    void loggedInSession_isNotReplacedByToken() throws Exception {
        MemberDTO regular = new MemberDTO();
        regular.setId("someone");
        regular.setGrade(1);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("member", regular);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/assistant/index/reindex");
        request.setSession(session);
        request.addHeader("Authorization", "Bearer secret-token");
        AtomicReference<Object> seen = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                seen.set(((MockHttpServletRequest) req).getSession(false).getAttribute("member")));

        assertSame(regular, seen.get());
        assertFalse(session.isInvalid());
    }

    @Test
    void contentApi_isLeftToItsOwnTokenCheck() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/content/images");
        request.addHeader("Authorization", "Bearer secret-token");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertNull(((MockHttpServletRequest) req).getSession(false)));

        assertNull(request.getSession(false));
    }
}
