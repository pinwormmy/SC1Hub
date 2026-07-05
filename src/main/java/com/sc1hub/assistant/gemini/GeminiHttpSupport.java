package com.sc1hub.assistant.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Shared helpers for handling Gemini HTTP error responses. Centralizes logic that was previously
 * duplicated between {@link GeminiClient} and {@link GeminiEmbeddingClient}.
 */
final class GeminiHttpSupport {

    private static final int MAX_BODY_CHARS = 500;

    private GeminiHttpSupport() {
    }

    /**
     * Returns a compact, single-line JSON rendering of the error response body, truncated to a safe
     * length for logging. Returns {@code null} when the body is missing or cannot be parsed.
     */
    static String safeShortBody(ObjectMapper objectMapper, HttpStatusCodeException e) {
        try {
            String raw = e.getResponseBodyAsString();
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            Object json = objectMapper.readValue(raw, Object.class);
            String normalized = objectMapper.writeValueAsString(json);
            if (normalized.length() <= MAX_BODY_CHARS) {
                return normalized;
            }
            return normalized.substring(0, MAX_BODY_CHARS) + "...";
        } catch (Exception ignored) {
            return null;
        }
    }
}
