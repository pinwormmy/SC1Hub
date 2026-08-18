package com.sc1hub.strategytip.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ExecutorConfigurationSupport;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StrategyTipAiConfigTest {

    @Test
    void propertiesUseCostBoundedDefaults() {
        StrategyTipAiProperties properties = new StrategyTipAiProperties();

        assertFalse(properties.isEnabled());
        assertFalse(properties.isAllowLiveCalls());
        assertEquals("", properties.getApiKey());
        assertEquals("https://api.openai.com/v1/responses",
                properties.getBaseUrl());
        assertEquals("gpt-5.6-luna", properties.getModel());
        assertEquals("high", properties.getReasoningEffort());
        assertEquals(6000, properties.getMaxOutputTokens());
        assertEquals(1, properties.getDailyDraftLimit());
        assertEquals(30, properties.getMaxPendingDrafts());
        assertEquals(2, properties.getMaxDailyApiCalls());
    }

    @Test
    void configCreatesDedicatedRestTemplateAndSingleThreadExecutor() {
        StrategyTipAiConfig config = new StrategyTipAiConfig();

        assertNotNull(config.strategyTipAiRestTemplate(new RestTemplateBuilder()));
        TaskExecutor taskExecutor = config.strategyTipAiExecutor();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) taskExecutor;
        try {
            assertEquals(1, executor.getCorePoolSize());
            assertEquals(1, executor.getMaxPoolSize());
            assertEquals(1, executor.getThreadPoolExecutor().getQueue().remainingCapacity());
            assertEquals(true, ReflectionTestUtils.getField(
                    executor, ExecutorConfigurationSupport.class,
                    "waitForTasksToCompleteOnShutdown"));
            assertEquals(40_000L, ReflectionTestUtils.getField(
                    executor, ExecutorConfigurationSupport.class, "awaitTerminationMillis"));
        } finally {
            executor.shutdown();
        }
    }
}
