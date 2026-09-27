package com.sc1hub.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeTextValidatorTest {

    @Test
    void nicknameRejectsMarkupQuotesAmpersandBacktickAndInvisibleCharacters() {
        for (String value : new String[]{"<b>", "a>b", "a\"b", "a'b", "a&b", "a`b", "a\tb", "a\nb", "a\u0000b",
                "a\u007Fb", "a‮b", "a​b", "a b"}) {
            assertTrue(SafeTextValidator.containsUnsafeNicknameChar(value), value);
            assertFalse(SafeTextValidator.isAcceptableNickname(value, 50), value);
        }
    }

    @Test
    void nicknameAcceptsOrdinaryKoreanEnglishAndSymbols() {
        for (String value : new String[]{"비회원", "SC Hub_1", "테란-장인!", "저그(초보)", "플토#2"}) {
            assertTrue(SafeTextValidator.isAcceptableNickname(value, 50), value);
        }
    }

    @Test
    void nicknameEnforcesCodePointLengthAndNonBlank() {
        assertTrue(SafeTextValidator.isAcceptableNickname("닉".repeat(50), 50));
        assertFalse(SafeTextValidator.isAcceptableNickname("닉".repeat(51), 50));
        assertFalse(SafeTextValidator.isAcceptableNickname("   ", 50));
        assertFalse(SafeTextValidator.isAcceptableNickname(null, 50));
    }

    @Test
    void generalTextStillAllowsQuotesAndTabButNotAngleBrackets() {
        assertFalse(SafeTextValidator.containsUnsafeTextChar("O'Brien\t\"x\""));
        assertTrue(SafeTextValidator.containsUnsafeTextChar("a<b"));
        assertTrue(SafeTextValidator.containsUnsafeTextChar("a\nb"));
    }
}
