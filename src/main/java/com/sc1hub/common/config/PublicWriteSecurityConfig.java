package com.sc1hub.common.config;

import com.sc1hub.common.interceptor.PublicWriteSecurityInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class PublicWriteSecurityConfig implements WebMvcConfigurer {
    private final PublicWriteSecurityInterceptor interceptor;

    public PublicWriteSecurityConfig(PublicWriteSecurityInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**").order(-10);
    }
}
