package com.sc1hub.common.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

/**
 * Samples Metaspace usage on a fixed delay so the fixed 64 MB cap gets a trend
 * instead of a single reading at deploy time. Cafe24 general hosting provides no
 * crontab, so the application itself has to take the samples.
 */
@Component
@Slf4j
public class MetaspaceUsageLogger {

    static final long UNBOUNDED = -1L;

    private static final String POOL_NAME = "Metaspace";
    private static final long BYTES_PER_KB = 1024L;

    private final int warnPercent;

    public MetaspaceUsageLogger(
            @Value("${sc1hub.monitoring.metaspaceWarnPercent:85}") int warnPercent) {
        this.warnPercent = warnPercent;
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

    static long toMaxKb(long maxBytes) {
        return maxBytes <= 0 ? UNBOUNDED : maxBytes / BYTES_PER_KB;
    }

    static long percentOf(long usedKb, long maxKb) {
        return maxKb <= 0 ? UNBOUNDED : usedKb * 100 / maxKb;
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
