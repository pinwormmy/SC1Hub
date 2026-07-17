package com.sc1hub.common.config;

import com.sc1hub.common.interceptor.AdminInterceptor;
import com.sc1hub.common.interceptor.BoardLvInterceptor;
import com.sc1hub.common.interceptor.CanonicalInterceptor;
import com.sc1hub.common.interceptor.MemberLoginInterceptor;
import com.sc1hub.common.interceptor.VisitorCountInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    private final VisitorCountInterceptor visitorCountInterceptor;
    private final CanonicalInterceptor canonicalInterceptor;
    private final BoardLvInterceptor boardLvInterceptor;
    private final AdminInterceptor adminInterceptor;
    private final MemberLoginInterceptor memberLoginInterceptor;

    public WebConfig(VisitorCountInterceptor visitorCountInterceptor,
                     CanonicalInterceptor canonicalInterceptor,
                     BoardLvInterceptor boardLvInterceptor,
                     AdminInterceptor adminInterceptor,
                     MemberLoginInterceptor memberLoginInterceptor) {
        this.visitorCountInterceptor = visitorCountInterceptor;
        this.canonicalInterceptor = canonicalInterceptor;
        this.boardLvInterceptor = boardLvInterceptor;
        this.adminInterceptor = adminInterceptor;
        this.memberLoginInterceptor = memberLoginInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Uploaded post images need controller-based recovery for legacy filenames.
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/favicon.ico");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(visitorCountInterceptor)
                .addPathPatterns(
                        "/", "/guidelines", "/login", "/signAgreement", "/signUp",
                        "/findId", "/findPassword", "/myPage", "/modifyMyInfo",
                        "/adminPage/**", "/boards/*", "/boards/*/readPost",
                        "/boards/*/writePost", "/boards/*/modifyPost", "/strategy-tips");
        registry.addInterceptor(canonicalInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/css/**", "/js/**", "/images/**", "/ckeditor/**",
                        "/favicon.ico", "/robots.txt", "/ads.txt", "/sitemap.xml",
                        "/img/**", "/uploadedImg/**", "/ckImgSubmit");
        registry.addInterceptor(boardLvInterceptor)
                .addPathPatterns("/**/writePost", "/**/modifyPost/**", "/**/deletePost/**")
                .excludePathPatterns("/boards/funBoard/**", "/boards/funboard/**");
        registry.addInterceptor(memberLoginInterceptor)
                .addPathPatterns("/myPage", "/modifyMyInfo", "/submitModifyMyInfo", "/deleteMyAccount");
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/adminPage/**", "/modifyMemberByAdmin/**", "/deleteMember",
                        "/**/writePost", "/**/modifyPost/**", "/**/deletePost/**",
                        "/**/movePost", "/migrate/**",
                        "/api/admin/alias-dictionary/**", "/api/admin/assistant-bot/**", "/api/admin/chat/**")
                .excludePathPatterns("/boards/supportBoard/**", "/boards/videoLinkBoard/**", "/boards/promotionBoard/**",
                        "/boards/freeBoard/**", "/boards/freeboard/**", "/boards/beginnerBoard/**", "/boards/beginnerboard/**",
                        "/boards/funBoard/**", "/boards/funboard/**", "/boards/userGuideBoard/**", "/boards/userguideboard/**");
    }
}
