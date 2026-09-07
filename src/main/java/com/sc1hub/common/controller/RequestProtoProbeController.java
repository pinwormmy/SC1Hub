package com.sc1hub.common.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 임시 진단: 앞단 프록시(openresty)가 앱에 어떤 스킴·포워딩 정보를 전달하는지 확인한다.
 * HTTP→HTTPS 강제 리다이렉트를 앱에서 루프 없이 구현할 수 있는지 판단하기 위한 것으로,
 * 클라이언트 IP 등 민감한 값은 반환하지 않는다(스킴·포트·프로토 값과 프록시 관련 헤더 이름만).
 * 판단이 끝나면 제거한다.
 */
@RestController
public class RequestProtoProbeController {

    @GetMapping(value = "/api/security/proto-probe", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> probe(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("secure", request.isSecure());
        body.put("scheme", request.getScheme());
        body.put("serverPort", request.getServerPort());
        body.put("forwardedProto", first(request.getHeader("X-Forwarded-Proto")));
        body.put("forwardedScheme", first(request.getHeader("X-Forwarded-Scheme")));
        body.put("forwardedPort", first(request.getHeader("X-Forwarded-Port")));
        body.put("forwardedSsl", first(request.getHeader("X-Forwarded-Ssl")));
        body.put("frontEndHttps", first(request.getHeader("Front-End-Https")));
        body.put("forwarded", first(request.getHeader("Forwarded")));
        List<String> proxyHeaderNames = new ArrayList<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("forward") || lower.equals("via") || lower.equals("x-real-ip")
                    || lower.equals("x-scheme") || lower.equals("x-url-scheme") || lower.equals("front-end-https")
                    || lower.startsWith("cf-") || lower.equals("x-client-port")) {
                proxyHeaderNames.add(lower);
            }
        }
        body.put("proxyHeaderNames", proxyHeaderNames);
        return body;
    }

    private static String first(String value) {
        if (value == null) {
            return null;
        }
        int comma = value.indexOf(',');
        return (comma >= 0 ? value.substring(0, comma) : value).trim();
    }
}
