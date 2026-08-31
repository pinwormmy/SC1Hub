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
        // 높은 reasoning effort의 AI 검색 호출이 30초를 넘길 수 있어 120초로 늘렸다.
        assertEquals(120000, ReflectionTestUtils.getField(requestFactory, "readTimeout"));
    }
}
