package com.sc1hub.common.config;

import com.sc1hub.common.interceptor.MemberSessionListener;
import com.sc1hub.member.service.MemberSessionRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import java.util.Collections;

@Configuration(proxyBeanMethods = false)
public class ServerConfig {

    /**
     * 세션 추적은 쿠키로만 하고, 세션 쿠키의 HttpOnly/Secure 를 컨테이너 수준에서 직접 지정한다.
     * {@code server.servlet.session.cookie.*} 프로퍼티는 내장 Tomcat 에만 적용되고 카페24 외부 Tomcat(WAR)
     * 응답에는 반영되지 않는 것이 운영에서 확인됐으므로, 같은 프로퍼티 값을 읽어 SessionCookieConfig 에
     * 적용한다(로컬 run-local.sh 는 평문 HTTP 라 false 로 덮어쓴다). SameSite 는 Servlet 5.0 API 에 없어
     * META-INF/context.xml 의 CookieProcessor 가 담당한다.
     */
    @Bean
    public ServletContextInitializer cookieOnlySessionTracking(
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureSessionCookie) {
        return servletContext -> {
            servletContext.setSessionTrackingModes(Collections.singleton(SessionTrackingMode.COOKIE));
            SessionCookieConfig cookieConfig = servletContext.getSessionCookieConfig();
            cookieConfig.setHttpOnly(true);
            cookieConfig.setSecure(secureSessionCookie);
        };
    }

    @Bean
    public ServletListenerRegistrationBean<MemberSessionListener> memberSessionListener(
            MemberSessionRegistry sessionRegistry) {
        return new ServletListenerRegistrationBean<>(new MemberSessionListener(sessionRegistry));
    }
}
