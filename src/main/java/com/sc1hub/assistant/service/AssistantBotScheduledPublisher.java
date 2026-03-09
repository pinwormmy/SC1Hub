package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantBotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AssistantBotScheduledPublisher {

    private final AssistantBotService assistantBotService;
    private final AssistantBotProperties botProperties;

    public AssistantBotScheduledPublisher(AssistantBotService assistantBotService,
                                         AssistantBotProperties botProperties) {
        this.assistantBotService = assistantBotService;
        this.botProperties = botProperties;
    }

    @Scheduled(cron = "${sc1hub.assistant.bot.autoPublishCron:0 * * * * *}",
            zone = "${sc1hub.assistant.bot.autoPublishZone:Asia/Seoul}")
    @SuppressWarnings("unused")
    public void autoPublish() {
        if (!botProperties.isEnabled() || !botProperties.isAutoPublishEnabled()) {
            return;
        }

        try {
            AssistantBotService.AutoPublishResult result = assistantBotService.autoPublishOnce();
            if ("published".equals(result.getOutcome())) {
                log.info("봇 자동 발행 완료. personaName={}, mode={}, historyId={}, publishedPostNum={}, redirectUrl={}",
                        result.getPersonaName(), result.getMode(), result.getHistoryId(), result.getPublishedPostNum(), result.getRedirectUrl());
            } else if ("failed".equals(result.getOutcome())) {
                log.error("봇 자동 발행 실패. personaName={}, detail={}", result.getPersonaName(), result.getDetail());
            } else {
                log.debug("봇 자동 발행 스킵. personaName={}, detail={}", result.getPersonaName(), result.getDetail());
            }
        } catch (Exception e) {
            log.error("봇 자동 발행 실행 실패", e);
        }
    }
}
