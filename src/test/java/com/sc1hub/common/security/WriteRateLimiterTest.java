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
    void refusesNewKeysWhenTheTableIsFullOfLiveWindows() {
        WriteRateLimiter limiter = new WriteRateLimiter();
        for (int i = 0; i < WriteRateLimiter.MAX_ENTRIES; i++) {
            assertTrue(limiter.allow("k" + i, 10, 60_000L));
        }
        assertFalse(limiter.allow("one-too-many", 10, 60_000L), "상한을 넘는 새 키는 거부해 메모리를 보호한다");
        assertTrue(limiter.allow("k0", 10, 60_000L), "기존 키는 계속 동작한다");
    }
}
