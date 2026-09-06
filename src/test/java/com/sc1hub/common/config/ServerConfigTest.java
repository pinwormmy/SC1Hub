package com.sc1hub.common.config;

import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigTest {

    @Test
    void sessionCookieIsCookieOnlyHttpOnlyAndSecureAtContainerLevel() throws Exception {
        MockServletContext servletContext = new MockServletContext();

        new ServerConfig().cookieOnlySessionTracking(true).onStartup(servletContext);

        assertEquals(Set.of(SessionTrackingMode.COOKIE), servletContext.getEffectiveSessionTrackingModes());
        SessionCookieConfig cookieConfig = servletContext.getSessionCookieConfig();
        assertTrue(cookieConfig.isHttpOnly());
        // 외부 Tomcat 은 Boot 프로퍼티를 무시하므로 컨테이너 설정으로 직접 Secure 를 켜야 한다.
        assertTrue(cookieConfig.isSecure());
    }

    @Test
    void secureFlagFollowsThePropertySoLocalPlainHttpCanDisableIt() throws Exception {
        MockServletContext servletContext = new MockServletContext();

        new ServerConfig().cookieOnlySessionTracking(false).onStartup(servletContext);

        assertFalse(servletContext.getSessionCookieConfig().isSecure());
        assertTrue(servletContext.getSessionCookieConfig().isHttpOnly());
    }
}
