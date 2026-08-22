package com.sc1hub.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AssistantResponseParser {

    private static final Pattern SOURCE_ID_PATTERN = Pattern.compile("^([a-z0-9_]+):(\\d+)$");

    private final ObjectMapper objectMapper;

    AssistantResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AssistantAnswerResult parseAnswerResult(String raw, Set<String> allowedSourceIds) {
        AssistantAnswerResult result = new AssistantAnswerResult();
        String trimmed = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(trimmed)) {
            result.setAnswer("");
            return result;
        }

        if (objectMapper == null) {
            result.setAnswer(trimmed);
            return result;
        }

        String cleaned = stripCodeFences(trimmed);
        String json = extractFirstJsonObject(cleaned);
        if (!StringUtils.hasText(json)) {
            AssistantAnswerResult loose = parseLooseAnswerResult(cleaned, allowedSourceIds);
            if (loose != null) {
                return loose;
            }
            result.setAnswer(trimmed);
            return result;
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            String answer = textOrNull(node.get("answer"));
            if (!StringUtils.hasText(answer)) {
                answer = textOrNull(node.get("text"));
            }
            if (!StringUtils.hasText(answer)) {
                answer = trimmed;
            }
            result.setAnswer(answer.trim());

            LinkedHashSet<String> used = new LinkedHashSet<>();
            addSourceIdsFromNode(used, node.get("citations"), allowedSourceIds);
            addSourceIdsFromNode(used, node.get("used_post_ids"), allowedSourceIds);
            addSourceIdsFromNode(used, node.get("usedPostIds"), allowedSourceIds);
            result.setUsedPostIds(new ArrayList<>(used));
            return result;
        } catch (Exception e) {
            AssistantAnswerResult loose = parseLooseAnswerResult(cleaned, allowedSourceIds);
            if (loose != null) {
                return loose;
            }
            result.setAnswer(trimmed);
            return result;
        }
    }

    JsonNode parseObject(String raw) {
        if (!StringUtils.hasText(raw) || objectMapper == null) {
            return null;
        }
        String cleaned = stripCodeFences(raw.trim());
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ignored) {
            String extracted = extractFirstJsonObject(cleaned);
            if (!StringUtils.hasText(extracted)) {
                return null;
            }
            try {
                return objectMapper.readTree(extracted);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    static String extractFirstJsonObject(String raw) {
        return extractFirstBalancedJson(raw, '{', '}');
    }

    static String extractFirstJsonArray(String raw) {
        return extractFirstBalancedJson(raw, '[', ']');
    }

    static String normalizeSourceId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = SOURCE_ID_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return "";
        }
        String boardTitle = matcher.group(1);
        int postNum;
        try {
            postNum = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            return "";
        }
        if (!StringUtils.hasText(boardTitle) || postNum <= 0) {
            return "";
        }
        return boardTitle + ":" + postNum;
    }

    static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private AssistantAnswerResult parseLooseAnswerResult(String raw, Set<String> allowedSourceIds) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String answer = extractLooseJsonStringField(raw, "answer");
        if (!StringUtils.hasText(answer)) {
            answer = extractLooseJsonStringField(raw, "text");
        }
        if (!StringUtils.hasText(answer)) {
            return null;
        }
        AssistantAnswerResult result = new AssistantAnswerResult();
        result.setAnswer(answer.trim());
        LinkedHashSet<String> used = new LinkedHashSet<>();
        addSourceIdsFromLooseArray(used, raw, "citations", allowedSourceIds);
        addSourceIdsFromLooseArray(used, raw, "used_post_ids", allowedSourceIds);
        addSourceIdsFromLooseArray(used, raw, "usedPostIds", allowedSourceIds);
        result.setUsedPostIds(new ArrayList<>(used));
        return result;
    }

    private static String stripCodeFences(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String cleaned = raw;
        cleaned = cleaned.replace("```json", "");
        cleaned = cleaned.replace("```JSON", "");
        cleaned = cleaned.replace("```", "");
        return cleaned.trim();
    }

    private static String extractLooseJsonStringField(String raw, String fieldName) {
        if (!StringUtils.hasText(raw) || !StringUtils.hasText(fieldName)) {
            return "";
        }
        String needle = "\"" + fieldName + "\"";
        int keyIndex = raw.indexOf(needle);
        if (keyIndex < 0) {
            return "";
        }
        int colonIndex = raw.indexOf(':', keyIndex + needle.length());
        if (colonIndex < 0) {
            return "";
        }
        int quoteIndex = raw.indexOf('"', colonIndex + 1);
        if (quoteIndex < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = quoteIndex + 1; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (escape) {
                appendJsonEscape(sb, ch);
                escape = false;
                continue;
            }
            if (ch == '\\') {
                escape = true;
                continue;
            }
            if (ch == '"') {
                break;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private static void appendJsonEscape(StringBuilder sb, char ch) {
        switch (ch) {
            case 'n':
                sb.append('\n');
                break;
            case 'r':
                sb.append('\r');
                break;
            case 't':
                sb.append('\t');
                break;
            case '"':
                sb.append('"');
                break;
            case '\\':
                sb.append('\\');
                break;
            default:
                sb.append(ch);
                break;
        }
    }

    private void addSourceIdsFromLooseArray(Set<String> target,
                                            String raw,
                                            String fieldName,
                                            Set<String> allowedSourceIds) {
        if (target == null || !StringUtils.hasText(raw) || !StringUtils.hasText(fieldName)) {
            return;
        }
        if (allowedSourceIds == null || allowedSourceIds.isEmpty()) {
            return;
        }
        String needle = "\"" + fieldName + "\"";
        int keyIndex = raw.indexOf(needle);
        if (keyIndex < 0) {
            return;
        }
        int colonIndex = raw.indexOf(':', keyIndex + needle.length());
        if (colonIndex < 0) {
            return;
        }
        int arrayStart = raw.indexOf('[', colonIndex + 1);
        if (arrayStart < 0) {
            return;
        }

        boolean inString = false;
        boolean escape = false;
        StringBuilder current = new StringBuilder();
        for (int i = arrayStart + 1; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (inString) {
                if (escape) {
                    appendJsonEscape(current, ch);
                    escape = false;
                    continue;
                }
                if (ch == '\\') {
                    escape = true;
                    continue;
                }
                if (ch == '"') {
                    inString = false;
                    addValidatedSourceId(target, current.toString(), allowedSourceIds);
                    current.setLength(0);
                    continue;
                }
                current.append(ch);
                continue;
            }

            if (ch == '"') {
                inString = true;
                current.setLength(0);
                continue;
            }
            if (ch == ']') {
                break;
            }
        }
    }

    private void addSourceIdsFromNode(Set<String> target, JsonNode node, Set<String> allowedSourceIds) {
        if (target == null || node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item == null) {
                    continue;
                }
                if (item.isObject()) {
                    addValidatedSourceId(target, textOrNull(item.get("sourceId")), allowedSourceIds);
                    addValidatedSourceId(target, textOrNull(item.get("id")), allowedSourceIds);
                } else {
                    addValidatedSourceId(target, textOrNull(item), allowedSourceIds);
                }
            }
            return;
        }
        if (node.isObject()) {
            addValidatedSourceId(target, textOrNull(node.get("sourceId")), allowedSourceIds);
            addValidatedSourceId(target, textOrNull(node.get("id")), allowedSourceIds);
            return;
        }
        addValidatedSourceId(target, textOrNull(node), allowedSourceIds);
    }

    private void addValidatedSourceId(Set<String> target, String raw, Set<String> allowedSourceIds) {
        if (target == null || !StringUtils.hasText(raw)) {
            return;
        }
        if (allowedSourceIds == null || allowedSourceIds.isEmpty()) {
            return;
        }
        String normalized = normalizeSourceId(raw);
        if (StringUtils.hasText(normalized) && allowedSourceIds.contains(normalized)) {
            target.add(normalized);
        }
    }

    private static String extractFirstBalancedJson(String raw, char open, char close) {
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
