package com.sc1hub.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteRateLimiterTest {

    @Test
    void allowsUpToTheLimitPerKeyAndKeysAreIndependent() {
        WriteRateLimiter limiter = new WriteRateLimiter();
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allow("a", 3, 60_000L));
        }
        assertFalse(limiter.allow("a", 3, 60_000L));
        assertTrue(limiter.allow("b", 3, 60_000L));
        assertEquals(4, limiter.count("a"));
        assertEquals(1, limiter.count("b"));
        assertEquals(0, limiter.count("missing"));
    }

    @Test
    void evictsOldestWindowsInsteadOfRefusingNewKeysWhenTheTableIsFull() {
        WriteRateLimiter limiter = new WriteRateLimiter();
        for (int i = 0; i < WriteRateLimiter.MAX_ENTRIES; i++) {
            assertTrue(limiter.allow("k" + i, 10, 60_000L));
        }
        assertTrue(limiter.allow("one-too-many", 10, 60_000L), "상한에 도달해도 새 작성자를 거부하지 않는다");
        assertEquals(1, limiter.count("one-too-many"));
        assertEquals(0, limiter.count("k0"), "가장 오래된 윈도가 밀려난다");
        int last = WriteRateLimiter.MAX_ENTRIES - 1;
        assertEquals(1, limiter.count("k" + last), "최근 윈도는 유지된다");
        assertTrue(limiter.allow("k" + last, 10, 60_000L), "기존 키는 계속 동작한다");
    }

    @Test
    void keepsTheTableBoundedUnderAFloodOfNewKeys() {
        WriteRateLimiter limiter = new WriteRateLimiter();
        for (int i = 0; i < WriteRateLimiter.MAX_ENTRIES * 2; i++) {
            assertTrue(limiter.allow("flood" + i, 10, 60_000L));
        }
        assertEquals(0, limiter.count("flood0"));
        assertEquals(1, limiter.count("flood" + (WriteRateLimiter.MAX_ENTRIES * 2 - 1)));
        assertTrue(limiter.size() <= WriteRateLimiter.MAX_ENTRIES);
    }
}
