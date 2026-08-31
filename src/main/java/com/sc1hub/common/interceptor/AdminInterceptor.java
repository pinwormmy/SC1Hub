package com.sc1hub.common.interceptor;

import com.sc1hub.member.dto.MemberDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Value("${sc1hub.content-api.token:}")
    private String contentApiToken = "";

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
        if (!request.getRequestURI().startsWith("/api/admin/content/")
                || contentApiToken == null || contentApiToken.trim().isEmpty()) {
            return false;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        byte[] expected = contentApiToken.trim().getBytes(StandardCharsets.UTF_8);
        byte[] actual = authorization.substring("Bearer ".length()).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
