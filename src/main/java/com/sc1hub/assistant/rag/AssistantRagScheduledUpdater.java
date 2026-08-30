package com.sc1hub.assistant.rag;

import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.common.monitoring.MetaspaceUsageLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "sc1hub.assistant.rag.autoUpdate", name = "enabled", havingValue = "true")
@Slf4j
public class AssistantRagScheduledUpdater {

    private final AssistantRagIndexService ragIndexService;
    private final AssistantRagProperties ragProperties;
    private final MetaspaceUsageLogger metaspaceUsageLogger;

    public AssistantRagScheduledUpdater(AssistantRagIndexService ragIndexService,
                                        AssistantRagProperties ragProperties,
                                        MetaspaceUsageLogger metaspaceUsageLogger) {
        this.ragIndexService = ragIndexService;
        this.ragProperties = ragProperties;
        this.metaspaceUsageLogger = metaspaceUsageLogger;
    }

    @Scheduled(cron = "${sc1hub.assistant.rag.autoUpdate.cron:0 0 5 * * *}",
            zone = "${sc1hub.assistant.rag.autoUpdate.zone:}")
    @SuppressWarnings("unused")
    public void autoUpdate() {
        if (ragProperties.getAutoUpdate() == null || !ragProperties.getAutoUpdate().isEnabled()) {
            return;
        }
        if (metaspaceUsageLogger.shouldPauseAiWork()) {
            log.warn("RAG 자동 업데이트 유예: JVM Metaspace 여유가 부족합니다.");
            return;
        }

        try {
            AssistantRagIndexService.UpdateResult result = ragIndexService.update();
            if (!result.isEnabled()) {
                log.info("RAG 자동 업데이트 스킵: rag.enabled=false");
            } else if (!result.isReady()) {
                log.info("RAG 자동 업데이트 스킵: 인덱스가 없습니다. reindex가 필요합니다. path={}", result.getIndexPath());
            } else {
                log.info("RAG 자동 업데이트 완료. updatedPosts={}, updatedChunks={}, dimension={}, path={}",
                        result.getUpdatedPosts(), result.getUpdatedChunks(), result.getDimension(), result.getIndexPath());
            }
        } catch (Exception e) {
            log.error("RAG 자동 업데이트 실패", e);
        }
    }
}
