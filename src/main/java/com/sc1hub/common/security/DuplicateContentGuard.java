package com.sc1hub.common.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 같은 내용을 짧은 시간에 반복 등록하는 도배를 막는다. 내용은 태그·공백·대소문자를 정규화한 해시로만
 * 기억하고(원문 보관 없음), 항목 수 상한을 두어 메모리를 보호한다.
 */
@Component
public class DuplicateContentGuard {

    static final int MAX_ENTRIES = 5_000;

    private final Map<String, Long> recentHashes = new LinkedHashMap<>();

    /**
     * 최근 {@code windowMillis} 안에 같은 내용이 등록됐으면 true(기록하지 않음). 아니면 기록하고 false.
     * {@code actorKey}가 null 이면 작성자와 무관하게 전역으로 판정한다. 짧은 내용({@code minLength} 미만)은
     * 인사말 같은 정상 반복이 많아 검사하지 않는다.
     */
    public synchronized boolean isDuplicate(String scope, String actorKey, String content, int minLength,
                                            long windowMillis) {
        String normalized = normalize(content);
        if (normalized.length() < minLength) {
            return false;
        }
        String key = scope + ":" + (actorKey == null ? "*" : actorKey) + ":" + sha256(normalized);
        long now = System.currentTimeMillis();
        Long seenAt = recentHashes.get(key);
        if (seenAt != null && now - seenAt < windowMillis) {
            return true;
        }
        recentHashes.remove(key);
        recentHashes.put(key, now);
        while (recentHashes.size() > MAX_ENTRIES) {
            Iterator<String> oldest = recentHashes.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return false;
    }

    static String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
