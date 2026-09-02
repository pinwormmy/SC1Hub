package com.sc1hub.assistant.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.dto.AliasDictionaryDTO;
import com.sc1hub.assistant.mapper.AliasDictionaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantQueryParserTest {

    @Mock
    private AliasDictionaryMapper aliasDictionaryMapper;

    private AssistantQueryParser queryParser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        AssistantQueryExpansion expansion = new AssistantQueryExpansion(objectMapper);
        queryParser = new AssistantQueryParser(aliasDictionaryMapper, objectMapper, expansion);
        lenient().when(aliasDictionaryMapper.selectAll()).thenReturn(Collections.emptyList());
    }

    @Test
    void parse_detectsPvT_fromRolePhrase() {
        AssistantQueryParseResult result = queryParser.parse("토스로 테란전 정석 빌드가 뭐야?");

        assertEquals("P", result.getPlayerRace());
        assertEquals("T", result.getOpponentRace());
        assertEquals("PvT", result.getMatchup());
        assertTrue(result.getConfidence() >= 0.8);
        assertTrue(result.getBoardWeights().containsKey("pvstboard"));
        assertTrue(result.getExpandedTerms().contains("pvt"));
        assertTrue(result.getExpandedTerms().contains("프테"));
    }

    @Test
    void parse_detectsTvP_fromRolePhrase() {
        AssistantQueryParseResult result = queryParser.parse("테란으로 토스전 정석 빌드 알려줘");

        assertEquals("T", result.getPlayerRace());
        assertEquals("P", result.getOpponentRace());
        assertEquals("TvP", result.getMatchup());
        assertTrue(result.getConfidence() >= 0.8);
        assertTrue(result.getBoardWeights().containsKey("tvspboard"));
        assertTrue(result.getExpandedTerms().contains("tvp"));
        assertTrue(result.getExpandedTerms().contains("테프"));
    }

    @Test
    void parse_detectsTvP_fromOpponentSuffixPhrase() {
        AssistantQueryParseResult result = queryParser.parse("테란 토스전 정석 빌드");

        assertEquals("T", result.getPlayerRace());
        assertEquals("P", result.getOpponentRace());
        assertEquals("TvP", result.getMatchup());
        assertTrue(result.getConfidence() >= 0.7);
        assertTrue(result.getBoardWeights().containsKey("tvspboard"));
    }

    @Test
    void parse_demotesTeamPlayBoard_whenQuestionHasNoTeamPlayKeyword() {
        AssistantQueryParseResult result = queryParser.parse("저그로 테란전 초반 운영 알려줘");

        assertFalse(result.isTeamPlayQuery());
        assertEquals(0.6, result.getBoardWeights().get("teamplayguideboard"), 0.0001);
        assertTrue(result.getBoardWeights().get("zvstboard") > 1.0);
    }

    @Test
    void parse_boostsTeamPlayBoard_whenQuestionMentionsTeamPlay() {
        AssistantQueryParseResult result = queryParser.parse("빨무에서 저그 초반 운영 알려줘");

        assertTrue(result.isTeamPlayQuery());
        assertTrue(result.getBoardWeights().get("teamplayguideboard") > 1.0);
    }

    @Test
    void parse_boostsTeamPlayBoard_whenQuestionMentionsHunterMap() {
        AssistantQueryParseResult result = queryParser.parse("헌터에서 테란 팀플 빌드 뭐가 좋아?");

        assertTrue(result.isTeamPlayQuery());
        assertTrue(result.getBoardWeights().get("teamplayguideboard") > 1.0);
    }

    @Test
    void parse_keepsAliasBoostedTeamPlayBoard_whenQuestionHasNoTeamPlayKeyword() {
        AliasDictionaryDTO alias = new AliasDictionaryDTO();
        alias.setAlias("무한자원");
        alias.setBoostBoardIds("teamplayguideboard");
        when(aliasDictionaryMapper.selectAll()).thenReturn(Collections.singletonList(alias));

        AssistantQueryParseResult result = queryParser.parse("무한자원 저그 운영 알려줘");

        assertFalse(result.isTeamPlayQuery());
        assertTrue(result.getBoardWeights().get("teamplayguideboard") > 1.0);
    }

    @Test
    void mentionsTeamPlay_detectsSpacedAndEnglishForms() {
        assertTrue(AssistantQueryParser.mentionsTeamPlay("빨리 무한 맵에서 추천 빌드"));
        assertTrue(AssistantQueryParser.mentionsTeamPlay("Hunter map team play build"));
        assertFalse(AssistantQueryParser.mentionsTeamPlay("저그 테란전 히드라 운영"));
        // "3:3업"은 팀플이 아니라 풀업그레이드를 가리키는 표현이다.
        assertFalse(AssistantQueryParser.mentionsTeamPlay("프테전 3:3업 타이밍"));
    }

    @Test
    void extractKeywords_normalizesParticlesAndFiltersStopwords() {
        assertEquals(
                Arrays.asList("히드라", "저그"),
                AssistantQueryParser.extractKeywords("저그로 운영 공략 알려줘 히드라를"));
    }
}
