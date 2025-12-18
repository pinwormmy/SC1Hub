package com.sc1hub.assistant.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class GeminiEmbeddingClient {

    private static final int MAX_EMBED_TEXT_CHARS = 8000;

    private final RestTemplate restTemplate;
    private final GeminiProperties geminiProperties;
    private final ObjectMapper objectMapper;

    public GeminiEmbeddingClient(RestTemplate geminiRestTemplate, GeminiProperties geminiProperties, ObjectMapper objectMapper) {
        this.restTemplate = geminiRestTemplate;
        this.geminiProperties = geminiProperties;
        this.objectMapper = objectMapper;
    }

    public float[] embedText(String text) {
        String apiKey = geminiProperties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            log.warn("Gemini API key가 설정되지 않았습니다.");
            throw new GeminiException("Gemini API key is not configured.");
        }

        String embeddingModel = geminiProperties.getEmbeddingModel();
        if (!StringUtils.hasText(embeddingModel)) {
            throw new GeminiException("Gemini embedding model is not configured.");
        }

        String url = String.format("%s/%s/models/%s:embedContent",
                geminiProperties.getBaseUrl(),
                geminiProperties.getApiVersion(),
                embeddingModel
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("x-goog-api-key", apiKey);

        String normalizedText = normalizeText(text);

        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", normalizedText);
        content.put("parts", Collections.singletonList(part));
        payload.put("content", content);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

        try {
            String responseBody = restTemplate.postForObject(url, requestEntity, String.class);
            return extractEmbeddingFromResponse(responseBody);
        } catch (HttpStatusCodeException e) {
            String body = safeShortBody(e);
            log.error("Gemini Embedding API 요청 실패. status={}, model={}, body={}", e.getStatusCode(), embeddingModel, body);
            throw new GeminiException("Gemini Embedding API request failed: " + e.getStatusCode() + (body == null ? "" : " " + body), e);
        } catch (Exception e) {
            log.error("Gemini Embedding API 요청 중 예외 발생. model={}", embeddingModel, e);
            throw new GeminiException("Gemini Embedding API request failed.", e);
        }
    }

    private float[] extractEmbeddingFromResponse(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return new float[0];
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode valuesNode = root.path("embedding").path("values");
            if (!valuesNode.isArray()) {
                return new float[0];
            }
            float[] vector = new float[valuesNode.size()];
            for (int i = 0; i < valuesNode.size(); i += 1) {
                vector[i] = (float) valuesNode.path(i).asDouble(0.0);
            }
            return vector;
        } catch (Exception e) {
            return new float[0];
        }
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= MAX_EMBED_TEXT_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_EMBED_TEXT_CHARS);
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
