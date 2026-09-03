package com.sc1hub.common.interceptor;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.mapper.MemberMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 콘텐츠 API 토큰을 관리자 로그인과 동등하게 만든다.
 * 유효한 토큰이 오면 그 요청 동안만 관리자 회원을 세션에 실어 주므로, 세션의 "member"를 보는
 * 인터셉터·컨트롤러·서비스 어디서든 AI 작업이 별도 로그인 없이 관리자 기능을 쓸 수 있다.
 * 요청이 끝나면 만들어 준 세션은 지운다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class ContentApiAdminSessionFilter extends OncePerRequestFilter {

    static final String MEMBER_ATTRIBUTE = "member";
    private static final String FALLBACK_ADMIN_NICKNAME = "운영자";

    private final ContentApiTokenAuthenticator tokenAuthenticator;
    private final MemberMapper memberMapper;
    private final AssistantProperties assistantProperties;

    public ContentApiAdminSessionFilter(ContentApiTokenAuthenticator tokenAuthenticator,
                                        MemberMapper memberMapper,
                                        AssistantProperties assistantProperties) {
        this.tokenAuthenticator = tokenAuthenticator;
        this.memberMapper = memberMapper;
        this.assistantProperties = assistantProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 콘텐츠 API는 토큰을 직접 검사하고 작성자 규칙(명시 writer, 기본 '운영자')이 따로 있어 세션을 붙이지 않는다.
        return request.getRequestURI().startsWith("/api/admin/content/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!tokenAuthenticator.hasValidToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        HttpSession existing = request.getSession(false);
        if (existing != null && existing.getAttribute(MEMBER_ATTRIBUTE) != null) {
            filterChain.doFilter(request, response);
            return;
        }

        MemberDTO admin = loadAdmin();
        HttpSession session = request.getSession(true);
        boolean created = existing == null;
        session.setAttribute(MEMBER_ATTRIBUTE, admin);
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                if (created) {
                    session.invalidate();
                } else {
                    session.removeAttribute(MEMBER_ATTRIBUTE);
                }
            } catch (IllegalStateException ignored) {
                // 처리 중 이미 무효화된 세션
            }
        }
    }

    /** DB의 관리자 계정을 쓰되(닉네임 등 실제 값), 없으면 관리자 등급만 가진 대체 회원으로 연다. */
    private MemberDTO loadAdmin() {
        String adminId = assistantProperties.getAdminId();
        try {
            MemberDTO member = adminId == null ? null : memberMapper.getMemberInfo(adminId);
            if (member != null) {
                member.setPw(null);
                if (member.getGrade() != assistantProperties.getAdminGrade()) {
                    member.setGrade(assistantProperties.getAdminGrade());
                }
                return member;
            }
        } catch (Exception e) {
            log.warn("콘텐츠 API 토큰용 관리자 계정 조회 실패. 대체 관리자 회원으로 진행합니다. adminId={}", adminId, e);
        }
        MemberDTO fallback = new MemberDTO();
        fallback.setId(adminId);
        fallback.setNickName(FALLBACK_ADMIN_NICKNAME);
        fallback.setGrade(assistantProperties.getAdminGrade());
        return fallback;
    }
}
