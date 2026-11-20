package com.sc1hub.assistant.service;

import com.sc1hub.assistant.anthropic.AnthropicAssistantClient;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.openai.OpenAiAssistantBotClient;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * AI 검색 계열 LLM 호출을 설정된 검색 제공자(Anthropic/OpenAI/Gemini)로 라우팅한다.
 */
public class AssistantLlmRouter {

    private static final String SEARCH_PROVIDER_OPENAI = "openai";
    private static final String SEARCH_PROVIDER_ANTHROPIC = "anthropic";

    private final AssistantProperties assistantProperties;
    private final GeminiClient geminiClient;
    private final OpenAiAssistantBotClient openAiAssistantBotClient;
    private final AnthropicAssistantClient anthropicAssistantClient;

    public AssistantLlmRouter(AssistantProperties assistantProperties,
                              GeminiClient geminiClient,
                              OpenAiAssistantBotClient openAiAssistantBotClient,
                              AnthropicAssistantClient anthropicAssistantClient) {
        this.assistantProperties = assistantProperties;
        this.geminiClient = geminiClient;
        this.openAiAssistantBotClient = openAiAssistantBotClient;
        this.anthropicAssistantClient = anthropicAssistantClient;
    }

    /**
     * 기본 출력 토큰 한도로 검색 LLM 응답을 생성한다.
     */
    public String generateSearchAnswer(String prompt) {
        return generateSearchAnswer(prompt, null);
    }

    /**
     * 지정된 최대 출력 토큰 예산으로 검색 LLM 응답을 생성한다.
     */
    public String generateSearchAnswer(String prompt, Integer maxOutputTokens) {
        if (usesSearchProvider(SEARCH_PROVIDER_ANTHROPIC)) {
            return anthropicAssistantClient.generateSearchAnswer(prompt, maxOutputTokens);
        }
        if (usesSearchProvider(SEARCH_PROVIDER_OPENAI)) {
            return openAiAssistantBotClient.generateSearchAnswer(prompt, maxOutputTokens);
        }
        return maxOutputTokens == null
                ? geminiClient.generateSearchAnswer(prompt)
                : geminiClient.generateSearchAnswer(prompt, maxOutputTokens);
    }

    private boolean usesSearchProvider(String expected) {
        if (assistantProperties == null) {
            return false;
        }
        String provider = assistantProperties.getSearchProvider();
        return StringUtils.hasText(provider)
                && expected.equals(provider.trim().toLowerCase(Locale.ROOT));
    }
}
