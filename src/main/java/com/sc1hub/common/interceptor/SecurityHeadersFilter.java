package com.sc1hub.common.interceptor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 모든 응답에 브라우저 보안 헤더를 붙인다. TLS 종단은 앞단 프록시(openresty)가 하므로 HTTP→HTTPS
 * 강제와 HSTS 최종 책임은 프록시에 있으나, 여기서도 방어적으로 헤더를 실어 준다.
 * 클릭재킹(X-Frame-Options / frame-ancestors), MIME 스니핑(nosniff), base-uri/plugin 주입,
 * Referrer 유출을 함께 막고, 관리자·회원 전용 페이지는 캐시에 남지 않도록 no-store 로 표시한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    // 인라인 스크립트와 애드센스·유튜브 임베드를 쓰는 사이트라 script-src 는 걸지 않는다.
    // 대신 프레이밍/베이스태그/플러그인만 잠그고 혼합 콘텐츠는 자동 승격시킨다.
    private static final String CONTENT_SECURITY_POLICY =
            "frame-ancestors 'self'; base-uri 'self'; object-src 'none'; upgrade-insecure-requests";
    private static final String PERMISSIONS_POLICY = "geolocation=(), microphone=(), camera=()";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("Permissions-Policy", PERMISSIONS_POLICY);
        // 브라우저는 평문 HTTP 응답의 HSTS 는 무시하므로 항상 실어도 안전하다(HTTPS 방문자만 적용).
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        if (isSensitivePath(request)) {
            response.setHeader("Cache-Control", "no-store");
        }
        filterChain.doFilter(request, response);
    }

    private boolean isSensitivePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
        return path.startsWith("/adminPage")
                || path.startsWith("/modifyMemberByAdmin")
                || path.equals("/myPage")
                || path.equals("/modifyMyInfo")
                || path.startsWith("/api/admin/");
    }
}
