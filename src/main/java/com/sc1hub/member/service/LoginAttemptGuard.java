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
    private static final int MAX_TRACKED_ACCOUNTS = 10_000;

    private final Clock clock;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptGuard() {
        this(Clock.systemUTC());
    }

    LoginAttemptGuard(Clock clock) {
        this.clock = clock;
    }

    /** 잠긴 계정이면 true. 로그인 검증을 시작하기 전에 호출한다. */
    public boolean isBlocked(String memberId) {
        Attempt attempt = attempts.get(key(memberId));
        if (attempt == null) {
            return false;
        }
        if (isExpired(attempt)) {
            attempts.remove(key(memberId));
            return false;
        }
        return attempt.failures >= MAX_FAILURES;
    }

    public void recordFailure(String memberId) {
        if (attempts.size() >= MAX_TRACKED_ACCOUNTS) {
            attempts.entrySet().removeIf(entry -> isExpired(entry.getValue()));
        }
        attempts.compute(key(memberId), (ignored, current) -> {
            Instant now = clock.instant();
            if (current == null || isExpired(current)) {
                return new Attempt(1, now);
            }
            return new Attempt(current.failures + 1, now);
        });
    }

    public void reset(String memberId) {
        attempts.remove(key(memberId));
    }

    private boolean isExpired(Attempt attempt) {
        return attempt.lastFailure.plus(LOCKOUT).isBefore(clock.instant());
    }

    private String key(String memberId) {
        return memberId == null ? "" : memberId.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempt(int failures, Instant lastFailure) {
    }
}
