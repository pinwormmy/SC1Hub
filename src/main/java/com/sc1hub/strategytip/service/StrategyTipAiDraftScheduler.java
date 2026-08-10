package com.sc1hub.strategytip.service;

import com.sc1hub.strategytip.ai.config.StrategyTipAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StrategyTipAiDraftScheduler {

    private final StrategyTipAiDraftService draftService;
    private final StrategyTipAiProperties properties;
    private final TaskExecutor taskExecutor;

    public StrategyTipAiDraftScheduler(StrategyTipAiDraftService draftService,
                                       StrategyTipAiProperties properties,
                                       @Qualifier("strategyTipAiExecutor") TaskExecutor taskExecutor) {
        this.draftService = draftService;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(cron = "${sc1hub.strategy-tip.ai.schedulerCron:0 5 * * * *}",
            zone = "${sc1hub.strategy-tip.ai.schedulerZone:Asia/Seoul}")
    @SuppressWarnings("unused")
    public void scheduleDailyDraftGeneration() {
        if (!properties.isEnabled() || !properties.isAllowLiveCalls()) {
            return;
        }
        try {
            taskExecutor.execute(this::generateSafely);
        } catch (RuntimeException e) {
            log.warn("AI 한줄 공략 생성 작업을 예약하지 못했습니다. type={}",
                    e.getClass().getSimpleName());
        }
    }

    private void generateSafely() {
        try {
            StrategyTipAiDraftService.GenerationResult result = draftService.generateDailyDrafts();
            log.info("AI 한줄 공략 예약 생성 결과. outcome={}, createdCount={}",
                    result.getOutcome(), result.getCreatedCount());
        } catch (RuntimeException e) {
            log.error("AI 한줄 공략 예약 생성 실패. type={}", e.getClass().getSimpleName());
        }
    }
}
