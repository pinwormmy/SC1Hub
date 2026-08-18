package com.sc1hub.chat.service;

import com.sc1hub.chat.config.ChatProperties;
import com.sc1hub.chat.dto.ChatMessageDTO;
import com.sc1hub.chat.mapper.ChatMapper;
import com.sc1hub.member.mapper.MemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private ChatMapper chatMapper;

    @Mock
    private ChatModerationService moderationService;

    @Mock
    private MemberMapper memberMapper;

    private ChatRoomService chatRoomService;

    @BeforeEach
    void setUp() {
        chatRoomService = new ChatRoomService(
                chatMapper,
                new ChatProperties(),
                moderationService,
                memberMapper);
    }

    @Test
    void getRecentMessagesExcludingNickname_returnsLatestMessagesWithoutSelf() {
        chatRoomService.postBotMessage("유저A", "첫 메시지");
        chatRoomService.postBotMessage("고수봇", "이전 공략");
        chatRoomService.postBotMessage("유저B", "두 번째 메시지");
        chatRoomService.postBotMessage("고수봇", "또 다른 공략");
        chatRoomService.postBotMessage("유저C", "세 번째 메시지");

        List<ChatMessageDTO> recent = chatRoomService
                .getRecentMessagesExcludingNickname("고수봇", 3);

        assertEquals(3, recent.size());
        assertEquals("유저A", recent.get(0).getNickname());
        assertEquals("유저B", recent.get(1).getNickname());
        assertEquals("유저C", recent.get(2).getNickname());
    }
}
