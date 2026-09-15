package com.sc1hub.board.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardTitleNormalizerTest {

    @Test
    void requireValid_normalizesCaseAndWhitespace() {
        assertEquals("pvszboard", BoardTitleNormalizer.requireValid("  pVsZBoard  "));
    }

    @Test
    void requireValid_throwsInvalidBoardExceptionForUnknownBoard() {
        // 전역 핸들러가 404 + WARN 으로 처리하는 타입. IllegalArgumentException 하위라 기존 catch 는 그대로 통한다.
        InvalidBoardException e = assertThrows(InvalidBoardException.class,
                () -> BoardTitleNormalizer.requireValid("supportboard"));
        assertEquals("올바르지 않은 게시판 주소입니다.", e.getMessage());
        assertThrows(InvalidBoardException.class, () -> BoardTitleNormalizer.requireValid(null));
    }

    @Test
    void requireValidRejectsNonBoardDatabaseTables() {
        assertThrows(IllegalArgumentException.class, () -> BoardTitleNormalizer.requireValid("member"));
        assertThrows(IllegalArgumentException.class, () -> BoardTitleNormalizer.requireValid("chatmessage"));
    }

    @Test
    void requireValid_rejectsDynamicSqlCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> BoardTitleNormalizer.requireValid("tipboard; drop table member"));
        assertThrows(IllegalArgumentException.class,
                () -> BoardTitleNormalizer.requireValid("../member"));
    }
}
