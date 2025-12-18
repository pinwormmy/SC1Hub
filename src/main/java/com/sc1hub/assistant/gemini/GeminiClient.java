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

        Map<String, Object> payload = new HashMap<>();

        Map<String, Object> userPart = new HashMap<>();
        userPart.put("text", String.valueOf(prompt));

        Map<String, Object> userContent = new HashMap<>();
        userContent.put("role", "user");
        userContent.put("parts", Collections.singletonList(userPart));

        payload.put("contents", Collections.singletonList(userContent));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", geminiProperties.getTemperature());
        generationConfig.put("maxOutputTokens", geminiProperties.getMaxOutputTokens());
        payload.put("generationConfig", generationConfig);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

        try {
            String responseBody = restTemplate.postForObject(url, requestEntity, String.class);
            return extractTextFromResponse(responseBody);
        } catch (HttpStatusCodeException e) {
            String body = safeShortBody(e);
            log.error("Gemini API 요청 실패. status={}, model={}, body={}", e.getStatusCode(), geminiProperties.getModel(), body);
            throw new GeminiException("Gemini API request failed: " + e.getStatusCode() + (body == null ? "" : " " + body), e);
        } catch (Exception e) {
            log.error("Gemini API 요청 중 예외 발생. model={}", geminiProperties.getModel(), e);
            throw new GeminiException("Gemini API request failed.", e);
        }
    }

    private String extractTextFromResponse(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode textNode = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");
            if (textNode.isMissingNode() || textNode.isNull()) {
                return "";
            }
            return textNode.asText("");
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
