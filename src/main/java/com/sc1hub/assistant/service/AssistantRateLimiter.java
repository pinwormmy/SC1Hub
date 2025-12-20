package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.member.dto.MemberDTO;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AssistantRateLimiter {

    private static final int MAX_COUNTER_SIZE_BEFORE_CLEANUP = 5000;

    private final AssistantProperties assistantProperties;
    private final Clock clock;
    private final Map<String, DailyCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    @SuppressWarnings("unused")
    public AssistantRateLimiter(AssistantProperties assistantProperties) {
        this(assistantProperties, Clock.systemDefaultZone());
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
        boolean unlimited = assistantProperties.isAdminUnlimited() && isAdmin(member);
        if (limit <= 0 && !unlimited) {
            return RateLimitResult.denied(0, 0);
        }

        String key = buildKey(member, ip, sessionId);
        LocalDate today = LocalDate.now(clock);

        DailyCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || !today.equals(existing.date)) {
                return new DailyCounter(today);
            }
            return existing;
        });

        int current;
        int used;
        synchronized (counter.lock) {
            if (!today.equals(counter.date)) {
                counter.date = today;
                counter.count.set(0);
            }
            current = counter.count.get();
            if (!unlimited && current >= limit) {
                return RateLimitResult.denied(limit, current);
            }
            used = counter.count.incrementAndGet();
        }

        maybeCleanup(today);

        if (unlimited) {
            return RateLimitResult.allowedUnlimited(used);
        }

        return RateLimitResult.allowed(limit, used);
    }

    private void maybeCleanup(LocalDate today) {
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

    private boolean isAdmin(MemberDTO member) {
        if (member == null) {
            return false;
        }
        if (member.getGrade() == assistantProperties.getAdminGrade()) {
            return true;
        }
        String adminId = assistantProperties.getAdminId();
        if (!StringUtils.hasText(adminId)) {
            return false;
        }
        return adminId.equals(member.getId());
    }

    private static final class DailyCounter {
        private final Object lock = new Object();
        private volatile LocalDate date;
        private final AtomicInteger count = new AtomicInteger(0);

        private DailyCounter(LocalDate date) {
            this.date = date;
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
