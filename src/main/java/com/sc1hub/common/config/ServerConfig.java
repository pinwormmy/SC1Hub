package com.sc1hub.common.config;

import com.sc1hub.common.interceptor.MemberSessionListener;
import com.sc1hub.member.service.MemberSessionRegistry;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.SessionTrackingMode;
import java.util.Collections;

@Configuration(proxyBeanMethods = false)
public class ServerConfig {

    @Bean
    public ServletContextInitializer cookieOnlySessionTracking() {
        return servletContext -> servletContext.setSessionTrackingModes(
                Collections.singleton(SessionTrackingMode.COOKIE));
    }

    @Bean
    public ServletListenerRegistrationBean<MemberSessionListener> memberSessionListener(
            MemberSessionRegistry sessionRegistry) {
        return new ServletListenerRegistrationBean<>(new MemberSessionListener(sessionRegistry));
    }
}
