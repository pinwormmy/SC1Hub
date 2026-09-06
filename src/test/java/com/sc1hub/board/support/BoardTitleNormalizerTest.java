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
