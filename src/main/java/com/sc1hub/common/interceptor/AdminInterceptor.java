package com.sc1hub.common.interceptor;

import com.sc1hub.member.dto.MemberDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Slf4j
public class AdminInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession();
        MemberDTO member = (MemberDTO)session.getAttribute("member");
        if (member == null || member.getGrade() != 3){
            String message = "해당 접근은 관리자 전용입니다.";
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<script>alert('" + message + "'); location.href='/';</script>");
            return false;
        }
        return true;
    }
}
