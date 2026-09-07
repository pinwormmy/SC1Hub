package com.sc1hub.common.config;

import com.sc1hub.common.interceptor.AdminInterceptor;
import com.sc1hub.common.interceptor.BoardLvInterceptor;
import com.sc1hub.common.interceptor.CanonicalInterceptor;
import com.sc1hub.common.interceptor.MemberLoginInterceptor;
import com.sc1hub.common.interceptor.VisitorCountInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.util.pattern.PatternParseException;

import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
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
        CacheControl versionedAssetCache = CacheControl.maxAge(365, TimeUnit.DAYS)
                .cachePublic();
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(versionedAssetCache);
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(versionedAssetCache);
        // Uploaded post images need controller-based recovery for legacy filenames.
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/favicon.ico");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(visitorCountInterceptor)
                .addPathPatterns(patterns(
                        "/", "/guidelines", "/login", "/signAgreement", "/signUp",
                        "/findId", "/findPassword", "/myPage", "/modifyMyInfo",
                        "/adminPage/**", "/boards/*", "/boards/*/readPost",
                        "/boards/*/writePost", "/boards/*/modifyPost"));
        registry.addInterceptor(canonicalInterceptor)
                .addPathPatterns(patterns("/**"))
                .excludePathPatterns(patterns(
                        "/css/**", "/js/**", "/images/**",
                        "/favicon.ico", "/robots.txt", "/ads.txt", "/sitemap.xml",
                        "/img/**", "/uploadedImg/**", "/ckImgSubmit"));
        // 게시판 쓰기 경로는 반드시 PathPatternParser 가 받아들이는 형태로만 등록한다. 예전의
        // "/**/writePost" 처럼 ** 가 가운데 오는 패턴은 파서가 거부해 MappedInterceptor 가 조용히
        // AntPathMatcher(원본 URI 기준) 로 후퇴했고, 핸들러 매핑은 디코딩된 경로를 쓰므로
        // "/boards/noticeboard/submit%50ost" 한 글자 인코딩만으로 관리자·로그인 검사를 건너뛸 수 있었다.
        registry.addInterceptor(boardLvInterceptor)
                .addPathPatterns(patterns("/boards/*/writePost", "/boards/*/modifyPost", "/boards/*/modifyPost/**",
                        "/boards/*/deletePost", "/boards/*/deletePost/**"))
                .excludePathPatterns(patterns("/boards/funBoard/**", "/boards/funboard/**"));
        registry.addInterceptor(memberLoginInterceptor)
                .addPathPatterns(patterns("/myPage", "/modifyMyInfo", "/submitModifyMyInfo", "/deleteMyAccount"));
        registry.addInterceptor(adminInterceptor).addPathPatterns(patterns("/boards/*/movePost"));
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns(patterns("/adminPage/**", "/modifyMemberByAdmin/**", "/submitModifyMemberByAdmin", "/deleteMember",
                        "/boards/*/writePost", "/boards/*/submitPost", "/boards/*/submitModifyPost",
                        "/boards/*/modifyPost", "/boards/*/modifyPost/**",
                        "/boards/*/deletePost", "/boards/*/deletePost/**", "/boards/*/movePost",
                        "/migrate/**", "/api/admin/**"))
                // 회원 작성이 허용되는 게시판. URL 은 소문자로 정규화되므로 소문자 패턴이 실제로 매칭되는 쪽이다.
                .excludePathPatterns(patterns("/boards/videoLinkBoard/**", "/boards/videolinkboard/**",
                        "/boards/promotionBoard/**", "/boards/promotionboard/**",
                        "/boards/funBoard/**", "/boards/funboard/**"));
    }

    /**
     * 모든 인터셉터 패턴이 PathPatternParser 로 해석되는지 기동 시 검증한다. 해석되지 않는 패턴은
     * AntPathMatcher 로 조용히 후퇴해 인코딩 우회가 생기므로 등록 자체를 실패시킨다.
     */
    static String[] patterns(String... values) {
        for (String value : values) {
            try {
                PathPatternParser.defaultInstance.parse(value);
            } catch (PatternParseException e) {
                throw new IllegalStateException("인터셉터 경로 패턴이 PathPatternParser 로 해석되지 않습니다: " + value, e);
            }
        }
        return values;
    }
}
