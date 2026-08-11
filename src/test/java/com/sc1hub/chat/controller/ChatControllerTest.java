package com.sc1hub.chat.controller;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.service.AssistantRateLimiter;
import com.sc1hub.assistant.service.AssistantService;
import com.sc1hub.chat.config.ChatProperties;
import com.sc1hub.chat.dto.ChatMessageDTO;
import com.sc1hub.chat.dto.ChatPollResponseDTO;
import com.sc1hub.chat.service.ChatModerationService;
import com.sc1hub.chat.service.ChatRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private ChatModerationService moderationService;
    @Mock
    private AssistantService assistantService;
    @Mock
    private AssistantProperties assistantProperties;
    @Mock
    private AssistantRateLimiter assistantRateLimiter;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpSession session;

    private ChatProperties chatProperties;
    private ChatController controller;

    @BeforeEach
    void setUp() {
        chatProperties = new ChatProperties();
        chatProperties.setEnabled(true);
        chatProperties.setInitialHistorySize(12);
        controller = new ChatController(
                chatRoomService,
                moderationService,
                chatProperties,
                assistantService,
                assistantProperties,
                assistantRateLimiter);
    }

    @Test
    void initialPollReturnsOnlyConfiguredRecentMessages() {
        ChatPollResponseDTO fullResponse = new ChatPollResponseDTO();
        List<ChatMessageDTO> recentMessages = Collections.singletonList(new ChatMessageDTO());
        when(chatRoomService.poll(0)).thenReturn(fullResponse);
        when(chatRoomService.getRecentMessages(12)).thenReturn(recentMessages);

        ResponseEntity<ChatPollResponseDTO> result = controller.messages(0, request, session);

        assertSame(recentMessages, result.getBody().getMessages());
        verify(chatRoomService).getRecentMessages(12);
    }

    @Test
    void subsequentPollKeepsAllMessagesAfterSequence() {
        ChatPollResponseDTO incrementalResponse = new ChatPollResponseDTO();
        when(chatRoomService.poll(42)).thenReturn(incrementalResponse);

        ResponseEntity<ChatPollResponseDTO> result = controller.messages(42, request, session);

        assertSame(incrementalResponse, result.getBody());
        verify(chatRoomService, never()).getRecentMessages(anyInt());
    }
}
