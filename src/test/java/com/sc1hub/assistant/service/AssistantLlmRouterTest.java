package com.sc1hub.assistant.service;

import com.sc1hub.assistant.anthropic.AnthropicAssistantClient;
import com.sc1hub.assistant.config.AssistantProperties;
import com.sc1hub.assistant.gemini.GeminiClient;
import com.sc1hub.assistant.openai.OpenAiAssistantBotClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantLlmRouterTest {

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private OpenAiAssistantBotClient openAiAssistantBotClient;

    @Mock
    private AnthropicAssistantClient anthropicAssistantClient;

    private AssistantProperties assistantProperties;
    private AssistantLlmRouter router;

    @BeforeEach
    void setUp() {
        assistantProperties = new AssistantProperties();
        router = new AssistantLlmRouter(
                assistantProperties,
                geminiClient,
                openAiAssistantBotClient,
                anthropicAssistantClient);
    }

    @Test
    void routesToAnthropicWhenConfigured() {
        assistantProperties.setSearchProvider("Anthropic ");
        when(anthropicAssistantClient.generateSearchAnswer("prompt", 768)).thenReturn("anthropic-out");
        when(anthropicAssistantClient.generateSearchAnswer("prompt", null)).thenReturn("anthropic-default");

        assertEquals("anthropic-out", router.generateSearchAnswer("prompt", 768));
        assertEquals("anthropic-default", router.generateSearchAnswer("prompt"));
        verifyNoInteractions(openAiAssistantBotClient, geminiClient);
    }

    @Test
    void routesToOpenAiWhenConfigured() {
        assistantProperties.setSearchProvider("openai");
        when(openAiAssistantBotClient.generateSearchAnswer("prompt", 512)).thenReturn("openai-out");

        assertEquals("openai-out", router.generateSearchAnswer("prompt", 512));
        verifyNoInteractions(anthropicAssistantClient, geminiClient);
    }

    @Test
    void routesToGeminiForExplicitBlankOrUnknownProvider() {
        assistantProperties.setSearchProvider("gemini");
        when(geminiClient.generateSearchAnswer("prompt")).thenReturn("gemini-default");
        assertEquals("gemini-default", router.generateSearchAnswer("prompt"));

        assistantProperties.setSearchProvider(" ");
        when(geminiClient.generateSearchAnswer("prompt", 1024)).thenReturn("gemini-with-budget");
        assertEquals("gemini-with-budget", router.generateSearchAnswer("prompt", 1024));

        assistantProperties.setSearchProvider("unknown");
        assertEquals("gemini-with-budget", router.generateSearchAnswer("prompt", 1024));

        verify(geminiClient).generateSearchAnswer("prompt");
        verifyNoInteractions(anthropicAssistantClient, openAiAssistantBotClient);
    }
}
