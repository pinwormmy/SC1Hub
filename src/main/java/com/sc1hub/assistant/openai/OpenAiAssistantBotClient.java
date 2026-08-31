package com.sc1hub.assistant.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.OpenAiProperties;
import com.sc1hub.common.monitoring.MetaspaceUsageLogger;
import lombok.extern.slf4j.Slf4j;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class OpenAiAssistantBotClient {

    private static final String OPENAI_API_HOST = "api.openai.com";
    private static final String RESPONSES_API_PATH = "/v1/responses";
    private static final int MAX_OUTPUT_TOKENS = 6000;
    private static final int MAX_ERROR_DETAIL_CHARS = 300;
    // Responses API에서는 reasoning 토큰이 max_output_tokens 예산을 함께 소모하므로,
    // 높은 reasoning effort로 도는 검색 호출은 요청 예산 위에 여유분을 더해 보낸다.
    private static final int SEARCH_REASONING_HEADROOM_TOKENS = 6000;
    private static final int SEARCH_MAX_OUTPUT_TOKENS = 8000;
    private static final int SEARCH_DEFAULT_ANSWER_TOKENS = 1024;

    private final RestTemplate restTemplate;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final MetaspaceUsageLogger metaspaceUsageLogger;

    public OpenAiAssistantBotClient(
            RestTemplate assistantRestTemplate,
            OpenAiProperties properties,
            ObjectMapper objectMapper,
            MetaspaceUsageLogger metaspaceUsageLogger) {
        this.restTemplate = assistantRestTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metaspaceUsageLogger = metaspaceUsageLogger;
    }

    public String generateAnswer(String prompt,
                                 Integer maxOutputTokens,
                                 String model,
                                 String reasoningEffort) {
        return generate(prompt, resolveMaxOutputTokens(maxOutputTokens), model, reasoningEffort, true);
    }

    /**
     * AI 검색용 자유 텍스트 생성. 봇 채팅 JSON 스키마를 강제하지 않고,
     * 프롬프트가 요구하는 JSON을 output_text로 그대로 돌려받는다.
     */
    public String generateSearchAnswer(String prompt, Integer maxOutputTokens) {
        int requested = maxOutputTokens == null || maxOutputTokens <= 0
                ? SEARCH_DEFAULT_ANSWER_TOKENS : maxOutputTokens;
        int budget = Math.min(SEARCH_MAX_OUTPUT_TOKENS, requested + SEARCH_REASONING_HEADROOM_TOKENS);
        return generate(prompt, budget, properties.getSearchModel(),
                properties.getSearchReasoningEffort(), false);
    }

    private String generate(String prompt,
                            int maxOutputTokens,
                            String model,
                            String reasoningEffort,
                            boolean botChatFormat) {
        if (!properties.isAllowLiveCalls()) {
            throw new OpenAiAssistantBotException("Live OpenAI API calls are disabled.");
        }
        if (metaspaceUsageLogger.shouldPauseAiWork()) {
            throw new OpenAiAssistantBotException(
                    "OpenAI API call paused because JVM Metaspace headroom is low.");
        }

        String apiKey = requireText(properties.getApiKey(), "OpenAI API key is not configured.");
        URI apiUri = validateApiUri(requireText(properties.getBaseUrl(),
                "OpenAI Responses API URL is not configured."));
        String validPrompt = requireText(prompt, "Assistant bot prompt is required.");
        String validModel = requireText(model, "OpenAI model is not configured.");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + apiKey);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUri,
                    HttpMethod.POST,
                    new HttpEntity<>(buildPayload(validPrompt, maxOutputTokens,
                            validModel, reasoningEffort, botChatFormat), headers),
                    String.class);
            return extractStructuredOutput(response.getBody());
        } catch (HttpStatusCodeException e) {
            String detail = safeErrorDetail(e, apiKey, validPrompt);
            log.warn("OpenAI assistant bot request failed. status={}, model={}",
                    e.getStatusCode(), validModel);
            throw new OpenAiAssistantBotException("OpenAI Responses API request failed: "
                    + e.getStatusCode()
                    + (StringUtils.hasText(detail) ? " " + detail : ""), e);
        } catch (RestClientException e) {
            log.warn("OpenAI assistant bot request failed. type={}, model={}",
                    e.getClass().getSimpleName(), validModel);
            throw new OpenAiAssistantBotException("OpenAI Responses API request failed.", e);
        }
    }

    private Map<String, Object> buildPayload(String prompt,
                                             int maxOutputTokens,
                                             String model,
                                             String reasoningEffort,
                                             boolean botChatFormat) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", Collections.singletonList(inputMessage("system", prompt)));
        payload.put("store", false);
        payload.put("max_output_tokens", maxOutputTokens);

        Map<String, Object> reasoning = new LinkedHashMap<>();
        reasoning.put("effort", resolveReasoningEffort(reasoningEffort));
        payload.put("reasoning", reasoning);

        Map<String, Object> text = new LinkedHashMap<>();
        text.put("verbosity", "low");
        if (botChatFormat) {
            Map<String, Object> format = new LinkedHashMap<>();
            format.put("type", "json_schema");
            format.put("name", "assistant_bot_chat");
            format.put("strict", true);
            format.put("schema", buildResponseSchema());
            text.put("format", format);
        }
        payload.put("text", text);
        return payload;
    }

    private Map<String, Object> inputMessage(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private Map<String, Object> buildResponseSchema() {
        Map<String, Object> mode = new LinkedHashMap<>();
        mode.put("type", "string");
        mode.put("enum", Arrays.asList("contextual_advice", "standalone_strategy"));

        Map<String, Object> riskNotes = new LinkedHashMap<>();
        riskNotes.put("type", "array");
        riskNotes.put("items", Collections.singletonMap("type", "string"));

        Map<String, Object> analysisProperties = new LinkedHashMap<>();
        analysisProperties.put("topic", Collections.singletonMap("type", "string"));
        analysisProperties.put("response_mode", mode);
        analysisProperties.put("risk_notes", riskNotes);

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("type", "object");
        analysis.put("properties", analysisProperties);
        analysis.put("required", Arrays.asList("topic", "response_mode", "risk_notes"));
        analysis.put("additionalProperties", false);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "string");
        body.put("minLength", 1);
        body.put("maxLength", 120);

        Map<String, Object> chatProperties = new LinkedHashMap<>();
        chatProperties.put("body", body);

        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("type", "object");
        chat.put("properties", chatProperties);
        chat.put("required", Collections.singletonList("body"));
        chat.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("analysis", analysis);
        properties.put("chat", chat);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Arrays.asList("analysis", "chat"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private String extractStructuredOutput(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new OpenAiAssistantBotException("OpenAI returned an empty response.");
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String status = root.path("status").asText("");
            if (!"completed".equals(status)) {
                String reason = safeDiagnosticCode(
                        root.path("incomplete_details").path("reason").asText(""));
                throw new OpenAiAssistantBotException("OpenAI response did not complete: "
                        + (StringUtils.hasText(status) ? status : "missing status")
                        + (StringUtils.hasText(reason) ? " (" + reason + ")" : ""));
            }

            JsonNode output = root.path("output");
            if (!output.isArray()) {
                throw new OpenAiAssistantBotException("OpenAI response is missing output items.");
            }

            StringBuilder outputText = new StringBuilder();
            for (JsonNode item : output) {
                String itemType = item.path("type").asText("");
                if (itemType.endsWith("_call") || itemType.endsWith("_call_output")) {
                    throw new OpenAiAssistantBotException(
                            "OpenAI unexpectedly attempted external tool use.");
                }
                if (!"message".equals(itemType)) {
                    continue;
                }
                for (JsonNode block : item.path("content")) {
                    String blockType = block.path("type").asText("");
                    if ("refusal".equals(blockType)) {
                        throw new OpenAiAssistantBotException(
                                "OpenAI refused the assistant bot request.");
                    }
                    if ("output_text".equals(blockType)) {
                        outputText.append(block.path("text").asText(""));
                    }
                }
            }
            if (!StringUtils.hasText(outputText.toString())) {
                throw new OpenAiAssistantBotException(
                        "OpenAI returned no structured text output.");
            }
            return outputText.toString();
        } catch (OpenAiAssistantBotException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAiAssistantBotException(
                    "OpenAI returned invalid response JSON.", e);
        }
    }

    private int resolveMaxOutputTokens(Integer requested) {
        int value = requested == null ? 1400 : requested;
        return Math.max(1, Math.min(value, MAX_OUTPUT_TOKENS));
    }

    private String resolveReasoningEffort(String value) {
        if (!StringUtils.hasText(value)) {
            return "high";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!Arrays.asList("none", "low", "medium", "high", "xhigh", "max")
                .contains(normalized)) {
            return "high";
        }
        return normalized;
    }

    private URI validateApiUri(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw new OpenAiAssistantBotException(
                    "OpenAI Responses API URL is invalid.", e);
        }
        int port = uri.getPort();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !OPENAI_API_HOST.equalsIgnoreCase(uri.getHost())
                || !RESPONSES_API_PATH.equals(uri.getPath())
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (port != -1 && port != 443)) {
            throw new OpenAiAssistantBotException(
                    "OpenAI Responses API URL must use the trusted OpenAI HTTPS endpoint.");
        }
        return uri;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new OpenAiAssistantBotException(message);
        }
        return value.trim();
    }

    private String safeErrorDetail(HttpStatusCodeException exception,
                                   String apiKey,
                                   String prompt) {
        try {
            JsonNode message = objectMapper.readTree(exception.getResponseBodyAsString())
                    .path("error").path("message");
            String detail = message.isTextual() ? message.asText() : "";
            if (!StringUtils.hasText(detail)) {
                return "";
            }
            detail = detail.replace(apiKey, "[redacted]")
                    .replace(prompt, "[redacted]")
                    .replaceAll("\\s+", " ")
                    .trim();
            return detail.length() <= MAX_ERROR_DETAIL_CHARS
                    ? detail : detail.substring(0, MAX_ERROR_DETAIL_CHARS);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeDiagnosticCode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_.-]{1,80}") ? normalized : "";
    }
}
