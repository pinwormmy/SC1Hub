package com.sc1hub.common.util;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class BlockedWordMatcher {

    private static final Pattern NON_TEXT_PATTERN = Pattern.compile("[^가-힣a-z0-9]");

    private BlockedWordMatcher() {
    }

    public static String findBlockedWord(List<String> blockedWords, String... texts) {
        if (blockedWords == null || blockedWords.isEmpty()) {
            return null;
        }

        for (String text : texts) {
            if (!StringUtils.hasText(text)) {
                continue;
            }

            String normalized = normalizeText(text);
            for (String blocked : blockedWords) {
                String token = normalizeText(blocked);
                if (!StringUtils.hasText(token)) {
                    continue;
                }
                // Single-syllable substring matching overblocks natural Korean sentences.
                if (token.length() <= 1) {
                    continue;
                }
                if (normalized.contains(token)) {
                    return blocked;
                }
            }
        }
        return null;
    }

    public static String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String lowered = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return NON_TEXT_PATTERN.matcher(lowered).replaceAll("");
    }
}
