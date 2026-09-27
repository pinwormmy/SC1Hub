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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
                memberMapper,
                new com.sc1hub.common.security.DuplicateContentGuard());
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

    @Test
    void getRecentMessagesAfterLatestNickname_returnsOnlyNewerMessages() {
        chatRoomService.postBotMessage("유저A", "예전 질문");
        chatRoomService.postBotMessage("고수봇", "이전 공략");
        chatRoomService.postBotMessage("유저B", "새 질문");
        chatRoomService.postBotMessage("유저C", "새 반응");

        List<ChatMessageDTO> recent = chatRoomService
                .getRecentMessagesAfterLatestNickname("고수봇", 3);

        assertEquals(2, recent.size());
        assertEquals("유저B", recent.get(0).getNickname());
        assertEquals("유저C", recent.get(1).getNickname());
    }

    @Test
    void getRecentMessagesAfterLatestNickname_returnsEmptyWhenSelfIsLatest() {
        chatRoomService.postBotMessage("유저A", "예전 질문");
        chatRoomService.postBotMessage("고수봇", "이전 공략");

        List<ChatMessageDTO> recent = chatRoomService
                .getRecentMessagesAfterLatestNickname("고수봇", 3);

        assertEquals(0, recent.size());
    }

    @Test
    void pollRecentReturnsOnlyLatestMessagesInChronologicalOrder() {
        for (int index = 1; index <= 60; index += 1) {
            chatRoomService.postBotMessage("유저" + index, "메시지" + index);
        }

        List<ChatMessageDTO> recent = chatRoomService.pollRecent(50).getMessages();

        assertEquals(50, recent.size());
        assertEquals("유저11", recent.get(0).getNickname());
        assertEquals("유저60", recent.get(49).getNickname());
    }

    @Test
    void shutdownFlush_drainsEveryPendingMessageBeyondOneBatch() {
        List<Integer> batchSizes = new java.util.ArrayList<>();
        doAnswer(invocation -> {
            batchSizes.add(invocation.<List<ChatMessageDTO>>getArgument(0).size());
            return null;
        }).when(chatMapper).insertMessages(anyList());
        for (int index = 0; index < 250; index += 1) {
            chatRoomService.postBotMessage("유저" + index, "메시지" + index);
        }

        chatRoomService.shutdownFlush();

        assertEquals(List.of(100, 100, 50), batchSizes);
        assertFalse(chatRoomService.hasPendingWrites());
    }

    @Test
    void flushPendingWrites_retriesFailedBatchOnNextRun() {
        doThrow(new RuntimeException("db down")).doNothing().when(chatMapper).insertMessages(anyList());
        chatRoomService.postBotMessage("유저A", "첫 메시지");
        chatRoomService.postBotMessage("유저B", "두 번째 메시지");

        chatRoomService.flushPendingWrites();
        assertTrue(chatRoomService.hasPendingWrites(), "실패한 배치는 다시 저장을 시도한다");
        assertEquals(2, chatRoomService.countPendingWrites());

        chatRoomService.flushPendingWrites();
        assertFalse(chatRoomService.hasPendingWrites());
        verify(chatMapper, times(2)).insertMessages(anyList());
    }

    @Test
    void flushPendingWrites_dropsBatchAfterMaxAttempts() {
        doThrow(new RuntimeException("poison")).when(chatMapper).insertMessages(anyList());
        chatRoomService.postBotMessage("유저A", "저장 안 되는 메시지");

        for (int attempt = 0; attempt < ChatRoomService.MAX_FLUSH_ATTEMPTS; attempt += 1) {
            chatRoomService.flushPendingWrites();
        }

        assertFalse(chatRoomService.hasPendingWrites(), "영구 실패 배치가 다음 저장을 막지 않는다");
        verify(chatMapper, times(ChatRoomService.MAX_FLUSH_ATTEMPTS)).insertMessages(anyList());
    }

    @Test
    void shutdownFlush_terminatesWhenDatabaseKeepsFailing() {
        doThrow(new RuntimeException("db down")).when(chatMapper).insertMessages(anyList());
        for (int index = 0; index < 250; index += 1) {
            chatRoomService.postBotMessage("유저" + index, "메시지" + index);
        }

        chatRoomService.shutdownFlush();

        assertFalse(chatRoomService.hasPendingWrites());
        verify(chatMapper, times(3 * ChatRoomService.MAX_FLUSH_ATTEMPTS)).insertMessages(anyList());
    }
}
