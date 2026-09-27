package com.sc1hub.common.util;

/**
 * 사용자가 입력해 화면에 다시 출력되는 짧은 문자열(별명·이름 등)의 허용 문자를 판정한다.
 * 출력단 이스케이프와 함께 저장형 XSS 를 이중으로 막기 위한 서버측 입력 검증이다.
 */
public final class SafeTextValidator {

    /** 회원 별명·비회원 작성자·비회원 댓글 닉네임의 최대 길이(코드 포인트 기준, 댓글 nickname 컬럼과 같다). */
    public static final int MAX_NICKNAME_LENGTH = 50;

    private SafeTextValidator() {
    }

    /** 일반 텍스트 필드: {@code <}, {@code >} 와 탭을 제외한 제어문자를 금지한다. */
    public static boolean containsUnsafeTextChar(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '<' || c == '>') {
                return true;
            }
            if (Character.isISOControl(c) && c != '\t') {
                return true;
            }
        }
        return false;
    }

    /**
     * 별명: HTML 태그·속성값·스크립트 문자열을 깰 수 있는 {@code < > " ' & `} 와 모든 제어문자,
     * 보이지 않는 서식 문자(방향 전환·폭 없는 문자 등), 줄/문단 구분자를 금지한다.
     */
    public static boolean containsUnsafeNicknameChar(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '<':
                case '>':
                case '"':
                case '\'':
                case '&':
                case '`':
                    return true;
                default:
                    break;
            }
            int type = Character.getType(c);
            if (type == Character.CONTROL || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                return true;
            }
        }
        return false;
    }

    /** 비어 있지 않고, 코드 포인트 기준 {@code maxLength} 이내이며, 금지 문자가 없는 별명이면 true. */
    public static boolean isAcceptableNickname(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        return value.codePointCount(0, value.length()) <= maxLength && !containsUnsafeNicknameChar(value);
    }
}
