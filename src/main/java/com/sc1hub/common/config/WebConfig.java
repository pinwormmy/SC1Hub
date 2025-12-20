package com.sc1hub.common.config;

import com.sc1hub.common.interceptor.AdminInterceptor;
import com.sc1hub.common.interceptor.BoardLvInterceptor;
import com.sc1hub.common.interceptor.CanonicalInterceptor;
import com.sc1hub.common.interceptor.VisitorCountInterceptor;
import com.sc1hub.visitor.service.VisitorCountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    VisitorCountService visitorCountService;

    @Autowired
    private CanonicalInterceptor canonicalInterceptor;

    @Value("/img/**")
    private String connectPath;

    // 로컬이랑 온라인 절대경로 차이 생김 '끝에 /'
    @Value("${path.upload.img}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.debug("업로드 컨피그 작동 확인 연결경로 : {}", connectPath);
        log.debug("업로드 컨피그 작동 확인 파일경로 : {}", uploadPath);
        registry.addResourceHandler(connectPath)
                .addResourceLocations(uploadPath);

        // favicon.ico에 대한 요청 처리 추가
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/favicon.ico");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new VisitorCountInterceptor(visitorCountService))
                .addPathPatterns("/**");
        registry.addInterceptor(canonicalInterceptor)
                .addPathPatterns("/**"); // 모든 경로에 대해 적용
        registry.addInterceptor(new BoardLvInterceptor())
                .addPathPatterns("/**/writePost", "/**/modifyPost/**", "/**/deletePost/**");
        registry.addInterceptor(new AdminInterceptor())
                .addPathPatterns("/adminPage/**", "/modifyMemberByAdmin/**", "/deleteMember",
                        "/**/writePost", "/**/modifyPost/**", "/**/deletePost/**")
                .excludePathPatterns("/boards/supportBoard/**", "/boards/videoLinkBoard/**", "/boards/promotionBoard/**",
                        "/boards/freeBoard/**", "/boards/beginnerBoard/**", "/boards/funBoard/**", "/boards/userGuideBoard/**");
    }
}
