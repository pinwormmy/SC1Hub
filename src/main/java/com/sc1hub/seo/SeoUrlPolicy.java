package com.sc1hub.seo;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SeoUrlPolicy {

    public static final String CANONICAL_ORIGIN = "https://sc1hub.com";
    public static final String DEFAULT_ROBOTS =
            "index,follow,max-image-preview:large,max-snippet:-1,max-video-preview:-1";
    public static final String NOINDEX_FOLLOW = "noindex,follow";
    public static final String NOINDEX_NOFOLLOW = "noindex,nofollow,noarchive";

    private static final Set<String> RESERVED_BOARD_ENDPOINTS = new HashSet<>(Arrays.asList(
            "boardlist",
            "showlatestposts"
    ));
    private static final Pattern SAFE_CATEGORY = Pattern.compile("[a-z0-9_-]{1,40}");
    private static final Pattern INTEGER = Pattern.compile("[0-9]+");

    public ResolvedUrl resolve(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String originalPath = extractPathWithoutContext(request.getRequestURI(), contextPath);
        String normalizedPath = normalizePath(originalPath);
        String canonicalQuery = buildCanonicalQuery(normalizedPath, request);
        String canonical = CANONICAL_ORIGIN + normalizedPath
                + (canonicalQuery.isEmpty() ? "" : "?" + canonicalQuery);
        String robots = resolveRobots(normalizedPath, request);
        String redirectLocation = buildRedirectLocation(request, contextPath, originalPath, normalizedPath);
        return new ResolvedUrl(canonical, robots, redirectLocation);
    }

    String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }

        String normalized = stripPathParameters(path).replaceAll("/{2,}", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        normalized = normalizeBoardSegment(normalized);
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String buildCanonicalQuery(String path, HttpServletRequest request) {
        List<String> parameters = new ArrayList<>();

        if (isPostPath(path)) {
            String postNum = normalizePositiveInteger(request.getParameter("postNum"));
            if (postNum != null) {
                parameters.add("postNum=" + postNum);
            }
            return String.join("&", parameters);
        }

        if ("/strategy-tips".equals(path)) {
            String category = normalizeCategory(request.getParameter("category"));
            if (category != null) {
                parameters.add("category=" + encode(category));
            }
            addPaginationParameter(parameters, request.getParameter("recentPage"));
            return String.join("&", parameters);
        }

        if (isBoardListPath(path)) {
            addPaginationParameter(parameters, request.getParameter("recentPage"));
        }
        return String.join("&", parameters);
    }

    private void addPaginationParameter(List<String> parameters, String value) {
        String page = normalizePositiveInteger(value);
        if (page != null && !"1".equals(page)) {
            parameters.add("recentPage=" + page);
        }
    }

    private String resolveRobots(String path, HttpServletRequest request) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (isMachineOrAdministrativePath(lowerPath)) {
            return NOINDEX_NOFOLLOW;
        }
        if (isUtilityPage(lowerPath) || isBoardEditorPath(lowerPath)
                || StringUtils.hasText(request.getParameter("keyword"))) {
            return NOINDEX_FOLLOW;
        }
        return DEFAULT_ROBOTS;
    }

    private boolean isMachineOrAdministrativePath(String path) {
        return path.startsWith("/api/")
                || path.startsWith("/adminpage")
                || path.startsWith("/migrate/")
                || path.startsWith("/imageupload")
                || path.startsWith("/modifymemberbyadmin");
    }

    private boolean isUtilityPage(String path) {
        return path.equals("/login")
                || path.equals("/signup")
                || path.equals("/signagreement")
                || path.equals("/findid")
                || path.equals("/findpassword")
                || path.equals("/mypage")
                || path.equals("/modifymyinfo")
                || path.equals("/modifymember")
                || path.equals("/logout");
    }

    private boolean isBoardEditorPath(String path) {
        return path.matches("/boards/[^/]+/(writepost|modifypost|submitpost|submitmodifypost)");
    }

    private String buildRedirectLocation(HttpServletRequest request, String contextPath,
                                         String originalPath, String normalizedPath) {
        boolean pathChanged = !originalPath.equals(normalizedPath);
        String host = resolveHost(request);
        boolean wwwHost = "www.sc1hub.com".equals(host);
        boolean explicitHttp = "http".equalsIgnoreCase(firstHeaderValue(request.getHeader("X-Forwarded-Proto")));

        if (!pathChanged && !wwwHost && !explicitHttp) {
            return null;
        }

        String targetPath = buildPathWithContext(contextPath, normalizedPath);
        String queryString = request.getQueryString();
        if (StringUtils.hasText(queryString)) {
            targetPath += "?" + queryString;
        }

        if (wwwHost || explicitHttp || "sc1hub.com".equals(host)) {
            return CANONICAL_ORIGIN + normalizedPath
                    + (StringUtils.hasText(queryString) ? "?" + queryString : "");
        }
        return targetPath;
    }

    private String resolveHost(HttpServletRequest request) {
        String host = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        if (!StringUtils.hasText(host)) {
            host = firstHeaderValue(request.getHeader("Host"));
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

    private String firstHeaderValue(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        int comma = value.indexOf(',');
        return comma >= 0 ? value.substring(0, comma).trim() : value.trim();
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

    private String stripPathParameters(String path) {
        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            int parameterStart = segments[i].indexOf(';');
            if (parameterStart >= 0) {
                segments[i] = segments[i].substring(0, parameterStart);
            }
        }
        return String.join("/", segments);
    }

    private String normalizeBoardSegment(String path) {
        if (!path.startsWith("/boards/")) {
            return path;
        }

        String[] segments = path.split("/", -1);
        if (segments.length < 3 || !StringUtils.hasText(segments[2])) {
            return path;
        }
        String lowerBoard = segments[2].toLowerCase(Locale.ROOT);
        if (!RESERVED_BOARD_ENDPOINTS.contains(lowerBoard)) {
            segments[2] = lowerBoard;
        }
        return String.join("/", segments);
    }

    private boolean isPostPath(String path) {
        return path.matches("/boards/[^/]+/readPost");
    }

    private boolean isBoardListPath(String path) {
        return path.matches("/boards/[^/]+");
    }

    private String normalizePositiveInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!INTEGER.matcher(trimmed).matches()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(trimmed);
            return parsed > 0 ? Long.toString(parsed) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return SAFE_CATEGORY.matcher(normalized).matches() ? normalized : null;
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 encoding is unavailable", e);
        }
    }

    public static final class ResolvedUrl {
        private final String canonical;
        private final String robots;
        private final String redirectLocation;

        ResolvedUrl(String canonical, String robots, String redirectLocation) {
            this.canonical = canonical;
            this.robots = robots;
            this.redirectLocation = redirectLocation;
        }

        public String getCanonical() {
            return canonical;
        }

        public String getRobots() {
            return robots;
        }

        public String getRedirectLocation() {
            return redirectLocation;
        }
    }
}
