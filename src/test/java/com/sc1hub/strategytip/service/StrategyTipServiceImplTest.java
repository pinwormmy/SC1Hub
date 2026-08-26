package com.sc1hub.strategytip.service;

import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.strategytip.dto.StrategyTipDTO;
import com.sc1hub.strategytip.mapper.StrategyTipMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyTipServiceImplTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-24T03:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private StrategyTipMapper strategyTipMapper;

    private StrategyTipServiceImpl strategyTipService;

    @BeforeEach
    void setUp() {
        strategyTipService = new StrategyTipServiceImpl(strategyTipMapper, FIXED_CLOCK);
    }

    @Test
    void recommend_recordsMemberOnceForTheKoreanCalendarDateAndIncrementsCount() {
        StrategyTipDTO before = tipWithRecommendCount(3);
        StrategyTipDTO after = tipWithRecommendCount(4);
        MemberDTO member = new MemberDTO();
        member.setId("member-1");

        when(strategyTipMapper.selectTip(7)).thenReturn(before, after);
        when(strategyTipMapper.insertDailyRecommendation(eq(7), eq(LocalDate.of(2026, 8, 24)), anyString()))
                .thenReturn(1);
        when(strategyTipMapper.incrementRecommendCount(7)).thenReturn(1);

        assertEquals(4, strategyTipService.recommend(7, member, "different-session"));

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(strategyTipMapper).insertDailyRecommendation(
                eq(7), eq(LocalDate.of(2026, 8, 24)), hashCaptor.capture());
        assertEquals(64, hashCaptor.getValue().length());
        assertFalse(hashCaptor.getValue().contains("member-1"));
        verify(strategyTipMapper).incrementRecommendCount(7);
    }

    @Test
    void recommend_rejectsDuplicateDailyRecommendationWithoutIncrementingCount() {
        when(strategyTipMapper.selectTip(7)).thenReturn(tipWithRecommendCount(3));
        when(strategyTipMapper.insertDailyRecommendation(eq(7), eq(LocalDate.of(2026, 8, 24)), anyString()))
                .thenReturn(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> strategyTipService.recommend(7, null, "anonymous-session")
        );

        assertTrue(exception.getMessage().contains("오늘 이미 추천"));
        verify(strategyTipMapper, never()).incrementRecommendCount(7);
    }

    @Test
    void recommend_usesSameIdentityForMemberAcrossDifferentSessions() {
        MemberDTO member = new MemberDTO();
        member.setId("member-1");
        when(strategyTipMapper.selectTip(7)).thenReturn(
                tipWithRecommendCount(3), tipWithRecommendCount(4),
                tipWithRecommendCount(4), tipWithRecommendCount(5));
        when(strategyTipMapper.insertDailyRecommendation(eq(7), eq(LocalDate.of(2026, 8, 24)), anyString()))
                .thenReturn(1);
        when(strategyTipMapper.incrementRecommendCount(7)).thenReturn(1);

        strategyTipService.recommend(7, member, "session-a");
        strategyTipService.recommend(7, member, "session-b");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(strategyTipMapper, times(2)).insertDailyRecommendation(
                eq(7), eq(LocalDate.of(2026, 8, 24)), hashCaptor.capture());
        assertEquals(hashCaptor.getAllValues().get(0), hashCaptor.getAllValues().get(1));
    }

    @Test
    void recommend_rejectsMissingTipBeforeRecordingRecommendation() {
        when(strategyTipMapper.selectTip(99)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> strategyTipService.recommend(99, null, "anonymous-session")
        );

        assertTrue(exception.getMessage().contains("존재하지 않는"));
        verify(strategyTipMapper, never()).insertDailyRecommendation(
                eq(99), eq(LocalDate.of(2026, 8, 24)), anyString());
        verify(strategyTipMapper, never()).incrementRecommendCount(99);
    }

    private StrategyTipDTO tipWithRecommendCount(int recommendCount) {
        StrategyTipDTO tip = new StrategyTipDTO();
        tip.setTipNum(7);
        tip.setRecommendCount(recommendCount);
        return tip;
    }
}
