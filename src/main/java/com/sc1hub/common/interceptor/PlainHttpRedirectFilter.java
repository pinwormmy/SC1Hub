package com.sc1hub.common.interceptor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * 평문 HTTP 로 들어온 열람 요청을 HTTPS 로 301 리다이렉트한다.
 *
 * <p>TLS 는 앞단 카페24 프록시(openresty)가 종단하고 앱에는 항상 평문으로 도착하므로 {@code isSecure()} 나
 * {@code X-Forwarded-Proto} 로는 구분할 수 없다(실측: 프록시가 그 헤더를 설정하지 않음). 실측된 차이는
 * <b>평문 HTTP 요청에만 {@code X-Real-IP} 가 붙고, TLS 를 거친 요청에는 {@code X-Forwarded-For} 만 붙는다</b>는
 * 점이다. 이 신호로 판정하되, 프록시 구성이 바뀌어도 사이트 전체가 리다이렉트 루프에 빠지지 않도록
 * 세 겹으로 방어한다: (1) 리다이렉트 응답에 짧은 마커 쿠키를 심고 마커가 있으면 다시 보내지 않는다,
 * (2) 어떤 상류가 {@code X-Forwarded-Proto: https} 를 말하면 그대로 믿고 넘긴다, (3) 분당 리다이렉트가
 * 폭주하면 10분간 기능을 끄고 경고를 남긴다. 쓰기 요청(POST 등)은 건드리지 않는다.
 * 브라우저에는 HSTS 도 함께 내려가므로 이 필터는 첫 방문과 비브라우저 클라이언트를 위한 보조 장치다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class PlainHttpRedirectFilter extends OncePerRequestFilter {

    static final String MARKER_COOKIE = "sc_https_redirected";
    static final int MARKER_MAX_AGE_SECONDS = 600;
    static final int DEFAULT_REDIRECTS_PER_MINUTE = 300;
    private static final long BREAKER_PAUSE_MILLIS = 10 * 60 * 1000L;
    private static final long WINDOW_MILLIS = 60 * 1000L;
    private static final Set<String> REDIRECTABLE_HOSTS = Set.of("sc1hub.com", "www.sc1hub.com");

    private final boolean enabled;
    private final String targetOrigin;
    private final int redirectsPerMinute;
    private long windowStart;
    private int windowCount;
    private long pausedUntil;

    @Autowired
    public PlainHttpRedirectFilter(
            @Value("${sc1hub.security.https-redirect-enabled:true}") boolean enabled,
            @Value("${sc1hub.security.https-redirect-origin:https://sc1hub.com}") String targetOrigin) {
        this(enabled, targetOrigin, DEFAULT_REDIRECTS_PER_MINUTE);
    }

    PlainHttpRedirectFilter(boolean enabled, String targetOrigin, int redirectsPerMinute) {
        this.enabled = enabled;
        this.targetOrigin = targetOrigin == null ? "" : targetOrigin.replaceAll("/+$", "");
        this.redirectsPerMinute = redirectsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (enabled && looksLikePlainHttp(request) && allowRedirect()) {
            String query = request.getQueryString();
            String location = targetOrigin + request.getRequestURI()
                    + (StringUtils.hasText(query) ? "?" + query : "");
            Cookie marker = new Cookie(MARKER_COOKIE, "1");
            marker.setMaxAge(MARKER_MAX_AGE_SECONDS);
            marker.setPath("/");
            marker.setHttpOnly(true);
            marker.setSecure(true);
            response.addCookie(marker);
            // 잘못 판정된 HTTPS 요청이 캐시된 301 로 영구 루프에 빠지지 않도록 캐시를 금지한다.
            response.setHeader("Cache-Control", "no-store");
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", location);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean looksLikePlainHttp(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        if (request.isSecure() || !"http".equalsIgnoreCase(request.getScheme())) {
            return false;
        }
        if ("https".equalsIgnoreCase(firstValue(request.getHeader("X-Forwarded-Proto")))) {
            return false;
        }
        if (!StringUtils.hasText(request.getHeader("X-Real-IP"))) {
            return false;
        }
        if (!REDIRECTABLE_HOSTS.contains(resolveHost(request))) {
            return false;
        }
        return !hasMarkerCookie(request);
    }

    private boolean hasMarkerCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (MARKER_COOKIE.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private String resolveHost(HttpServletRequest request) {
        String host = firstValue(request.getHeader("X-Forwarded-Host"));
        if (!StringUtils.hasText(host)) {
            host = firstValue(request.getHeader("Host"));
        }
        if (!StringUtils.hasText(host)) {
            host = request.getServerName();
        }
        if (!StringUtils.hasText(host)) {
            return "";
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        int portSeparator = normalized.indexOf(':');
        return portSeparator >= 0 ? normalized.substring(0, portSeparator) : normalized;
    }

    private static String firstValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int comma = value.indexOf(',');
        return (comma >= 0 ? value.substring(0, comma) : value).trim();
    }

    /** 분당 상한을 넘기면 10분간 리다이렉트를 멈춘다(프록시 구성 변경으로 인한 루프 폭주 방어). */
    private synchronized boolean allowRedirect() {
        long now = System.currentTimeMillis();
        if (now < pausedUntil) {
            return false;
        }
        if (now - windowStart >= WINDOW_MILLIS) {
            windowStart = now;
            windowCount = 0;
        }
        windowCount++;
        if (windowCount > redirectsPerMinute) {
            pausedUntil = now + BREAKER_PAUSE_MILLIS;
            log.warn("HTTP→HTTPS 리다이렉트가 분당 {}회를 넘어 10분간 중지합니다. 프록시 헤더 구성 변경을 확인하세요.",
                    redirectsPerMinute);
            return false;
        }
        return true;
    }
}
