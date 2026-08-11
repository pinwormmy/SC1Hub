package com.sc1hub.strategytip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.board.dto.BoardDTO;
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
    private static final List<String> BOARDS =
            Arrays.asList("tvszboard", "tvspboard", "tvstboard");
    private static final List<String> CONTENTS = Arrays.asList(
            "저그의 초반 압박을 보면 입구 수비 동선을 정리하세요.",
            "프로토스 정찰 경로를 살핀 뒤 병력 진출 방향을 조정하세요.",
            "상대 생산 건물을 확인하며 중앙 시야를 천천히 확보하세요."
    );
    private static final List<String> EVIDENCES = Arrays.asList(
            "입구 수비 동선을 정리",
            "프로토스 정찰 경로를 살핀",
            "상대 생산 건물을 확인"
    );
    private static final String EXACT_EVIDENCE = EVIDENCES.get(0);

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
        properties.setMaxPendingDrafts(3);
        properties.setMaxDailyApiCalls(2);
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
    void generateDailyDrafts_skipsAtDailyOrPendingLimit() {
        when(store.countGeneratedOn(GENERATION_DATE)).thenReturn(3);
        assertTrue(generate().getMessage().contains("이미 채웠"));
        verifyNoInteractions(geminiClient);

        when(store.countGeneratedOn(GENERATION_DATE)).thenReturn(0);
        when(store.countPending()).thenReturn(3);
        assertTrue(generate().getMessage().contains("검수 대기"));
        verifyNoInteractions(geminiClient);
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
        verify(store).claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10));
        verifyNoInteractions(geminiClient);
    }

    @Test
    void getStatus_exposesClampedDailyApiCallLimitForAdminUi() {
        properties.setMaxDailyApiCalls(99);
        when(store.countPending()).thenReturn(0);
        when(store.countGeneratedOn(any(LocalDate.class))).thenReturn(0);

        StrategyTipAiStatusDTO status = service.getStatus();

        assertEquals(2, status.getMaxDailyApiCalls());
    }

    @Test
    void generateDailyDrafts_createsThreeDraftsInOneInternalOnlyBatch() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(1);
        when(geminiClient.generate(anyString(), anyString(), eq(3), anyList(), anyList()))
                .thenReturn(batchForIndexes(4100, 700, 0, 1, 2));

        StrategyTipAiDraftService.GenerationResult result = generate();

        assertEquals("CREATED", result.getOutcome());
        assertEquals(3, result.getCreatedCount());
        verify(geminiClient, times(1)).generate(
                contains("오직 SOURCE_DATA_JSON"), contains("SOURCE_DATA_JSON"), eq(3),
                eq(CATEGORIES), eq(Arrays.asList(
                        "tvszboard:101", "tvspboard:102", "tvstboard:103")));

        ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftsCaptor = draftListCaptor();
        verify(store).saveGeneratedDrafts(
                eq(GENERATION_DATE), eq(1), draftsCaptor.capture(), eq(4100), eq(700), eq(0));
        assertEquals(3, draftsCaptor.getValue().size());
        for (int index = 0; index < 3; index++) {
            StrategyTipAiDraftDTO draft = draftsCaptor.getValue().get(index);
            assertEquals(index + 1, draft.getSlotNo());
            assertEquals(CATEGORIES.get(index), draft.getCategory());
            assertEquals(BOARDS.get(index), draft.getSourceBoard());
            assertEquals(EVIDENCES.get(index), draft.getEvidenceSummary());
            assertEquals("", draft.getExternalSourceUrl());
            assertEquals("", draft.getExternalSourceTitle());
            assertEquals("", draft.getExternalEvidenceSummary());
        }
    }

    @Test
    void generateDailyDrafts_topUpUsesRemainingSlotsInOneBatch() {
        arrangeReadyGeneration(1, 0, Collections.emptyList(),
                Collections.singletonList(1), Collections.singletonList("t_vs_z"));
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(2);
        when(geminiClient.generate(anyString(), anyString(), eq(2), anyList(), anyList()))
                .thenReturn(batchForIndexes(2500, 420, 1, 2));

        StrategyTipAiDraftService.GenerationResult result = generate();

        assertEquals(2, result.getCreatedCount());
        ArgumentCaptor<List<StrategyTipAiDraftDTO>> draftsCaptor = draftListCaptor();
        verify(store).saveGeneratedDrafts(
                eq(GENERATION_DATE), eq(2), draftsCaptor.capture(), eq(2500), eq(420), eq(0));
        assertEquals(2, draftsCaptor.getValue().get(0).getSlotNo());
        assertEquals(3, draftsCaptor.getValue().get(1).getSlotNo());
        verify(geminiClient).generate(anyString(), anyString(), eq(2),
                eq(Arrays.asList("t_vs_p", "t_vs_t")),
                eq(Arrays.asList("tvspboard:102", "tvstboard:103")));
    }

    @Test
    void generateDailyDrafts_skipsEmptyCategoryAndUsesNextInternalSource() {
        when(store.countGeneratedOn(GENERATION_DATE)).thenReturn(2);
        when(store.countPending()).thenReturn(0);
        when(store.getRecentContents(20)).thenReturn(Collections.emptyList());
        when(store.getUsedSlots(GENERATION_DATE)).thenReturn(Arrays.asList(2, 3));
        when(store.getUsedCategories(GENERATION_DATE))
                .thenReturn(Arrays.asList("t_vs_t", "z_vs_t"));
        when(store.getSourcePosts("tvszboard", 3)).thenReturn(Collections.emptyList());
        when(store.getSourcePosts("tvspboard", 3))
                .thenReturn(Collections.singletonList(internalPost("tvspboard")));
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(1);
        when(geminiClient.generate(anyString(), anyString(), eq(1), anyList(), anyList()))
                .thenReturn(batchForIndexes(1200, 240, 1));

        StrategyTipAiDraftService.GenerationResult result = generate();

        assertEquals(1, result.getCreatedCount());
        verify(geminiClient).generate(anyString(), anyString(), eq(1),
                eq(Collections.singletonList("t_vs_p")),
                eq(Collections.singletonList("tvspboard:102")));
    }

    @Test
    void generateDailyDrafts_skipsBeforePaidCallWhenNoInternalSourcesExist() {
        when(store.countGeneratedOn(GENERATION_DATE)).thenReturn(0);
        when(store.countPending()).thenReturn(0);
        when(store.getRecentContents(20)).thenReturn(Collections.emptyList());
        when(store.getUsedSlots(GENERATION_DATE)).thenReturn(Collections.emptyList());
        when(store.getUsedCategories(GENERATION_DATE)).thenReturn(Collections.emptyList());
        when(store.getSourcePosts(anyString(), eq(3))).thenReturn(Collections.emptyList());

        StrategyTipAiDraftService.GenerationResult result = generate();

        assertEquals("SKIPPED", result.getOutcome());
        assertTrue(result.getMessage().contains("내부 근거"));
        verify(store, never()).claimDailyApiCall(any(), anyInt(), any());
        verifyNoInteractions(geminiClient);
    }

    @Test
    void generateDailyDrafts_rejectsWholeBatchWhenDraftCountIsWrong() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        assertInvalidBatchFails(new StrategyTipAiGeneratedBatch(
                Collections.emptyList(), "gemini-3.6-flash", 100, 20), "요청한 수");
    }

    @Test
    void generateDailyDrafts_rejectsWrongCategory() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(0, 1, 2);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                "honey_tip", CONTENTS.get(0), "tvszboard:101", EXACT_EVIDENCE));
        assertInvalidBatchFails(batch(drafts, 100, 20), "카테고리");
    }

    @Test
    void generateDailyDrafts_rejectsCategorySourceMismatch() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(0, 1, 2);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                "t_vs_z", CONTENTS.get(0), "tvspboard:102", EXACT_EVIDENCE));
        assertInvalidBatchFails(batch(drafts, 100, 20), "근거 출처");
    }

    @Test
    void generateDailyDrafts_requiresEvidenceCopiedFromSelectedExcerpt() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(0, 1, 2);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                "t_vs_z", CONTENTS.get(0), "tvszboard:101", "원문에 없는 근거 문장입니다"));

        assertInvalidBatchFails(batch(drafts, 100, 20), "원문에 없습니다");
    }

    @Test
    void generateDailyDrafts_rejectsContentUnrelatedToExactEvidence() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(0, 1, 2);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                "t_vs_z", "아비터로 본진에 리콜해 생산 건물을 노리세요.",
                "tvszboard:101", EXACT_EVIDENCE));

        assertInvalidBatchFails(batch(drafts, 100, 20), "근거 구절 전체");
    }

    @Test
    void generateDailyDrafts_rejectsUngroundedNumber() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(0, 1, 2);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                "t_vs_z", "초반 9분에는 입구 수비 동선을 먼저 정리하세요.",
                "tvszboard:101", EXACT_EVIDENCE));
        assertInvalidBatchFails(batch(drafts, 100, 20), "숫자");
    }

    @Test
    void generateDailyDrafts_rejectsDuplicateContent() {
        arrangeReadyGeneration(0, 0, Collections.singletonList(CONTENTS.get(0)),
                Collections.emptyList(), Collections.emptyList());
        assertInvalidBatchFails(batchForIndexes(100, 20, 0, 1, 2), "유사");
    }

    @Test
    void generateDailyDrafts_rejectsCurrentMetaClaim() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(0, 1, 2);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                "t_vs_z", "현재 메타에서는 입구 수비 동선을 먼저 정리하세요.",
                "tvszboard:101", EXACT_EVIDENCE));
        assertInvalidBatchFails(batch(drafts, 100, 20), "시의성");
    }

    @Test
    void generateDailyDrafts_rejectsRecentPatchClaim() {
        arrangeReadyGeneration(0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        List<StrategyTipAiGeneratedBatch.Draft> drafts = validDrafts(0, 1, 2);
        drafts.set(0, new StrategyTipAiGeneratedBatch.Draft(
                "t_vs_z", "최근 패치 후에는 입구 수비 동선을 먼저 정리하세요.",
                "tvszboard:101", EXACT_EVIDENCE));
        assertInvalidBatchFails(batch(drafts, 100, 20), "시의성");
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
        when(geminiClient.generate(anyString(), anyString(), eq(3), anyList(), anyList()))
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
        arrangeReadyGeneration(2, 0, Collections.emptyList(),
                Arrays.asList(1, 2), Arrays.asList("t_vs_z", "t_vs_p"));
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(1);
        CountDownLatch enteredClient = new CountDownLatch(1);
        CountDownLatch releaseClient = new CountDownLatch(1);
        when(geminiClient.generate(anyString(), anyString(), eq(1), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    enteredClient.countDown();
                    assertTrue(releaseClient.await(5, TimeUnit.SECONDS));
                    return batchForIndexes(1200, 240, 2);
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
                anyString(), anyString(), eq(1), anyList(), anyList());
    }

    @Test
    void approve_revalidatesEditedContentAndPublishes() {
        StrategyTipAiDraftDTO draft = pendingDraft(41L, "t_vs_z",
                "상대가 5분에 진출하면 입구 시야부터 확인합니다.",
                "상대가 5분에 진출하면 입구 시야부터 확인");
        when(store.getPendingDraft(41L)).thenReturn(draft);
        when(store.getRecentPublishedContents(20)).thenReturn(Collections.emptyList());
        when(store.approve(41L, "t_vs_z",
                "상대가 5분에 진출하면 입구 시야부터 확인하세요.",
                "admin", "SC1Hub")).thenReturn(501);

        int tipNum = service.approve(41L, "t_vs_z",
                " 상대가 5분에 진출하면  입구 시야부터 확인하세요. ", "admin");

        assertEquals(501, tipNum);
    }

    @Test
    void approve_rejectsMissingInternalSourceOrChangedCategory() {
        StrategyTipAiDraftDTO legacy = pendingDraft(42L, "t_vs_z", "근거 문장");
        legacy.setSourcePostNum(0);
        when(store.getPendingDraft(42L)).thenReturn(legacy);
        IllegalArgumentException noSource = assertThrows(IllegalArgumentException.class,
                () -> service.approve(42L, "t_vs_z",
                        "입구 시야를 확인한 뒤 병력 동선을 조정하세요.", "admin"));
        assertTrue(noSource.getMessage().contains("내부 근거"));

        when(store.getPendingDraft(43L)).thenReturn(
                pendingDraft(43L, "t_vs_z", "근거 문장"));
        assertThrows(IllegalArgumentException.class,
                () -> service.approve(43L, "t_vs_p",
                        "입구 시야를 확인한 뒤 병력 동선을 조정하세요.", "admin"));
        verify(store, never()).approve(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void approve_rejectsLegacyEvidenceThatIsNotAnExactInternalQuote() {
        StrategyTipAiDraftDTO legacy = pendingDraft(44L, "t_vs_z",
                "입구 수비 동선을 정리해 초반 압박에 대비합니다.",
                "입구를 잘 막아 초반을 버틴다");
        when(store.getPendingDraft(44L)).thenReturn(legacy);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approve(44L, "t_vs_z",
                        "입구 수비 동선을 정리해 초반 압박에 대비하세요.", "admin"));

        assertTrue(exception.getMessage().contains("원문에서 확인할 수 없어"));
        verify(store, never()).approve(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void approve_rejectsLengthAbsoluteNumberDuplicateAndCurrentMeta() {
        when(store.getPendingDraft(anyLong())).thenAnswer(invocation -> pendingDraft(
                invocation.getArgument(0), "t_vs_z", "상대가 5분에 진출하면 시야를 확보합니다."));

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.approve(50L, "t_vs_z", "짧은 공략", "admin"))
                .getMessage().contains("12~160"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.approve(51L, "t_vs_z",
                        "정찰을 한 뒤에는 무조건 입구를 막으세요.", "admin"))
                .getMessage().contains("단정"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.approve(52L, "t_vs_z",
                        "상대가 7분에 진출하면 입구 시야부터 확인하세요.", "admin"))
                .getMessage().contains("숫자"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.approve(53L, "t_vs_z",
                        "현재 메타에서는 입구 시야부터 확인하세요.", "admin"))
                .getMessage().contains("시의성"));

        when(store.getRecentPublishedContents(20)).thenReturn(Collections.singletonList(
                "상대 정찰 경로를 확인한 뒤 입구 수비 동선을 조정하세요."));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.approve(54L, "t_vs_z",
                        "상대 정찰 경로를 확인한 뒤 입구 수비 동선을 조정하세요.", "admin"))
                .getMessage().contains("이미 공개"));
    }

    @Test
    void approve_rejectsEditedContentUnrelatedToEvidence() {
        StrategyTipAiDraftDTO draft = pendingDraft(55L, "t_vs_z",
                "상대 정찰 경로를 확인하고 입구 수비 동선을 조정합니다.");
        when(store.getPendingDraft(55L)).thenReturn(draft);
        when(store.getRecentPublishedContents(20)).thenReturn(Collections.emptyList());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approve(55L, "t_vs_z",
                        "아비터로 본진에 리콜해 생산 건물을 노리세요.", "admin"));

        assertTrue(exception.getMessage().contains("근거 구절 전체"));
        verify(store, never()).approve(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void approve_allowsEditedContentUpTo160Characters() {
        String editedContent = String.join("", Collections.nCopies(120, "가"));
        when(store.getPendingDraft(60L)).thenReturn(
                pendingDraft(60L, "t_vs_z", "가가가가가가가가가가가가 숫자 없는 근거",
                        "가가가가가가가가가가가가"));
        when(store.getRecentPublishedContents(20)).thenReturn(Collections.emptyList());
        when(store.approve(60L, "t_vs_z", editedContent, "admin", "SC1Hub"))
                .thenReturn(560);

        assertEquals(560, service.approve(60L, "t_vs_z", editedContent, "admin"));
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
        when(store.getSourcePosts(anyString(), eq(3)))
                .thenAnswer(invocation -> Collections.singletonList(
                        internalPost(invocation.getArgument(0))));
    }

    private BoardDTO internalPost(String boardTitle) {
        int index = BOARDS.indexOf(boardTitle);
        if (index < 0) {
            index = 3;
        }
        int evidenceIndex = Math.min(index, EVIDENCES.size() - 1);
        BoardDTO post = new BoardDTO();
        post.setPostNum(101 + index);
        post.setTitle(boardTitle + " 내부 공략");
        post.setContent("이 내부 공략은 " + EVIDENCES.get(evidenceIndex)
                + "하라고 설명합니다.");
        return post;
    }

    private StrategyTipAiGeneratedBatch batchForIndexes(
            int inputTokens, int outputTokens, int... indexes) {
        return batch(validDrafts(indexes), inputTokens, outputTokens);
    }

    private StrategyTipAiGeneratedBatch batch(
            List<StrategyTipAiGeneratedBatch.Draft> drafts,
            int inputTokens, int outputTokens) {
        return new StrategyTipAiGeneratedBatch(
                drafts, "gemini-3.6-flash", inputTokens, outputTokens);
    }

    private List<StrategyTipAiGeneratedBatch.Draft> validDrafts(int... indexes) {
        List<StrategyTipAiGeneratedBatch.Draft> drafts = new ArrayList<>();
        for (int index : indexes) {
            drafts.add(new StrategyTipAiGeneratedBatch.Draft(
                    CATEGORIES.get(index), CONTENTS.get(index),
                    BOARDS.get(index) + ":" + (101 + index), EVIDENCES.get(index)));
        }
        return drafts;
    }

    private void assertInvalidBatchFails(StrategyTipAiGeneratedBatch invalidBatch,
                                         String expectedMessagePart) {
        when(store.claimDailyApiCall(GENERATION_DATE, 2, NOW.minusMinutes(10)))
                .thenReturn(1);
        when(geminiClient.generate(anyString(), anyString(), eq(3), anyList(), anyList()))
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

    private StrategyTipAiDraftDTO pendingDraft(long draftId, String category,
                                                String sourceExcerpt) {
        return pendingDraft(draftId, category, sourceExcerpt, sourceExcerpt);
    }

    private StrategyTipAiDraftDTO pendingDraft(long draftId, String category,
                                                String sourceExcerpt, String evidenceSummary) {
        StrategyTipAiDraftDTO draft = new StrategyTipAiDraftDTO();
        draft.setDraftId(draftId);
        draft.setStatus("PENDING");
        draft.setCategory(category);
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
