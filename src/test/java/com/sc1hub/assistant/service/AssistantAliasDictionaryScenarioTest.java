package com.sc1hub.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.config.AssistantRagProperties;
import com.sc1hub.assistant.dto.AliasDictionaryDTO;
import com.sc1hub.assistant.dto.AssistantChatResponseDTO;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.mapper.AliasDictionaryMapper;
import com.sc1hub.assistant.rag.AssistantRagSearchService;
import com.sc1hub.assistant.search.AssistantQueryExpansion;
import com.sc1hub.assistant.search.AssistantQueryParser;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantAliasDictionaryScenarioTest {

    @Mock
    private BoardMapper boardMapper;

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private AssistantRagSearchService ragSearchService;

    @Mock
    private AliasDictionaryMapper aliasDictionaryMapper;

    private AssistantService assistantService;

    @BeforeEach
    void setUp() {
        AssistantProperties assistantProperties = new AssistantProperties();
        assistantProperties.setEnabled(true);
        assistantProperties.setRequireLogin(false);
        assistantProperties.setMaxRelatedPosts(3);
        assistantProperties.setContextPosts(5);
        assistantProperties.setAnswerMaxSentences(8);
        assistantProperties.setAnswerMaxChars(1500);
        assistantProperties.setAnswerMaxOutputTokens(2048);
        assistantProperties.setPerBoardLimit(5);

        AssistantRagProperties ragProperties = new AssistantRagProperties();
        ragProperties.setEnabled(false);

        ObjectMapper objectMapper = new ObjectMapper();
        AssistantQueryExpansion expansion = new AssistantQueryExpansion(objectMapper);
        AssistantQueryParser queryParser = new AssistantQueryParser(aliasDictionaryMapper, objectMapper, expansion);

        assistantService = new AssistantService(
                boardMapper,
                geminiClient,
                assistantProperties,
                ragSearchService,
                ragProperties,
                queryParser,
                objectMapper
        );
    }

    @Test
    void scenario1_aliasDictionary_andAnswerGroundedEvidencePost() throws Exception {
        // 1) alias_dictionary에 다음 레코드를 넣는다:
        //    alias="커공발"
        //    canonical_terms=["커공발","커세어","공업","발업","운영"]
        //    matchup_hint="PvZ"
        //    boost_board_ids=[프저전게시판ID]
        AliasDictionaryDTO alias = new AliasDictionaryDTO();
        alias.setAlias("커공발");
        alias.setCanonicalTerms("[\"커공발\",\"커세어\",\"공업\",\"발업\",\"운영\"]");
        alias.setMatchupHint("PvZ");
        alias.setBoostBoardIds("[\"pvszboard\"]");
        when(aliasDictionaryMapper.selectAll()).thenReturn(Collections.singletonList(alias));

        BoardListDTO pvz = new BoardListDTO();
        pvz.setBoardTitle("pVsZBoard");

        BoardListDTO free = new BoardListDTO();
        free.setBoardTitle("FreeBoard");

        BoardListDTO tip = new BoardListDTO();
        tip.setBoardTitle("TipBoard");

        when(boardMapper.getBoardList()).thenReturn(Arrays.asList(pvz, free, tip));

        // 2) 프저전 게시판에 제목 "커공발 운영 정리" 글이 존재할 때,
        //    질문 "커공발 빌드 알려줘"로 검색하면 해당 글이 evidence(필수 1개)로 포함되어야 한다.
        BoardDTO target = new BoardDTO();
        target.setPostNum(1);
        target.setTitle("커공발 운영 정리");
        target.setContent("내용");
        target.setRegDate(new Date());

        // 경쟁 글(다른 보드) - 후보가 5개를 넘도록 만들어 "top5" 여부를 검증한다.
        List<BoardDTO> freePosts = Arrays.asList(
                buildCompetitor(10, "커공발 관련 글", new Date()),
                buildCompetitor(11, "커공발 관련 글2", new Date()),
                buildCompetitor(12, "커공발 질문", new Date()),
                buildCompetitor(13, "커공발 후기", new Date())
        );
        List<BoardDTO> tipPosts = Arrays.asList(
                buildCompetitor(20, "커공발 팁", new Date()),
                buildCompetitor(21, "커공발 초보 팁", new Date())
        );

        doReturn(Collections.singletonList(target))
                .when(boardMapper)
                .searchPostsByKeywords(eq("pvszboard"), anyList(), anyInt());
        doReturn(freePosts)
                .when(boardMapper)
                .searchPostsByKeywords(eq("freeboard"), anyList(), anyInt());
        doReturn(tipPosts)
                .when(boardMapper)
                .searchPostsByKeywords(eq("tipboard"), anyList(), anyInt());

        when(geminiClient.generateAnswer(anyString(), anyInt()))
                .thenReturn("{\"answer\":\"답변입니다.\",\"citations\":[\"pvszboard:1\"]}");

        AssistantChatResponseDTO response = assistantService.chat("커공발 빌드 알려줘", null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateAnswer(promptCaptor.capture(), anyInt());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("sourceId=pvszboard:1");
        assertThat(prompt.split("sourceId=").length - 1).isEqualTo(5);

        assertThat(response.getUsedPostIds()).containsExactly("pvszboard:1");
        assertThat(response.getRelatedPosts())
                .isNotEmpty();
        assertThat(response.getRelatedPosts().get(0).getBoardTitle()).isEqualTo("pvszboard");
        assertThat(response.getRelatedPosts().get(0).getTitle()).isEqualTo("커공발 운영 정리");
    }

    @Test
    void scenario2_sparseQuery_doesNotForceRelatedPosts() {
        when(aliasDictionaryMapper.selectAll()).thenReturn(Collections.emptyList());
        when(boardMapper.getBoardList()).thenReturn(Collections.emptyList());
        when(geminiClient.generateAnswer(anyString(), anyInt())).thenReturn("{\"answer\":\"답변입니다.\",\"citations\":[]}");

        AssistantChatResponseDTO response = assistantService.chat("그냥 질문", null);

        assertThat(response.getUsedPostIds()).isEmpty();
        assertThat(response.getRelatedPosts()).isEmpty();
        assertThat(response.getRelatedPostsNotice()).contains("관련 글");
    }

    private static BoardDTO buildCompetitor(int postNum, String title, Date regDate) {
        BoardDTO dto = new BoardDTO();
        dto.setPostNum(postNum);
        dto.setTitle(title);
        dto.setContent("내용");
        dto.setRegDate(regDate);
        return dto;
    }
}
