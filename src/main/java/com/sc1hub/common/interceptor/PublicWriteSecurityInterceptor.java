package com.sc1hub.common.interceptor;

import com.sc1hub.common.util.IpService;
import com.sc1hub.member.dto.MemberDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class PublicWriteSecurityInterceptor implements HandlerInterceptor {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> BOARD_READ_ACTIONS = Set.of("showCommentList", "commentPageSetting");
    private final ContentApiTokenAuthenticator tokenAuthenticator;
    private final boolean publicWritesEnabled;
    private final Map<String, Window> windows = new HashMap<>();

    public PublicWriteSecurityInterceptor(ContentApiTokenAuthenticator tokenAuthenticator,
            @Value("${sc1hub.security.public-writes-enabled:true}") boolean publicWritesEnabled) {
        this.tokenAuthenticator = tokenAuthenticator;
        this.publicWritesEnabled = publicWritesEnabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        if (tokenAuthenticator.hasValidToken(request)) {
            return true;
        }
        HttpSession session = request.getSession(false);
        MemberDTO member = session == null ? null : (MemberDTO) session.getAttribute("member");
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        if ("cross-site".equals(request.getHeader("Sec-Fetch-Site"))
                || (origin != null && !sameOrigin(origin, request))
                || (origin == null && referer != null && !sameOrigin(referer, request))
                || (member != null && origin == null && referer == null)) {
            return reject(response, 403, "요청 출처를 확인할 수 없습니다. 사이트에서 다시 시도해주세요.");
        }
        String path = resolveMappedPath(request);
        String action = path.substring(path.lastIndexOf('/') + 1);
        boolean contentWrite = (path.startsWith("/boards/") && !BOARD_READ_ACTIONS.contains(action))
                || path.startsWith("/api/chat/") || path.startsWith("/strategy-tips")
                || path.equals("/imageUpload") || path.equals("/submitSignUp")
                || path.equals("/submitModifyMyInfo");
        if (!contentWrite || (member != null && member.getGrade() == 3)) {
            return true;
        }
        if (!publicWritesEnabled) {
            return reject(response, 503, "도배 방지를 위한 보안 조치로 글·댓글·채팅 작성과 신규 가입을 잠시 제한하고 있습니다.");
        }
        if (member == null && !path.equals("/submitSignUp")) {
            return reject(response, 401, "로그인 후 작성해주세요.");
        }
        if (member != null && member.getGrade() < 1) {
            return reject(response, 403, "이 계정의 작성 권한이 제한되었습니다.");
        }
        String ip = IpService.getRemoteIP(request);
        if (!allow("ip:" + ip, 20) || (member != null && !allow("member:" + member.getId(), 10))) {
            response.setHeader("Retry-After", "60");
            return reject(response, 429, "작성 횟수가 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
        return true;
    }

    private String resolveMappedPath(HttpServletRequest request) {
        // Classify the route Spring actually matched. Raw request URIs may contain
        // matrix parameters or escaped characters that the router normalizes.
        Object matchedPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (matchedPattern instanceof String pattern && !pattern.isBlank()) {
            return pattern;
        }
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }

    private boolean sameOrigin(String value, HttpServletRequest request) {
        try {
            URI uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && Set.of("sc1hub.com", "www.sc1hub.com").contains(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)) {
                return true;
            }
            int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
            return request.getScheme().equalsIgnoreCase(uri.getScheme())
                    && request.getServerName().equalsIgnoreCase(uri.getHost()) && request.getServerPort() == port;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private synchronized boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().start >= 60_000);
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= 10_000) {
                return false;
            }
            window = new Window(now);
            windows.put(key, window);
        }
        return ++window.count <= limit;
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }

    private static class Window {
        private final long start;
        private int count;
        private Window(long start) {
            this.start = start;
        }
    }
}
