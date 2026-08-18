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
public class OpenAiStrategyTipClient {

    private static final int MAX_ERROR_DETAIL_CHARS = 300;
    private static final int ABSOLUTE_MAX_OUTPUT_TOKENS = 6000;
    private static final int MAX_GENERATED_CONTENT_CHARS = 96;
    private static final String OPENAI_API_HOST = "api.openai.com";
    private static final String RESPONSES_API_PATH = "/v1/responses";

    private final RestTemplate restTemplate;
    private final StrategyTipAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiStrategyTipClient(
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
                                                 List<String> categories) {
        if (!properties.isEnabled()) {
            throw new StrategyTipAiClientException("Strategy tip AI generation is disabled.");
        }
        if (!properties.isAllowLiveCalls()) {
            throw new StrategyTipAiClientException("Live OpenAI API calls are disabled.");
        }
        String apiKey = requireText(properties.getApiKey(),
                "OpenAI API key is not configured.");
        URI apiUri = validateApiUri(requireText(properties.getBaseUrl(),
                "OpenAI Responses API URL is not configured."));
        String model = requireText(properties.getModel(), "OpenAI model is not configured.");
        String validSystemPrompt = requireText(systemPrompt, "System prompt is required.");
        String validUserPrompt = requireText(userPrompt, "User prompt is required.");
        if (requestedCount < 1) {
            throw new StrategyTipAiClientException("Requested draft count must be positive.");
        }

        List<String> allowedCategories = requireDistinctValues(categories, "categories");
        if (allowedCategories.size() != requestedCount) {
            throw new StrategyTipAiClientException(
                    "Requested draft count must match the category count.");
        }

        Map<String, Object> payload = buildPayload(validSystemPrompt, validUserPrompt,
                requestedCount, allowedCategories, model);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + apiKey);

