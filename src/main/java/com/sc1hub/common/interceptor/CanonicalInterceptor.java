package com.sc1hub.common.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class CanonicalInterceptor implements HandlerInterceptor {

    private static final Set<String> RESERVED_BOARD_ENDPOINTS = new HashSet<>(Arrays.asList(
            "boardlist",
            "showlatestposts"
    ));

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String pathWithoutContext = extractPathWithoutContext(requestUri, contextPath);
        String normalizedPath = normalizeBoardPath(pathWithoutContext);
        String queryString = request.getQueryString();

        if (shouldRedirectToNormalizedPath(request.getMethod(), pathWithoutContext, normalizedPath)) {
            String redirectTarget = buildPathWithContext(contextPath, normalizedPath);
            if (StringUtils.hasText(queryString)) {
                redirectTarget = redirectTarget + "?" + queryString;
            }
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", redirectTarget);
            return false;
        }

        StringBuilder canonicalUrl = new StringBuilder("https://sc1hub.com");
        canonicalUrl.append(normalizedPath);
        if (queryString != null && !queryString.isEmpty()) {
            canonicalUrl.append('?').append(queryString);
        }

        request.setAttribute("canonical", canonicalUrl.toString());
        return true;
    }

    private boolean shouldRedirectToNormalizedPath(String method, String originalPath, String normalizedPath) {
        if (!StringUtils.hasText(originalPath) || !StringUtils.hasText(normalizedPath)) {
            return false;
        }
        if (!("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))) {
            return false;
        }
        return !originalPath.equals(normalizedPath);
    }

    private String extractPathWithoutContext(String requestUri, String contextPath) {
        if (!StringUtils.hasText(requestUri)) {
            return "/";
        }
        if (!StringUtils.hasText(contextPath) || "/".equals(contextPath)) {
            return requestUri;
        }
        if (requestUri.startsWith(contextPath)) {
            String path = requestUri.substring(contextPath.length());
            return StringUtils.hasText(path) ? path : "/";
        }
        return requestUri;
    }

    private String buildPathWithContext(String contextPath, String pathWithoutContext) {
        if (!StringUtils.hasText(contextPath) || "/".equals(contextPath)) {
            return pathWithoutContext;
        }
        return contextPath + pathWithoutContext;
    }

    private String normalizeBoardPath(String path) {
        if (!StringUtils.hasText(path) || !path.startsWith("/boards/")) {
            return path;
        }

        String[] segments = path.split("/", -1);
        if (segments.length < 3) {
            return path;
        }

        String boardSegment = segments[2];
        if (!StringUtils.hasText(boardSegment)
                || RESERVED_BOARD_ENDPOINTS.contains(boardSegment.toLowerCase(Locale.ROOT))) {
            return path;
        }

        String normalizedBoard = boardSegment.toLowerCase(Locale.ROOT);
        if (boardSegment.equals(normalizedBoard)) {
            return path;
        }

        segments[2] = normalizedBoard;
        return String.join("/", segments);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        // 필요한 경우 후처리 로직
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 요청 처리 완료 후 호출
    }
}
