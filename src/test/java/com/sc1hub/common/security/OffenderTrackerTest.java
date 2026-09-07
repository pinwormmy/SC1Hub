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
    void alreadyRestrictedTargetsAreNotBannedAgain() {
        when(moderationService.checkRestricted(isNull(), eq("203.0.113.9"))).thenReturn("이용이 제한되었습니다.");
        OffenderTracker tracker = tracker();
        for (int i = 0; i < OffenderTracker.IP_STRIKE_LIMIT + 1; i++) {
            tracker.strike("203.0.113.9", true, null, null, "속도 제한");
        }
        verify(moderationService, never()).addSanction(anyString(), any(), any(), any(), any(), any(), any());
    }
}
