package com.sc1hub.interceptor;

import com.sc1hub.member.MemberDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Slf4j
public class BoardLvInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession();
        MemberDTO member = (MemberDTO) session.getAttribute("member");
        if (member == null || member.getGrade() < 1) {
            String message = "로그인 혹은 게시글 권한을 확인해주세요~!";
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<script>alert('" + message + "'); history.go(-1);</script>");
            return false;
        }
        return true;
    }
}
