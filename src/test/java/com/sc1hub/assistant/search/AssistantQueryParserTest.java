package com.sc1hub.assistant.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.mapper.AliasDictionaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        when(aliasDictionaryMapper.selectAll()).thenReturn(Collections.emptyList());
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
}
