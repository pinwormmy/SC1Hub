package com.sc1hub.strategytip.service;

import com.sc1hub.strategytip.ai.config.StrategyTipAiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyTipAiDraftSchedulerTest {

    @Mock
    private StrategyTipAiDraftService draftService;

    @Mock
    private StrategyTipAiProperties properties;

    @Mock
    private TaskExecutor taskExecutor;

    @Test
    void scheduleDailyDraftGeneration_doesNothingWhenDisabled() {
        when(properties.isEnabled()).thenReturn(false);
        StrategyTipAiDraftScheduler scheduler = scheduler();

        scheduler.scheduleDailyDraftGeneration();

        verify(taskExecutor, never()).execute(any(Runnable.class));
        verify(draftService, never()).generateDailyDrafts();
    }

    @Test
    void scheduleDailyDraftGeneration_doesNothingWithoutLiveCallOptIn() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isAllowLiveCalls()).thenReturn(false);
        StrategyTipAiDraftScheduler scheduler = scheduler();

        scheduler.scheduleDailyDraftGeneration();

        verify(taskExecutor, never()).execute(any(Runnable.class));
        verify(draftService, never()).generateDailyDrafts();
    }

    @Test
    void scheduleDailyDraftGeneration_submitsWorkToDedicatedExecutor() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isAllowLiveCalls()).thenReturn(true);
        when(draftService.generateDailyDrafts())
                .thenReturn(StrategyTipAiDraftService.GenerationResult.created(3));
        StrategyTipAiDraftScheduler scheduler = scheduler();

        scheduler.scheduleDailyDraftGeneration();

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskExecutor).execute(taskCaptor.capture());
        verify(draftService, never()).generateDailyDrafts();

        taskCaptor.getValue().run();

        verify(draftService).generateDailyDrafts();
    }

    @Test
    void scheduledTask_swallowsGenerationFailureSoExecutorThreadSurvives() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isAllowLiveCalls()).thenReturn(true);
        doThrow(new IllegalStateException("generation failed"))
                .when(draftService).generateDailyDrafts();
        TaskExecutor directExecutor = Runnable::run;
        StrategyTipAiDraftScheduler scheduler = new StrategyTipAiDraftScheduler(
                draftService, properties, directExecutor);

        assertDoesNotThrow(scheduler::scheduleDailyDraftGeneration);

        verify(draftService).generateDailyDrafts();
    }

    @Test
    void scheduleDailyDraftGeneration_swallowsExecutorRejection() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isAllowLiveCalls()).thenReturn(true);
        doThrow(new IllegalStateException("executor full"))
                .when(taskExecutor).execute(any(Runnable.class));
        StrategyTipAiDraftScheduler scheduler = scheduler();

        assertDoesNotThrow(scheduler::scheduleDailyDraftGeneration);

        verify(draftService, never()).generateDailyDrafts();
    }

    private StrategyTipAiDraftScheduler scheduler() {
        return new StrategyTipAiDraftScheduler(draftService, properties, taskExecutor);
    }
}
