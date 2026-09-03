package com.sc1hub.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 브라우저 세션 없이 AI 작업이 관리자 권한으로 호출할 때 쓰는 콘텐츠 API 토큰 검사.
 * 경로 제한은 호출하는 쪽(AdminInterceptor, AssistantMaintenanceAccess)이 정한다.
 */
@Component
public class ContentApiTokenAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${sc1hub.content-api.token:}")
    private String contentApiToken = "";

    public ContentApiTokenAuthenticator() {
    }

    public ContentApiTokenAuthenticator(String contentApiToken) {
        this.contentApiToken = contentApiToken;
    }

    public boolean hasValidToken(HttpServletRequest request) {
        if (request == null || contentApiToken == null || contentApiToken.trim().isEmpty()) {
            return false;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] expected = contentApiToken.trim().getBytes(StandardCharsets.UTF_8);
        byte[] actual = authorization.substring(BEARER_PREFIX.length()).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
