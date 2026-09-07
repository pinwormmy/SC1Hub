package com.sc1hub.common.security;

import com.sc1hub.chat.service.ChatModerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OffenderTrackerTest {

    @Mock
    private ChatModerationService moderationService;

    private OffenderTracker tracker() {
        return new OffenderTracker(moderationService, new WriteRateLimiter());
    }

    @Test
    void repeatedRejectionsFromOnePublicIpTriggerAnAutomaticBlock() {
        OffenderTracker tracker = tracker();
        for (int i = 0; i < OffenderTracker.IP_STRIKE_LIMIT + 1; i++) {
            tracker.strike("203.0.113.9", true, null, null, "속도 제한");
        }
        verify(moderationService, times(1)).addSanction(eq(ChatModerationService.TYPE_BLOCK_IP), isNull(),
                eq("203.0.113.9"), any(), eq(OffenderTracker.FIRST_BAN_MINUTES), contains("자동 차단"), eq("auto"));
        assertEquals(1, tracker.autoBanCount());
        assertEquals(OffenderTracker.IP_STRIKE_LIMIT + 1, tracker.recentStrikeCount());
    }

    @Test
    void untrustedOrPrivateAddressesAreNeverBanned() {
        OffenderTracker tracker = tracker();
        for (int i = 0; i < OffenderTracker.IP_STRIKE_LIMIT + 5; i++) {
            tracker.strike("203.0.113.9", false, null, null, "속도 제한");
            tracker.strike("10.0.0.7", true, null, null, "속도 제한");
        }
        verify(moderationService, never()).addSanction(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void repeatedRejectionsFromOneMemberTriggerAMute() {
        OffenderTracker tracker = tracker();
        for (int i = 0; i < OffenderTracker.MEMBER_STRIKE_LIMIT + 1; i++) {
            tracker.strike("203.0.113.9", true, "spammer", "도배꾼", "회원 속도 제한");
        }
        verify(moderationService, times(1)).addSanction(eq(ChatModerationService.TYPE_MUTE), eq("spammer"), isNull(),
                eq("도배꾼"), eq(OffenderTracker.FIRST_BAN_MINUTES), contains("자동 차단"), eq("auto"));
    }

    @Test
    void highSeverityAttackContentBansImmediatelyForADay() {
        OffenderTracker tracker = tracker();
        AttackContentDetector.Verdict high = new AttackContentDetector.Verdict(AttackContentDetector.Severity.HIGH, "script-tag");

        tracker.attackDetected("203.0.113.9", true, "attacker", "공격자", high);
        verify(moderationService).addSanction(eq(ChatModerationService.TYPE_MUTE), eq("attacker"), isNull(),
                eq("공격자"), eq(OffenderTracker.ATTACK_BAN_MINUTES), contains("공격 패턴 감지: script-tag"), eq("auto"));
        // 회원이라도 신뢰할 수 있는 공인 주소는 함께 차단한다(로그아웃 뒤 비회원·신규 가입으로 이어 쓰는 우회 방지).
        verify(moderationService).addSanction(eq(ChatModerationService.TYPE_BLOCK_IP), isNull(), eq("203.0.113.9"),
                eq("공격자"), eq(OffenderTracker.ATTACK_BAN_MINUTES), contains("공격 패턴 감지: script-tag"), eq("auto"));

        tracker.attackDetected("203.0.113.10", true, null, null, high);
        verify(moderationService).addSanction(eq(ChatModerationService.TYPE_BLOCK_IP), isNull(), eq("203.0.113.10"),
                any(), eq(OffenderTracker.ATTACK_BAN_MINUTES), contains("공격 패턴 감지"), eq("auto"));
        assertEquals(3, tracker.autoBanCount());
        assertEquals(2, tracker.recentStrikeCount());
    }

    @Test
    void memberAttackFromPrivateOrUntrustedAddressOnlyMutesTheMember() {
        OffenderTracker tracker = tracker();
        AttackContentDetector.Verdict high = new AttackContentDetector.Verdict(AttackContentDetector.Severity.HIGH, "script-tag");

        tracker.attackDetected("10.0.0.7", true, "attacker", null, high);
        tracker.attackDetected("203.0.113.9", false, "attacker2", null, high);

        verify(moderationService).addSanction(eq(ChatModerationService.TYPE_MUTE), eq("attacker"), isNull(), any(), any(), any(), eq("auto"));
        verify(moderationService).addSanction(eq(ChatModerationService.TYPE_MUTE), eq("attacker2"), isNull(), any(), any(), any(), eq("auto"));
        verify(moderationService, never()).addSanction(eq(ChatModerationService.TYPE_BLOCK_IP), any(), any(), any(), any(), any(), any());
    }

    @Test
    void highSeverityFromUntrustedGuestAddressIsRecordedButNotBanned() {
        OffenderTracker tracker = tracker();
        AttackContentDetector.Verdict high = new AttackContentDetector.Verdict(AttackContentDetector.Severity.HIGH, "sql-injection");
        tracker.attackDetected("203.0.113.9", false, null, null, high);
        tracker.attackDetected("10.0.0.7", true, null, null, high);
        verify(moderationService, never()).addSanction(anyString(), any(), any(), any(), any(), any(), any());
        assertEquals(2, tracker.recentStrikeCount());
    }

    @Test
    void mediumSeverityContentCountsAsWeightedStrikes() {
        OffenderTracker tracker = tracker();
        AttackContentDetector.Verdict medium = new AttackContentDetector.Verdict(AttackContentDetector.Severity.MEDIUM, "link-flood");
        int attemptsToBan = (int) Math.ceil((OffenderTracker.MEMBER_STRIKE_LIMIT + 1) / (double) OffenderTracker.SUSPICIOUS_CONTENT_WEIGHT);
        for (int i = 0; i < attemptsToBan - 1; i++) {
            tracker.attackDetected("203.0.113.9", true, "linker", null, medium);
        }
        verify(moderationService, never()).addSanction(anyString(), any(), any(), any(), any(), any(), any());
        tracker.attackDetected("203.0.113.9", true, "linker", null, medium);
        verify(moderationService).addSanction(eq(ChatModerationService.TYPE_MUTE), eq("linker"), isNull(), any(),
                eq(OffenderTracker.FIRST_BAN_MINUTES), contains("link-flood"), eq("auto"));
        tracker.attackDetected("203.0.113.9", true, "linker", null, AttackContentDetector.Verdict.NONE);
        tracker.attackDetected("203.0.113.9", true, "linker", null, null);
    }

    @Test
    void alreadyRestrictedTargetsAreNotBannedAgain() {
        when(moderationService.checkRestricted(isNull(), eq("203.0.113.9"))).thenReturn("이용이 제한되었습니다.");
        OffenderTracker tracker = tracker();
        for (int i = 0; i < OffenderTracker.IP_STRIKE_LIMIT + 1; i++) {
            tracker.strike("203.0.113.9", true, null, null, "속도 제한");
        }
        verify(moderationService, never()).addSanction(anyString(), any(), any(), any(), any(), any(), any());
    }
}
