package com.sc1hub.common.config;

import com.sc1hub.common.interceptor.AdminInterceptor;
import com.sc1hub.common.interceptor.BoardLvInterceptor;
import com.sc1hub.common.interceptor.CanonicalInterceptor;
import com.sc1hub.common.interceptor.VisitorCountInterceptor;
import com.sc1hub.visitor.service.VisitorCountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    private final VisitorCountService visitorCountService;
    private final CanonicalInterceptor canonicalInterceptor;

    public WebConfig(VisitorCountService visitorCountService, CanonicalInterceptor canonicalInterceptor) {
        this.visitorCountService = visitorCountService;
        this.canonicalInterceptor = canonicalInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Uploaded post images need controller-based recovery for legacy filenames.
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/favicon.ico");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new VisitorCountInterceptor(visitorCountService))
                .addPathPatterns("/**");
        registry.addInterceptor(canonicalInterceptor)
                .addPathPatterns("/**");
        registry.addInterceptor(new BoardLvInterceptor())
                .addPathPatterns("/**/writePost", "/**/modifyPost/**", "/**/deletePost/**")
                .excludePathPatterns("/boards/funBoard/**", "/boards/funboard/**");
        registry.addInterceptor(new AdminInterceptor())
                .addPathPatterns("/adminPage/**", "/modifyMemberByAdmin/**", "/deleteMember",
                        "/**/writePost", "/**/modifyPost/**", "/**/deletePost/**",
                        "/api/admin/alias-dictionary/**", "/api/admin/assistant-bot/**")
                .excludePathPatterns("/boards/supportBoard/**", "/boards/videoLinkBoard/**", "/boards/promotionBoard/**",
                        "/boards/freeBoard/**", "/boards/freeboard/**", "/boards/beginnerBoard/**", "/boards/beginnerboard/**",
                        "/boards/funBoard/**", "/boards/funboard/**", "/boards/userGuideBoard/**", "/boards/userguideboard/**");
    }
}
