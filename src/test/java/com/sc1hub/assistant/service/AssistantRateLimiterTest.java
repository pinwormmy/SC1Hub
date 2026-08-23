package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.member.dto.MemberDTO;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantRateLimiterTest {

    @Test
    void tryConsume_limitsAnonymousByIpPerDay() {
        AssistantProperties props = new AssistantProperties();
        props.setEnabled(true);
        props.setAnonymousDailyLimit(3);
        props.setMemberDailyLimit(10);
        props.setAdminUnlimited(true);
        props.setAdminGrade(3);

        Clock clock = Clock.fixed(Instant.parse("2025-12-18T00:00:00Z"), ZoneId.of("UTC"));
        AssistantRateLimiter limiter = new AssistantRateLimiter(props, clock);

        assertTrue(limiter.tryConsume(null, "1.2.3.4", "s1").isAllowed());
        assertTrue(limiter.tryConsume(null, "1.2.3.4", "s1").isAllowed());
        assertTrue(limiter.tryConsume(null, "1.2.3.4", "s1").isAllowed());
        assertFalse(limiter.tryConsume(null, "1.2.3.4", "s1").isAllowed());
    }

    @Test
    void tryConsume_limitsMemberByIdPerDay() {
        AssistantProperties props = new AssistantProperties();
        props.setEnabled(true);
        props.setAnonymousDailyLimit(3);
        props.setMemberDailyLimit(10);
        props.setAdminUnlimited(true);
        props.setAdminGrade(3);

        Clock clock = Clock.fixed(Instant.parse("2025-12-18T00:00:00Z"), ZoneId.of("UTC"));
        AssistantRateLimiter limiter = new AssistantRateLimiter(props, clock);

        MemberDTO member = new MemberDTO();
        member.setId("user1");

        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryConsume(member, "9.9.9.9", "s1").isAllowed());
        }
        assertFalse(limiter.tryConsume(member, "9.9.9.9", "s1").isAllowed());
    }

    @Test
    void tryConsume_allowsUnlimitedForAdminGrade() {
        AssistantProperties props = new AssistantProperties();
        props.setEnabled(true);
        props.setAnonymousDailyLimit(3);
        props.setMemberDailyLimit(10);
        props.setAdminUnlimited(true);
        props.setAdminGrade(3);

        Clock clock = Clock.fixed(Instant.parse("2025-12-18T00:00:00Z"), ZoneId.of("UTC"));
        AssistantRateLimiter limiter = new AssistantRateLimiter(props, clock);

        MemberDTO admin = new MemberDTO();
        admin.setId("someone");
        admin.setGrade(3);

        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.tryConsume(admin, "9.9.9.9", "s1").isAllowed());
        }
    }

    @Test
    void tryConsume_enforcesLimitAcrossConcurrentRequests() throws Exception {
        AssistantProperties props = new AssistantProperties();
        props.setEnabled(true);
        props.setAnonymousDailyLimit(10);

        Clock clock = Clock.fixed(Instant.parse("2025-12-18T00:00:00Z"), ZoneId.of("UTC"));
        AssistantRateLimiter limiter = new AssistantRateLimiter(props, clock);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                tasks.add(() -> limiter.tryConsume(null, "1.2.3.4", "s1").isAllowed());
            }

            int allowed = 0;
            for (Future<Boolean> result : executor.invokeAll(tasks)) {
                if (result.get()) {
                    allowed++;
                }
            }
            assertEquals(10, allowed);
        } finally {
            executor.shutdownNow();
        }
    }
}
