package com.sc1hub.board.support;

import java.util.Locale;
import java.util.regex.Pattern;

public final class BoardTitleNormalizer {

    private static final Pattern SAFE_BOARD_TITLE = Pattern.compile("[a-z][a-z0-9]{0,63}");

    private BoardTitleNormalizer() {
    }

    public static String requireValid(String boardTitle) {
        if (boardTitle == null) {
            throw new IllegalArgumentException("게시판을 확인해주세요.");
        }

        String normalized = boardTitle.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_BOARD_TITLE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("올바르지 않은 게시판 주소입니다.");
        }
        return normalized;
    }

    public static String normalizeNullable(String boardTitle) {
        return boardTitle == null ? null : requireValid(boardTitle);
    }
}
