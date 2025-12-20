package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.dto.AssistantChatResponseDTO;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.rag.AssistantRagChunk;
import com.sc1hub.assistant.rag.AssistantRagSearchService;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.member.dto.MemberDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock
    private BoardMapper boardMapper;

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private AssistantRagSearchService ragSearchService;

    private AssistantProperties assistantProperties;
    private AssistantRagProperties ragProperties;
    private AssistantService assistantService;

    @BeforeEach
    void setUp() {
        assistantProperties = new AssistantProperties();
        assistantProperties.setEnabled(true);
        assistantProperties.setRequireLogin(false);
        assistantProperties.setMaxRelatedPosts(5);
        assistantProperties.setContextPosts(3);
        assistantProperties.setPerBoardLimit(5);
        ragProperties = new AssistantRagProperties();
        ragProperties.setEnabled(false);
        assistantService = new AssistantService(boardMapper, geminiClient, assistantProperties, ragSearchService, ragProperties);
    }

    @Test
    void chat_returnsError_whenDisabled() {
        assistantProperties.setEnabled(false);

        AssistantChatResponseDTO response = assistantService.chat("질문", null);

        assertTrue(response.getError().contains("비활성화"));
    }

    @Test
    void chat_returnsError_whenRequireLogin_andNotLoggedIn() {
        assistantProperties.setRequireLogin(true);

        AssistantChatResponseDTO response = assistantService.chat("질문", null);

        assertTrue(response.getError().contains("로그인"));
    }

    @Test
    void chat_returnsAnswer_andRelatedPosts() throws Exception {
        BoardListDTO free = new BoardListDTO();
        free.setBoardTitle("FreeBoard");
        free.setKoreanTitle("자유게시판");

        BoardListDTO tip = new BoardListDTO();
        tip.setBoardTitle("TipBoard");
        tip.setKoreanTitle("팁게시판");

        when(boardMapper.getBoardList()).thenReturn(Arrays.asList(free, tip));

        BoardDTO strongMatch = new BoardDTO();
        strongMatch.setPostNum(9);
        strongMatch.setTitle("5팩 골리앗 운영");
        strongMatch.setContent("테란 5팩 골리앗 운영 팁");
        strongMatch.setRegDate(new Date());

        BoardDTO weakMatch = new BoardDTO();
        weakMatch.setPostNum(2);
        weakMatch.setTitle("운영 질문 모음");
        weakMatch.setContent("내용에 5팩이 포함되어 있습니다.");
        weakMatch.setRegDate(new Date());

        when(boardMapper.searchPostsByKeywords(eq("freeboard"), anyList(), anyInt()))
                .thenReturn(Collections.singletonList(strongMatch));
        when(boardMapper.searchPostsByKeywords(eq("tipboard"), anyList(), anyInt()))
                .thenReturn(Collections.singletonList(weakMatch));

        when(geminiClient.generateAnswer(anyString())).thenReturn("답변입니다.");

        MemberDTO member = new MemberDTO();
        member.setNickName("tester");

        AssistantChatResponseDTO response = assistantService.chat("5팩 골리앗 운영 알려줘", member);

        assertEquals("답변입니다.", response.getAnswer());
        assertNotNull(response.getRelatedPosts());
        assertEquals(2, response.getRelatedPosts().size());
        assertEquals("freeboard", response.getRelatedPosts().get(0).getBoardTitle());
        assertEquals("/boards/freeboard/readPost?postNum=9", response.getRelatedPosts().get(0).getUrl());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateAnswer(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("User question"));
        assertTrue(prompt.contains("board=freeboard"));
        assertTrue(prompt.contains("title=5팩 골리앗 운영"));
    }

    @Test
    void chat_skipsUnsafeBoardTitles() {
        BoardListDTO unsafe = new BoardListDTO();
        unsafe.setBoardTitle("freeboard;drop table member;");

        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(unsafe));
        when(geminiClient.generateAnswer(anyString())).thenReturn("답변입니다.");

        AssistantChatResponseDTO response = assistantService.chat("질문", null);

        verify(boardMapper).getBoardList();
        verify(geminiClient).generateAnswer(anyString());
        assertTrue(response.getRelatedPosts().isEmpty());
    }

    @Test
    void chat_usesRag_whenEnabledAndIndexReady() {
        ragProperties.setEnabled(true);
        when(ragSearchService.isEnabled()).thenReturn(true);

        AssistantRagChunk chunk = new AssistantRagChunk();
        chunk.setBoardTitle("freeboard");
        chunk.setPostNum(9);
        chunk.setTitle("5팩 골리앗 운영");
        chunk.setChunkIndex(0);
        chunk.setText("테란 5팩 골리앗 운영 팁");
        chunk.setUrl("/boards/freeboard/readPost?postNum=9");

        AssistantRagSearchService.Match match = AssistantRagSearchService.Match.of(chunk, 0.9);

        when(ragSearchService.search(anyString(), anyInt())).thenReturn(Collections.singletonList(match));
        when(geminiClient.generateAnswer(anyString())).thenReturn("답변입니다.");

        AssistantChatResponseDTO response = assistantService.chat("5팩 골리앗 운영 알려줘", null);

        assertEquals("답변입니다.", response.getAnswer());
        assertEquals(1, response.getRelatedPosts().size());
        assertEquals("freeboard", response.getRelatedPosts().get(0).getBoardTitle());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateAnswer(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("Site snippets"));
        assertTrue(prompt.contains("chunkIndex=0"));
        assertTrue(prompt.contains("title=5팩 골리앗 운영"));
    }
}
