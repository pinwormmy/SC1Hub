package com.sc1hub.strategytip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.strategytip.ai.client.GeminiStrategyTipClient;
import com.sc1hub.strategytip.ai.client.GeminiStrategyTipException;
import com.sc1hub.strategytip.ai.client.StrategyTipAiGeneratedBatch;
import com.sc1hub.strategytip.ai.config.StrategyTipAiProperties;
import com.sc1hub.strategytip.dto.StrategyTipAiDraftDTO;
import com.sc1hub.strategytip.dto.StrategyTipAiStatusDTO;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private static final List<String> CONTENTS = Arrays.asList(
            "초반 압박을 보면 입구 수비 동선을 먼저 정리하세요.",
            "정찰 경로를 살핀 뒤 병력 진출 방향을 조정하세요.",
            "상대 생산 건물을 확인하며 중앙 시야를 확보하세요."
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
        properties.setModel("gemini-3.6-flash");
        properties.setDailyDraftLimit(3);
        properties.setMaxPendingDrafts(30);
        properties.setMaxDailyApiCalls(2);
        properties.setStaleRunMinutes(10);
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
    void generateDailyDrafts_skipsBeforeStoreWhenFeatureIsNotReady() {
        properties.setEnabled(false);
        assertEquals("SKIPPED", generate().getOutcome());
        verifyNoInteractions(store, geminiClient);

        properties.setEnabled(true);
        properties.setAllowLiveCalls(false);
        assertEquals("SKIPPED", generate().getOutcome());
        verifyNoInteractions(store, geminiClient);

        properties.setAllowLiveCalls(true);
        properties.setApiKey(" ");
        assertEquals("SKIPPED", generate().getOutcome());
        verifyNoInteractions(store, geminiClient);
    }

    @Test
    void generateDailyDrafts_rejectsInvalidEndpointBeforeClaimingPaidCall() {
        properties.setBaseUrl("https://example.org/v1beta/interactions");

        StrategyTipAiDraftService.GenerationResult result = generate();

        assertEquals("SKIPPED", result.getOutcome());
        assertTrue(result.getMessage().contains("API 주소"));
        verifyNoInteractions(store, geminiClient);
    }

    @Test
    void generateDailyDrafts_stopsAtDailyOrThirtyPendingLimit() {
        when(store.countGeneratedOn(GENERATION_DATE)).thenReturn(3);
        assertTrue(generate().getMessage().contains("이미 채웠"));
        verifyNoInteractions(geminiClient);

        when(store.countGeneratedOn(GENERATION_DATE)).thenReturn(0);
        when(store.countPending()).thenReturn(30);
        assertTrue(generate().getMessage().contains("30건"));
        verifyNoInteractions(geminiClient);
    }

    @Test
    void generateDailyDrafts_withTwentyNinePendingCreatesOnlyOneDraft() {
        arrangeReadyGeneration(0, 29, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(1);
        when(geminiClient.generate(anyString(), anyString(), eq(1), anyList()))
                .thenReturn(batch(1000, 200, draft(0)));

        StrategyTipAiDraftService.GenerationResult result = generate();

        assertEquals(1, result.getCreatedCount());
        verify(geminiClient).generate(anyString(), anyString(), eq(1),
                eq(Collections.singletonList("t_vs_z")));
    }

    @Test
    void getStatus_clampsPendingLimitToThirty() {
        properties.setMaxPendingDrafts(999);
        when(store.countPending()).thenReturn(0);
        when(store.countGeneratedOn(any(LocalDate.class))).thenReturn(0);

        StrategyTipAiStatusDTO status = service.getStatus();

        assertEquals(30, status.getMaxPendingDrafts());
    }

    @Test
    void generateDailyDrafts_clampsBatchRetryBudgetToTwo() {
        properties.setMaxDailyApiCalls(99);
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(0);

        StrategyTipAiDraftService.GenerationResult result = generate();

        assertEquals("SKIPPED", result.getOutcome());
        assertTrue(result.getMessage().contains("호출 상한"));
        verifyNoInteractions(geminiClient);
    }

    @Test
    void generateDailyDrafts_createsThreeCheckpointDraftsInOneBatch() {
        arrangeReadyGeneration(0, 0, Collections.singletonList("기존 문장입니다."),
                Collections.emptyList(), Collections.emptyList());
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(1);
        when(geminiClient.generate(anyString(), anyString(), eq(3), anyList()))
                .thenReturn(validBatch(4100, 700));

        StrategyTipAiDraftService.GenerationResult result = generate();

        assertEquals("CREATED", result.getOutcome());
        assertEquals(3, result.getCreatedCount());
        verify(geminiClient).generate(
                contains("학습 체크포인트"), contains("GENERATION_REQUEST_JSON"),
                eq(3), eq(CATEGORIES));
        verify(store, never()).getSourcePosts(anyString(), anyInt());

        ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftsCaptor = draftListCaptor();
        verify(store).saveGeneratedDrafts(
                eq(GENERATION_DATE), eq(1), draftsCaptor.capture(), eq(4100), eq(700), eq(0));
        assertEquals(3, draftsCaptor.getValue().size());
        for (int index = 0; index < 3; index++) {
            StrategyTipAiDraftDTO saved = draftsCaptor.getValue().get(index);
            assertEquals(index + 1, saved.getSlotNo());
            assertEquals(CATEGORIES.get(index), saved.getCategory());
            assertEquals("", saved.getEvidenceSummary());
            assertEquals("", saved.getSourceBoard());
            assertEquals(0, saved.getSourcePostNum());
            assertEquals("", saved.getSourceExcerpt());
            assertEquals("", saved.getExternalSourceUrl());
        }
    }

    @Test
    void generateDailyDrafts_topUpUsesRemainingSlotsAndCategories() {
        arrangeReadyGeneration(1, 3, Collections.emptyList(),
                Collections.singletonList(1), Collections.singletonList("t_vs_z"));
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(2);
        when(geminiClient.generate(anyString(), anyString(), eq(2), anyList()))
                .thenReturn(batch(2500, 420, draft(1), draft(2)));

        StrategyTipAiDraftService.GenerationResult result = generate();

        assertEquals(2, result.getCreatedCount());
        verify(geminiClient).generate(anyString(), anyString(), eq(2),
                eq(Arrays.asList("t_vs_p", "t_vs_t")));
        ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftsCaptor = draftListCaptor();
        verify(store).saveGeneratedDrafts(
                eq(GENERATION_DATE), eq(2), draftsCaptor.capture(), eq(2500), eq(420), eq(0));
        assertEquals(2, draftsCaptor.getValue().get(0).getSlotNo());
        assertEquals(3, draftsCaptor.getValue().get(1).getSlotNo());
    }

    @Test
    void generateDailyDrafts_exceptionOneRejectsUnexpectedCategory() {
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts();
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                "honey_tip", CONTENTS.get(0)));

        assertInvalidBatchFails(batch(100, 20, drafts), "카테고리");
    }

    @Test
    void generateDailyDrafts_exceptionTwoRejectsCheckpointNumber() {
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts();
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                "t_vs_z", "초반 3기로 입구 압박 동선을 먼저 확인하세요."));

        assertInvalidBatchFails(batch(100, 20, drafts), "숫자");
    }

    @Test
    void generateDailyDrafts_exceptionThreeRejectsTimeSensitiveClaim() {
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts();
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                "t_vs_z", "최근 패치 메타에서는 입구 수비 동선을 먼저 정리하세요."));

        assertInvalidBatchFails(batch(100, 20, drafts), "시의성");
    }

    @Test
    void generateDailyDrafts_rejectsWholeBatchForWrongCountAbsoluteOrDuplicate() {
        assertInvalidBatchFails(batch(100, 20, Collections.emptyList()), "요청한 수");

        List<StrategyTipAiGeneratedBatch.Draft> absolute = validDrafts();
        absolute.set(0, new StrategyTipAiGeneratedBatch.Draft(
                "t_vs_z", "정찰을 마치면 무조건 입구 수비 동선을 정리하세요."));
        assertInvalidBatchFailsAfterReset(batch(100, 20, absolute), "단정");

        org.mockito.Mockito.reset(store, geminiClient);
        arrangeReadyGeneration(0, 0, Collections.singletonList(CONTENTS.get(0)),
                Collections.emptyList(), Collections.emptyList());
        assertInvalidBatchFailsCurrentSetup(validBatch(100, 20), "유사");
    }

    @Test
    void generateDailyDrafts_recordsIncompleteUsageWithoutSavingPartialDrafts() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(1);
        GeminiStrategyTipException failure = new GeminiStrategyTipException(
                "Gemini interaction did not complete: incomplete (max_output_tokens)",
                null, 3293, 2978, 0);
        when(geminiClient.generate(anyString(), anyString(), eq(3), anyList()))
                .thenThrow(failure);

        assertThrows(GeminiStrategyTipException.class, this::generate);

        verify(store).failDailyRun(GENERATION_DATE, 1,
                "Gemini interaction did not complete: incomplete (max_output_tokens)",
                3293, 2978, 0);
        verify(store, never()).saveGeneratedDrafts(
                any(LocalDate.class), anyInt(), anyList(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void generateDailyDrafts_skipsConcurrentInvocationInsideOneJvm() throws Exception {
        arrangeReadyGeneration(2, 3, Collections.emptyList(),
                Arrays.asList(1, 2), Arrays.asList("t_vs_z", "t_vs_p"));
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(1);
        CountDownLatch enteredClient = new CountDownLatch(1);
        CountDownLatch releaseClient = new CountDownLatch(1);
        when(geminiClient.generate(anyString(), anyString(), eq(1), anyList()))
                .thenAnswer(invocation -> {
                    enteredClient.countDown();
                    assertTrue(releaseClient.await(5, TimeUnit.SECONDS));
                    return batch(1200, 240, draft(2));
                });
        executor = Executors.newSingleThreadExecutor();
        Future<StrategyTipAiDraftService.GenerationResult> first = executor.submit(this::generate);
        assertTrue(enteredClient.await(5, TimeUnit.SECONDS));

        StrategyTipAiDraftService.GenerationResult concurrent = generate();
        releaseClient.countDown();

        assertEquals("SKIPPED", concurrent.getOutcome());
        assertTrue(concurrent.getMessage().contains("이미 실행 중"));
        assertEquals("CREATED", first.get(5, TimeUnit.SECONDS).getOutcome());
        verify(geminiClient, times(1)).generate(
                anyString(), anyString(), eq(1), anyList());
    }

    @Test
    void approve_checkpointDraftAllowsOperatorVerifiedNumericEdit() {
        StrategyTipAiDraftDTO draft = checkpointDraft(41L, "t_vs_z");
        when(store.getPendingDraft(41L)).thenReturn(draft);
        when(store.getRecentPublishedContents(20)).thenReturn(Collections.emptyList());
        String edited = "정찰 후 확인한 타이밍에 맞춰 마린 3기를 전진 배치하세요.";
        when(store.approve(41L, "t_vs_z", edited, "admin", "SC1Hub"))
                .thenReturn(501);

        assertEquals(501, service.approve(41L, "t_vs_z", edited, "admin"));
    }

    @Test
    void approve_checkpointDraftStillRejectsAbsoluteTimeSensitiveAndDuplicateText() {
        when(store.getPendingDraft(anyLong())).thenAnswer(invocation ->
                checkpointDraft(invocation.getArgument(0), "t_vs_z"));

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.approve(51L, "t_vs_z",
                        "정찰을 한 뒤에는 무조건 입구를 막으세요.", "admin"))
                .getMessage().contains("단정"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.approve(52L, "t_vs_z",
                        "현재 메타에서는 입구 시야부터 확인하세요.", "admin"))
                .getMessage().contains("시의성"));

        String duplicate = "상대 정찰 경로를 확인한 뒤 입구 수비 동선을 조정하세요.";
        when(store.getRecentPublishedContents(20))
                .thenReturn(Collections.singletonList(duplicate));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.approve(53L, "t_vs_z", duplicate, "admin"))
                .getMessage().contains("이미 공개"));
    }

    @Test
    void approve_legacyInternalDraftKeepsEvidenceValidation() {
        String excerpt = "상대가 5분에 진출하면 입구 시야부터 확인합니다.";
        String evidence = "상대가 5분에 진출하면 입구 시야부터 확인";
        StrategyTipAiDraftDTO draft = internalDraft(61L, "t_vs_z", excerpt, evidence);
        when(store.getPendingDraft(61L)).thenReturn(draft);
        when(store.getRecentPublishedContents(20)).thenReturn(Collections.emptyList());
        String edited = "상대가 5분에 진출하면 입구 시야부터 확인하세요.";
        when(store.approve(61L, "t_vs_z", edited, "admin", "SC1Hub"))
                .thenReturn(601);

        assertEquals(601, service.approve(61L, "t_vs_z", edited, "admin"));
    }

    @Test
    void approve_rejectsUnknownLegacyModeAndChangedCategory() {
        StrategyTipAiDraftDTO unknown = checkpointDraft(71L, "t_vs_z");
        unknown.setExternalSourceUrl("https://example.com/legacy");
        when(store.getPendingDraft(71L)).thenReturn(unknown);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.approve(71L, "t_vs_z",
                        "입구 시야를 확인한 뒤 병력 동선을 조정하세요.", "admin"))
                .getMessage().contains("생성 방식"));

        when(store.getPendingDraft(72L)).thenReturn(checkpointDraft(72L, "t_vs_z"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.approve(72L, "t_vs_p",
                        "입구 시야를 확인한 뒤 병력 동선을 조정하세요.", "admin"))
                .getMessage().contains("분류"));
        verify(store, never()).approve(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    private StrategyTipAiDraftService.GenerationResult generate() {
        return service.generateDailyDrafts(GENERATION_DATE, NOW);
    }

    private void arrangeReadyGeneration(int generatedToday, int pendingCount,
                                        List<String> recentContents, List<Integer> usedSlots,
                                        List<String> usedCategories) {
        when(store.countGeneratedOn(GENERATION_DATE)).thenReturn(generatedToday);
        when(store.countPending()).thenReturn(pendingCount);
        when(store.getRecentContents(20)).thenReturn(recentContents);
        when(store.getUsedSlots(GENERATION_DATE)).thenReturn(usedSlots);
        when(store.getUsedCategories(GENERATION_DATE)).thenReturn(usedCategories);
    }

    private StrategyTipAiGeneratedBatch validBatch(int inputTokens, int outputTokens) {
        return batch(inputTokens, outputTokens, validDrafts());
    }

    private StrategyTipAiGeneratedBatch batch(int inputTokens, int outputTokens,
                                               StrategyTipAiGeneratedBatch.Draft... drafts) {
        return batch(inputTokens, outputTokens, Arrays.asList(drafts));
    }

    private StrategyTipAiGeneratedBatch batch(int inputTokens, int outputTokens,
                                               List<StrategyTipAiGeneratedBatch.Draft> drafts) {
        return new StrategyTipAiGeneratedBatch(
                drafts, "gemini-3.6-flash", inputTokens, outputTokens);
    }

    private List<StrategyTipAiGeneratedBatch.Draft> validDrafts() {
        List<StrategyTipAiGeneratedBatch.Draft> drafts = new ArrayList<>();
        for (int index = 0; index < CATEGORIES.size(); index++) {
            drafts.add(draft(index));
        }
        return drafts;
    }

    private StrategyTipAiGeneratedBatch.Draft draft(int index) {
        return new StrategyTipAiGeneratedBatch.Draft(
                CATEGORIES.get(index), CONTENTS.get(index));
    }

    private void assertInvalidBatchFails(StrategyTipAiGeneratedBatch invalidBatch,
                                         String expectedMessagePart) {
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        assertInvalidBatchFailsCurrentSetup(invalidBatch, expectedMessagePart);
    }

    private void assertInvalidBatchFailsAfterReset(StrategyTipAiGeneratedBatch invalidBatch,
                                                   String expectedMessagePart) {
        org.mockito.Mockito.reset(store, geminiClient);
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        assertInvalidBatchFailsCurrentSetup(invalidBatch, expectedMessagePart);
    }

    private void assertInvalidBatchFailsCurrentSetup(StrategyTipAiGeneratedBatch invalidBatch,
                                                     String expectedMessagePart) {
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(1);
        when(geminiClient.generate(anyString(), anyString(), eq(3), anyList()))
                .thenReturn(invalidBatch);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, this::generate);

        assertTrue(exception.getMessage().contains(expectedMessagePart));
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).failDailyRun(eq(GENERATION_DATE), eq(1), errorCaptor.capture(),
                eq(invalidBatch.getInputTokens()), eq(invalidBatch.getOutputTokens()), eq(0));
        assertTrue(errorCaptor.getValue().contains(expectedMessagePart));
        verify(store, never()).saveGeneratedDrafts(
                eq(GENERATION_DATE), anyInt(), anyList(), anyInt(), anyInt(), anyInt());
    }

    private StrategyTipAiDraftDTO checkpointDraft(long draftId, String category) {
        StrategyTipAiDraftDTO draft = new StrategyTipAiDraftDTO();
        draft.setDraftId(draftId);
        draft.setStatus("PENDING");
        draft.setCategory(category);
        draft.setSourcePostNum(0);
        return draft;
    }

    private StrategyTipAiDraftDTO internalDraft(long draftId, String category,
                                                 String sourceExcerpt, String evidenceSummary) {
        StrategyTipAiDraftDTO draft = checkpointDraft(draftId, category);
        draft.setSourceBoard("tvszboard");
        draft.setSourcePostNum(101);
        draft.setSourceExcerpt(sourceExcerpt);
        draft.setEvidenceSummary(evidenceSummary);
        return draft;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftListCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
