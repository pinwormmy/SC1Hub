package com.sc1hub.assistant.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sc1hub.assistant.config.GeminiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class GeminiClient {

    private final RestTemplate restTemplate;
    private final GeminiProperties geminiProperties;
    private final ObjectMapper objectMapper;

    public GeminiClient(RestTemplate geminiRestTemplate, GeminiProperties geminiProperties, ObjectMapper objectMapper) {
        this.restTemplate = geminiRestTemplate;
        this.geminiProperties = geminiProperties;
        this.objectMapper = objectMapper;
    }

    public String generateAnswer(String prompt) {
        return generateAnswer(prompt, null);
    }

    public String generateAnswer(String prompt, Integer maxOutputTokens) {
        String apiKey = geminiProperties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            log.warn("Gemini API key가 설정되지 않았습니다.");
            throw new GeminiException("Gemini API key is not configured.");
        }

        String url = String.format("%s/%s/models/%s:generateContent",
                geminiProperties.getBaseUrl(),
                geminiProperties.getApiVersion(),
                geminiProperties.getModel()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("x-goog-api-key", apiKey);

        Map<String, Object> payload = buildPayload(prompt, maxOutputTokens, true);

        try {
            String responseBody = restTemplate.postForObject(url, new HttpEntity<>(payload, headers), String.class);
            return extractTextFromResponse(responseBody);
        } catch (HttpStatusCodeException e) {
            if (isUnknownFieldError(e, "responseMimeType")) {
                try {
                    Map<String, Object> fallback = buildPayload(prompt, maxOutputTokens, false);
                    String responseBody = restTemplate.postForObject(url, new HttpEntity<>(fallback, headers), String.class);
                    return extractTextFromResponse(responseBody);
                } catch (HttpStatusCodeException fallbackException) {
                    String body = safeShortBody(fallbackException);
                    log.error("Gemini API 요청 실패. status={}, model={}, body={}", fallbackException.getStatusCode(), geminiProperties.getModel(), body);
                    throw new GeminiException("Gemini API request failed: " + fallbackException.getStatusCode() + (body == null ? "" : " " + body), fallbackException);
                } catch (Exception fallbackException) {
                    log.error("Gemini API 요청 중 예외 발생. model={}", geminiProperties.getModel(), fallbackException);
                    throw new GeminiException("Gemini API request failed.", fallbackException);
                }
            }
            String body = safeShortBody(e);
            log.error("Gemini API 요청 실패. status={}, model={}, body={}", e.getStatusCode(), geminiProperties.getModel(), body);
            throw new GeminiException("Gemini API request failed: " + e.getStatusCode() + (body == null ? "" : " " + body), e);
        } catch (Exception e) {
            log.error("Gemini API 요청 중 예외 발생. model={}", geminiProperties.getModel(), e);
            throw new GeminiException("Gemini API request failed.", e);
        }
    }

    private int resolveMaxOutputTokens(Integer override) {
        if (override != null && override > 0) {
            return override;
        }
        return Math.max(0, geminiProperties.getMaxOutputTokens());
    }

    private Map<String, Object> buildPayload(String prompt, Integer maxOutputTokens, boolean jsonMode) {
        Map<String, Object> payload = new HashMap<>();

        Map<String, Object> userPart = new HashMap<>();
        userPart.put("text", String.valueOf(prompt));

        Map<String, Object> userContent = new HashMap<>();
        userContent.put("role", "user");
        userContent.put("parts", Collections.singletonList(userPart));

        payload.put("contents", Collections.singletonList(userContent));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", geminiProperties.getTemperature());
        int resolvedMaxOutputTokens = resolveMaxOutputTokens(maxOutputTokens);
        if (resolvedMaxOutputTokens > 0) {
            generationConfig.put("maxOutputTokens", resolvedMaxOutputTokens);
        }
        if (jsonMode) {
            generationConfig.put("responseMimeType", "application/json");
        }
        payload.put("generationConfig", generationConfig);

        return payload;
    }

    private static boolean isUnknownFieldError(HttpStatusCodeException e, String fieldName) {
        if (e == null || !StringUtils.hasText(fieldName)) {
            return false;
        }
        try {
            String body = e.getResponseBodyAsString();
            if (!StringUtils.hasText(body)) {
                return false;
            }
            return body.contains("Unknown name") && body.contains(fieldName);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extractTextFromResponse(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode partsNode = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts");
            if (partsNode.isMissingNode() || partsNode.isNull()) {
                return "";
            }
            if (partsNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : partsNode) {
                    if (part == null || part.isNull() || part.isMissingNode()) {
                        continue;
                    }
                    JsonNode textNode = part.get("text");
                    if (textNode == null || textNode.isNull() || textNode.isMissingNode()) {
                        continue;
                    }
                    sb.append(textNode.asText(""));
                }
                return sb.toString();
            }
            JsonNode singleText = partsNode.path("text");
            if (singleText.isMissingNode() || singleText.isNull()) {
                return "";
            }
            return singleText.asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private String safeShortBody(HttpStatusCodeException e) {
        try {
            String raw = e.getResponseBodyAsString();
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            Object json = objectMapper.readValue(raw, Object.class);
            String normalized = objectMapper.writeValueAsString(json);
            if (normalized.length() <= 500) {
                return normalized;
            }
            return normalized.substring(0, 500) + "...";
        } catch (Exception ignored) {
            return null;
        }
    }
}
