package com.sc1hub.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebConfigPatternTest {

    @Test
    void rejectsPatternsThatPathPatternParserCannotParse() {
        // "**" 가 가운데 오는 패턴은 Spring 6 파서가 거부해 인터셉터가 AntPathMatcher(원본 URI) 로 후퇴한다.
        // 그 후퇴가 인코딩 우회("/boards/x/submit%50ost")의 원인이었으므로 등록 자체를 막는다.
        assertThrows(IllegalStateException.class, () -> WebConfig.patterns("/**/writePost"));
        assertThrows(IllegalStateException.class, () -> WebConfig.patterns("/adminPage/**", "/**/modifyPost/**"));
    }

    @Test
    void returnsParseablePatternsUnchanged() {
        String[] patterns = {"/boards/*/submitPost", "/boards/*/modifyPost/**", "/api/admin/**", "/"};
        assertArrayEquals(patterns, WebConfig.patterns(patterns));
    }
}
