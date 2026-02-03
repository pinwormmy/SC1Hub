package com.sc1hub.assistant.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.GeminiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    }
}

