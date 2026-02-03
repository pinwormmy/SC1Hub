package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.dto.AssistantChatResponseDTO;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.rag.AssistantRagChunk;
import com.sc1hub.assistant.rag.AssistantRagSearchService;
import com.sc1hub.assistant.search.AssistantQueryParseResult;
import com.sc1hub.assistant.search.AssistantQueryParser;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.member.dto.MemberDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
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

    @Mock
    private AssistantQueryParser queryParser;

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
        assistantProperties.setAnswerMaxSentences(8);
        assistantProperties.setAnswerMaxChars(1500);
        assistantProperties.setAnswerMaxOutputTokens(2048);
        assistantProperties.setPerBoardLimit(5);
        ragProperties = new AssistantRagProperties();
        ragProperties.setEnabled(false);
        ObjectMapper objectMapper = new ObjectMapper();
        assistantService = new AssistantService(boardMapper, geminiClient, assistantProperties, ragSearchService, ragProperties, queryParser, objectMapper);
        lenient().when(queryParser.parse(anyString()))
                .thenAnswer(invocation -> buildParseResult(invocation.getArgument(0)));
    }

    @Test
    void chat_returnsError_whenDisabled() {
        assistantProperties.setEnabled(false);

        AssistantChatResponseDTO response = assistantService.chat("5팩 질문", null);

        assertTrue(response.getError().contains("비활성화"));
    }

    @Test
    void chat_returnsError_whenRequireLogin_andNotLoggedIn() {
        assistantProperties.setRequireLogin(true);

        AssistantChatResponseDTO response = assistantService.chat("5팩 질문", null);

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
        weakMatch.setTitle("5팩 골리앗 빌드 정리");
        weakMatch.setContent("테란 5팩 골리앗 빌드/운영 정리 내용입니다. 5팩 골리앗 빌드 정리 내용입니다.");
        weakMatch.setRegDate(new Date());

        doReturn(Collections.singletonList(strongMatch))
                .when(boardMapper)
                .searchPostsByKeywords(eq("freeboard"), anyList(), anyInt());
        doReturn(Collections.singletonList(weakMatch))
                .when(boardMapper)
                .searchPostsByKeywords(eq("tipboard"), anyList(), anyInt());

        when(geminiClient.generateAnswer(anyString(), anyInt())).thenReturn("{\"answer\":\"답변입니다.\",\"citations\":[\"freeboard:9\"]}");

        MemberDTO member = new MemberDTO();
        member.setNickName("tester");

        AssistantChatResponseDTO response = assistantService.chat("5팩 골리앗 운영 알려줘", member);

        assertEquals("답변입니다.", response.getAnswer());
        assertEquals(Collections.singletonList("freeboard:9"), response.getUsedPostIds());
        assertNotNull(response.getRelatedPosts());
        assertEquals(2, response.getRelatedPosts().size());
        assertEquals("freeboard", response.getRelatedPosts().get(0).getBoardTitle());
        assertEquals("/boards/freeboard/readPost?postNum=9", response.getRelatedPosts().get(0).getUrl());
        assertEquals("tipboard", response.getRelatedPosts().get(1).getBoardTitle());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateAnswer(promptCaptor.capture(), anyInt());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("User question"));
        assertTrue(prompt.contains("max 8 sentences"));
        assertTrue(prompt.contains("<= 1500 chars"));
        assertTrue(prompt.contains("board=freeboard"));
        assertTrue(prompt.contains("title=5팩 골리앗 운영"));
    }

    @Test
    void chat_fallsBackToKeywordCandidates_whenCitationsEmpty() throws Exception {
        BoardListDTO free = new BoardListDTO();
        free.setBoardTitle("FreeBoard");
        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(free));

        BoardDTO match = new BoardDTO();
        match.setPostNum(9);
        match.setTitle("커공발 운영");
        match.setContent("프로토스 대저그 커공발 운영 정리");
        match.setRegDate(new Date());

        doReturn(Collections.singletonList(match))
                .when(boardMapper)
                .searchPostsByKeywords(eq("freeboard"), anyList(), anyInt());

        when(geminiClient.generateAnswer(anyString(), anyInt())).thenReturn("{\"answer\":\"답변입니다.\",\"citations\":[]}");

        AssistantChatResponseDTO response = assistantService.chat("커공발 알려줘", null);

        assertEquals("답변입니다.", response.getAnswer());
        assertTrue(response.getUsedPostIds().isEmpty());
        assertNotNull(response.getRelatedPosts());
        assertFalse(response.getRelatedPosts().isEmpty());
        assertEquals("freeboard", response.getRelatedPosts().get(0).getBoardTitle());
        assertEquals(9, response.getRelatedPosts().get(0).getPostNum());
        assertTrue(response.getRelatedPostsNotice() == null || !response.getRelatedPostsNotice().contains("못했습니다"));
    }

    @Test
    void chat_ordersRelatedPostsByRelevance_andFillsUpToThree() throws Exception {
        BoardListDTO pvz = new BoardListDTO();
        pvz.setBoardTitle("PvZBoard");
        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(pvz));

        BoardDTO best = new BoardDTO();
        best.setPostNum(4);
        best.setTitle("대저그전 중반 정석 빌드, 커공발 운영법");
        best.setContent("커공발은 커세어 공업 발업 질럿 운영입니다. 커세어로 상대 스커지/오버로드를 견제하고 멀티를 안전하게 가져갑니다.");
        best.setRegDate(new Date());

        BoardDTO weaker = new BoardDTO();
        weaker.setPostNum(17);
        weaker.setTitle("깔끔하게 정리해보자~ 프저전 빌드 로드맵 (커공발 포함)");
        weaker.setContent("프저전 빌드 로드맵 정리 글입니다. 커공발도 포함되어 있습니다.");
        weaker.setRegDate(new Date(System.currentTimeMillis() - 1000));

        BoardDTO third = new BoardDTO();
        third.setPostNum(8);
        third.setTitle("커공발 이후 운영 팁 모음");
        third.setContent("커공발 운영 이후 운영 팁들을 정리했습니다. 커세어와 질럿 컨트롤, 공업/발업 타이밍을 다룹니다.");
        third.setRegDate(new Date(System.currentTimeMillis() - 2000));

        doReturn(Arrays.asList(best, weaker, third))
                .when(boardMapper)
                .searchPostsByKeywords(eq("pvzboard"), anyList(), anyInt());

        when(geminiClient.generateAnswer(anyString(), anyInt())).thenReturn(
                "{\"answer\":\"커공발은 커세어 공업 발업 질럿 운영입니다.\",\"citations\":[\"pvzboard:4\"]}"
        );

        AssistantChatResponseDTO response = assistantService.chat("커공발 알려줘", null);

        assertEquals(3, response.getRelatedPosts().size());
        assertEquals("pvzboard", response.getRelatedPosts().get(0).getBoardTitle());
        assertEquals(4, response.getRelatedPosts().get(0).getPostNum());
    }

    @Test
    void chat_returnsNotice_whenNoRelatedPosts() throws Exception {
        BoardListDTO free = new BoardListDTO();
        free.setBoardTitle("FreeBoard");
        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(free));

        doReturn(Collections.emptyList())
                .when(boardMapper)
                .searchPostsByKeywords(eq("freeboard"), anyList(), anyInt());

        when(geminiClient.generateAnswer(anyString(), anyInt())).thenReturn(
                "{\"answer\":\"관련 글을 찾지 못했습니다.\",\"citations\":[]}"
        );

        AssistantChatResponseDTO response = assistantService.chat("커공발 알려줘", null);

        assertTrue(response.getRelatedPosts().isEmpty());
        assertEquals("관련 글이 없습니다.", response.getRelatedPostsNotice());
    }

    @Test
    void chat_skipsUnsafeBoardTitles() {
        BoardListDTO unsafe = new BoardListDTO();
        unsafe.setBoardTitle("freeboard;drop table member;");

        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(unsafe));
        when(geminiClient.generateAnswer(anyString(), anyInt())).thenReturn("답변입니다.");

        AssistantChatResponseDTO response = assistantService.chat("5팩", null);

        verify(boardMapper).getBoardList();
        verify(geminiClient).generateAnswer(anyString(), anyInt());
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
        when(geminiClient.generateAnswer(anyString(), anyInt())).thenReturn("{\"answer\":\"답변입니다.\",\"citations\":[\"freeboard:9\"]}");

        AssistantChatResponseDTO response = assistantService.chat("5팩 골리앗 운영 알려줘", null);

        assertEquals("답변입니다.", response.getAnswer());
        assertEquals(1, response.getRelatedPosts().size());
        assertEquals("freeboard", response.getRelatedPosts().get(0).getBoardTitle());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateAnswer(promptCaptor.capture(), anyInt());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("Site snippets"));
        assertTrue(prompt.contains("chunkIndex=0"));
        assertTrue(prompt.contains("title=5팩 골리앗 운영"));
    }

    @Test
    void chat_mergesRagAndKeywordResults_whenAvailable() throws Exception {
        ragProperties.setEnabled(true);
        when(ragSearchService.isEnabled()).thenReturn(true);

        BoardListDTO free = new BoardListDTO();
        free.setBoardTitle("FreeBoard");

        BoardListDTO tip = new BoardListDTO();
        tip.setBoardTitle("TipBoard");

        when(boardMapper.getBoardList()).thenReturn(Arrays.asList(free, tip));

        BoardDTO keywordMatch = new BoardDTO();
        keywordMatch.setPostNum(3);
        keywordMatch.setTitle("5팩 골리앗 운영 정리");
        keywordMatch.setContent("테란 5팩 골리앗 운영 팁 정리 내용입니다.");
        keywordMatch.setRegDate(new Date());

        doReturn(Collections.emptyList())
                .when(boardMapper)
                .searchPostsByKeywords(eq("freeboard"), anyList(), anyInt());
        doReturn(Collections.singletonList(keywordMatch))
                .when(boardMapper)
                .searchPostsByKeywords(eq("tipboard"), anyList(), anyInt());

        AssistantRagChunk chunk = new AssistantRagChunk();
        chunk.setBoardTitle("freeboard");
        chunk.setPostNum(9);
        chunk.setTitle("5팩 골리앗 운영");
        chunk.setChunkIndex(0);
        chunk.setText("테란 5팩 골리앗 운영 팁");
        chunk.setUrl("/boards/freeboard/readPost?postNum=9");

        AssistantRagSearchService.Match match = AssistantRagSearchService.Match.of(chunk, 0.9);

        when(ragSearchService.search(anyString(), anyInt())).thenReturn(Collections.singletonList(match));
        when(geminiClient.generateAnswer(anyString(), anyInt())).thenReturn("{\"answer\":\"답변입니다.\",\"citations\":[\"freeboard:9\"]}");

        AssistantChatResponseDTO response = assistantService.chat("5팩 골리앗 운영 알려줘", null);

        assertEquals(2, response.getRelatedPosts().size());
        assertEquals("freeboard", response.getRelatedPosts().get(0).getBoardTitle());
        assertEquals("tipboard", response.getRelatedPosts().get(1).getBoardTitle());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateAnswer(promptCaptor.capture(), anyInt());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("Site snippets"));
        assertTrue(prompt.contains("Site posts"));
        assertTrue(prompt.contains("excerpt="));
    }

    @Test
    void chat_appliesLlmMatchupClassification_whenEnabledAndParserConfidenceLow() throws Exception {
        assistantProperties.setLlmMatchupClassificationEnabled(true);
        assistantProperties.setLlmMatchupClassificationCacheSeconds(60);
        assistantProperties.setLlmMatchupClassificationRateLimitPerMinute(10);

        BoardListDTO tvp = new BoardListDTO();
        tvp.setBoardTitle("tVsPBoard");
        BoardListDTO free = new BoardListDTO();
        free.setBoardTitle("FreeBoard");
        when(boardMapper.getBoardList()).thenReturn(Arrays.asList(tvp, free));

        BoardDTO tvpPost = new BoardDTO();
        tvpPost.setPostNum(1);
        tvpPost.setTitle("5팩 운영");
        tvpPost.setContent("내용");
        tvpPost.setRegDate(new Date(System.currentTimeMillis() - 1000));

        BoardDTO freePost = new BoardDTO();
        freePost.setPostNum(2);
        freePost.setTitle("5팩 운영");
        freePost.setContent("내용");
        freePost.setRegDate(new Date());

        doReturn(Collections.singletonList(tvpPost))
                .when(boardMapper)
                .searchPostsByKeywords(eq("tvspboard"), anyList(), anyInt());
        doReturn(Collections.singletonList(freePost))
                .when(boardMapper)
                .searchPostsByKeywords(eq("freeboard"), anyList(), anyInt());

        String classificationJson = "{\"intent\":\"guide\",\"playerRace\":\"T\",\"opponentRace\":\"P\",\"confidence\":0.9}";
        when(geminiClient.generateAnswer(anyString())).thenReturn(classificationJson);
        when(geminiClient.generateAnswer(anyString(), anyInt())).thenReturn("{\"answer\":\"답변입니다.\",\"citations\":[\"tvspboard:1\"]}");

        AssistantChatResponseDTO response = assistantService.chat("5팩 운영", null);

        assertEquals("답변입니다.", response.getAnswer());
        // supporting 후보가 evidence와 제목이 동일하면(중복글) 다양성 규칙으로 제외될 수 있다.
        assertEquals(1, response.getRelatedPosts().size());
        assertEquals("tvspboard", response.getRelatedPosts().get(0).getBoardTitle());
        assertTrue(response.getRelatedPostsNotice() == null || response.getRelatedPostsNotice().contains("관련 글"));
        verify(geminiClient).generateAnswer(anyString());
        verify(geminiClient).generateAnswer(anyString(), anyInt());
    }

    @Test
    void chat_reranksCandidates_withLlm_andCachesOrder() throws Exception {
        assistantProperties.setLlmRerankEnabled(true);
        assistantProperties.setLlmRerankTopN(20);
        assistantProperties.setLlmRerankCacheSeconds(60);
        assistantProperties.setLlmRerankRateLimitPerMinute(10);

        BoardListDTO free = new BoardListDTO();
        free.setBoardTitle("FreeBoard");
        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(free));

        BoardDTO first = new BoardDTO();
        first.setPostNum(1);
        first.setTitle("5팩 운영 A");
        first.setContent("내용");
        first.setRegDate(new Date());

        BoardDTO second = new BoardDTO();
        second.setPostNum(2);
        second.setTitle("5팩 운영 B");
        second.setContent("내용");
        second.setRegDate(new Date(System.currentTimeMillis() - 1000));

        doReturn(Arrays.asList(first, second))
                .when(boardMapper)
                .searchPostsByKeywords(eq("freeboard"), anyList(), anyInt());

        when(geminiClient.generateAnswer(anyString())).thenReturn("[2,1]");
        when(geminiClient.generateAnswer(anyString(), anyInt())).thenReturn(
                "{\"answer\":\"첫번째 답변\",\"citations\":[\"freeboard:2\"]}",
                "{\"answer\":\"두번째 답변\",\"citations\":[\"freeboard:2\"]}"
        );

        AssistantChatResponseDTO firstCall = assistantService.chat("5팩 운영", null);
        assertEquals("freeboard", firstCall.getRelatedPosts().get(0).getBoardTitle());
        assertEquals(2, firstCall.getRelatedPosts().get(0).getPostNum());

        AssistantChatResponseDTO secondCall = assistantService.chat("5팩 운영", null);
        assertEquals("freeboard", secondCall.getRelatedPosts().get(0).getBoardTitle());
        assertEquals(2, secondCall.getRelatedPosts().get(0).getPostNum());

        // rerank 1회 + answer 2회
        verify(geminiClient).generateAnswer(anyString());
        verify(geminiClient, times(2)).generateAnswer(anyString(), anyInt());
    }

    private AssistantQueryParseResult buildParseResult(String message) {
        AssistantQueryParseResult result = new AssistantQueryParseResult();
        if (message == null) {
            return result;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return result;
        }
        String[] tokens = trimmed.split("\\s+");
        ArrayList<String> keywords = new ArrayList<>();
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String cleaned = token.trim();
            if (!cleaned.isEmpty()) {
                keywords.add(cleaned);
            }
        }
        result.setKeywords(keywords);
        result.setExpandedTerms(new ArrayList<>(keywords));
        return result;
    }
}
