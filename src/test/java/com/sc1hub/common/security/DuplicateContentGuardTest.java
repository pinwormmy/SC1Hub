package com.sc1hub.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateContentGuardTest {

    private final DuplicateContentGuard guard = new DuplicateContentGuard();

    @Test
    void secondIdenticalContentInsideTheWindowIsADuplicate() {
        String content = "같은 내용을 반복해서 올리는 도배 글입니다.";
        assertFalse(guard.isDuplicate("post", null, content, 10, 60_000L));
        assertTrue(guard.isDuplicate("post", null, content, 10, 60_000L));
    }

    @Test
    void normalisationIgnoresMarkupWhitespaceAndCase() {
        assertFalse(guard.isDuplicate("post", null, "<p>Hello   World, this is SPAM</p>", 10, 60_000L));
        assertTrue(guard.isDuplicate("post", null, "hello world, this is spam", 10, 60_000L));
        assertEquals("hello world", DuplicateContentGuard.normalize("<b>Hello</b>&nbsp; World "));
    }

    @Test
    void shortContentIsNeverTreatedAsDuplicate() {
        assertFalse(guard.isDuplicate("chat", "member:a", "ㅋㅋㅋ", 8, 60_000L));
        assertFalse(guard.isDuplicate("chat", "member:a", "ㅋㅋㅋ", 8, 60_000L));
    }

    @Test
    void actorScopedChecksDoNotCollideAcrossActors() {
        String content = "오늘 저녁에 같이 팀플 하실 분 구합니다";
        assertFalse(guard.isDuplicate("chat", "member:a", content, 8, 60_000L));
        assertFalse(guard.isDuplicate("chat", "member:b", content, 8, 60_000L));
        assertTrue(guard.isDuplicate("chat", "member:a", content, 8, 60_000L));
    }
}
