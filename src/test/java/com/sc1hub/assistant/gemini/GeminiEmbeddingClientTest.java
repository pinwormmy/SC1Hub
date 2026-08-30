package com.sc1hub.assistant.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.GeminiProperties;
import com.sc1hub.common.monitoring.MetaspaceUsageLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiEmbeddingClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private MetaspaceUsageLogger metaspaceUsageLogger;

    @Test
    void embedText_pausesBeforeNetworkWhenMetaspaceIsLow() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("test-key");
        properties.setAllowLiveCalls(true);
        when(metaspaceUsageLogger.shouldPauseAiWork()).thenReturn(true);
        GeminiEmbeddingClient client = new GeminiEmbeddingClient(
                restTemplate,
                properties,
                new ObjectMapper(),
                metaspaceUsageLogger
        );

        GeminiException exception = assertThrows(
                GeminiException.class,
                () -> client.embedText("test text")
        );

        assertEquals(
                "Gemini embedding call paused because JVM Metaspace headroom is low.",
                exception.getMessage()
        );
        verifyNoInteractions(restTemplate);
    }
}
