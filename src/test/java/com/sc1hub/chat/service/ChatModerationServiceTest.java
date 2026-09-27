package com.sc1hub.chat.service;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.chat.config.ChatProperties;
import com.sc1hub.chat.dto.ChatSanctionDTO;
import com.sc1hub.chat.mapper.ChatMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatModerationServiceTest {

    @Mock
    private ChatMapper chatMapper;

    private ChatModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ChatModerationService(chatMapper, new ChatProperties(), new AssistantProperties());
    }

    @Test
    void reloadSanctions_keepsExistingSanctionsVisibleWhileReloading() {
        List<String> seenDuringReload = new ArrayList<>();
        when(chatMapper.selectActiveSanctions())
                .thenReturn(List.of(mute("muted-member"), blockIp("203.0.113.7")))
                .thenAnswer(invocation -> {
                    // 재로딩(DB 조회) 도중에 들어온 요청도 기존 제재를 그대로 봐야 한다.
                    seenDuringReload.add(moderationService.checkRestricted("muted-member", null));
                    seenDuringReload.add(moderationService.checkRestricted(null, "203.0.113.7"));
                    return List.of(mute("muted-member"), blockIp("203.0.113.7"));
                });

        moderationService.reloadSanctions();
        moderationService.reloadSanctions();

        assertNotNull(seenDuringReload.get(0));
        assertNotNull(seenDuringReload.get(1));
        assertNotNull(moderationService.checkRestricted("muted-member", null));
        assertNotNull(moderationService.checkRestricted(null, "203.0.113.7"));
    }

    @Test
    void reloadSanctions_replacesCacheWithLatestSanctions() {
        when(chatMapper.selectActiveSanctions())
                .thenReturn(List.of(mute("muted-member")))
                .thenReturn(Collections.emptyList());

        moderationService.reloadSanctions();
        assertNotNull(moderationService.checkRestricted("muted-member", "198.51.100.1"));

        moderationService.revokeSanction(1L);
        assertNull(moderationService.checkRestricted("muted-member", "198.51.100.1"), "해제된 제재는 사라진다");
    }

    @Test
    void reloadSanctions_keepsPreviousSanctionsWhenLoadingFails() {
        when(chatMapper.selectActiveSanctions())
                .thenReturn(List.of(blockIp("203.0.113.8")))
                .thenThrow(new RuntimeException("db down"));

        moderationService.reloadSanctions();
        moderationService.reloadSanctions();

        assertNotNull(moderationService.checkRestricted(null, "203.0.113.8"));
    }

    @Test
    void addSanction_appliesNewSanctionAfterReload() {
        when(chatMapper.selectActiveSanctions()).thenReturn(List.of(mute("new-offender")));

        moderationService.addSanction(ChatModerationService.TYPE_MUTE, "new-offender", null, "닉", 10, "spam", "admin");

        assertNotNull(moderationService.checkRestricted("new-offender", null));
        assertNull(moderationService.checkRestricted("someone-else", null));
    }

    private static ChatSanctionDTO mute(String memberId) {
        ChatSanctionDTO sanction = new ChatSanctionDTO();
        sanction.setSanctionType(ChatModerationService.TYPE_MUTE);
        sanction.setMemberId(memberId);
        sanction.setExpiresAt(LocalDateTime.now().plusHours(1));
        return sanction;
    }

    private static ChatSanctionDTO blockIp(String ip) {
        ChatSanctionDTO sanction = new ChatSanctionDTO();
        sanction.setSanctionType(ChatModerationService.TYPE_BLOCK_IP);
        sanction.setIp(ip);
        return sanction;
    }
}
