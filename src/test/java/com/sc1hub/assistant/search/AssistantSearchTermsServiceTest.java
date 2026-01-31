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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantSearchTermsServiceTest {

    @Mock
    private AliasDictionaryMapper aliasDictionaryMapper;

    private AssistantSearchTermsService searchTermsService;

    @BeforeEach
    void setUp() {
        searchTermsService = new AssistantSearchTermsService(aliasDictionaryMapper, new ObjectMapper());
    }

    @Test
    void includesCanonicalTermsWhenAliasPresent() {
        AliasDictionaryDTO alias = new AliasDictionaryDTO();
        alias.setAlias("커공발");
        alias.setCanonicalTerms("[\"커맨드센터\",\"공중발진\"]");

        when(aliasDictionaryMapper.selectAll()).thenReturn(Collections.singletonList(alias));

        String terms = searchTermsService.buildSearchTerms("커공발 빌드", "테란 커공발 운영");

        assertThat(terms).contains("커맨드센터");
        assertThat(terms).contains("공중발진");
    }
}
