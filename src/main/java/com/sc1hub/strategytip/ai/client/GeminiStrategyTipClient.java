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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class GeminiStrategyTipClient {

    private static final int MAX_ERROR_DETAIL_CHARS = 300;
    private static final int ABSOLUTE_MAX_OUTPUT_TOKENS = 1200;
    private static final int MAX_GENERATED_CONTENT_CHARS = 96;
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
        payload.put("input", buildConstrainedInput(userPrompt, requestedCount, categories, sourceIds));
        payload.put("tools", Collections.singletonList(Collections.singletonMap("type", "google_search")));
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
        constraints.put("minimumGoogleSearchQueries", requestedCount);
        constraints.put("maximumGoogleSearchQueries", requestedCount);
        constraints.put("maximumContentChars", MAX_GENERATED_CONTENT_CHARS);
        constraints.put("maximumEvidenceSummaryChars", MAX_EVIDENCE_SUMMARY_CHARS);
        try {
            return userPrompt
                    + "\n\nOUTPUT_CONSTRAINTS_JSON=" + objectMapper.writeValueAsString(constraints)
                    + "\nGoogle Search query budget: execute exactly " + requestedCount
                    + " queries total, exactly one query for each requested category. "
                    + "Return only the schema JSON. Keep each evidence summary concise. "
                    + "Put exactly one native url_citation annotation inside each corresponding "
                    + "externalEvidenceSummary. Do not output a source URL or title; "
                    + "the server reads both from the native annotation.";
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
                "Korean one-line strategy, 12 to " + MAX_GENERATED_CONTENT_CHARS + " characters.");
        Map<String, Object> evidenceSummary = describedStringProperty(
                "Concise internal evidence, 5 to " + MAX_EVIDENCE_SUMMARY_CHARS + " characters.");
        Map<String, Object> externalEvidenceSummary = describedStringProperty(
                "Concise external evidence, 5 to " + MAX_EVIDENCE_SUMMARY_CHARS + " characters.");

        Map<String, Object> draftProperties = new LinkedHashMap<>();
        draftProperties.put("category", category);
        draftProperties.put("content", content);
        draftProperties.put("sourceId", sourceId);
        draftProperties.put("evidenceSummary", evidenceSummary);
        draftProperties.put("externalEvidenceSummary", externalEvidenceSummary);

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", "object");
        draft.put("properties", draftProperties);
        draft.put("required", Arrays.asList("category", "content", "sourceId",
                "evidenceSummary", "externalEvidenceSummary"));
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
                throw new GeminiStrategyTipException("Gemini interaction did not complete: "
                        + (StringUtils.hasText(status) ? status : "missing status"));
            }

            ResponseParts parts = collectResponseParts(root.path("steps"));
            if (parts.refusal) {
                throw new GeminiStrategyTipException("Gemini refused the strategy tip request.");
            }
            if (!StringUtils.hasText(parts.outputText)) {
                throw new GeminiStrategyTipException("Gemini returned no structured text output.");
            }

            List<StrategyTipAiGeneratedBatch.Draft> drafts = parseDrafts(parts.outputText,
                    requestedCount, allowedCategories, allowedSourceIds,
                    parts.citationTitlesByUrl, parts.citationSpans);
            String model = root.path("model").asText(fallbackModel);
            return new StrategyTipAiGeneratedBatch(drafts, model,
                    usage.inputTokens, usage.outputTokens,
                    parts.searchQueryCount, parts.citationTitlesByUrl);
        } catch (GeminiStrategyTipException e) {
            if (e.hasUsage() || !usage.available) {
                throw e;
            }
            throw new GeminiStrategyTipException(e.getMessage(), e,
                    usage.inputTokens, usage.outputTokens, usage.searchQueryCount);
        }
    }

    private ResponseParts collectResponseParts(JsonNode steps) {
        if (!steps.isArray()) {
            throw new GeminiStrategyTipException("Gemini response is missing interaction steps.");
        }

        Map<String, String> citations = Collections.emptyMap();
        List<CitationSpan> citationSpans = Collections.emptyList();
        int searchQueryCount = 0;
        boolean refusal = false;
        String outputText = null;
        for (JsonNode step : steps) {
            String stepType = step.path("type").asText("");
            if ("google_search_call".equals(stepType)) {
                JsonNode queries = step.path("arguments").path("queries");
                if (queries.isArray()) {
                    for (JsonNode query : queries) {
                        if (StringUtils.hasText(query.asText(""))) {
                            searchQueryCount++;
                        }
                    }
                }
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
            Map<String, String> stepCitations = new LinkedHashMap<>();
            List<CitationSpan> stepCitationSpans = new ArrayList<>();
            for (JsonNode block : content) {
                String blockType = block.path("type").asText("");
                if ("refusal".equals(blockType)) {
                    refusal = true;
                    continue;
                }
                if (!"text".equals(blockType)) {
                    continue;
                }
                int blockStartByte = utf8Length(stepText.toString());
                stepText.append(block.path("text").asText(""));
                collectCitations(block.path("annotations"), stepCitations,
                        stepCitationSpans, blockStartByte);
            }
            if (StringUtils.hasText(stepText.toString())) {
                outputText = stepText.toString();
                citations = stepCitations;
                citationSpans = stepCitationSpans;
            }
        }
        return new ResponseParts(outputText, citations, citationSpans, searchQueryCount, refusal);
    }

    private void collectCitations(JsonNode annotations, Map<String, String> citations,
                                  List<CitationSpan> citationSpans, int blockStartByte) {
        if (!annotations.isArray()) {
            return;
        }
        for (JsonNode annotation : annotations) {
            if (!"url_citation".equals(annotation.path("type").asText(""))) {
                continue;
            }
            String url = annotation.path("url").asText("").trim();
            if (!StringUtils.hasText(url)) {
                continue;
            }
            String title = annotation.path("title").asText("").trim();
            String previous = citations.get(url);
            if (previous == null || (!StringUtils.hasText(previous) && StringUtils.hasText(title))) {
                citations.put(url, title);
            }
            JsonNode startNode = annotation.get("start_index");
            JsonNode endNode = annotation.get("end_index");
            if (startNode != null && endNode != null
                    && startNode.canConvertToInt() && endNode.canConvertToInt()) {
                int start = startNode.asInt();
                int end = endNode.asInt();
                if (start >= 0 && end > start) {
                    citationSpans.add(new CitationSpan(
                            url, blockStartByte + start, blockStartByte + end));
                }
            }
        }
    }

    private List<StrategyTipAiGeneratedBatch.Draft> parseDrafts(
            String outputText,
            int requestedCount,
            List<String> allowedCategories,
            List<String> allowedSourceIds,
            Map<String, String> citations,
            List<CitationSpan> citationSpans) {
        JsonNode output;
        try {
            output = objectMapper.readTree(outputText);
        } catch (JsonProcessingException e) {
            throw new GeminiStrategyTipException("Gemini structured output is invalid JSON.", e);
        }
        JsonNode draftNodes = output.path("drafts");
        if (!output.isObject() || !draftNodes.isArray() || draftNodes.size() != requestedCount) {
            throw new GeminiStrategyTipException("Gemini returned an unexpected draft count.");
        }

        Set<String> categorySet = new LinkedHashSet<>(allowedCategories);
        Set<String> sourceIdSet = new LinkedHashSet<>(allowedSourceIds);
        List<DraftByteRanges> draftRanges = findDraftByteRanges(outputText);
        if (draftRanges.size() != requestedCount) {
            throw new GeminiStrategyTipException("Gemini structured output draft ranges are invalid.");
        }
        List<StrategyTipAiGeneratedBatch.Draft> drafts = new ArrayList<>();
        for (int draftIndex = 0; draftIndex < draftNodes.size(); draftIndex++) {
            JsonNode draftNode = draftNodes.get(draftIndex);
            String category = requiredOutputText(draftNode, "category");
            String content = requiredOutputText(draftNode, "content");
            String sourceId = requiredOutputText(draftNode, "sourceId");
            String evidenceSummary = requiredOutputText(draftNode, "evidenceSummary");
            String externalEvidenceSummary = requiredOutputText(draftNode, "externalEvidenceSummary");

            if (!categorySet.contains(category) || !sourceIdSet.contains(sourceId)) {
                throw new GeminiStrategyTipException("Gemini returned a draft outside the allowed source scope.");
            }
            DraftByteRanges ranges = draftRanges.get(draftIndex);
            String externalSourceUrl = singleCitationUrlWithinEvidenceRange(
                    ranges.draftRange, ranges.externalEvidenceRange, citationSpans);
            if (!StringUtils.hasText(externalSourceUrl)
                    || !citations.containsKey(externalSourceUrl)) {
                throw new GeminiStrategyTipException(
                        "Gemini did not bind exactly one native Google Search citation to that draft.");
            }
            String externalSourceTitle = citationTitleOrHost(
                    externalSourceUrl, citations.get(externalSourceUrl));
            drafts.add(new StrategyTipAiGeneratedBatch.Draft(category, content, sourceId,
                    evidenceSummary, externalSourceUrl, externalSourceTitle, externalEvidenceSummary));
        }
        return drafts;
    }

    private List<DraftByteRanges> findDraftByteRanges(String outputText) {
        int field = outputText.indexOf("\"drafts\"");
        int arrayStart = field < 0 ? -1 : outputText.indexOf('[', field + 8);
        if (arrayStart < 0) {
            return Collections.emptyList();
        }

        List<DraftByteRanges> ranges = new ArrayList<>();
        int index = arrayStart + 1;
        while (index < outputText.length()) {
            char current = outputText.charAt(index);
            if (Character.isWhitespace(current) || current == ',') {
                index++;
                continue;
            }
            if (current == ']') {
                break;
            }
            if (current != '{') {
                return Collections.emptyList();
            }

            int objectStart = index;
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (; index < outputText.length(); index++) {
                char value = outputText.charAt(index);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (value == '\\') {
                        escaped = true;
                    } else if (value == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (value == '"') {
                    inString = true;
                } else if (value == '{') {
                    depth++;
                } else if (value == '}' && --depth == 0) {
                    int objectEnd = index + 1;
                    ByteRange draftRange = new ByteRange(
                            utf8Length(outputText.substring(0, objectStart)),
                            utf8Length(outputText.substring(0, objectEnd)));
                    ByteRange externalEvidenceRange = findStringPropertyByteRange(
                            outputText, objectStart, objectEnd, "externalEvidenceSummary");
                    if (externalEvidenceRange == null) {
                        return Collections.emptyList();
                    }
                    ranges.add(new DraftByteRanges(draftRange, externalEvidenceRange));
                    index = objectEnd;
                    break;
                }
            }
            if (depth != 0) {
                return Collections.emptyList();
            }
        }
        return ranges;
    }

    private ByteRange findStringPropertyByteRange(String json, int objectStart,
                                                  int objectEnd, String propertyName) {
        ByteRange result = null;
        int index = objectStart + 1;
        while (index < objectEnd - 1) {
            while (index < objectEnd - 1
                    && (Character.isWhitespace(json.charAt(index)) || json.charAt(index) == ',')) {
                index++;
            }
            if (index >= objectEnd - 1 || json.charAt(index) == '}') {
                break;
            }
            if (json.charAt(index) != '"') {
                return null;
            }
            int keyEnd = findJsonStringEnd(json, index, objectEnd);
            if (keyEnd < 0) {
                return null;
            }
            String key = json.substring(index + 1, keyEnd);
            index = keyEnd + 1;
            while (index < objectEnd && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
            if (index >= objectEnd || json.charAt(index) != ':') {
                return null;
            }
            index++;
            while (index < objectEnd && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
            if (index >= objectEnd || json.charAt(index) != '"') {
                return null;
            }
            int valueEnd = findJsonStringEnd(json, index, objectEnd);
            if (valueEnd < 0) {
                return null;
            }
            if (propertyName.equals(key)) {
                if (result != null) {
                    return null;
                }
                result = new ByteRange(
                        utf8Length(json.substring(0, index + 1)),
                        utf8Length(json.substring(0, valueEnd)));
            }
            index = valueEnd + 1;
        }
        return result;
    }

    private int findJsonStringEnd(String json, int openingQuote, int limit) {
        boolean escaped = false;
        for (int index = openingQuote + 1; index < limit; index++) {
            char value = json.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (value == '\\') {
                escaped = true;
            } else if (value == '"') {
                return index;
            }
        }
        return -1;
    }

    private String singleCitationUrlWithinEvidenceRange(
            ByteRange draftRange,
            ByteRange externalEvidenceRange,
            List<CitationSpan> citationSpans) {
        if (citationSpans == null) {
            return null;
        }
        String citationUrl = null;
        int citationCount = 0;
        for (CitationSpan citation : citationSpans) {
            boolean overlapsDraft = citation.startByte < draftRange.endByte
                    && citation.endByte > draftRange.startByte;
            if (!overlapsDraft) {
                continue;
            }
            if (citation.startByte < externalEvidenceRange.startByte
                    || citation.endByte > externalEvidenceRange.endByte) {
                return null;
            }
            citationCount++;
            if (citationCount > 1) {
                return null;
            }
            citationUrl = citation.url;
        }
        return citationUrl;
    }

    private String citationTitleOrHost(String url, String title) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        try {
            String host = new URI(url).getHost();
            return StringUtils.hasText(host)
                    ? host.toLowerCase(java.util.Locale.ROOT) : "";
        } catch (URISyntaxException e) {
            return "";
        }
    }

    private int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private String requiredOutputText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
            throw new GeminiStrategyTipException("Gemini structured output is missing " + fieldName + ".");
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
                throw new GeminiStrategyTipException(fieldName + " must not contain blank values.");
            }
            distinct.add(value.trim());
        }
        if (distinct.size() != values.size()) {
            throw new GeminiStrategyTipException(fieldName + " must not contain duplicate values.");
        }
        return new ArrayList<>(distinct);
    }

    private String resolveThinkingLevel() {
        String value = properties.getThinkingLevel();
        if (!StringUtils.hasText(value)) {
            return "minimal";
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!Arrays.asList("minimal", "low", "medium", "high").contains(normalized)) {
            log.warn("Invalid strategy tip thinkingLevel; using minimal.");
            return "minimal";
        }
        return normalized;
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
        int searchQueryCount = countSearchQueries(root.path("steps"));
        return new UsageSnapshot(usage.isObject() || searchQueryCount > 0,
                inputTokens, outputTokens, searchQueryCount);
    }

    private int countSearchQueries(JsonNode steps) {
        if (!steps.isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode step : steps) {
            if (!"google_search_call".equals(step.path("type").asText(""))) {
                continue;
            }
            JsonNode queries = step.path("arguments").path("queries");
            if (!queries.isArray()) {
                continue;
            }
            for (JsonNode query : queries) {
                if (StringUtils.hasText(query.asText(""))) {
                    count = saturatingAdd(count, 1);
                }
            }
        }
        return count;
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
        private final Map<String, String> citationTitlesByUrl;
        private final List<CitationSpan> citationSpans;
        private final int searchQueryCount;
        private final boolean refusal;

        private ResponseParts(String outputText,
                              Map<String, String> citationTitlesByUrl,
                              List<CitationSpan> citationSpans,
                              int searchQueryCount,
                              boolean refusal) {
            this.outputText = outputText;
            this.citationTitlesByUrl = citationTitlesByUrl;
            this.citationSpans = citationSpans;
            this.searchQueryCount = searchQueryCount;
            this.refusal = refusal;
        }
    }

    private static final class UsageSnapshot {
        private final boolean available;
        private final int inputTokens;
        private final int outputTokens;
        private final int searchQueryCount;

        private UsageSnapshot(boolean available, int inputTokens,
                              int outputTokens, int searchQueryCount) {
            this.available = available;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.searchQueryCount = searchQueryCount;
        }
    }

    private static final class CitationSpan {
        private final String url;
        private final int startByte;
        private final int endByte;

        private CitationSpan(String url, int startByte, int endByte) {
            this.url = url;
            this.startByte = startByte;
            this.endByte = endByte;
        }
    }

    private static final class ByteRange {
        private final int startByte;
        private final int endByte;

        private ByteRange(int startByte, int endByte) {
            this.startByte = startByte;
            this.endByte = endByte;
        }
    }

    private static final class DraftByteRanges {
        private final ByteRange draftRange;
        private final ByteRange externalEvidenceRange;

        private DraftByteRanges(ByteRange draftRange, ByteRange externalEvidenceRange) {
            this.draftRange = draftRange;
            this.externalEvidenceRange = externalEvidenceRange;
        }
    }
}
