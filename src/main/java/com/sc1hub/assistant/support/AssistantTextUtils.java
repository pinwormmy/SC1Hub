package com.sc1hub.assistant.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 검색/봇/별칭 사전 모듈에서 공통으로 사용하는 HTML·텍스트·JSON 추출 유틸리티.
 */
@Slf4j
public final class AssistantTextUtils {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() {
    };

    private AssistantTextUtils() {
    }

    /**
     * HTML 마크업과 기본 엔티티를 제거하고 연속 공백을 단일 공백으로 정규화한다.
     */
    public static String stripHtmlToText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String text = html.replaceAll("(?s)<[^>]*>", " ");
        text = text.replace("&nbsp;", " ");
        text = text.replace("&amp;", "&");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&quot;", "\"");
        text = text.replace("&#39;", "'");
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * JSON 배열 리터럴 형태(`[ ... ]`)인지 확인한다.
     */
    public static boolean looksLikeJsonArray(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    /**
     * JSON 배열 문자열 또는 쉼표/줄바꿈 구분 문자열에서 용어 목록을 파싱한다.
     */
    public static List<String> parseTerms(String raw, ObjectMapper objectMapper) {
        String trimmed = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(trimmed)) {
            return Collections.emptyList();
        }
        if (objectMapper != null && looksLikeJsonArray(trimmed)) {
            try {
                List<String> parsed = objectMapper.readValue(trimmed, STRING_LIST_TYPE);
                return parsed == null ? Collections.emptyList() : parsed;
            } catch (Exception e) {
                log.debug("alias term JSON 파싱 실패: {}", trimmed, e);
            }
        }
        List<String> results = new ArrayList<>();
        String[] tokens = trimmed.split("[,\\n\\r]+");
        for (String token : tokens) {
            if (StringUtils.hasText(token)) {
                results.add(token.trim());
            }
        }
        if (results.isEmpty()) {
            results.add(trimmed);
        }
        return results;
    }

    /**
     * LLM 응답에 포함된 마크다운 JSON 코드 펜스(```json, ```)를 제거한다.
     */
    public static String stripCodeFences(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();
    }

    /**
     * 문자열에서 첫 번째 괄호 균형이 맞는 JSON 객체(`{...}`)를 추출한다.
     */
    public static String extractFirstJsonObject(String raw) {
        return extractFirstBalancedJson(raw, '{', '}');
    }

    /**
     * 문자열에서 첫 번째 괄호 균형이 맞는 JSON 배열(`[...]`)을 추출한다.
     */
    public static String extractFirstJsonArray(String raw) {
        return extractFirstBalancedJson(raw, '[', ']');
    }

    /**
     * 문자열 리터럴 및 이스케이프(`\"`, `\\`)를 고려해 괄호 균형이 맞는 첫 번째 JSON 블록을 추출한다.
     */
    public static String extractFirstBalancedJson(String raw, char open, char close) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        int start = raw.indexOf(open);
        if (start < 0) {
            return "";
        }
        boolean inString = false;
        boolean escape = false;
        int depth = 0;
        for (int i = start; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (ch == '\\') {
                if (inString) {
                    escape = true;
                }
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == open) {
                depth += 1;
                continue;
            }
            if (ch == close) {
                depth -= 1;
                if (depth == 0) {
                    return raw.substring(start, i + 1).trim();
                }
                if (depth < 0) {
                    return "";
                }
            }
        }
        return "";
    }
}
