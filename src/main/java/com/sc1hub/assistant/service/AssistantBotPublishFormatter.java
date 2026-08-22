package com.sc1hub.assistant.service;

import com.sc1hub.assistant.config.AssistantBotProperties.PersonaProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AssistantBotPublishFormatter {

    String safeTitleForPublish(PersonaProperties persona, String title) {
        String normalized = normalizePublishText(persona, title);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return HtmlUtils.htmlEscape(normalized);
    }

    String toHtmlBody(PersonaProperties persona, String body) {
        String normalized = normalizePublishText(persona, body);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String escaped = HtmlUtils.htmlEscape(formatBodyForPublish(normalized,
                sentenceLimitForPersona(persona, false),
                sentenceSeparatorForPersona(persona, false)));
        return escaped.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>");
    }

    String safeCommentForPublish(PersonaProperties persona, String body) {
        String normalized = normalizePublishText(persona, body);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return formatBodyForPublish(normalized, sentenceLimitForPersona(persona, true), "\n");
    }

    String normalizeMeowPublishText(String text) {
        List<String> meowSegments = new ArrayList<>();
        for (String segment : splitMeowSegments(text)) {
            if (isMeowOnlySegment(segment)) {
                meowSegments.add(segment.trim().replaceAll("\\s+", " "));
            }
            if (meowSegments.size() >= 3) {
                break;
            }
        }
        if (meowSegments.isEmpty()) {
            return "야옹";
        }
        return String.join("\n", meowSegments);
    }

    String firstSentenceOnly(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        List<String> sentences = splitSentences(text);
        if (!sentences.isEmpty()) {
            return sentences.get(0);
        }
        return text.trim();
    }

    boolean isSingleSentenceOnlyPersona(PersonaProperties persona) {
        return persona != null
                && ("저묵묵봇".equals(persona.getName()) || hasPersonaName(persona, "고수봇"));
    }

    boolean isRepetitiveByDesignPersona(PersonaProperties persona) {
        return hasPersonaName(persona, "야옹봇");
    }

    private String formatBodyForPublish(String body, int maxSentences, String sentenceSeparator) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String normalized = body.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ");
        List<String> sentences = splitSentences(normalized);
        if (maxSentences > 0 && sentences.size() > maxSentences) {
            sentences = sentences.subList(0, maxSentences);
        }
        if (sentences.isEmpty()) {
            return normalized;
        }
        String separator = sentenceSeparator == null ? "\n" : sentenceSeparator;
        return String.join(separator, sentences).trim();
    }

    private String normalizePublishText(PersonaProperties persona, String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ");
        if (isRepetitiveByDesignPersona(persona)) {
            return normalizeMeowPublishText(normalized);
        }
        return isSingleSentenceOnlyPersona(persona) ? firstSentenceOnly(normalized) : normalized;
    }

    private int sentenceLimitForPersona(PersonaProperties persona, boolean commentMode) {
        if (persona == null || !StringUtils.hasText(persona.getName())) {
            return 0;
        }
        String name = persona.getName();
        if ("야옹봇".equals(name)) {
            return 3;
        }
        if ("건강봇".equals(name)) {
            return commentMode ? 2 : 5;
        }
        if ("저묵묵봇".equals(name)) {
            return 1;
        }
        if ("테뻔뻔봇".equals(name) || "프징징봇".equals(name)) {
            return commentMode ? 2 : 5;
        }
        return 0;
    }

    private String sentenceSeparatorForPersona(PersonaProperties persona, boolean commentMode) {
        if (!commentMode && persona != null && "건강봇".equals(persona.getName())) {
            return "\n\n\n";
        }
        return "\n";
    }

    private List<String> splitSentences(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        String normalized = text.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        List<String> sentences = new ArrayList<>();
        String[] lines = normalized.split("\\n+");
        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String[] parts = line.trim().split("(?<=[.!?。！？])\\s+");
            for (String part : parts) {
                if (StringUtils.hasText(part)) {
                    sentences.add(part.trim());
                }
            }
        }
        if (sentences.isEmpty() && StringUtils.hasText(normalized)) {
            sentences.add(normalized.replace('\n', ' ').trim());
        }
        return sentences;
    }

    private List<String> splitMeowSegments(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        String normalized = text.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String[] parts = normalized.split("(?<=[.!?。！？])\\s+|\\n+");
        List<String> segments = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                segments.add(part.trim());
            }
        }
        return segments;
    }

    private boolean isMeowOnlySegment(String segment) {
        if (!StringUtils.hasText(segment)) {
            return false;
        }
        String core = segment.replaceAll("[\\s~.!?。！？]+", "");
        return StringUtils.hasText(core) && core.matches("[야옹]+");
    }

    private boolean hasPersonaName(PersonaProperties persona, String expectedName) {
        return persona != null
                && StringUtils.hasText(persona.getName())
                && persona.getName().trim().equals(expectedName);
    }
}
