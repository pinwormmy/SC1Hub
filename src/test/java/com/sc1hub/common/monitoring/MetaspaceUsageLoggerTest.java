package com.sc1hub.common.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void pausesAiWorkAtTheConfiguredThreshold() {
        assertFalse(MetaspaceUsageLogger.isAtOrAboveThreshold(55000, 65536, 85));
        assertTrue(MetaspaceUsageLogger.isAtOrAboveThreshold(55706, 65536, 85));
        assertFalse(MetaspaceUsageLogger.isAtOrAboveThreshold(
                65536,
                MetaspaceUsageLogger.UNBOUNDED,
                85
        ));
    }

    @Test
    void backgroundWorkYieldsBeforeUserFacingAiWork() {
        // 측정된 정상 구간(약 88%)에서는 어느 쪽도 멈추지 않는다.
        assertFalse(MetaspaceUsageLogger.isAtOrAboveThreshold(57505, 65536, 92));
        assertFalse(MetaspaceUsageLogger.isAtOrAboveThreshold(57505, 65536, 96));

        // 92%에서는 예약 작업만 멈춘다.
        assertTrue(MetaspaceUsageLogger.isAtOrAboveThreshold(60294, 65536, 92));
        assertFalse(MetaspaceUsageLogger.isAtOrAboveThreshold(60294, 65536, 96));

        // 96%부터 사용자 대면 AI까지 멈춘다.
        assertTrue(MetaspaceUsageLogger.isAtOrAboveThreshold(62915, 65536, 92));
        assertTrue(MetaspaceUsageLogger.isAtOrAboveThreshold(62915, 65536, 96));
    }

    @Test
    void samplingTheLiveJvmDoesNotThrow() {
        new MetaspaceUsageLogger(85, 92, 96).sampleMetaspaceUsage();
    }
}
