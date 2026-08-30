package com.sc1hub.common.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Samples Metaspace usage on a fixed delay so the fixed 64 MB cap gets a trend
 * instead of a single reading at deploy time. Cafe24 general hosting provides no
 * crontab, so the application itself has to take the samples.
 *
 * <p>Loaded classes are never unloaded here, so usage only ever climbs within one
 * JVM lifetime and a pause is effectively permanent until the next restart. The
 * thresholds are therefore tiered: expendable background work yields first, and
 * user-facing AI keeps running until the cap is genuinely close.
 */
@Component
@Slf4j
public class MetaspaceUsageLogger {

    static final long UNBOUNDED = -1L;

    private static final String POOL_NAME = "Metaspace";
    private static final long BYTES_PER_KB = 1024L;

    private final int warnPercent;
    private final int backgroundPausePercent;
    private final int aiPausePercent;

    private final AtomicBoolean backgroundPaused = new AtomicBoolean(false);
    private final AtomicBoolean outboundPaused = new AtomicBoolean(false);

    public MetaspaceUsageLogger(
            @Value("${sc1hub.monitoring.metaspaceWarnPercent:85}") int warnPercent,
            @Value("${sc1hub.monitoring.metaspaceBackgroundPausePercent:92}") int backgroundPausePercent,
            @Value("${sc1hub.monitoring.metaspaceAiPausePercent:96}") int aiPausePercent) {
        this.warnPercent = warnPercent;
        this.backgroundPausePercent = backgroundPausePercent;
        this.aiPausePercent = aiPausePercent;
    }

    @Scheduled(
            initialDelayString = "${sc1hub.monitoring.metaspaceInitialDelayMillis:60000}",
            fixedDelayString = "${sc1hub.monitoring.metaspaceSampleMillis:600000}")
    public void sampleMetaspaceUsage() {
        MemoryUsage usage = findMetaspaceUsage();
        if (usage == null) {
            return;
        }

        long usedKb = usage.getUsed() / BYTES_PER_KB;
        long maxKb = toMaxKb(usage.getMax());
        long percent = percentOf(usedKb, maxKb);

        if (percent == UNBOUNDED) {
            log.info("metaspace used={}KB max=unbounded", usedKb);
        } else if (percent >= warnPercent) {
            log.warn("metaspace used={}KB max={}KB pct={}", usedKb, maxKb, percent);
        } else {
            log.info("metaspace used={}KB max={}KB pct={}", usedKb, maxKb, percent);
        }
    }

    /**
     * Scheduled bot publishing and RAG index updates. These repeat on their own
     * schedule, so skipping them costs nothing the next run cannot recover.
     */
    public boolean shouldPauseBackgroundAiWork() {
        return evaluate(backgroundPausePercent, backgroundPaused, "scheduled AI background work");
    }

    /**
     * Every outbound AI call, including user-facing search. Reserved for the point
     * where the cap is close enough that any further class loading is a risk.
     */
    public boolean shouldPauseAiWork() {
        return evaluate(aiPausePercent, outboundPaused, "all outbound AI calls");
    }

    private boolean evaluate(int thresholdPercent, AtomicBoolean state, String label) {
        MemoryUsage usage = findMetaspaceUsage();
        if (usage == null) {
            return false;
        }

        long usedKb = usage.getUsed() / BYTES_PER_KB;
        long maxKb = toMaxKb(usage.getMax());
        boolean paused = isAtOrAboveThreshold(usedKb, maxKb, thresholdPercent);

        // Log only the transition. These are polled per request and per minute.
        if (state.compareAndSet(!paused, paused)) {
            if (paused) {
                log.warn("Pausing {}: metaspace used={}KB max={}KB pct={} threshold={}%",
                        label, usedKb, maxKb, percentOf(usedKb, maxKb), thresholdPercent);
            } else {
                log.warn("Resuming {}: metaspace used={}KB max={}KB pct={} threshold={}%",
                        label, usedKb, maxKb, percentOf(usedKb, maxKb), thresholdPercent);
            }
        }
        return paused;
    }

    static long toMaxKb(long maxBytes) {
        return maxBytes <= 0 ? UNBOUNDED : maxBytes / BYTES_PER_KB;
    }

    static long percentOf(long usedKb, long maxKb) {
        return maxKb <= 0 ? UNBOUNDED : usedKb * 100 / maxKb;
    }

    static boolean isAtOrAboveThreshold(long usedKb, long maxKb, int thresholdPercent) {
        long percent = percentOf(usedKb, maxKb);
        return percent != UNBOUNDED && percent >= thresholdPercent;
    }

    private MemoryUsage findMetaspaceUsage() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (POOL_NAME.equals(pool.getName())) {
                return pool.getUsage();
            }
        }
        return null;
    }
}
