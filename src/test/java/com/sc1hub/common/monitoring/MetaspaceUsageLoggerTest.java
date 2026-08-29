package com.sc1hub.common.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetaspaceUsageLoggerTest {

    @Test
    void reportsPercentageAgainstTheConfiguredCap() {
        assertEquals(75, MetaspaceUsageLogger.percentOf(49155, 65536));
        assertEquals(87, MetaspaceUsageLogger.percentOf(57186, 65536));
        assertEquals(100, MetaspaceUsageLogger.percentOf(65536, 65536));
    }

    @Test
    void treatsAnAbsentCapAsUnbounded() {
        assertEquals(MetaspaceUsageLogger.UNBOUNDED, MetaspaceUsageLogger.toMaxKb(-1L));
        assertEquals(MetaspaceUsageLogger.UNBOUNDED, MetaspaceUsageLogger.toMaxKb(0L));
        assertEquals(MetaspaceUsageLogger.UNBOUNDED, MetaspaceUsageLogger.percentOf(1000, MetaspaceUsageLogger.UNBOUNDED));
    }

    @Test
    void convertsByteCapToKilobytes() {
        assertEquals(65536, MetaspaceUsageLogger.toMaxKb(67108864L));
    }

    @Test
    void samplingTheLiveJvmDoesNotThrow() {
        new MetaspaceUsageLogger(85).sampleMetaspaceUsage();
    }
}