        String rawResponse;
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUri, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
            rawResponse = response.getBody();
        } catch (HttpStatusCodeException e) {
            String detail = safeErrorDetail(e, apiKey, validSystemPrompt, validUserPrompt);
            log.warn("OpenAI Responses API request failed. status={}, model={}",
                    e.getStatusCode(), model);
            throw new StrategyTipAiClientException("OpenAI Responses API request failed: "
                    + e.getStatusCode() + (StringUtils.hasText(detail) ? " " + detail : ""), e);
        } catch (RestClientException e) {
            log.warn("OpenAI Responses API request failed. type={}, model={}",
                    e.getClass().getSimpleName(), model);
            throw new StrategyTipAiClientException("OpenAI Responses API request failed.", e);
        }

        return parseResponse(rawResponse, requestedCount, allowedCategories, model);
    }

    private Map<String, Object> buildPayload(String systemPrompt,
                                             String userPrompt,
                                             int requestedCount,
                                             List<String> categories,
                                             String model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", Arrays.asList(
                inputMessage("system", systemPrompt),
                inputMessage("user", buildConstrainedInput(
                        userPrompt, requestedCount, categories))));
        payload.put("store", false);
        payload.put("max_output_tokens", Math.max(1,
                Math.min(properties.getMaxOutputTokens(), ABSOLUTE_MAX_OUTPUT_TOKENS)));

        Map<String, Object> reasoning = new LinkedHashMap<>();
        reasoning.put("effort", resolveReasoningEffort());
        payload.put("reasoning", reasoning);

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "strategy_tip_batch");
        format.put("strict", true);
        format.put("schema", buildResponseSchema(requestedCount, categories));

        Map<String, Object> text = new LinkedHashMap<>();
        text.put("verbosity", "low");
        text.put("format", format);
        payload.put("text", text);
        return payload;
    }

    private Map<String, Object> inputMessage(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String buildConstrainedInput(String userPrompt,
                                         int requestedCount,
                                         List<String> categories) {
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("exactDraftCount", requestedCount);
        constraints.put("allowedCategories", categories);
        constraints.put("maximumContentChars", MAX_GENERATED_CONTENT_CHARS);
        constraints.put("checkpointKnowledgeOnly", true);
        constraints.put("sourceMaterialProvided", false);
        constraints.put("toolUseAllowed", false);
        constraints.put("timeSensitiveClaimsAllowed", false);
        constraints.put("preciseNumbersAllowed", false);
        try {
            return userPrompt
                    + "\n\nOUTPUT_CONSTRAINTS_JSON=" + objectMapper.writeValueAsString(constraints)
                    + "\nUse only your built-in checkpoint knowledge. Do not use web search, "
                    + "URL context, tools, supplied sources, or time-sensitive facts. "
                    + "Return only the schema JSON and complete every requested draft.";
        } catch (JsonProcessingException e) {
            throw new StrategyTipAiClientException("Could not build the OpenAI request.", e);
        }
    }

    private Map<String, Object> buildResponseSchema(int requestedCount,
                                                    List<String> categories) {
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "string");
        category.put("enum", categories);

        Map<String, Object> content = describedStringProperty(
                "Evergreen Korean one-line StarCraft: Brood War strategy based only on "
                        + "checkpoint knowledge, 12 to " + MAX_GENERATED_CONTENT_CHARS
                        + " characters, without precise numbers or time-sensitive claims.");

        Map<String, Object> draftProperties = new LinkedHashMap<>();
        draftProperties.put("category", category);
        draftProperties.put("content", content);

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", "object");
        draft.put("properties", draftProperties);
        draft.put("required", Arrays.asList("category", "content"));
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
                                                       String fallbackModel) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new StrategyTipAiClientException("OpenAI returned an empty response.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException e) {
            throw new StrategyTipAiClientException("OpenAI returned invalid response JSON.", e);
        }

        UsageSnapshot usage = extractUsage(root);
        try {
            String status = root.path("status").asText("");
            if (!"completed".equals(status)) {
                String reason = diagnosticCode(
                        root.path("incomplete_details").path("reason").asText(""));
                throw new StrategyTipAiClientException("OpenAI response did not complete: "
                        + (StringUtils.hasText(status) ? status : "missing status")
                        + (StringUtils.hasText(reason) ? " (" + reason + ")" : ""));
            }

            ResponseParts parts = collectResponseParts(root.path("output"));
            if (parts.refusal) {
                throw new StrategyTipAiClientException(
                        "OpenAI refused the strategy tip request.");
            }
            if (parts.unexpectedToolUse) {
                throw new StrategyTipAiClientException(
                        "OpenAI unexpectedly attempted external tool use.");
            }
            if (!StringUtils.hasText(parts.outputText)) {
                throw new StrategyTipAiClientException(
                        "OpenAI returned no structured text output.");
            }

            List<StrategyTipAiGeneratedBatch.Draft> drafts = parseDrafts(parts.outputText,
                    requestedCount, allowedCategories);
            String model = root.path("model").asText(fallbackModel);
            return new StrategyTipAiGeneratedBatch(
                    drafts, model, usage.inputTokens, usage.outputTokens);
        } catch (StrategyTipAiClientException e) {
            if (e.hasUsage() || !usage.available) {
                throw e;
            }
            throw new StrategyTipAiClientException(e.getMessage(), e,
                    usage.inputTokens, usage.outputTokens);
        }
    }

    private ResponseParts collectResponseParts(JsonNode output) {
        if (!output.isArray()) {
            throw new StrategyTipAiClientException(
                    "OpenAI response is missing output items.");
        }

        boolean refusal = false;
        boolean unexpectedToolUse = false;
        StringBuilder outputText = new StringBuilder();
        for (JsonNode item : output) {
            String itemType = item.path("type").asText("");
            if (isToolCall(itemType)) {
                unexpectedToolUse = true;
                continue;
            }
            if (!"message".equals(itemType)) {
                continue;
            }
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode block : content) {
                String blockType = block.path("type").asText("");
                if ("refusal".equals(blockType)) {
                    refusal = true;
                } else if ("output_text".equals(blockType)) {
                    outputText.append(block.path("text").asText(""));
                }
            }
        }
        return new ResponseParts(outputText.toString(), refusal, unexpectedToolUse);
    }

    private boolean isToolCall(String itemType) {
        if (!StringUtils.hasText(itemType)) {
            return false;
        }
        return itemType.endsWith("_call") || itemType.endsWith("_call_output");
    }

    private List<StrategyTipAiGeneratedBatch.Draft> parseDrafts(
            String outputText,
            int requestedCount,
            List<String> allowedCategories) {
        JsonNode output;
        try {
            output = objectMapper.readTree(outputText);
        } catch (JsonProcessingException e) {
            throw new StrategyTipAiClientException(
                    "OpenAI structured output is invalid JSON.", e);
        }
        JsonNode draftNodes = output.path("drafts");
        if (!output.isObject() || !draftNodes.isArray()
                || draftNodes.size() != requestedCount) {
            throw new StrategyTipAiClientException(
                    "OpenAI returned an unexpected draft count.");
        }

        Set<String> categorySet = new LinkedHashSet<>(allowedCategories);
        Set<String> seenCategories = new LinkedHashSet<>();
        List<StrategyTipAiGeneratedBatch.Draft> drafts = new ArrayList<>();
        for (JsonNode draftNode : draftNodes) {
            String category = requiredOutputText(draftNode, "category");
            String content = requiredOutputText(draftNode, "content");

            if (!categorySet.contains(category)) {
                throw new StrategyTipAiClientException(
                        "OpenAI returned a draft outside the allowed category scope.");
            }
            if (!seenCategories.add(category)) {
                throw new StrategyTipAiClientException(
                        "OpenAI returned duplicate draft categories.");
            }
            drafts.add(new StrategyTipAiGeneratedBatch.Draft(category, content));
        }
        if (seenCategories.size() != categorySet.size()) {
            throw new StrategyTipAiClientException(
                    "OpenAI did not return every requested category.");
        }
        return drafts;
    }

    private String requiredOutputText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
            throw new StrategyTipAiClientException(
                    "OpenAI structured output is missing " + fieldName + ".");
        }
        return value.asText().trim();
    }

    private List<String> requireDistinctValues(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new StrategyTipAiClientException(fieldName + " must not be empty.");
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                throw new StrategyTipAiClientException(
                        fieldName + " must not contain blank values.");
            }
            distinct.add(value.trim());
        }
        if (distinct.size() != values.size()) {
            throw new StrategyTipAiClientException(
                    fieldName + " must not contain duplicate values.");
        }
        return new ArrayList<>(distinct);
    }

    private String resolveReasoningEffort() {
        String value = properties.getReasoningEffort();
        if (!StringUtils.hasText(value)) {
            return "high";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!Arrays.asList("none", "low", "medium", "high", "xhigh", "max")
                .contains(normalized)) {
            log.warn("Invalid strategy tip reasoningEffort; using high.");
            return "high";
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
            throw new StrategyTipAiClientException(message);
        }
        return value.trim();
    }

    private URI validateApiUri(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw new StrategyTipAiClientException("OpenAI Responses API URL is invalid.", e);
        }
        int port = uri.getPort();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !OPENAI_API_HOST.equalsIgnoreCase(uri.getHost())
                || !RESPONSES_API_PATH.equals(uri.getPath())
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (port != -1 && port != 443)) {
            throw new StrategyTipAiClientException(
                    "OpenAI Responses API URL must use the trusted OpenAI HTTPS endpoint.");
        }
        return uri;
    }

    private UsageSnapshot extractUsage(JsonNode root) {
        JsonNode usage = root.path("usage");
        int inputTokens = nonNegativeInt(usage.path("input_tokens"));
        int outputTokens = nonNegativeInt(usage.path("output_tokens"));
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
