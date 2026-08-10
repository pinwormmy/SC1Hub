package com.sc1hub.strategytip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.strategytip.ai.client.GeminiStrategyTipClient;
import com.sc1hub.strategytip.ai.client.GeminiStrategyTipException;
import com.sc1hub.strategytip.ai.client.StrategyTipAiGeneratedBatch;
import com.sc1hub.strategytip.ai.config.StrategyTipAiProperties;
import com.sc1hub.strategytip.dto.StrategyTipAiDraftDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyTipAiDraftServiceTest {

    private static final LocalDate GENERATION_DATE = LocalDate.of(1970, 1, 1);
    private static final LocalDateTime NOW = LocalDateTime.of(1970, 1, 1, 12, 0);
    private static final List<String> CATEGORIES =
            Arrays.asList("t_vs_z", "t_vs_p", "t_vs_t");
    private static final List<String> BOARDS =
            Arrays.asList("tvszboard", "tvspboard", "tvstboard");
    private static final List<String> CONTENTS = Arrays.asList(
            "저그의 초반 압박을 확인하면 입구 수비 동선을 먼저 정리하세요.",
            "프로토스의 정찰 경로를 살핀 뒤 병력 진출 방향을 조정하세요.",
            "상대 생산 건물 움직임을 보며 중앙 시야를 천천히 확보하세요."
    );

    @Mock
    private StrategyTipAiDraftStore store;

    @Mock
    private GeminiStrategyTipClient geminiClient;

    private StrategyTipAiProperties properties;
    private StrategyTipAiDraftService service;

    @BeforeEach
    void setUp() {
        properties = new StrategyTipAiProperties();
        properties.setEnabled(true);
        properties.setAllowLiveCalls(true);
        properties.setApiKey("test-gemini-key");
        properties.setModel("gemini-test-model");
        properties.setDailyDraftLimit(3);
        properties.setMaxPendingDrafts(3);
        properties.setMaxDailyApiCalls(1);
        properties.setStaleRunMinutes(10);
        properties.setSourcePostsPerCategory(3);
        properties.setSourceExcerptChars(480);
        properties.setDuplicateContextLimit(20);
        properties.setWriter("SC1Hub");
        service = new StrategyTipAiDraftService(
                store, geminiClient, properties, new ObjectMapper());
    }

    @Test
    void generateDailyDrafts_skipsWithoutTouchingStoreOrClientWhenDisabled() {
        properties.setEnabled(false);

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("SKIPPED", result.getOutcome());
        assertTrue(result.getMessage().contains("비활성화"));
        verifyNoInteractions(store, geminiClient);
    }

    @Test
    void generateDailyDrafts_skipsWithoutApiKey() {
        properties.setApiKey("  ");

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("SKIPPED", result.getOutcome());
        assertTrue(result.getMessage().contains("API 키"));
        verifyNoInteractions(store, geminiClient);
    }

    @Test
    void generateDailyDrafts_skipsWhenLiveCallsAreNotExplicitlyAllowed() {
        properties.setAllowLiveCalls(false);

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("SKIPPED", result.getOutcome());
        assertTrue(result.getMessage().contains("실호출"));
        verifyNoInteractions(store, geminiClient);
    }

    @Test
    void generateDailyDrafts_skipsAtAbsoluteDailyLimit() {
        when(store.countGeneratedOn(GENERATION_DATE)).thenReturn(3);

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("SKIPPED", result.getOutcome());
        assertTrue(result.getMessage().contains("이미 채웠"));
        verifyNoInteractions(geminiClient);
    }

    @Test
    void generateDailyDrafts_skipsWhenPendingReviewQueueHasThreeDrafts() {
        when(store.countGeneratedOn(GENERATION_DATE)).thenReturn(0);
        when(store.countPending()).thenReturn(3);

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("SKIPPED", result.getOutcome());
        assertTrue(result.getMessage().contains("검수 대기"));
        verifyNoInteractions(geminiClient);
    }

    @Test
    void generateDailyDrafts_skipsWhenDailyApiClaimIsUnavailable() {
        properties.setMaxDailyApiCalls(99);
        when(store.countGeneratedOn(GENERATION_DATE)).thenReturn(0);
        when(store.countPending()).thenReturn(0);
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(0);

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("SKIPPED", result.getOutcome());
        assertTrue(result.getMessage().contains("호출 상한"));
        verify(store).claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10));
        verify(store, never()).getUsedSlots(GENERATION_DATE);
        verifyNoInteractions(geminiClient);
    }

    @Test
    void generateDailyDrafts_callsGeminiOnceAndSavesExactlyThreeGroundedDrafts() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        StrategyTipAiGeneratedBatch batch = validBatch(3, false);
        when(geminiClient.generate(anyString(), anyString(), eq(3), anyList(), anyList()))
                .thenReturn(batch);

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("CREATED", result.getOutcome());
        assertEquals(3, result.getCreatedCount());
        verify(geminiClient, times(1)).generate(
                anyString(), contains("SOURCE_DATA_JSON"), eq(3), eq(CATEGORIES),
                eq(Arrays.asList("tvszboard:101", "tvspboard:102", "tvstboard:103")));

        ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftsCaptor = draftListCaptor();
        verify(store).saveGeneratedDrafts(
                eq(GENERATION_DATE), eq(1), draftsCaptor.capture(), eq(120), eq(45), eq(3));
        List<StrategyTipAiDraftDTO> drafts = draftsCaptor.getValue();
        assertEquals(3, drafts.size());
        for (int index = 0; index < drafts.size(); index++) {
            StrategyTipAiDraftDTO draft = drafts.get(index);
            assertEquals(index + 1, draft.getSlotNo());
            assertEquals(CATEGORIES.get(index), draft.getCategory());
            assertEquals(BOARDS.get(index), draft.getSourceBoard());
            assertEquals(101 + index, draft.getSourcePostNum());
            assertEquals(externalUrl(index), draft.getExternalSourceUrl());
            assertEquals("외부 전략 가이드 " + (index + 1), draft.getExternalSourceTitle());
            assertEquals("gemini-test-model", draft.getModel());
        }
    }

    @Test
    void generateDailyDrafts_generatesOnlyTheTwoRemainingDailySlots() {
        arrangeReadyGeneration(1, 0, Collections.emptyList(),
                Collections.singletonList(1), true);
        when(geminiClient.generate(anyString(), anyString(), eq(2),
                eq(Arrays.asList("t_vs_p", "t_vs_t")),
                eq(Arrays.asList("tvspboard:102", "tvstboard:103"))))
                .thenReturn(validBatchForIndexes(false, 1, 2));

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("CREATED", result.getOutcome());
        assertEquals(2, result.getCreatedCount());
        ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftsCaptor = draftListCaptor();
        verify(store).saveGeneratedDrafts(
                eq(GENERATION_DATE), eq(1), draftsCaptor.capture(), eq(120), eq(45), eq(2));
        assertEquals(2, draftsCaptor.getValue().size());
        assertEquals(2, draftsCaptor.getValue().get(0).getSlotNo());
        assertEquals("t_vs_p", draftsCaptor.getValue().get(0).getCategory());
        assertEquals(3, draftsCaptor.getValue().get(1).getSlotNo());
        assertEquals("t_vs_t", draftsCaptor.getValue().get(1).getCategory());
        verify(store, never()).getSourcePosts("tvszboard", 3);
    }

    @Test
    void generateDailyDrafts_keepsCategoryBoundToItsSlotWhenModelReordersDrafts() {
        arrangeReadyGeneration(1, 0, Collections.emptyList(),
                Collections.singletonList(2), true);
        List<StrategyTipAiGeneratedBatch.Draft> reordered = new ArrayList<>();
        reordered.add(validDraftForIndex(2, false));
        reordered.add(validDraftForIndex(0, false));
        when(geminiClient.generate(anyString(), anyString(), eq(2),
                eq(Arrays.asList("t_vs_z", "t_vs_t")),
                eq(Arrays.asList("tvszboard:101", "tvstboard:103"))))
                .thenReturn(batch(reordered, 2, citationsForIndexes(2, 0)));

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("CREATED", result.getOutcome());
        ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftsCaptor = draftListCaptor();
        verify(store).saveGeneratedDrafts(
                eq(GENERATION_DATE), eq(1), draftsCaptor.capture(), eq(120), eq(45), eq(2));
        assertEquals("t_vs_t", draftsCaptor.getValue().get(0).getCategory());
        assertEquals(3, draftsCaptor.getValue().get(0).getSlotNo());
        assertEquals("t_vs_z", draftsCaptor.getValue().get(1).getCategory());
        assertEquals(1, draftsCaptor.getValue().get(1).getSlotNo());
    }

    @Test
    void generateDailyDrafts_allowsExternalOnlyDraftsWithoutFabricatingInternalEvidence() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), false);
        when(geminiClient.generate(anyString(), anyString(), eq(3), anyList(), anyList()))
                .thenReturn(validBatch(3, true));

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("CREATED", result.getOutcome());
        ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftsCaptor = draftListCaptor();
        verify(store).saveGeneratedDrafts(
                eq(GENERATION_DATE), eq(1), draftsCaptor.capture(), eq(120), eq(45), eq(3));
        for (StrategyTipAiDraftDTO draft : draftsCaptor.getValue()) {
            assertEquals(0, draft.getSourcePostNum());
            assertEquals("", draft.getSourceExcerpt());
            assertTrue(draft.getEvidenceSummary().contains("사이트 내부 근거 없음"));
            assertTrue(draft.getExternalSourceUrl().startsWith("https://"));
        }
    }

    @Test
    void generateDailyDrafts_failsAndMarksRunForInvalidCategory() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(3, false);
        StrategyTipAiGeneratedBatch.Draft first = drafts.get(0);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                "honey_tip", first.getContent(), first.getSourceId(), first.getEvidenceSummary(),
                first.getExternalSourceUrl(), first.getExternalSourceTitle(),
                first.getExternalEvidenceSummary()));

        assertInvalidBatchFails(batch(drafts, 3, citations(3)), "카테고리");
    }

    @Test
    void generateDailyDrafts_failsAndMarksRunForCategoryMismatchedSource() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(3, false);
        StrategyTipAiGeneratedBatch.Draft first = drafts.get(0);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                first.getCategory(), first.getContent(), "tvspboard:102", first.getEvidenceSummary(),
                first.getExternalSourceUrl(), first.getExternalSourceTitle(),
                first.getExternalEvidenceSummary()));

        assertInvalidBatchFails(batch(drafts, 3, citations(3)), "근거 출처");
    }

    @Test
    void generateDailyDrafts_failsAndMarksRunForUngroundedNumericClaim() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(3, false);
        StrategyTipAiGeneratedBatch.Draft first = drafts.get(0);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                first.getCategory(), "초반 9분에는 입구 수비 동선을 먼저 정리하세요.",
                first.getSourceId(), first.getEvidenceSummary(), first.getExternalSourceUrl(),
                first.getExternalSourceTitle(), first.getExternalEvidenceSummary()));

        assertInvalidBatchFails(batch(drafts, 3, citations(3)), "숫자");
    }

    @Test
    void generateDailyDrafts_rejectsGeneratedContentAbove96Characters() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(3, false);
        StrategyTipAiGeneratedBatch.Draft first = drafts.get(0);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                first.getCategory(), repeated("가", 97), first.getSourceId(),
                first.getEvidenceSummary(), first.getExternalSourceUrl(),
                first.getExternalSourceTitle(), first.getExternalEvidenceSummary()));

        assertInvalidBatchFails(batch(drafts, 3, citations(3)), "12~96");
    }

    @Test
    void generateDailyDrafts_rejectsInternalEvidenceAbove72Characters() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(3, false);
        StrategyTipAiGeneratedBatch.Draft first = drafts.get(0);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                first.getCategory(), first.getContent(), first.getSourceId(), repeated("나", 73),
                first.getExternalSourceUrl(), first.getExternalSourceTitle(),
                first.getExternalEvidenceSummary()));

        assertInvalidBatchFails(batch(drafts, 3, citations(3)), "근거 설명 길이");
    }

    @Test
    void generateDailyDrafts_rejectsExternalOnlyInternalEvidenceAbove72Characters() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), false);
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(3, true);
        StrategyTipAiGeneratedBatch.Draft first = drafts.get(0);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                first.getCategory(), first.getContent(), first.getSourceId(), repeated("나", 73),
                first.getExternalSourceUrl(), first.getExternalSourceTitle(),
                first.getExternalEvidenceSummary()));

        assertInvalidBatchFails(batch(drafts, 3, citations(3)), "근거 설명 길이");
    }

    @Test
    void generateDailyDrafts_rejectsExternalEvidenceAbove72Characters() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(3, false);
        StrategyTipAiGeneratedBatch.Draft first = drafts.get(0);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                first.getCategory(), first.getContent(), first.getSourceId(),
                first.getEvidenceSummary(), first.getExternalSourceUrl(),
                first.getExternalSourceTitle(), repeated("다", 73)));

        assertInvalidBatchFails(batch(drafts, 3, citations(3)), "외부 근거 설명 길이");
    }

    @Test
    void generateDailyDrafts_failsForNumericClaimWhenOnlyExternalSourceExists() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), false);
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(3, true);
        StrategyTipAiGeneratedBatch.Draft first = drafts.get(0);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                first.getCategory(), "초반 9분에는 입구 수비 동선을 먼저 정리하세요.",
                first.getSourceId(), first.getEvidenceSummary(), first.getExternalSourceUrl(),
                first.getExternalSourceTitle(), first.getExternalEvidenceSummary()));

        assertInvalidBatchFails(batch(drafts, 3, citations(3)), "숫자");
    }

    @Test
    void generateDailyDrafts_failsAndMarksRunForDuplicateContent() {
        arrangeReadyGeneration(0, 0, Collections.singletonList(CONTENTS.get(0)),
                Collections.emptyList(), true);

        assertInvalidBatchFails(validBatch(3, false), "유사");
    }

    @Test
    void generateDailyDrafts_requiresOneNativeSearchCitationPerRequestedCategory() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);

        assertInvalidBatchFails(batch(validDrafts(3, false), 2, citations(3)),
                "정확히 한 번");
    }

    @Test
    void generateDailyDrafts_rejectsSearchQueriesAboveThePerCategoryBudget() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);

        assertInvalidBatchFails(batch(validDrafts(3, false), 4, citations(3)),
                "정확히 한 번");
    }

    @Test
    void generateDailyDrafts_rejectsExternalUrlMissingFromNativeCitations() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(3, false);
        StrategyTipAiGeneratedBatch.Draft first = drafts.get(0);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                first.getCategory(), first.getContent(), first.getSourceId(),
                first.getEvidenceSummary(), "https://uncited.example.org/guide",
                "인용되지 않은 가이드", first.getExternalEvidenceSummary()));

        assertInvalidBatchFails(batch(drafts, 3, citations(3)), "외부 출처");
    }

    @Test
    void generateDailyDrafts_rejectsIpv4LiteralExternalSource() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        assertIpLiteralExternalSourceRejected("https://192.168.1.10/guide");
    }

    @Test
    void generateDailyDrafts_rejectsIpv6LiteralExternalSource() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        assertIpLiteralExternalSourceRejected("https://[2001:db8::1]/guide");
    }

    @Test
    void generateDailyDrafts_recordsUsageCarriedByGeminiFailure() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        GeminiStrategyTipException failure = new GeminiStrategyTipException(
                "Gemini structured output failed", null, 321, 87, 3);
        when(geminiClient.generate(anyString(), anyString(), eq(3), anyList(), anyList()))
                .thenThrow(failure);

        assertThrows(GeminiStrategyTipException.class,
                () -> service.generateDailyDrafts(GENERATION_DATE, NOW));

        verify(store).failDailyRun(GENERATION_DATE, 1,
                "Gemini structured output failed", 321, 87, 3);
    }

    @Test
    void approve_revalidatesEditedContentAgainstPendingDraftAndPublishedTips() {
        StrategyTipAiDraftDTO draft = pendingDraft(41L, "t_vs_z",
                "상대가 5분에 진출하면 입구 시야부터 확인합니다.");
        when(store.getPendingDraft(41L)).thenReturn(draft);
        when(store.getRecentPublishedContents(20))
                .thenReturn(Collections.singletonList("중앙 시야를 확보하면 병력 동선을 조정하세요."));
        when(store.approve(41L, "t_vs_z", "상대가 5분에 진출하면 입구 시야부터 확인하세요.",
                "admin", "SC1Hub")).thenReturn(501);

        int tipNum = service.approve(41L, "t_vs_z",
                "  상대가 5분에 진출하면  입구 시야부터 확인하세요. ", "admin");

        assertEquals(501, tipNum);
        verify(store).approve(41L, "t_vs_z",
                "상대가 5분에 진출하면 입구 시야부터 확인하세요.", "admin", "SC1Hub");
    }

    @Test
    void approve_rejectsMissingOrNonPendingDraftBeforeCasApproval() {
        when(store.getPendingDraft(42L)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approve(42L, "t_vs_z",
                        "입구 시야를 확인한 뒤 병력 동선을 조정하세요.", "admin"));

        assertTrue(exception.getMessage().contains("이미 처리"));
        verify(store, never()).approve(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void approve_rejectsEditedContentOutsideAllowedLength() {
        when(store.getPendingDraft(43L)).thenReturn(pendingDraft(43L, "t_vs_z", "숫자 없는 근거"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approve(43L, "t_vs_z", "짧은 공략", "admin"));

        assertTrue(exception.getMessage().contains("12~160"));
        verify(store, never()).approve(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void approve_allowsEditedContentLongerThanGeneratedLimitUpTo160Characters() {
        String editedContent = repeated("가", 120);
        when(store.getPendingDraft(47L)).thenReturn(
                pendingDraft(47L, "t_vs_z", "숫자 없는 근거"));
        when(store.getRecentPublishedContents(20)).thenReturn(Collections.emptyList());
        when(store.approve(47L, "t_vs_z", editedContent, "admin", "SC1Hub"))
                .thenReturn(507);

        int tipNum = service.approve(47L, "t_vs_z", editedContent, "admin");

        assertEquals(507, tipNum);
        verify(store).approve(47L, "t_vs_z", editedContent, "admin", "SC1Hub");
    }

    @Test
    void approve_rejectsAbsoluteClaimInEditedContent() {
        when(store.getPendingDraft(44L)).thenReturn(pendingDraft(44L, "t_vs_z", "숫자 없는 근거"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approve(44L, "t_vs_z",
                        "정찰을 한 뒤에는 무조건 입구를 막으세요.", "admin"));

        assertTrue(exception.getMessage().contains("단정"));
        verify(store, never()).approve(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void approve_rejectsNumberMissingFromOriginalSourceExcerpt() {
        when(store.getPendingDraft(45L)).thenReturn(pendingDraft(45L, "t_vs_z",
                "상대가 5분에 진출하면 시야를 확보합니다."));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approve(45L, "t_vs_z",
                        "상대가 7분에 진출하면 입구 시야부터 확인하세요.", "admin"));

        assertTrue(exception.getMessage().contains("숫자"));
        verify(store, never()).approve(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void approve_rejectsContentSimilarToRecentPublishedTip() {
        when(store.getPendingDraft(46L)).thenReturn(pendingDraft(46L, "t_vs_z", "숫자 없는 근거"));
        when(store.getRecentPublishedContents(20)).thenReturn(Collections.singletonList(
                "상대 정찰 경로를 확인한 뒤 입구 수비 동선을 조정하세요."));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approve(46L, "t_vs_z",
                        "상대 정찰 경로를 확인한 뒤 입구 수비 동선을 조정하세요.", "admin"));

        assertTrue(exception.getMessage().contains("이미 공개"));
        verify(store, never()).approve(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    private void arrangeReadyGeneration(int generatedToday, int pendingCount,
                                        List<String> recentContents, List<Integer> usedSlots,
                                        boolean internalSources) {
        when(store.countGeneratedOn(GENERATION_DATE)).thenReturn(generatedToday);
        when(store.countPending()).thenReturn(pendingCount);
        if (internalSources) {
            when(store.getSourcePosts(anyString(), eq(3)))
                    .thenAnswer(invocation -> Collections.singletonList(
                            internalPost(invocation.getArgument(0))));
        } else {
            when(store.getSourcePosts(anyString(), eq(3))).thenReturn(Collections.emptyList());
        }
        when(store.claimDailyApiCall(GENERATION_DATE, 1, NOW.minusMinutes(10)))
                .thenReturn(1);
        when(store.getRecentContents(20)).thenReturn(recentContents);
        when(store.getUsedSlots(GENERATION_DATE)).thenReturn(usedSlots);
    }

    private BoardDTO internalPost(String boardTitle) {
        int index = BOARDS.indexOf(boardTitle);
        if (index < 0) {
            index = 3;
        }
        BoardDTO post = new BoardDTO();
        post.setPostNum(101 + index);
        post.setTitle(boardTitle + " 내부 공략");
        post.setContent("이 내부 공략은 상대 정찰과 병력 동선을 먼저 확인하라고 설명합니다.");
        return post;
    }

    private StrategyTipAiGeneratedBatch validBatch(int count, boolean externalOnly) {
        return batch(validDrafts(count, externalOnly), count, citations(count));
    }

    private StrategyTipAiGeneratedBatch validBatchForIndexes(boolean externalOnly, int... indexes) {
        List<StrategyTipAiGeneratedBatch.Draft> drafts = new ArrayList<>();
        for (int index : indexes) {
            drafts.add(validDraftForIndex(index, externalOnly));
        }
        return batch(drafts, indexes.length, citationsForIndexes(indexes));
    }

    private StrategyTipAiGeneratedBatch batch(List<StrategyTipAiGeneratedBatch.Draft> drafts,
                                               int searchQueryCount,
                                               Map<String, String> citations) {
        return new StrategyTipAiGeneratedBatch(
                drafts, "gemini-test-model", 120, 45, searchQueryCount, citations);
    }

    private List<StrategyTipAiGeneratedBatch.Draft> validDrafts(int count, boolean externalOnly) {
        List<StrategyTipAiGeneratedBatch.Draft> drafts = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            drafts.add(validDraftForIndex(index, externalOnly));
        }
        return drafts;
    }

    private StrategyTipAiGeneratedBatch.Draft validDraftForIndex(int index,
                                                                  boolean externalOnly) {
        String sourceId = externalOnly
                ? "external-only:" + CATEGORIES.get(index)
                : BOARDS.get(index) + ":" + (101 + index);
        return new StrategyTipAiGeneratedBatch.Draft(
                CATEGORIES.get(index), CONTENTS.get(index), sourceId,
                "내부 글의 정찰과 병력 동선 설명이 공략을 뒷받침합니다.",
                externalUrl(index), "모델이 제안한 출처 제목 " + (index + 1),
                "외부 자료의 정찰과 병력 운용 설명이 같은 행동을 뒷받침합니다."
        );
    }

    private Map<String, String> citations(int count) {
        Map<String, String> citations = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            citations.put(externalUrl(index), "외부 전략 가이드 " + (index + 1));
        }
        return citations;
    }

    private Map<String, String> citationsForIndexes(int... indexes) {
        Map<String, String> citations = new LinkedHashMap<>();
        for (int index : indexes) {
            citations.put(externalUrl(index), "외부 전략 가이드 " + (index + 1));
        }
        return citations;
    }

    private void assertIpLiteralExternalSourceRejected(String url) {
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(3, false);
        StrategyTipAiGeneratedBatch.Draft first = drafts.get(0);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                first.getCategory(), first.getContent(), first.getSourceId(),
                first.getEvidenceSummary(), url, first.getExternalSourceTitle(),
                first.getExternalEvidenceSummary()));
        Map<String, String> citations = citations(3);
        citations.put(url, "IP 리터럴 출처");

        assertInvalidBatchFails(batch(drafts, 3, citations), "안전하지");
    }

    private StrategyTipAiDraftDTO pendingDraft(long draftId, String category,
                                                String sourceExcerpt) {
        StrategyTipAiDraftDTO draft = new StrategyTipAiDraftDTO();
        draft.setDraftId(draftId);
        draft.setStatus("PENDING");
        draft.setCategory(category);
        draft.setSourceExcerpt(sourceExcerpt);
        return draft;
    }

    private String externalUrl(int index) {
        return "https://strategy.example.org/brood-war/guide-" + (index + 1);
    }

    private String repeated(String value, int count) {
        return String.join("", Collections.nCopies(count, value));
    }

    private void assertInvalidBatchFails(StrategyTipAiGeneratedBatch invalidBatch,
                                         String expectedMessagePart) {
        when(geminiClient.generate(anyString(), anyString(), eq(invalidBatch.getDrafts().size()),
                anyList(), anyList())).thenReturn(invalidBatch);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.generateDailyDrafts(GENERATION_DATE, NOW));

        assertTrue(exception.getMessage().contains(expectedMessagePart));
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).failDailyRun(eq(GENERATION_DATE), eq(1), errorCaptor.capture(),
                eq(120), eq(45), eq(invalidBatch.getSearchQueryCount()));
        assertTrue(errorCaptor.getValue().contains(expectedMessagePart));
        verify(store, never()).saveGeneratedDrafts(
                eq(GENERATION_DATE), anyInt(), anyList(), anyInt(), anyInt(), anyInt());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftListCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
