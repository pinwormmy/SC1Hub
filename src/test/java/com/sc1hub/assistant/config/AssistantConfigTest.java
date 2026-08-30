package com.sc1hub.assistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantConfigTest {

    @Test
    void sharedAssistantTransportKeepsTheExistingTimeoutsWithoutBootBuilder() {
        RestTemplate restTemplate = new AssistantConfig().assistantRestTemplate();
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();

        assertTrue(requestFactory instanceof SimpleClientHttpRequestFactory);
        assertEquals(5000, ReflectionTestUtils.getField(requestFactory, "connectTimeout"));
        assertEquals(30000, ReflectionTestUtils.getField(requestFactory, "readTimeout"));
    }
}
