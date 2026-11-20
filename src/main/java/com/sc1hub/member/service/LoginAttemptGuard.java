package com.sc1hub.member.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 계정 단위 온라인 브루트포스 차단. Cafe24 프록시 뒤에서는 원격 주소가 프록시로
 * 뭉개지고 X-Forwarded-For는 위조 가능하므로, IP가 아니라 계정 ID를 키로 잠근다.
 * (공격자가 남의 계정을 몇 분 잠글 수 있는 대신, 잠금 우회는 불가능해진다.)
 */
@Component
public class LoginAttemptGuard {

    static final int MAX_FAILURES = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(5);
    static final int MAX_TRACKED_ACCOUNTS = 10_000;
    // 접속 주소 기준: 여러 계정을 돌아가며 시도하는 무차별 대입을 막는다(프록시 헤더가 신뢰될 때만 사용).
    static final int IP_MAX_FAILURES = 30;
    static final Duration IP_LOCKOUT = Duration.ofMinutes(15);
    private static final int MAX_KEY_LENGTH = 100;
    private static final String IP_KEY_PREFIX = "ip:";

    private final Clock clock;
    private final Object cleanupLock = new Object();
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptGuard() {
        this(Clock.systemUTC());
    }

    LoginAttemptGuard(Clock clock) {
        this.clock = clock;
    }

    /** 잠긴 계정이면 true. 로그인 검증을 시작하기 전에 호출한다. */
    public boolean isBlocked(String memberId) {
        String key = key(memberId);
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        if (isExpired(attempt)) {
            attempts.remove(key, attempt);
            return false;
        }
        return attempt.failures >= MAX_FAILURES;
    }

    public void recordFailure(String memberId) {
        String key = key(memberId);
        ensureCapacity(key);
        attempts.compute(key, (ignored, current) -> {
            Instant now = clock.instant();
            if (current == null || isExpired(current)) {
                return new Attempt(1, now);
            }
            return new Attempt(current.failures + 1, now);
        });
    }

    private void ensureCapacity(String incomingKey) {
        if (attempts.size() < MAX_TRACKED_ACCOUNTS || attempts.containsKey(incomingKey)) {
            return;
        }
        synchronized (cleanupLock) {
            if (attempts.size() < MAX_TRACKED_ACCOUNTS || attempts.containsKey(incomingKey)) {
                return;
            }
            // 만료된 항목을 먼저 회수하고(IP 항목은 15분, 계정 항목은 5분 기준),
            // 그래도 상한을 넘으면 가장 오래된 항목을 밀어낸다.
            attempts.entrySet().removeIf(this::isExpiredEntry);
            while (attempts.size() >= MAX_TRACKED_ACCOUNTS) {
                if (!evictOldest()) {
                    break;
                }
            }
        }
    }

    private boolean isExpiredEntry(Map.Entry<String, Attempt> entry) {
        Duration lockout = entry.getKey().startsWith(IP_KEY_PREFIX) ? IP_LOCKOUT : LOCKOUT;
        return isExpired(entry.getValue(), lockout);
    }

    /** 가장 오래 실패 기록이 갱신되지 않은 항목 하나를 제거한다. 제거할 항목이 없으면 false. */
    private boolean evictOldest() {
        String oldestKey = null;
        Instant oldest = null;
        for (Map.Entry<String, Attempt> entry : attempts.entrySet()) {
            Instant last = entry.getValue().lastFailure();
            if (oldest == null || last.isBefore(oldest)) {
                oldest = last;
                oldestKey = entry.getKey();
            }
        }
        return oldestKey != null && attempts.remove(oldestKey) != null;
    }

    public void reset(String memberId) {
        attempts.remove(key(memberId));
    }

    /** 접속 주소가 잠겨 있으면 true. */
    public boolean isIpBlocked(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String key = IP_KEY_PREFIX + key(ip);
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        if (isExpired(attempt, IP_LOCKOUT)) {
            attempts.remove(key, attempt);
            return false;
        }
        return attempt.failures >= IP_MAX_FAILURES;
    }

    public void recordIpFailure(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        String key = IP_KEY_PREFIX + key(ip);
        ensureCapacity(key);
        attempts.compute(key, (ignored, current) -> {
            Instant now = clock.instant();
            if (current == null || isExpired(current, IP_LOCKOUT)) {
                return new Attempt(1, now);
            }
            return new Attempt(current.failures + 1, now);
        });
    }

    private boolean isExpired(Attempt attempt) {
        return isExpired(attempt, LOCKOUT);
    }

    private boolean isExpired(Attempt attempt, Duration lockout) {
        return attempt.lastFailure.plus(lockout).isBefore(clock.instant());
    }

    private String key(String memberId) {
        if (memberId == null) {
            return "";
        }
        String normalized = memberId.trim().toLowerCase(Locale.ROOT);
        // 비정상적으로 긴 입력이 메모리에 그대로 쌓이지 않도록 키 길이를 제한한다.
        return normalized.length() > MAX_KEY_LENGTH ? normalized.substring(0, MAX_KEY_LENGTH) : normalized;
    }

    private record Attempt(int failures, Instant lastFailure) {
    }
}
