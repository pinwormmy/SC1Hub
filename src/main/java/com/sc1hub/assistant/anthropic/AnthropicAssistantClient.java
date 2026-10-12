package com.sc1hub.assistant.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.sc1hub.assistant.config.AnthropicProperties;
import com.sc1hub.common.monitoring.MetaspaceUsageLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class AnthropicAssistantClient {

    private static final int MAX_TOKENS = 24000;
    // thinking 토큰이 max_tokens 예산을 함께 소모하므로, 답변 예산 위에 여유분을 얹어 보낸다.
    // 여유가 모자라면 답변 없이 stop_reason=max_tokens로 끝난다. max 강도에서는 근거 글이 긴 질문 하나에
    // thinking만 11000 토큰을 넘게 쓰는 것이 실측되어(2026-10-12) 12000으로는 모자랐다.
    private static final int THINKING_HEADROOM_TOKENS = 20000;
    // 그래도 예산이 바닥나면 이 강도로 한 번 더 보낸다. thinking이 크게 줄어 답변까지 도달한다.
    private static final String TRUNCATION_RETRY_EFFORT = "high";
    private static final int BOT_DEFAULT_ANSWER_TOKENS = 1400;
    private static final int SEARCH_DEFAULT_ANSWER_TOKENS = 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final AnthropicProperties properties;
    private final MetaspaceUsageLogger metaspaceUsageLogger;
    private volatile AnthropicClient client;
    private String clientApiKey;
    private String clientBaseUrl;

    public AnthropicAssistantClient(AnthropicProperties properties,
                                    MetaspaceUsageLogger metaspaceUsageLogger) {
        this.properties = properties;
        this.metaspaceUsageLogger = metaspaceUsageLogger;
    }

    /** 봇 채팅. 채팅 전용 JSON 스키마를 structured output으로 강제한다. */
    public String generateAnswer(String prompt,
                                 Integer maxOutputTokens,
                                 String model,
                                 String effort) {
        return generate(prompt, resolveBudget(maxOutputTokens, BOT_DEFAULT_ANSWER_TOKENS), model, effort, true);
    }

    /** 글·댓글 초안은 채팅 전용 스키마 없이 프롬프트의 JSON 형식을 사용한다. */
    public String generateDraftAnswer(String prompt,
                                      Integer maxOutputTokens,
                                      String model,
                                      String effort) {
        return generate(prompt, resolveBudget(maxOutputTokens, BOT_DEFAULT_ANSWER_TOKENS), model, effort, false);
    }

    /** AI 검색용 자유 텍스트 생성. 프롬프트가 요구하는 JSON을 그대로 돌려받는다. */
    public String generateSearchAnswer(String prompt, Integer maxOutputTokens) {
        return generate(prompt, resolveBudget(maxOutputTokens, SEARCH_DEFAULT_ANSWER_TOKENS),
                properties.getSearchModel(), properties.getSearchEffort(), false);
    }

    private String generate(String prompt,
                            int maxTokens,
                            String model,
                            String effort,
                            boolean botChatFormat) {
        if (!properties.isAllowLiveCalls()) {
            throw new AnthropicAssistantException("Live Anthropic API calls are disabled.");
        }
        if (metaspaceUsageLogger.shouldPauseAiWork()) {
            throw new AnthropicAssistantException(
                    "Anthropic API call paused because JVM Metaspace headroom is low.");
        }

        String apiKey = requireText(properties.getApiKey(), "Anthropic API key is not configured.");
        String validPrompt = requireText(prompt, "Assistant prompt is required.");
        String validModel = requireText(model, "Anthropic model is not configured.");

        String validEffort = resolveEffort(effort);
        try {
            return requestText(apiKey, validPrompt, maxTokens, validModel, validEffort, botChatFormat);
        } catch (TruncatedResponseException e) {
            String retryEffort = resolveTruncationRetryEffort(validEffort);
            if (retryEffort == null) {
                throw e;
            }
            log.warn("Anthropic response hit max_tokens before answering. Retrying at lower effort. "
                            + "model={}, effort={}, retryEffort={}, maxTokens={}, outputTokens={}",
                    validModel, validEffort, retryEffort, maxTokens, e.getOutputTokens());
            return requestText(apiKey, validPrompt, maxTokens, validModel, retryEffort, botChatFormat);
        }
    }

    private String requestText(String apiKey,
                               String validPrompt,
                               int maxTokens,
                               String validModel,
                               String validEffort,
                               boolean botChatFormat) {
        OutputConfig.Builder outputConfig = OutputConfig.builder()
                .effort(OutputConfig.Effort.of(validEffort));
        if (botChatFormat) {
            JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
            buildResponseSchema().forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
            outputConfig.format(JsonOutputFormat.builder().schema(schema.build()).build());
        }
        MessageCreateParams params = MessageCreateParams.builder()
                .model(validModel)
                .maxTokens((long) maxTokens)
                .outputConfig(outputConfig.build())
                .addUserMessage(validPrompt)
                .build();

        try {
            return extractText(resolveClient(apiKey).messages().create(params));
        } catch (AnthropicServiceException e) {
            log.warn("Anthropic request failed. status={}, model={}", e.statusCode(), validModel);
            throw new AnthropicAssistantException(
                    "Anthropic Messages API request failed: " + e.statusCode(), e);
        } catch (AnthropicException e) {
            log.warn("Anthropic request failed. type={}, model={}",
                    e.getClass().getSimpleName(), validModel);
            throw new AnthropicAssistantException("Anthropic Messages API request failed.", e);
        }
    }

    private String extractText(Message message) {
        StopReason stopReason = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stopReason)) {
            throw new AnthropicAssistantException("Anthropic refused the assistant request.");
        }
        if (StopReason.MAX_TOKENS.equals(stopReason)) {
            throw new TruncatedResponseException(message.usage().outputTokens());
        }
        if (!StopReason.END_TURN.equals(stopReason)) {
            throw new AnthropicAssistantException("Anthropic response did not complete: "
                    + (stopReason == null ? "missing stop_reason" : stopReason.asString()));
        }
        StringBuilder text = new StringBuilder();
        message.content().forEach(block -> block.text().ifPresent(t -> text.append(t.text())));
        if (!StringUtils.hasText(text.toString())) {
            throw new AnthropicAssistantException("Anthropic returned no text output.");
        }
        return text.toString();
    }

    private AnthropicClient resolveClient(String apiKey) {
        String baseUrl = StringUtils.hasText(properties.getBaseUrl()) ? properties.getBaseUrl().trim() : "";
        AnthropicClient current = client;
        if (current != null && apiKey.equals(clientApiKey) && baseUrl.equals(clientBaseUrl)) {
            return current;
        }
        synchronized (this) {
            if (client == null || !apiKey.equals(clientApiKey) || !baseUrl.equals(clientBaseUrl)) {
                AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                        .apiKey(apiKey)
                        .timeout(REQUEST_TIMEOUT)
                        .maxRetries(1);
                if (StringUtils.hasText(baseUrl)) {
                    builder.baseUrl(baseUrl);
                }
                client = builder.build();
                clientApiKey = apiKey;
                clientBaseUrl = baseUrl;
            }
            return client;
        }
    }

    // structured output은 문자열 길이 제약(minLength/maxLength)을 지원하지 않는다.
    // 채팅 길이는 프롬프트와 서비스 쪽 검증이 맡는다.
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

        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("type", "object");
        chat.put("properties", Collections.singletonMap("body", Collections.singletonMap("type", "string")));
        chat.put("required", Collections.singletonList("body"));
        chat.put("additionalProperties", false);

        Map<String, Object> schemaProperties = new LinkedHashMap<>();
        schemaProperties.put("analysis", analysis);
        schemaProperties.put("chat", chat);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", schemaProperties);
        schema.put("required", Arrays.asList("analysis", "chat"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private int resolveBudget(Integer requested, int defaultAnswerTokens) {
        int answerTokens = requested == null || requested <= 0 ? defaultAnswerTokens : requested;
        return Math.min(MAX_TOKENS, answerTokens + THINKING_HEADROOM_TOKENS);
    }

    /** 다른 제공자용 강도 표기(none/minimal)가 넘어와도 Claude가 받는 값으로 맞춘다. */
    private String resolveEffort(String value) {
        if (!StringUtils.hasText(value)) {
            return "high";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("none".equals(normalized) || "minimal".equals(normalized)) {
            return "low";
        }
        if (!Arrays.asList("low", "medium", "high", "xhigh", "max").contains(normalized)) {
            return "high";
        }
        return normalized;
    }

    /** max_tokens로 잘렸을 때 다시 보낼 강도. 이미 그 이하 강도였다면 재시도하지 않는다(null). */
    private String resolveTruncationRetryEffort(String effort) {
        return "max".equals(effort) || "xhigh".equals(effort) ? TRUNCATION_RETRY_EFFORT : null;
    }

    private static final class TruncatedResponseException extends AnthropicAssistantException {
        private final long outputTokens;

        private TruncatedResponseException(long outputTokens) {
            super("Anthropic response did not complete: max_tokens");
            this.outputTokens = outputTokens;
        }

        private long getOutputTokens() {
            return outputTokens;
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new AnthropicAssistantException(message);
        }
        return value.trim();
    }
}
