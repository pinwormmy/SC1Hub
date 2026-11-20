package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.member.dto.MemberDTO;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AssistantRateLimiter {

    private static final ZoneId RATE_LIMIT_ZONE = ZoneId.of("Asia/Seoul");
    static final int MAX_COUNTER_SIZE_BEFORE_CLEANUP = 5000;
    static final int HARD_MAX_COUNTER_SIZE = 20_000;
    private static final int EVICTION_BATCH = 500;

    private final AssistantProperties assistantProperties;
    private final Clock clock;
    private final Object cleanupLock = new Object();
    private final Map<String, DailyCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    public AssistantRateLimiter(AssistantProperties assistantProperties) {
        this(assistantProperties, Clock.system(RATE_LIMIT_ZONE));
    }

    AssistantRateLimiter(AssistantProperties assistantProperties, Clock clock) {
        this.assistantProperties = assistantProperties;
        this.clock = clock;
    }

    public RateLimitResult tryConsume(MemberDTO member, String ip, String sessionId) {
        if (!assistantProperties.isEnabled()) {
            return RateLimitResult.allowedUnlimited(0);
        }

        boolean loggedIn = member != null && StringUtils.hasText(member.getId());
        int limit = loggedIn ? assistantProperties.getMemberDailyLimit() : assistantProperties.getAnonymousDailyLimit();
        boolean unlimited = assistantProperties.isAdminUnlimited() && assistantProperties.isAdmin(member);
        if (limit <= 0 && !unlimited) {
            return RateLimitResult.denied(0, 0);
        }

        String key = buildKey(member, ip, sessionId);
        LocalDate today = LocalDate.now(clock);
        maybeCleanup(today);

        DailyCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || !today.equals(existing.date)) {
                return new DailyCounter(today, clock.millis());
            }
            return existing;
        });

        int current;
        int used;
        synchronized (counter) {
            if (!today.equals(counter.date)) {
                counter.date = today;
                counter.count = 0;
            }
            current = counter.count;
            if (!unlimited && current >= limit) {
                return RateLimitResult.denied(limit, current);
            }
            used = ++counter.count;
            counter.lastAccessMillis = clock.millis();
        }

        if (unlimited) {
            return RateLimitResult.allowedUnlimited(used);
        }

        return RateLimitResult.allowed(limit, used);
    }

    /** 업스트림 API 장애 등 사용자 귀책이 아닌 실패 시 방금 차감한 1회를 복원한다. */
    public void refund(MemberDTO member, String ip, String sessionId) {
        String key = buildKey(member, ip, sessionId);
        DailyCounter counter = counters.get(key);
        if (counter == null) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        synchronized (counter) {
            if (today.equals(counter.date) && counter.count > 0) {
                counter.count--;
            }
        }
    }

    private void maybeCleanup(LocalDate today) {
        if (counters.size() < MAX_COUNTER_SIZE_BEFORE_CLEANUP) {
            return;
        }
        synchronized (cleanupLock) {
            if (counters.size() < MAX_COUNTER_SIZE_BEFORE_CLEANUP) {
                return;
            }
            Iterator<Map.Entry<String, DailyCounter>> iterator = counters.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, DailyCounter> entry = iterator.next();
                DailyCounter counter = entry.getValue();
                if (counter == null || !today.equals(counter.date)) {
                    iterator.remove();
                }
            }
            if (counters.size() >= HARD_MAX_COUNTER_SIZE) {
                int toRemove = counters.size() - HARD_MAX_COUNTER_SIZE + EVICTION_BATCH;
                evictOldest(toRemove);
            }
        }
    }

    private void evictOldest(int count) {
        for (int i = 0; i < count; i++) {
            String oldestKey = null;
            long oldestMillis = Long.MAX_VALUE;
            for (Map.Entry<String, DailyCounter> entry : counters.entrySet()) {
                DailyCounter value = entry.getValue();
                long access = value == null ? 0L : value.lastAccessMillis;
                if (access < oldestMillis) {
                    oldestMillis = access;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                break;
            }
            counters.remove(oldestKey);
        }
    }

    private String buildKey(MemberDTO member, String ip, String sessionId) {
        if (member != null && StringUtils.hasText(member.getId())) {
            return "member:" + member.getId();
        }

        if (StringUtils.hasText(ip)) {
            return "ip:" + ip;
        }

        if (StringUtils.hasText(sessionId)) {
            return "session:" + sessionId;
        }

        return "anonymous:unknown";
    }

    private static final class DailyCounter {
        private volatile LocalDate date;
        private volatile long lastAccessMillis;
        private int count;

        private DailyCounter(LocalDate date, long lastAccessMillis) {
            this.date = date;
            this.lastAccessMillis = lastAccessMillis;
        }
    }

    @Getter
    public static final class RateLimitResult {
        private final boolean allowed;
        private final boolean unlimited;
        private final int limit;
        private final int used;

        private RateLimitResult(boolean allowed, boolean unlimited, int limit, int used) {
            this.allowed = allowed;
            this.unlimited = unlimited;
            this.limit = limit;
            this.used = used;
        }

        public static RateLimitResult allowed(int limit, int used) {
            return new RateLimitResult(true, false, limit, used);
        }

        public static RateLimitResult allowedUnlimited(int used) {
            return new RateLimitResult(true, true, -1, used);
        }

        public static RateLimitResult denied(int limit, int used) {
            return new RateLimitResult(false, false, limit, used);
        }
    }
}
