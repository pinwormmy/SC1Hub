package com.sc1hub.strategytip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.strategytip.ai.client.GeminiStrategyTipClient;
import com.sc1hub.strategytip.ai.client.GeminiStrategyTipException;
import com.sc1hub.strategytip.ai.client.StrategyTipAiGeneratedBatch;
import com.sc1hub.strategytip.ai.config.StrategyTipAiProperties;
import com.sc1hub.strategytip.dto.StrategyTipAiDraftDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
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
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        properties = new StrategyTipAiProperties();
        properties.setEnabled(true);
        properties.setAllowLiveCalls(true);
        properties.setApiKey("test-gemini-key");
        properties.setModel("gemini-test-model");
        properties.setDailyDraftLimit(3);
        properties.setMaxPendingDrafts(3);
        properties.setMaxDailyApiCalls(3);
        properties.setStaleRunMinutes(10);
        properties.setSourcePostsPerCategory(3);
        properties.setSourceExcerptChars(480);
        properties.setDuplicateContextLimit(20);
        properties.setWriter("SC1Hub");
        service = new StrategyTipAiDraftService(
                store, geminiClient, properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
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
    void generateDailyDrafts_rejectsInvalidEndpointBeforeClaimingPaidCall() {
        properties.setBaseUrl("https://example.org/v1beta/interactions");

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("SKIPPED", result.getOutcome());
        assertTrue(result.getMessage().contains("API 주소"));
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
    void generateDailyDrafts_clampsActualCallBudgetToThree() {
        properties.setMaxDailyApiCalls(99);
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        when(store.claimDailyApiCall(GENERATION_DATE, 3, NOW.minusMinutes(10)))
                .thenReturn(0);

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("SKIPPED", result.getOutcome());
        assertTrue(result.getMessage().contains("호출 상한"));
        verify(store).claimDailyApiCall(GENERATION_DATE, 3, NOW.minusMinutes(10));
        verifyNoInteractions(geminiClient);
    }

    @Test
    void generateDailyDrafts_callsOncePerCategoryAndSavesThreeSlotsWithActualUsage() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        when(store.claimDailyApiCall(GENERATION_DATE, 3, NOW.minusMinutes(10)))
                .thenReturn(1, 2, 3);
        when(geminiClient.generate(anyString(), anyString(), eq(1), anyList(), anyList()))
                .thenReturn(singleBatch(0, false, 101, 21),
                        singleBatch(1, false, 102, 22),
                        singleBatch(2, false, 103, 23));

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("CREATED", result.getOutcome());
        assertEquals(3, result.getCreatedCount());
        verify(geminiClient).generate(anyString(), contains("SOURCE_DATA_JSON"), eq(1),
                eq(Collections.singletonList("t_vs_z")),
                eq(Collections.singletonList("tvszboard:101")));
        verify(geminiClient).generate(anyString(), contains("SOURCE_DATA_JSON"), eq(1),
                eq(Collections.singletonList("t_vs_p")),
                eq(Collections.singletonList("tvspboard:102")));
        verify(geminiClient).generate(anyString(), contains("SOURCE_DATA_JSON"), eq(1),
                eq(Collections.singletonList("t_vs_t")),
                eq(Collections.singletonList("tvstboard:103")));

        ArgumentCaptor<Integer> attemptCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftsCaptor = draftListCaptor();
        ArgumentCaptor<Integer> inputCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> outputCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(store, times(3)).saveGeneratedDrafts(
                eq(GENERATION_DATE), attemptCaptor.capture(), draftsCaptor.capture(),
                inputCaptor.capture(), outputCaptor.capture(), eq(1));
        assertEquals(Arrays.asList(1, 2, 3), attemptCaptor.getAllValues());
        assertEquals(Arrays.asList(101, 102, 103), inputCaptor.getAllValues());
        assertEquals(306, inputCaptor.getAllValues().stream().mapToInt(Integer::intValue).sum());
        assertEquals(66, outputCaptor.getAllValues().stream().mapToInt(Integer::intValue).sum());
        for (int index = 0; index < draftsCaptor.getAllValues().size(); index++) {
            List<StrategyTipAiDraftDTO> saved = draftsCaptor.getAllValues().get(index);
            assertEquals(1, saved.size());
            StrategyTipAiDraftDTO draft = saved.get(0);
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
    void generateDailyDrafts_secondCallFailurePreservesFirstAndStopsBeforeThird() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        when(store.claimDailyApiCall(GENERATION_DATE, 3, NOW.minusMinutes(10)))
                .thenReturn(1, 2);
        GeminiStrategyTipException failure = new GeminiStrategyTipException(
                "second interaction failed", null, 222, 44, 1);
        when(geminiClient.generate(anyString(), anyString(), eq(1), anyList(), anyList()))
                .thenReturn(singleBatch(0, false, 111, 33))
                .thenThrow(failure);

        assertThrows(GeminiStrategyTipException.class,
                () -> service.generateDailyDrafts(GENERATION_DATE, NOW));

        ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftsCaptor = draftListCaptor();
        verify(store).saveGeneratedDrafts(
                eq(GENERATION_DATE), eq(1), draftsCaptor.capture(), eq(111), eq(33), eq(1));
        assertEquals(1, draftsCaptor.getValue().size());
        assertEquals(1, draftsCaptor.getValue().get(0).getSlotNo());
        verify(store).failDailyRun(GENERATION_DATE, 2,
                "second interaction failed", 222, 44, 1);
        verify(geminiClient, times(2)).generate(
                anyString(), anyString(), eq(1), anyList(), anyList());
        verify(store, times(2)).claimDailyApiCall(
                GENERATION_DATE, 3, NOW.minusMinutes(10));
        verify(store, never()).getSourcePosts("tvstboard", 3);
    }

    @Test
    void generateDailyDrafts_laterTopUpUsesRemainingSlotsAndCallBudget() {
        arrangeReadyGeneration(1, 0, Collections.emptyList(),
                Collections.singletonList(1), true);
        when(store.claimDailyApiCall(GENERATION_DATE, 3, NOW.minusMinutes(10)))
                .thenReturn(2, 3);
        when(geminiClient.generate(anyString(), anyString(), eq(1), anyList(), anyList()))
                .thenReturn(singleBatch(1, false), singleBatch(2, false));

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("CREATED", result.getOutcome());
        assertEquals(2, result.getCreatedCount());
        ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftsCaptor = draftListCaptor();
        verify(store, times(2)).saveGeneratedDrafts(
                eq(GENERATION_DATE), anyInt(), draftsCaptor.capture(), eq(120), eq(45), eq(1));
        assertEquals(2, draftsCaptor.getAllValues().get(0).get(0).getSlotNo());
        assertEquals("t_vs_p", draftsCaptor.getAllValues().get(0).get(0).getCategory());
        assertEquals(3, draftsCaptor.getAllValues().get(1).get(0).getSlotNo());
        assertEquals("t_vs_t", draftsCaptor.getAllValues().get(1).get(0).getCategory());
        verify(store, times(2)).claimDailyApiCall(
                GENERATION_DATE, 3, NOW.minusMinutes(10));
        verify(store, never()).getSourcePosts("tvszboard", 3);
    }

    @Test
    void generateDailyDrafts_returnsCreatedCountWhenCallBudgetEndsAfterPartialSuccess() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        when(store.claimDailyApiCall(GENERATION_DATE, 3, NOW.minusMinutes(10)))
                .thenReturn(3, 0);
        when(geminiClient.generate(anyString(), anyString(), eq(1), anyList(), anyList()))
                .thenReturn(singleBatch(0, false));

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("CREATED", result.getOutcome());
        assertEquals(1, result.getCreatedCount());
        verify(geminiClient).generate(anyString(), anyString(), eq(1), anyList(), anyList());
        verify(store).saveGeneratedDrafts(
                eq(GENERATION_DATE), eq(3), anyList(), eq(120), eq(45), eq(1));
    }

    @Test
    void generateDailyDrafts_skipsConcurrentInvocationInsideOneJvm() throws Exception {
        arrangeReadyGeneration(2, 0, Collections.emptyList(), Arrays.asList(1, 2), true);
        when(store.claimDailyApiCall(GENERATION_DATE, 3, NOW.minusMinutes(10)))
                .thenReturn(3);
        CountDownLatch enteredClient = new CountDownLatch(1);
        CountDownLatch releaseClient = new CountDownLatch(1);
        when(geminiClient.generate(anyString(), anyString(), eq(1), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    enteredClient.countDown();
                    assertTrue(releaseClient.await(5, TimeUnit.SECONDS));
                    return singleBatch(2, false);
                });
        executor = Executors.newSingleThreadExecutor();
        Future<StrategyTipAiDraftService.GenerationResult> first = executor.submit(
                () -> service.generateDailyDrafts(GENERATION_DATE, NOW));
        assertTrue(enteredClient.await(5, TimeUnit.SECONDS));

        StrategyTipAiDraftService.GenerationResult concurrent =
                service.generateDailyDrafts(GENERATION_DATE, NOW);
        releaseClient.countDown();

        assertEquals("SKIPPED", concurrent.getOutcome());
        assertTrue(concurrent.getMessage().contains("이미 실행 중"));
        assertEquals("CREATED", first.get(5, TimeUnit.SECONDS).getOutcome());
        verify(geminiClient).generate(anyString(), anyString(), eq(1), anyList(), anyList());
    }

    @Test
    void generateDailyDrafts_allowsExternalOnlyDraftWithoutFabricatingInternalEvidence() {
        arrangeReadyGeneration(2, 0, Collections.emptyList(), Arrays.asList(1, 2), false);
        when(store.claimDailyApiCall(GENERATION_DATE, 3, NOW.minusMinutes(10)))
                .thenReturn(3);
        when(geminiClient.generate(anyString(), anyString(), eq(1), anyList(), anyList()))
                .thenReturn(singleBatch(2, true));

        StrategyTipAiDraftService.GenerationResult result =
                service.generateDailyDrafts(GENERATION_DATE, NOW);

        assertEquals("CREATED", result.getOutcome());
        ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftsCaptor = draftListCaptor();
        verify(store).saveGeneratedDrafts(
                eq(GENERATION_DATE), eq(3), draftsCaptor.capture(), eq(120), eq(45), eq(1));
        StrategyTipAiDraftDTO draft = draftsCaptor.getValue().get(0);
        assertEquals(0, draft.getSourcePostNum());
        assertEquals("", draft.getSourceExcerpt());
        assertTrue(draft.getEvidenceSummary().contains("사이트 내부 근거 없음"));
    }

    @Test
    void generateDailyDrafts_rejectsMalformedDraftCount() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        StrategyTipAiGeneratedBatch malformed = new StrategyTipAiGeneratedBatch(
                Collections.emptyList(), "gemini-test-model", 120, 45, 1,
                citationsForIndexes(0));

        assertInvalidBatchFails(malformed, "요청한 수");
    }

    @Test
    void generateDailyDrafts_rejectsWrongCategory() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        StrategyTipAiGeneratedBatch.Draft first = validDraftForIndex(0, false);
        StrategyTipAiGeneratedBatch.Draft invalid = new StrategyTipAiGeneratedBatch.Draft(
                "honey_tip", first.getContent(), first.getSourceId(), first.getEvidenceSummary(),
                first.getExternalSourceUrl(), first.getExternalSourceTitle(),
                first.getExternalEvidenceSummary());

        assertInvalidBatchFails(singleBatch(invalid, 120, 45, 1, citationsForIndexes(0)),
                "카테고리");
    }

    @Test
    void generateDailyDrafts_rejectsWrongCategorySource() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        StrategyTipAiGeneratedBatch.Draft first = validDraftForIndex(0, false);
        StrategyTipAiGeneratedBatch.Draft invalid = new StrategyTipAiGeneratedBatch.Draft(
                first.getCategory(), first.getContent(), "tvspboard:102", first.getEvidenceSummary(),
                first.getExternalSourceUrl(), first.getExternalSourceTitle(),
                first.getExternalEvidenceSummary());

        assertInvalidBatchFails(singleBatch(invalid, 120, 45, 1, citationsForIndexes(0)),
                "근거 출처");
    }

    @Test
    void generateDailyDrafts_rejectsCitationMissingFromNativeSearchResults() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        StrategyTipAiGeneratedBatch.Draft first = validDraftForIndex(0, false);
        StrategyTipAiGeneratedBatch.Draft invalid = new StrategyTipAiGeneratedBatch.Draft(
                first.getCategory(), first.getContent(), first.getSourceId(),
                first.getEvidenceSummary(), "https://uncited.example.org/guide",
                first.getExternalSourceTitle(), first.getExternalEvidenceSummary());

        assertInvalidBatchFails(singleBatch(invalid, 120, 45, 1, citationsForIndexes(0)),
                "외부 출처");
    }

    @Test
    void generateDailyDrafts_rejectsUngroundedNumericClaim() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        StrategyTipAiGeneratedBatch.Draft first = validDraftForIndex(0, false);
        StrategyTipAiGeneratedBatch.Draft invalid = new StrategyTipAiGeneratedBatch.Draft(
                first.getCategory(), "초반 9분에는 입구 수비 동선을 먼저 정리하세요.",
                first.getSourceId(), first.getEvidenceSummary(), first.getExternalSourceUrl(),
                first.getExternalSourceTitle(), first.getExternalEvidenceSummary());

        assertInvalidBatchFails(singleBatch(invalid, 120, 45, 1, citationsForIndexes(0)),
                "숫자");
    }

    @Test
    void generateDailyDrafts_rejectsDuplicateContent() {
        arrangeReadyGeneration(0, 0, Collections.singletonList(CONTENTS.get(0)),
                Collections.emptyList(), true);

        assertInvalidBatchFails(singleBatch(0, false), "유사");
    }

    @Test
    void generateDailyDrafts_requiresExactlyOneSearchPerCall() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        StrategyTipAiGeneratedBatch noSearch = singleBatch(
                validDraftForIndex(0, false), 120, 45, 0, citationsForIndexes(0));

        assertInvalidBatchFails(noSearch, "정확히 한 번");
    }

    @Test
    void generateDailyDrafts_recordsUsageCarriedByGeminiFailure() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(), Collections.emptyList(), true);
        when(store.claimDailyApiCall(GENERATION_DATE, 3, NOW.minusMinutes(10)))
                .thenReturn(1);
        GeminiStrategyTipException failure = new GeminiStrategyTipException(
                "Gemini structured output failed", null, 321, 87, 1);
        when(geminiClient.generate(anyString(), anyString(), eq(1), anyList(), anyList()))
                .thenThrow(failure);

        assertThrows(GeminiStrategyTipException.class,
                () -> service.generateDailyDrafts(GENERATION_DATE, NOW));

        verify(store).failDailyRun(GENERATION_DATE, 1,
                "Gemini structured output failed", 321, 87, 1);
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

    private StrategyTipAiGeneratedBatch singleBatch(int index, boolean externalOnly) {
        return singleBatch(index, externalOnly, 120, 45);
    }

    private StrategyTipAiGeneratedBatch singleBatch(int index, boolean externalOnly,
                                                      int inputTokens, int outputTokens) {
        return singleBatch(validDraftForIndex(index, externalOnly), inputTokens, outputTokens,
                1, citationsForIndexes(index));
    }

    private StrategyTipAiGeneratedBatch singleBatch(StrategyTipAiGeneratedBatch.Draft draft,
                                                      int inputTokens, int outputTokens,
                                                      int searchQueryCount,
                                                      Map<String, String> citations) {
        return new StrategyTipAiGeneratedBatch(
                Collections.singletonList(draft), "gemini-test-model",
                inputTokens, outputTokens, searchQueryCount, citations);
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

    private Map<String, String> citationsForIndexes(int... indexes) {
        Map<String, String> citations = new LinkedHashMap<>();
        for (int index : indexes) {
            citations.put(externalUrl(index), "외부 전략 가이드 " + (index + 1));
        }
        return citations;
    }

    private void assertInvalidBatchFails(StrategyTipAiGeneratedBatch invalidBatch,
                                         String expectedMessagePart) {
        when(store.claimDailyApiCall(GENERATION_DATE, 3, NOW.minusMinutes(10)))
                .thenReturn(1);
        when(geminiClient.generate(anyString(), anyString(), eq(1), anyList(), anyList()))
                .thenReturn(invalidBatch);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.generateDailyDrafts(GENERATION_DATE, NOW));

        assertTrue(exception.getMessage().contains(expectedMessagePart));
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).failDailyRun(eq(GENERATION_DATE), eq(1), errorCaptor.capture(),
                eq(invalidBatch.getInputTokens()), eq(invalidBatch.getOutputTokens()),
                eq(invalidBatch.getSearchQueryCount()));
        assertTrue(errorCaptor.getValue().contains(expectedMessagePart));
        verify(store, never()).saveGeneratedDrafts(
                eq(GENERATION_DATE), anyInt(), anyList(), anyInt(), anyInt(), anyInt());
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftListCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
