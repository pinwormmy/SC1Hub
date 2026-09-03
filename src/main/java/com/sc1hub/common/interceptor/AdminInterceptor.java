package com.sc1hub.common.interceptor;

import com.sc1hub.member.dto.MemberDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final ContentApiTokenAuthenticator tokenAuthenticator;

    public AdminInterceptor(ContentApiTokenAuthenticator tokenAuthenticator) {
        this.tokenAuthenticator = tokenAuthenticator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        MemberDTO member = session == null ? null : (MemberDTO) session.getAttribute("member");
        if ((member == null || member.getGrade() != 3) && !hasValidContentApiToken(request)) {
            String message = "해당 접근은 관리자 전용입니다.";
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            if (request.getRequestURI().startsWith("/api/")) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"" + message + "\"}");
            } else {
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write("<script>alert('" + message + "'); location.href='/';</script>");
            }
            return false;
        }
        return true;
    }

    private boolean hasValidContentApiToken(HttpServletRequest request) {
        // 여기서 토큰을 직접 받는 건 콘텐츠 API뿐이다. 그 밖의 경로는 ContentApiAdminSessionFilter가
        // 토큰 요청에 관리자 세션을 실어 주므로 위의 세션 검사로 통과한다.
        return request.getRequestURI().startsWith("/api/admin/content/")
                && tokenAuthenticator.hasValidToken(request);
    }
}
