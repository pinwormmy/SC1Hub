package com.sc1hub.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * 클라이언트 IP 판정.
 *
 * <p>운영 앱은 카페24 앞단 프록시 뒤에 있어 {@code getRemoteAddr()}는 항상 프록시 주소다. 실측 결과
 * 프록시는 {@code X-Forwarded-For}를 직접 덧붙이므로(클라이언트가 보낸 값이 있으면 그 뒤에 추가),
 * <b>가장 오른쪽 값</b>이 프록시가 본 실제 클라이언트 주소다. 왼쪽 값들은 클라이언트가 위조할 수 있어
 * 절대 쓰지 않는다. 헤더가 없거나(직접 접속·헬스체크) 형식이 틀리면 {@code getRemoteAddr()}로 돌아가며,
 * 그 경우 {@link #hasForwardedClient}가 false 라 IP 기반 제한·차단은 적용하지 않는다(프록시 주소를 차단해
 * 전체 이용자를 막는 사고 방지).
 */
public final class IpService {

    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:]{2,45}$");

    private IpService() {
    }

    /** 신뢰 가능한 클라이언트 IP. 프록시 경유가 아니면 원격 주소 자체. */
    public static String getRemoteIP(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = resolveForwardedClient(request);
        return forwarded != null ? forwarded : request.getRemoteAddr();
    }

    /** 프록시가 덧붙인 X-Forwarded-For 에서 유효한 클라이언트 주소를 얻었을 때만 true. */
    public static boolean hasForwardedClient(HttpServletRequest request) {
        return request != null && resolveForwardedClient(request) != null;
    }

    /** 카페24 앞단은 평문 HTTP 요청에만 X-Real-IP 를 붙인다(TLS 종단 요청에는 X-Forwarded-For 만). */
    public static boolean isPlainHttpViaProxy(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (!StringUtils.hasText(request.getHeader("X-Real-IP"))) {
            return false;
        }
        return !"https".equalsIgnoreCase(firstValue(request.getHeader("X-Forwarded-Proto")));
    }

    public static boolean isValidIp(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String candidate = value.trim();
        return IPV4.matcher(candidate).matches()
                || (candidate.indexOf(':') >= 0 && IPV6.matcher(candidate).matches());
    }

    /** 루프백·사설·링크로컬이 아닌 공인 주소만 차단 대상으로 삼는다. */
    public static boolean isPublicAddress(String ip) {
        if (!isValidIp(ip)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip.trim());
            return !(address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                    || address.isMulticastAddress());
        } catch (Exception e) {
            return false;
        }
    }

    static String resolveForwardedClient(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(header)) {
            return null;
        }
        int comma = header.lastIndexOf(',');
        String last = (comma >= 0 ? header.substring(comma + 1) : header).trim();
        return isValidIp(last) ? last : null;
    }

    private static String firstValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int comma = value.indexOf(',');
        return (comma >= 0 ? value.substring(0, comma) : value).trim();
    }
}
