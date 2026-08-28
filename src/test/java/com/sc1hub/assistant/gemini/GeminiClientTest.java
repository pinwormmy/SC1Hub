package com.sc1hub.assistant.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.GeminiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiClientTest {

    @Mock
    private RestTemplate restTemplate;

    private GeminiProperties geminiProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        geminiProperties = new GeminiProperties();
        geminiProperties.setApiKey("test-key");
        geminiProperties.setAllowLiveCalls(true);
        objectMapper = new ObjectMapper();
    }

    @Test
    void generateAnswer_concatenatesMultipleTextParts() {
        String responseJson = "{"
                + "\"candidates\":[{"
                + "\"content\":{"
                + "\"parts\":[{\"text\":\"hello \"},{\"text\":\"world\"}]"
                + "}"
                + "}]"
                + "}";
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseJson);

        GeminiClient client = new GeminiClient(restTemplate, geminiProperties, objectMapper);

        assertEquals("hello world", client.generateAnswer("prompt"));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), entityCaptor.capture(), eq(String.class));
        Map<?, ?> payload = (Map<?, ?>) entityCaptor.getValue().getBody();
        Map<?, ?> generationConfig = (Map<?, ?>) payload.get("generationConfig");
        assertFalse(generationConfig.containsKey("temperature"));
        assertFalse(generationConfig.containsKey("thinkingConfig"));
    }

    @Test
    void generateSearchAnswer_usesLowThinkingLevel() {
        String responseJson = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"answer\"}]}}]}";
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseJson);

        GeminiClient client = new GeminiClient(restTemplate, geminiProperties, objectMapper);

        assertEquals("answer", client.generateSearchAnswer("prompt", 1024));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), entityCaptor.capture(), eq(String.class));
        Map<?, ?> payload = (Map<?, ?>) entityCaptor.getValue().getBody();
        Map<?, ?> generationConfig = (Map<?, ?>) payload.get("generationConfig");
        Map<?, ?> thinkingConfig = (Map<?, ?>) generationConfig.get("thinkingConfig");
        assertEquals(1024, generationConfig.get("maxOutputTokens"));
        assertEquals("low", thinkingConfig.get("thinkingLevel"));
    }

    @Test
    void generateAnswer_throws_whenLiveCallsDisabled() {
        geminiProperties.setAllowLiveCalls(false);
        GeminiClient client = new GeminiClient(restTemplate, geminiProperties, objectMapper);

        GeminiException exception = assertThrows(GeminiException.class, () -> client.generateAnswer("prompt"));

        assertEquals("Live Gemini API calls are disabled.", exception.getMessage());
        verifyNoInteractions(restTemplate);
    }
}
