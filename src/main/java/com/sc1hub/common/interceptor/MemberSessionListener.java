package com.sc1hub.common.interceptor;

import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.service.MemberSessionRegistry;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * 세션이 소멸(로그아웃·만료·강제 무효화)될 때 회원 세션 레지스트리에서 제거한다.
 * WAR 배포에서도 확실히 등록되도록 {@link com.sc1hub.common.config.ServerConfig} 에서
 * ServletListenerRegistrationBean 으로 명시 등록한다.
 */
public class MemberSessionListener implements HttpSessionListener {

    private final MemberSessionRegistry sessionRegistry;

    public MemberSessionListener(MemberSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        if (session == null) {
            return;
        }
        Object member = session.getAttribute("member");
        if (member instanceof MemberDTO memberDTO) {
            sessionRegistry.unregister(memberDTO.getId(), session);
        }
    }
}
