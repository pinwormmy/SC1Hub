package com.sc1hub.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.dto.AssistantChatResponseDTO;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.rag.AssistantRagSearchService;
import com.sc1hub.assistant.search.AssistantQueryParseResult;
import com.sc1hub.assistant.search.AssistantQueryParser;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantServiceRelatedPostsTest {

    @Test
    void fillsUpToThreeRelatedPostsWhenWriterDuplicatesExist() throws Exception {
        BoardMapper boardMapper = mock(BoardMapper.class);
        GeminiClient geminiClient = mock(GeminiClient.class);
        AssistantRagSearchService ragSearchService = mock(AssistantRagSearchService.class);
        AssistantQueryParser queryParser = mock(AssistantQueryParser.class);

        AssistantProperties assistantProperties = new AssistantProperties();
        assistantProperties.setEnabled(true);
        assistantProperties.setRequireLogin(false);
        assistantProperties.setMaxRelatedPosts(3);
        assistantProperties.setContextPosts(3);
        assistantProperties.setPerBoardLimit(10);

        AssistantRagProperties ragProperties = new AssistantRagProperties();
        ragProperties.setEnabled(false);

        AssistantService service = new AssistantService(
                boardMapper,
                geminiClient,
                assistantProperties,
                ragSearchService,
                ragProperties,
                queryParser,
                new ObjectMapper()
        );

        BoardListDTO board = new BoardListDTO();
        board.setBoardTitle("testboard");
        board.setKoreanTitle("테스트");
        when(boardMapper.getBoardList()).thenReturn(Collections.singletonList(board));

        List<BoardDTO> posts = Arrays.asList(
                buildPost(1, "테스트 빌드 A", "writerA"),
                buildPost(2, "테스트 빌드 B", "writerB"),
                buildPost(3, "테스트 빌드 C", "writerA")
        );
        when(boardMapper.searchPostsByKeywords(eq("testboard"), anyList(), anyInt())).thenReturn(posts);

        AssistantQueryParseResult parseResult = new AssistantQueryParseResult();
        parseResult.setKeywords(Arrays.asList("테스트", "빌드"));
        parseResult.setExpandedTerms(Arrays.asList("테스트", "빌드"));
        when(queryParser.parse(anyString())).thenReturn(parseResult);

        when(geminiClient.generateAnswer(anyString(), Mockito.<Integer>any()))
                .thenReturn("{\"answer\":\"테스트 빌드 답변\",\"citations\":[\"testboard:1\"]}");

        AssistantChatResponseDTO response = service.chat("테스트 빌드", null);

        assertNull(response.getError());
        assertEquals(3, response.getRelatedPosts().size());
        assertNull(response.getRelatedPostsNotice());
    }

    private static BoardDTO buildPost(int postNum, String title, String writer) {
        BoardDTO dto = new BoardDTO();
        dto.setPostNum(postNum);
        dto.setTitle(title);
        dto.setWriter(writer);
        dto.setRegDate(new Date());
        dto.setSearchTerms("테스트 빌드");
        dto.setContent(buildLongContent());
        return dto;
    }

    private static String buildLongContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i += 1) {
            sb.append("테스트 빌드 내용 ");
        }
        return sb.toString();
    }
}

