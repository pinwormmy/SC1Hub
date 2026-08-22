package com.sc1hub.common.config;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.SessionTrackingMode;
import java.util.Collections;

@Configuration
public class ServerConfig {

    @Bean
    public ServletContextInitializer cookieOnlySessionTracking() {
        return servletContext -> servletContext.setSessionTrackingModes(
                Collections.singleton(SessionTrackingMode.COOKIE));
    }
}
