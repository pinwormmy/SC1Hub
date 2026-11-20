package com.sc1hub.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebConfigPatternTest {

    @Test
    void rejectsPatternsThatPathPatternParserCannotParse() {
        // Spring 6.2 PathPatternParser 가 거부하는 잘못된 패턴을 넣으면 인터셉터가 AntPathMatcher(원본 URI) 로
        // 조용히 후퇴해 인코딩 우회("/boards/x/submit%50ost")가 생길 수 있으므로 등록 자체를 막는다.
        assertThrows(IllegalStateException.class, () -> WebConfig.patterns("/boards/**/modifyPost/**"));
        assertThrows(IllegalStateException.class, () -> WebConfig.patterns("/adminPage/**", "/boards/{unclosed"));
    }

    @Test
    void returnsParseablePatternsUnchanged() {
        String[] patterns = {"/boards/*/submitPost", "/boards/*/modifyPost/**", "/api/admin/**", "/"};
        assertArrayEquals(patterns, WebConfig.patterns(patterns));
    }
}
