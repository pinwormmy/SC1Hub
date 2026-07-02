package com.sc1hub.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.catalina.connector.Connector;

@Configuration
public class ServerConfig {

    @Value("${sc1hub.http-redirect.enabled:true}")
    private boolean httpRedirectEnabled;

    @Bean
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
        if (httpRedirectEnabled) {
            tomcat.addAdditionalTomcatConnectors(httpToHttpsRedirectConnector());
        }
        return tomcat;
    }

    private Connector httpToHttpsRedirectConnector() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setScheme("http");
        connector.setPort(80); // HTTP 포트
        connector.setSecure(false);
        connector.setRedirectPort(41696); // 카페24제공 HTTPS 포트
        return connector;
    }
}
