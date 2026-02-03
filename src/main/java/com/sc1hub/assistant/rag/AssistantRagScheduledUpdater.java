package com.sc1hub.assistant.rag;

import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.search.AssistantSearchTermsIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AssistantRagScheduledUpdater {

    private final AssistantRagIndexService ragIndexService;
    private final AssistantSearchTermsIndexService searchTermsIndexService;
    private final AssistantRagProperties ragProperties;

    public AssistantRagScheduledUpdater(AssistantRagIndexService ragIndexService,
                                        AssistantSearchTermsIndexService searchTermsIndexService,
                                        AssistantRagProperties ragProperties) {
        this.ragIndexService = ragIndexService;
        this.searchTermsIndexService = searchTermsIndexService;
        this.ragProperties = ragProperties;
    }

    @Scheduled(cron = "${sc1hub.assistant.rag.autoUpdate.cron:0 0 5 * * *}",
            zone = "${sc1hub.assistant.rag.autoUpdate.zone:}")
    @SuppressWarnings("unused")
    public void autoUpdate() {
        if (ragProperties.getAutoUpdate() == null || !ragProperties.getAutoUpdate().isEnabled()) {
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

        try {
            AssistantSearchTermsIndexService.Status status = searchTermsIndexService.getStatus();
            if (status != null && status.isRunning()) {
                log.info("search_terms 자동 업데이트 스킵: 이미 실행 중");
                return;
            }

            AssistantSearchTermsIndexService.ReindexResult result = searchTermsIndexService.reindexAllDefault();
            log.info("search_terms 자동 업데이트 완료. boards={}, scannedPosts={}, updatedPosts={}, batchSize={}, failedBoards={}",
                    result.getBoardCount(), result.getScannedPosts(), result.getUpdatedPosts(), result.getBatchSize(), result.getFailedBoards());
        } catch (Exception e) {
            log.error("search_terms 자동 업데이트 실패", e);
        }
    }
}
