package com.sc1hub.common.interceptor;

import com.sc1hub.member.dto.MemberDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Component
public class MemberLoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        MemberDTO member = session == null ? null : (MemberDTO) session.getAttribute("member");
        if (member != null) {
            return true;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod())) {
            response.sendRedirect("/login");
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
        return false;
    }
}
