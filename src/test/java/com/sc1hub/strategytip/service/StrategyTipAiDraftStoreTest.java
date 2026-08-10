package com.sc1hub.strategytip.service;

import com.sc1hub.strategytip.dto.StrategyTipAiDailyRunDTO;
import com.sc1hub.strategytip.dto.StrategyTipAiDraftDTO;
import com.sc1hub.strategytip.dto.StrategyTipDTO;
import com.sc1hub.strategytip.mapper.StrategyTipAiMapper;
import com.sc1hub.strategytip.mapper.StrategyTipMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyTipAiDraftStoreTest {

    @Mock
    private StrategyTipAiMapper strategyTipAiMapper;

    @Mock
    private StrategyTipMapper strategyTipMapper;

    @InjectMocks
    private StrategyTipAiDraftStore store;

    @Test
    void getPendingDraft_usesPendingOnlyMapperLookup() {
        StrategyTipAiDraftDTO draft = new StrategyTipAiDraftDTO();
        draft.setDraftId(31L);
        draft.setStatus("PENDING");
        when(strategyTipAiMapper.selectPendingDraft(31L)).thenReturn(draft);

        assertEquals(draft, store.getPendingDraft(31L));

        verify(strategyTipAiMapper).selectPendingDraft(31L);
        verify(strategyTipAiMapper, never()).selectDraft(31L);
    }

    @Test
    void getRecentPublishedContents_clampsLimitAndNormalizesNullResult() {
        List<String> contents = Collections.singletonList("공개된 한줄 공략입니다.");
        when(strategyTipAiMapper.selectRecentPublishedContents(200)).thenReturn(contents);
        when(strategyTipAiMapper.selectRecentPublishedContents(1)).thenReturn(null);

        assertEquals(contents, store.getRecentPublishedContents(999));
        assertTrue(store.getRecentPublishedContents(0).isEmpty());

        verify(strategyTipAiMapper).selectRecentPublishedContents(200);
        verify(strategyTipAiMapper).selectRecentPublishedContents(1);
    }

    @Test
    void claimDailyApiCall_insertsRunThenClaimsAtomically() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        LocalDateTime staleBefore = LocalDateTime.of(2026, 8, 10, 10, 0);
        StrategyTipAiDailyRunDTO run = new StrategyTipAiDailyRunDTO();
        run.setLastStatus("RUNNING");
        run.setApiCallCount(2);
        when(strategyTipAiMapper.claimDailyRun(date, 2, staleBefore)).thenReturn(1);
        when(strategyTipAiMapper.selectDailyRun(date)).thenReturn(run);

        assertEquals(2, store.claimDailyApiCall(date, 2, staleBefore));

        InOrder order = inOrder(strategyTipAiMapper);
        order.verify(strategyTipAiMapper).insertDailyRunIfAbsent(date);
        order.verify(strategyTipAiMapper).claimDailyRun(date, 2, staleBefore);
        order.verify(strategyTipAiMapper).selectDailyRun(date);
    }

    @Test
    void saveGeneratedDrafts_insertsAllDraftsBeforeCompletingRun() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        StrategyTipAiDraftDTO first = new StrategyTipAiDraftDTO();
        StrategyTipAiDraftDTO second = new StrategyTipAiDraftDTO();
        when(strategyTipAiMapper.completeDailyRun(date, 1, 12, 34, 2)).thenReturn(1);

        store.saveGeneratedDrafts(date, 1, Arrays.asList(first, second), 12, 34, 2);

        InOrder order = inOrder(strategyTipAiMapper);
        order.verify(strategyTipAiMapper).insertDraft(same(first));
        order.verify(strategyTipAiMapper).insertDraft(same(second));
        order.verify(strategyTipAiMapper).completeDailyRun(date, 1, 12, 34, 2);
    }

    @Test
    void saveGeneratedDrafts_clampsNegativeUsageCountersToZero() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        StrategyTipAiDraftDTO draft = new StrategyTipAiDraftDTO();
        when(strategyTipAiMapper.completeDailyRun(date, 1, 0, 0, 0)).thenReturn(1);

        store.saveGeneratedDrafts(date, 1, Arrays.asList(draft), -1, -2, -3);

        verify(strategyTipAiMapper).insertDraft(draft);
        verify(strategyTipAiMapper).completeDailyRun(date, 1, 0, 0, 0);
    }

    @Test
    void failDailyRun_keepsAttemptOwnershipAndClampsUsageCounters() {
        LocalDate date = LocalDate.of(2026, 8, 10);

        store.failDailyRun(date, 2, " provider validation failed ", -1, 7, 3);

        verify(strategyTipAiMapper).failDailyRun(
                date, 2, "provider validation failed", 0, 7, 3);
    }

    @Test
    void approve_claimsDraftPublishesTipAndCompletesWithGeneratedKeyInOrder() {
        long draftId = 91L;
        StrategyTipAiDraftDTO claimed = new StrategyTipAiDraftDTO();
        claimed.setDraftId(draftId);
        claimed.setStatus("APPROVING");
        claimed.setCategory("t_vs_z");
        when(strategyTipAiMapper.claimDraftForApproval(draftId, "admin")).thenReturn(1);
        when(strategyTipAiMapper.selectDraft(draftId)).thenReturn(claimed);
        doAnswer(invocation -> {
            StrategyTipDTO published = invocation.getArgument(0);
            published.setTipNum(407);
            return null;
        }).when(strategyTipMapper).insertTip(any(StrategyTipDTO.class));
        when(strategyTipAiMapper.completeDraftApproval(
                draftId, "t_vs_z", "입구를 좁혀 초반 압박 동선을 줄이세요.", 407)).thenReturn(1);

        int tipNum = store.approve(draftId, "t_vs_z",
                "입구를 좁혀 초반 압박 동선을 줄이세요.", "admin", "SC1Hub");

        assertEquals(407, tipNum);
        ArgumentCaptor<StrategyTipDTO> publishedCaptor = ArgumentCaptor.forClass(StrategyTipDTO.class);
        InOrder order = inOrder(strategyTipAiMapper, strategyTipMapper);
        order.verify(strategyTipAiMapper).claimDraftForApproval(draftId, "admin");
        order.verify(strategyTipAiMapper).selectDraft(draftId);
        order.verify(strategyTipMapper).insertTip(publishedCaptor.capture());
        order.verify(strategyTipAiMapper).completeDraftApproval(
                draftId, "t_vs_z", "입구를 좁혀 초반 압박 동선을 줄이세요.", 407);

        StrategyTipDTO published = publishedCaptor.getValue();
        assertEquals("t_vs_z", published.getCategory());
        assertEquals("입구를 좁혀 초반 압박 동선을 줄이세요.", published.getContent());
        assertEquals("SC1Hub", published.getWriter());
        assertEquals("admin", published.getMemberId());
        assertEquals(407, published.getTipNum());
    }

    @Test
    void approve_keepsClaimPublicationAndCompletionInOneTransaction() throws Exception {
        Transactional transactional = StrategyTipAiDraftStore.class
                .getMethod("approve", long.class, String.class, String.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
    }

    @Test
    void approve_rejectsConcurrentSecondApprovalWhenCompareAndSetFails() {
        long draftId = 91L;
        when(strategyTipAiMapper.claimDraftForApproval(draftId, "admin2")).thenReturn(0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> store.approve(draftId, "t_vs_z", "검수된 한줄 공략입니다.",
                        "admin2", "SC1Hub"));

        assertTrue(exception.getMessage().contains("이미 처리"));
        verify(strategyTipAiMapper, never()).selectDraft(draftId);
        verify(strategyTipMapper, never()).insertTip(any(StrategyTipDTO.class));
        verify(strategyTipAiMapper, never()).completeDraftApproval(
                anyLong(), any(String.class), any(String.class), anyInt());
    }

    @Test
    void approve_rejectsCategoryChangeAfterClaimBeforePublishing() {
        long draftId = 93L;
        StrategyTipAiDraftDTO claimed = new StrategyTipAiDraftDTO();
        claimed.setStatus("APPROVING");
        claimed.setCategory("t_vs_z");
        when(strategyTipAiMapper.claimDraftForApproval(draftId, "admin")).thenReturn(1);
        when(strategyTipAiMapper.selectDraft(draftId)).thenReturn(claimed);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> store.approve(draftId, "t_vs_p", "분류가 바뀌면 안 되는 공략입니다.",
                        "admin", "SC1Hub"));

        assertTrue(exception.getMessage().contains("분류"));
        verify(strategyTipMapper, never()).insertTip(any(StrategyTipDTO.class));
        verify(strategyTipAiMapper, never()).completeDraftApproval(
                anyLong(), any(String.class), any(String.class), anyInt());
    }

    @Test
    void approve_doesNotCompleteWhenMapperDoesNotReturnGeneratedKey() {
        long draftId = 92L;
        StrategyTipAiDraftDTO claimed = new StrategyTipAiDraftDTO();
        claimed.setStatus("APPROVING");
        claimed.setCategory("t_vs_p");
        when(strategyTipAiMapper.claimDraftForApproval(draftId, "admin")).thenReturn(1);
        when(strategyTipAiMapper.selectDraft(draftId)).thenReturn(claimed);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> store.approve(draftId, "t_vs_p", "드라군 진입 경로를 먼저 확인하세요.",
                        "admin", "SC1Hub"));

        assertTrue(exception.getMessage().contains("번호"));
        verify(strategyTipAiMapper, never()).completeDraftApproval(
                anyLong(), any(String.class), any(String.class), anyInt());
    }

    @Test
    void reject_usesCompareAndSetAndReportsAlreadyHandledDraft() {
        when(strategyTipAiMapper.rejectDraft(12L, "admin")).thenReturn(1);
        when(strategyTipAiMapper.rejectDraft(13L, "admin")).thenReturn(0);

        store.reject(12L, "admin");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> store.reject(13L, "admin"));

        assertTrue(exception.getMessage().contains("이미 처리"));
        verify(strategyTipAiMapper).rejectDraft(12L, "admin");
        verify(strategyTipAiMapper).rejectDraft(13L, "admin");
    }
}
