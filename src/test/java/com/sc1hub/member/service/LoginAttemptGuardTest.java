package com.sc1hub.member.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptGuardTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-01T00:00:00Z"));

    private final Clock movableClock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private final LoginAttemptGuard guard = new LoginAttemptGuard(movableClock);

    @Test
    void blocksOnlyAfterMaxConsecutiveFailures() {
        for (int i = 0; i < LoginAttemptGuard.MAX_FAILURES - 1; i++) {
            guard.recordFailure("tester");
            assertFalse(guard.isBlocked("tester"), (i + 1) + "회 실패까지는 잠기지 않아야 한다");
        }
        guard.recordFailure("tester");
        assertTrue(guard.isBlocked("tester"));
    }

    @Test
    void lockExpiresAfterTheLockoutWindow() {
        for (int i = 0; i < LoginAttemptGuard.MAX_FAILURES; i++) {
            guard.recordFailure("tester");
        }
        assertTrue(guard.isBlocked("tester"));

        now.set(now.get().plus(LoginAttemptGuard.LOCKOUT).plus(Duration.ofSeconds(1)));
        assertFalse(guard.isBlocked("tester"), "잠금 시간이 지나면 다시 시도할 수 있어야 한다");
    }

    @Test
    void successfulLoginResetsTheCounter() {
        for (int i = 0; i < LoginAttemptGuard.MAX_FAILURES - 1; i++) {
            guard.recordFailure("tester");
        }
        guard.reset("tester");
        guard.recordFailure("tester");
        assertFalse(guard.isBlocked("tester"), "성공 후에는 실패 카운터가 0부터 다시 시작해야 한다");
    }

    @Test
    void accountKeyIsCaseAndWhitespaceInsensitive() {
        for (int i = 0; i < LoginAttemptGuard.MAX_FAILURES; i++) {
            guard.recordFailure("Tester ");
        }
        assertTrue(guard.isBlocked("tester"), "대소문자·공백 변형으로 잠금을 우회할 수 없어야 한다");
    }

    @Test
    void otherAccountsAreNotAffected() {
        for (int i = 0; i < LoginAttemptGuard.MAX_FAILURES; i++) {
            guard.recordFailure("tester");
        }
        assertFalse(guard.isBlocked("someoneElse"));
    }
}
