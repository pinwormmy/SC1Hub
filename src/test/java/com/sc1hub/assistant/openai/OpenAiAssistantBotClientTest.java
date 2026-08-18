package com.sc1hub.assistant.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.OpenAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiAssistantBotClientTest {

    private static final String API_URL = "https://api.openai.com/v1/responses";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private OpenAiProperties properties;
    private OpenAiAssistantBotClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        properties = new OpenAiProperties();
        properties.setApiKey("test-openai-key");
        properties.setAllowLiveCalls(true);
        client = new OpenAiAssistantBotClient(restTemplate, properties, objectMapper);
    }

    @Test
    void generateAnswer_sendsLunaHighStructuredRequestAndReturnsChatJson() {
        String chatJson = "{\"analysis\":{\"topic\":\"저그전\","
                + "\"response_mode\":\"contextual_advice\",\"risk_notes\":[]},"
                + "\"chat\":{\"body\":\"옵저버로 시야부터 확보해\"}}";
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-openai-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("gpt-5.6-luna"))
                .andExpect(jsonPath("$.input[0].role").value("system"))
                .andExpect(jsonPath("$.input[0].content").value("고수봇 규칙"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andExpect(jsonPath("$.reasoning.effort").value("high"))
                .andExpect(jsonPath("$.max_output_tokens").value(1400))
                .andExpect(jsonPath("$.text.verbosity").value("low"))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.name").value("assistant_bot_chat"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.schema.properties.analysis.properties"
                        + ".response_mode.enum[0]").value("contextual_advice"))
                .andExpect(jsonPath("$.text.format.schema.properties.analysis.properties"
                        + ".response_mode.enum[1]").value("standalone_strategy"))
                .andExpect(jsonPath("$.text.format.schema.properties.chat.properties"
                        + ".body.maxLength").value(120))
                .andRespond(withSuccess(completedResponse(chatJson),
                        MediaType.APPLICATION_JSON));

        String result = client.generateAnswer(
                "고수봇 규칙", 1400, "gpt-5.6-luna", "high");

        assertEquals(chatJson, result);
        server.verify();
    }

    @Test
    void generateAnswer_clampsOutputAndFallsBackToHighReasoning() {
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.max_output_tokens").value(6000))
                .andExpect(jsonPath("$.reasoning.effort").value("high"))
                .andRespond(withSuccess(completedResponse(validStandaloneJson()),
                        MediaType.APPLICATION_JSON));

        client.generateAnswer("prompt", 99999, "gpt-5.6-luna", "invalid");

        server.verify();
    }

    @Test
    void generateAnswer_rejectsDisabledCallsMissingKeyAndUntrustedUrl() {
        properties.setAllowLiveCalls(false);
        assertThrows(OpenAiAssistantBotException.class,
                () -> client.generateAnswer("prompt", 1400, "gpt-5.6-luna", "high"));

        properties.setAllowLiveCalls(true);
        properties.setApiKey(" ");
        assertThrows(OpenAiAssistantBotException.class,
                () -> client.generateAnswer("prompt", 1400, "gpt-5.6-luna", "high"));

        properties.setApiKey("test-openai-key");
        properties.setBaseUrl("https://api.openai.com.evil.example/v1/responses");
        OpenAiAssistantBotException exception = assertThrows(
                OpenAiAssistantBotException.class,
                () -> client.generateAnswer("prompt", 1400, "gpt-5.6-luna", "high"));
        assertTrue(exception.getMessage().contains("trusted OpenAI HTTPS endpoint"));
        server.verify();
    }

    @Test
    void generateAnswer_reportsSanitizedHttpError() {
        server.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"bad test-openai-key prompt\"}}"));

        OpenAiAssistantBotException exception = assertThrows(
                OpenAiAssistantBotException.class,
                () -> client.generateAnswer("prompt", 1400, "gpt-5.6-luna", "high"));

        assertTrue(exception.getMessage().contains("[redacted]"));
        assertTrue(!exception.getMessage().contains("test-openai-key"));
    }

    private String completedResponse(String outputText) {
        return "{\"status\":\"completed\",\"output\":[{\"type\":\"message\","
                + "\"content\":[{\"type\":\"output_text\",\"text\":"
                + quote(outputText) + "}]}]}";
    }

    private String validStandaloneJson() {
        return "{\"analysis\":{\"topic\":\"정찰\","
                + "\"response_mode\":\"standalone_strategy\",\"risk_notes\":[]},"
                + "\"chat\":{\"body\":\"정찰이 끊기면 병력보다 시야부터 복구해\"}}";
    }

    private String quote(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
