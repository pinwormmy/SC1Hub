package com.sc1hub.strategytip.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.strategytip.ai.config.StrategyTipAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class GeminiStrategyTipClient {

    private static final int MAX_ERROR_DETAIL_CHARS = 300;
    private static final int ABSOLUTE_MAX_OUTPUT_TOKENS = 6000;
    private static final int MAX_GENERATED_CONTENT_CHARS = 96;
    private static final int MIN_EVIDENCE_SUMMARY_CHARS = 10;
    private static final int MAX_EVIDENCE_SUMMARY_CHARS = 72;
    private static final String GEMINI_API_HOST = "generativelanguage.googleapis.com";

    private final RestTemplate restTemplate;
    private final StrategyTipAiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiStrategyTipClient(
            @Qualifier("strategyTipAiRestTemplate") RestTemplate restTemplate,
            StrategyTipAiProperties properties,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public StrategyTipAiGeneratedBatch generate(String systemPrompt,
                                                 String userPrompt,
                                                 int requestedCount,
                                                 List<String> categories,
                                                 List<String> sourceIds) {
        if (!properties.isEnabled()) {
            throw new GeminiStrategyTipException("Strategy tip AI generation is disabled.");
        }
        if (!properties.isAllowLiveCalls()) {
            throw new GeminiStrategyTipException("Live Gemini API calls are disabled.");
        }
        String apiKey = requireText(properties.getApiKey(), "Gemini API key is not configured.");
        URI apiUri = validateApiUri(requireText(properties.getBaseUrl(),
                "Gemini Interactions API URL is not configured."));
        String model = requireText(properties.getModel(), "Gemini model is not configured.");
        String validSystemPrompt = requireText(systemPrompt, "System prompt is required.");
        String validUserPrompt = requireText(userPrompt, "User prompt is required.");
        if (requestedCount < 1) {
            throw new GeminiStrategyTipException("Requested draft count must be positive.");
        }

        List<String> allowedCategories = requireDistinctValues(categories, "categories");
        List<String> allowedSourceIds = requireDistinctValues(sourceIds, "sourceIds");
        if (allowedCategories.size() != requestedCount) {
            throw new GeminiStrategyTipException("Requested draft count must match the category count.");
        }

        Map<String, Object> payload = buildPayload(validSystemPrompt, validUserPrompt,
                requestedCount, allowedCategories, allowedSourceIds, model);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("x-goog-api-key", apiKey);

        String rawResponse;
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUri, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
            rawResponse = response.getBody();
        } catch (HttpStatusCodeException e) {
            String detail = safeErrorDetail(e, apiKey, validSystemPrompt, validUserPrompt);
            log.warn("Gemini Interactions API request failed. status={}, model={}",
                    e.getStatusCode(), model);
            throw new GeminiStrategyTipException("Gemini Interactions API request failed: "
                    + e.getStatusCode() + (StringUtils.hasText(detail) ? " " + detail : ""), e);
        } catch (RestClientException e) {
            log.warn("Gemini Interactions API request failed. type={}, model={}",
                    e.getClass().getSimpleName(), model);
            throw new GeminiStrategyTipException("Gemini Interactions API request failed.", e);
        }

        return parseResponse(rawResponse, requestedCount, allowedCategories, allowedSourceIds, model);
    }

    private Map<String, Object> buildPayload(String systemPrompt,
                                             String userPrompt,
                                             int requestedCount,
                                             List<String> categories,
                                             List<String> sourceIds,
                                             String model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("system_instruction", systemPrompt);
        payload.put("input", buildConstrainedInput(
                userPrompt, requestedCount, categories, sourceIds));
        payload.put("store", false);

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("thinking_level", resolveThinkingLevel());
        generationConfig.put("thinking_summaries", "none");
        generationConfig.put("max_output_tokens", Math.max(1,
                Math.min(properties.getMaxOutputTokens(), ABSOLUTE_MAX_OUTPUT_TOKENS)));
        payload.put("generation_config", generationConfig);

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "text");
        responseFormat.put("mime_type", "application/json");
        responseFormat.put("schema", buildResponseSchema(requestedCount, categories, sourceIds));
        payload.put("response_format", Collections.singletonList(responseFormat));
        return payload;
    }

    private String buildConstrainedInput(String userPrompt,
                                         int requestedCount,
                                         List<String> categories,
                                         List<String> sourceIds) {
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("exactDraftCount", requestedCount);
        constraints.put("allowedCategories", categories);
        constraints.put("allowedInternalSourceIds", sourceIds);
        constraints.put("maximumContentChars", MAX_GENERATED_CONTENT_CHARS);
        constraints.put("maximumEvidenceSummaryChars", MAX_EVIDENCE_SUMMARY_CHARS);
        constraints.put("evidenceMustBeVerbatimFromSelectedExcerpt", true);
        constraints.put("contentMustContainEvidenceSummaryVerbatim", true);
        constraints.put("externalKnowledgeAllowed", false);
        constraints.put("toolUseAllowed", false);
        try {
            return userPrompt
                    + "\n\nOUTPUT_CONSTRAINTS_JSON=" + objectMapper.writeValueAsString(constraints)
                    + "\nDo not use Google Search, URL Context, tools, or model-memory facts. "
                    + "Use only SOURCE_DATA_JSON. Return only the schema JSON. "
                    + "This is a direct extraction task; use only the reasoning needed and "
                    + "prioritize completing all requested JSON fields. "
                    + "For evidenceSummary, copy one short, exact passage from the selected "
                    + "source excerpt without paraphrasing it. Include the complete "
                    + "evidenceSummary passage unchanged inside content.";
        } catch (JsonProcessingException e) {
            throw new GeminiStrategyTipException("Could not build the Gemini request.", e);
        }
    }

    private Map<String, Object> buildResponseSchema(int requestedCount,
                                                    List<String> categories,
                                                    List<String> sourceIds) {
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "string");
        category.put("enum", categories);

        Map<String, Object> sourceId = new LinkedHashMap<>();
        sourceId.put("type", "string");
        sourceId.put("enum", sourceIds);

        Map<String, Object> content = describedStringProperty(
                "Korean one-line strategy directly supported by the selected internal source, "
                        + "12 to " + MAX_GENERATED_CONTENT_CHARS + " characters; it must contain "
                        + "the complete evidenceSummary passage unchanged.");
        Map<String, Object> evidenceSummary = describedStringProperty(
                "An exact passage copied verbatim from the selected internal source excerpt, "
                        + MIN_EVIDENCE_SUMMARY_CHARS + " to "
                        + MAX_EVIDENCE_SUMMARY_CHARS + " characters.");

        Map<String, Object> draftProperties = new LinkedHashMap<>();
        draftProperties.put("category", category);
        draftProperties.put("content", content);
        draftProperties.put("sourceId", sourceId);
        draftProperties.put("evidenceSummary", evidenceSummary);

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", "object");
        draft.put("properties", draftProperties);
        draft.put("required", Arrays.asList(
                "category", "content", "sourceId", "evidenceSummary"));
        draft.put("additionalProperties", false);

        Map<String, Object> drafts = new LinkedHashMap<>();
        drafts.put("type", "array");
        drafts.put("items", draft);
        drafts.put("minItems", requestedCount);
        drafts.put("maxItems", requestedCount);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.singletonMap("drafts", drafts));
        schema.put("required", Collections.singletonList("drafts"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> describedStringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private StrategyTipAiGeneratedBatch parseResponse(String rawResponse,
                                                       int requestedCount,
                                                       List<String> allowedCategories,
                                                       List<String> allowedSourceIds,
                                                       String fallbackModel) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new GeminiStrategyTipException("Gemini returned an empty response.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException e) {
            throw new GeminiStrategyTipException("Gemini returned invalid response JSON.", e);
        }

        UsageSnapshot usage = extractUsage(root);
        try {
            String status = root.path("status").asText("");
            if (!"completed".equals(status)) {
                String reason = diagnosticCode(
                        root.path("incomplete_details").path("reason").asText(""));
                throw new GeminiStrategyTipException("Gemini interaction did not complete: "
                        + (StringUtils.hasText(status) ? status : "missing status")
                        + (StringUtils.hasText(reason) ? " (" + reason + ")" : ""));
            }

            ResponseParts parts = collectResponseParts(root.path("steps"));
            if (parts.refusal) {
                throw new GeminiStrategyTipException("Gemini refused the strategy tip request.");
            }
            if (parts.unexpectedToolUse) {
                throw new GeminiStrategyTipException(
                        "Gemini unexpectedly attempted external tool use.");
            }
            if (!StringUtils.hasText(parts.outputText)) {
                throw new GeminiStrategyTipException("Gemini returned no structured text output.");
            }

            List<StrategyTipAiGeneratedBatch.Draft> drafts = parseDrafts(parts.outputText,
                    requestedCount, allowedCategories, allowedSourceIds);
            String model = root.path("model").asText(fallbackModel);
            return new StrategyTipAiGeneratedBatch(
                    drafts, model, usage.inputTokens, usage.outputTokens);
        } catch (GeminiStrategyTipException e) {
            if (e.hasUsage() || !usage.available) {
                throw e;
            }
            throw new GeminiStrategyTipException(e.getMessage(), e,
                    usage.inputTokens, usage.outputTokens, 0);
        }
    }

    private ResponseParts collectResponseParts(JsonNode steps) {
        if (!steps.isArray()) {
            throw new GeminiStrategyTipException("Gemini response is missing interaction steps.");
        }

        boolean refusal = false;
        boolean unexpectedToolUse = false;
        String outputText = null;
        for (JsonNode step : steps) {
            String stepType = step.path("type").asText("");
            if ("google_search_call".equals(stepType)
                    || "url_context_call".equals(stepType)) {
                unexpectedToolUse = true;
                continue;
            }
            if (!"model_output".equals(stepType)) {
                continue;
            }
            JsonNode content = step.path("content");
            if (!content.isArray()) {
                continue;
            }
            StringBuilder stepText = new StringBuilder();
            for (JsonNode block : content) {
                String blockType = block.path("type").asText("");
                if ("refusal".equals(blockType)) {
                    refusal = true;
                } else if ("text".equals(blockType)) {
                    stepText.append(block.path("text").asText(""));
                }
            }
            if (StringUtils.hasText(stepText.toString())) {
                outputText = stepText.toString();
            }
        }
        return new ResponseParts(outputText, refusal, unexpectedToolUse);
    }

    private List<StrategyTipAiGeneratedBatch.Draft> parseDrafts(
            String outputText,
            int requestedCount,
            List<String> allowedCategories,
            List<String> allowedSourceIds) {
        JsonNode output;
        try {
            output = objectMapper.readTree(outputText);
        } catch (JsonProcessingException e) {
            throw new GeminiStrategyTipException("Gemini structured output is invalid JSON.", e);
        }
        JsonNode draftNodes = output.path("drafts");
        if (!output.isObject() || !draftNodes.isArray()
                || draftNodes.size() != requestedCount) {
            throw new GeminiStrategyTipException("Gemini returned an unexpected draft count.");
        }

        Set<String> categorySet = new LinkedHashSet<>(allowedCategories);
        Set<String> sourceIdSet = new LinkedHashSet<>(allowedSourceIds);
        Set<String> seenCategories = new LinkedHashSet<>();
        Set<String> seenSourceIds = new LinkedHashSet<>();
        List<StrategyTipAiGeneratedBatch.Draft> drafts = new ArrayList<>();
        for (JsonNode draftNode : draftNodes) {
            String category = requiredOutputText(draftNode, "category");
            String content = requiredOutputText(draftNode, "content");
            String sourceId = requiredOutputText(draftNode, "sourceId");
            String evidenceSummary = requiredOutputText(draftNode, "evidenceSummary");

            if (!categorySet.contains(category) || !sourceIdSet.contains(sourceId)) {
                throw new GeminiStrategyTipException(
                        "Gemini returned a draft outside the allowed source scope.");
            }
            if (!seenCategories.add(category) || !seenSourceIds.add(sourceId)) {
                throw new GeminiStrategyTipException(
                        "Gemini returned duplicate draft categories or sources.");
            }
            drafts.add(new StrategyTipAiGeneratedBatch.Draft(
                    category, content, sourceId, evidenceSummary));
        }
        if (seenCategories.size() != categorySet.size()) {
            throw new GeminiStrategyTipException(
                    "Gemini did not return every requested category.");
        }
        return drafts;
    }

    private String requiredOutputText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
            throw new GeminiStrategyTipException(
                    "Gemini structured output is missing " + fieldName + ".");
        }
        return value.asText().trim();
    }

    private List<String> requireDistinctValues(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new GeminiStrategyTipException(fieldName + " must not be empty.");
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                throw new GeminiStrategyTipException(
                        fieldName + " must not contain blank values.");
            }
            distinct.add(value.trim());
        }
        if (distinct.size() != values.size()) {
            throw new GeminiStrategyTipException(
                    fieldName + " must not contain duplicate values.");
        }
        return new ArrayList<>(distinct);
    }

    private String resolveThinkingLevel() {
        String value = properties.getThinkingLevel();
        if (!StringUtils.hasText(value)) {
            return "medium";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!Arrays.asList("minimal", "low", "medium", "high").contains(normalized)) {
            log.warn("Invalid strategy tip thinkingLevel; using medium.");
            return "medium";
        }
        return normalized;
    }

    private String diagnosticCode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_.-]{1,80}") ? normalized : "";
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new GeminiStrategyTipException(message);
        }
        return value.trim();
    }

    private URI validateApiUri(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw new GeminiStrategyTipException("Gemini Interactions API URL is invalid.", e);
        }
        int port = uri.getPort();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !GEMINI_API_HOST.equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null
                || (port != -1 && port != 443)) {
            throw new GeminiStrategyTipException(
                    "Gemini Interactions API URL must use the trusted Google HTTPS endpoint.");
        }
        return uri;
    }

    private UsageSnapshot extractUsage(JsonNode root) {
        JsonNode usage = root.path("usage");
        int inputTokens = nonNegativeInt(usage.path("total_input_tokens"));
        int outputTokens = saturatingAdd(
                nonNegativeInt(usage.path("total_output_tokens")),
                nonNegativeInt(usage.path("total_thought_tokens")));
        return new UsageSnapshot(usage.isObject(), inputTokens, outputTokens);
    }

    private int nonNegativeInt(JsonNode node) {
        if (node == null || !node.isIntegralNumber()) {
            return 0;
        }
        BigInteger value = node.bigIntegerValue();
        if (value.signum() <= 0) {
            return 0;
        }
        return value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0
                ? Integer.MAX_VALUE : value.intValue();
    }

    private int saturatingAdd(int left, int right) {
        long total = (long) Math.max(0, left) + Math.max(0, right);
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private String safeErrorDetail(HttpStatusCodeException exception,
                                   String apiKey,
                                   String systemPrompt,
                                   String userPrompt) {
        try {
            String raw = exception.getResponseBodyAsString();
            if (!StringUtils.hasText(raw)) {
                return "";
            }
            JsonNode error = objectMapper.readTree(raw).path("error").path("message");
            String detail = error.isTextual() ? error.asText() : "";
            if (!StringUtils.hasText(detail)) {
                return "";
            }
            detail = detail.replaceAll("\\s+", " ").trim();
            detail = redact(detail, apiKey);
            detail = redact(detail, systemPrompt);
            detail = redact(detail, userPrompt);
            return truncate(detail, MAX_ERROR_DETAIL_CHARS);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String redact(String value, String sensitive) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(sensitive)) {
            return value;
        }
        return value.replace(sensitive, "[redacted]");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static final class ResponseParts {
        private final String outputText;
        private final boolean refusal;
        private final boolean unexpectedToolUse;

        private ResponseParts(String outputText, boolean refusal,
                              boolean unexpectedToolUse) {
            this.outputText = outputText;
            this.refusal = refusal;
            this.unexpectedToolUse = unexpectedToolUse;
        }
    }

    private static final class UsageSnapshot {
        private final boolean available;
        private final int inputTokens;
        private final int outputTokens;

        private UsageSnapshot(boolean available, int inputTokens, int outputTokens) {
            this.available = available;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }
    }
}
