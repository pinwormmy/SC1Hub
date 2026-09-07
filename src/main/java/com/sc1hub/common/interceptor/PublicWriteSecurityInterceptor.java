package com.sc1hub.common.interceptor;

import com.sc1hub.chat.service.ChatModerationService;
import com.sc1hub.common.security.OffenderTracker;
import com.sc1hub.common.security.SecuritySwitches;
import com.sc1hub.common.security.WriteRateLimiter;
import com.sc1hub.common.util.IpService;
import com.sc1hub.member.dto.MemberDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import java.net.URI;
import java.util.Set;

/**
 * 모든 쓰기 요청의 관문. 출처(CSRF) 검사 → 평문 HTTP 거부 → 제재 확인 → 쓰기 스위치 → 회원/비회원별
 * 속도 제한 순으로 판정하고, 거부는 {@link OffenderTracker}에 누적해 반복 위반자를 자동 제재한다.
 * IP 기준 제한은 프록시 헤더에서 클라이언트 주소를 신뢰할 수 있을 때만 적용한다.
 */
@Component
public class PublicWriteSecurityInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> BOARD_READ_ACTIONS = Set.of("showCommentList", "commentPageSetting");

    static final long MINUTE = 60_000L;
    static final long TEN_MINUTES = 10 * MINUTE;
    static final long HOUR = 60 * MINUTE;
    static final long NEW_MEMBER_PROBATION_MILLIS = 24 * HOUR;

    static final int MEMBER_WRITES_PER_MINUTE = 10;
    static final int IP_WRITES_PER_MINUTE = 20;
    static final int MEMBER_POSTS_PER_10_MINUTES = 5;
    static final int NEW_MEMBER_POSTS_PER_10_MINUTES = 2;
    static final int MEMBER_COMMENTS_PER_10_MINUTES = 20;
    static final int NEW_MEMBER_COMMENTS_PER_10_MINUTES = 6;
    static final int MEMBER_CHAT_PER_MINUTE = 30;
    static final int SIGNUPS_PER_IP_PER_HOUR = 3;
    static final int SIGNUPS_PER_HOUR_GLOBAL = 30;
    static final int GUEST_WRITES_PER_IP_PER_MINUTE = 10;
    static final int GUEST_WRITES_PER_MINUTE_GLOBAL = 60;

    private final ContentApiTokenAuthenticator tokenAuthenticator;
    private final SecuritySwitches switches;
    private final WriteRateLimiter rateLimiter;
    private final ChatModerationService moderationService;
    private final OffenderTracker offenderTracker;

    public PublicWriteSecurityInterceptor(ContentApiTokenAuthenticator tokenAuthenticator,
                                          SecuritySwitches switches,
                                          WriteRateLimiter rateLimiter,
                                          ChatModerationService moderationService,
                                          OffenderTracker offenderTracker) {
        this.tokenAuthenticator = tokenAuthenticator;
        this.switches = switches;
        this.rateLimiter = rateLimiter;
        this.moderationService = moderationService;
        this.offenderTracker = offenderTracker;
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
        String memberId = member == null ? null : member.getId();
        String nickname = member == null ? null : member.getNickName();
        String ip = IpService.getRemoteIP(request);
        boolean ipTrusted = IpService.hasForwardedClient(request);

        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        if ("cross-site".equals(request.getHeader("Sec-Fetch-Site"))
                || (origin != null && !sameOrigin(origin, request))
                || (origin == null && referer != null && !sameOrigin(referer, request))
                || (member != null && origin == null && referer == null)) {
            offenderTracker.strike(ip, ipTrusted, memberId, nickname, "출처 검사 실패");
            return reject(response, 403, "요청 출처를 확인할 수 없습니다. 사이트에서 다시 시도해주세요.");
        }

        String path = resolveMappedPath(request);
        String action = path.substring(path.lastIndexOf('/') + 1);
        boolean signup = path.equals("/submitSignUp");
        boolean login = path.equals("/submitLogin");
        boolean boardWrite = path.startsWith("/boards/") && !BOARD_READ_ACTIONS.contains(action);
        boolean post = boardWrite && action.equals("submitPost");
        boolean comment = boardWrite && action.equals("addComment");
        boolean chat = path.equals("/api/chat/messages");
        boolean contentWrite = boardWrite || path.startsWith("/api/chat/") || path.startsWith("/strategy-tips")
                || path.equals("/imageUpload") || signup || path.equals("/submitModifyMyInfo");

        if (member != null && member.getGrade() == 3) {
            return true;
        }
        if ((contentWrite || login) && IpService.isPlainHttpViaProxy(request)) {
            return reject(response, 403, "보안 연결(HTTPS)로 접속한 뒤 다시 시도해주세요.");
        }
        String restriction = moderationService.checkRestricted(memberId, ipTrusted ? ip : null);
        if (restriction != null) {
            return reject(response, 403, restriction);
        }
        if (!contentWrite) {
            return true;
        }
        if (!switches.isPublicWritesEnabled()) {
            return reject(response, 503, "도배 방지를 위한 보안 조치로 글·댓글·채팅 작성과 신규 가입을 잠시 제한하고 있습니다.");
        }

        if (member == null) {
            if (signup) {
                if (!rateLimiter.allow("signup:global", SIGNUPS_PER_HOUR_GLOBAL, HOUR)
                        || (ipTrusted && !rateLimiter.allow("signup-ip:" + ip, SIGNUPS_PER_IP_PER_HOUR, HOUR))) {
                    offenderTracker.strike(ip, ipTrusted, null, null, "가입 속도 제한");
                    response.setHeader("Retry-After", "3600");
                    return reject(response, 429, "가입 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
                }
                return true;
            }
            if (!switches.isGuestWritesEnabled()) {
                offenderTracker.strike(ip, ipTrusted, null, null, "비회원 쓰기 시도");
                return reject(response, 401, "로그인 후 작성해주세요.");
            }
            if (!rateLimiter.allow("guest:global", GUEST_WRITES_PER_MINUTE_GLOBAL, MINUTE)
                    || (ipTrusted && !rateLimiter.allow("guest-ip:" + ip, GUEST_WRITES_PER_IP_PER_MINUTE, MINUTE))) {
                offenderTracker.strike(ip, ipTrusted, null, null, "비회원 속도 제한");
                response.setHeader("Retry-After", "60");
                return reject(response, 429, "작성 횟수가 너무 많습니다. 잠시 후 다시 시도해주세요.");
            }
            return true;
        }

        if (member.getGrade() < 1) {
            return reject(response, 403, "이 계정의 작성 권한이 제한되었습니다.");
        }
        boolean probation = member.getRegDate() != null
                && System.currentTimeMillis() - member.getRegDate().getTime() < NEW_MEMBER_PROBATION_MILLIS;
        boolean allowed = rateLimiter.allow("member:" + memberId, MEMBER_WRITES_PER_MINUTE, MINUTE)
                && (!ipTrusted || rateLimiter.allow("ip:" + ip, IP_WRITES_PER_MINUTE, MINUTE));
        if (allowed && post) {
            allowed = rateLimiter.allow("member-post:" + memberId,
                    probation ? NEW_MEMBER_POSTS_PER_10_MINUTES : MEMBER_POSTS_PER_10_MINUTES, TEN_MINUTES);
        }
        if (allowed && comment) {
            allowed = rateLimiter.allow("member-comment:" + memberId,
                    probation ? NEW_MEMBER_COMMENTS_PER_10_MINUTES : MEMBER_COMMENTS_PER_10_MINUTES, TEN_MINUTES);
        }
        if (allowed && chat) {
            allowed = rateLimiter.allow("member-chat:" + memberId, MEMBER_CHAT_PER_MINUTE, MINUTE);
        }
        if (!allowed) {
            offenderTracker.strike(ip, ipTrusted, memberId, nickname, "회원 속도 제한");
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

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }
}
