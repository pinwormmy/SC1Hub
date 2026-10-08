package com.sc1hub.assistant.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.assistant.config.AnthropicProperties;
import com.sc1hub.common.monitoring.MetaspaceUsageLogger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnthropicAssistantClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestApiKey = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private HttpServer server;
    private int responseStatus;
    private String responseBody;
    private AnthropicProperties properties;
    private MetaspaceUsageLogger metaspaceUsageLogger;
    private AnthropicAssistantClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requestCount.incrementAndGet();
            requestApiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        properties = new AnthropicProperties();
        properties.setApiKey("test-anthropic-key");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setAllowLiveCalls(true);
        metaspaceUsageLogger = mock(MetaspaceUsageLogger.class);
        client = new AnthropicAssistantClient(properties, metaspaceUsageLogger);
        respond(200, message("end_turn", "{\"answer\":\"질럿 먼저\",\"sources\":[]}"));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void generateSearchAnswer_usesHaikuAtMaxEffortWithoutBotSchemaAndAddsThinkingHeadroom() throws Exception {
        String result = client.generateSearchAnswer("검색 프롬프트", 1024);

        assertEquals("{\"answer\":\"질럿 먼저\",\"sources\":[]}", result);
        JsonNode request = objectMapper.readTree(requestBody.get());
        assertEquals("test-anthropic-key", requestApiKey.get());
        assertEquals("claude-haiku-5-5", request.path("model").asText());
        assertEquals("max", request.path("output_config").path("effort").asText());
        // 요청 1024 + thinking 여유분 12000
        assertEquals(13024, request.path("max_tokens").asInt());
        assertTrue(request.path("output_config").path("format").isMissingNode());
        assertEquals("user", request.path("messages").get(0).path("role").asText());
        // Haiku 5.5는 thinking 비활성화·샘플링 파라미터를 거부하므로 보내지 않는다.
        assertTrue(request.path("thinking").isMissingNode());
        assertTrue(request.path("temperature").isMissingNode());
    }

    @Test
    void generateAnswer_forcesBotChatSchemaWithoutLengthConstraints() throws Exception {
        respond(200, message("end_turn", "{\"analysis\":{},\"chat\":{\"body\":\"ㅎㅇ\"}}"));

        client.generateAnswer("prompt", 3000, "claude-haiku-5-5", "max");

        JsonNode request = objectMapper.readTree(requestBody.get());
        JsonNode format = request.path("output_config").path("format");
        assertEquals("json_schema", format.path("type").asText());
        JsonNode body = format.path("schema").path("properties").path("chat").path("properties").path("body");
        assertEquals("string", body.path("type").asText());
        assertFalse(body.has("maxLength"));
        assertEquals(15000, request.path("max_tokens").asInt());
    }

    @Test
    void generateDraftAnswer_mapsForeignEffortValuesAndSkipsSchema() throws Exception {
        client.generateDraftAnswer("prompt", 1400, "claude-haiku-5-5", "minimal");

        JsonNode request = objectMapper.readTree(requestBody.get());
        assertEquals("low", request.path("output_config").path("effort").asText());
        assertTrue(request.path("output_config").path("format").isMissingNode());
    }

    @Test
    void refusalAndTruncationAreReportedInsteadOfReturningPartialText() {
        respond(200, message("refusal", ""));
        assertTrue(assertThrows(AnthropicAssistantException.class,
                () -> client.generateSearchAnswer("prompt", 512)).getMessage().contains("refused"));

        respond(200, message("max_tokens", "{\"answer\":\"잘린"));
        assertTrue(assertThrows(AnthropicAssistantException.class,
                () -> client.generateSearchAnswer("prompt", 512)).getMessage().contains("max_tokens"));
    }

    @Test
    void apiErrorsSurfaceStatusWithoutLeakingKey() {
        respond(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}");

        AnthropicAssistantException exception = assertThrows(AnthropicAssistantException.class,
                () -> client.generateSearchAnswer("prompt", 512));

        assertTrue(exception.getMessage().contains("401"));
        assertFalse(exception.getMessage().contains("test-anthropic-key"));
    }

    @Test
    void safetySwitchesBlockCallsBeforeAnyRequest() {
        properties.setAllowLiveCalls(false);
        assertThrows(AnthropicAssistantException.class, () -> client.generateSearchAnswer("prompt", 512));

        properties.setAllowLiveCalls(true);
        when(metaspaceUsageLogger.shouldPauseAiWork()).thenReturn(true);
        assertThrows(AnthropicAssistantException.class, () -> client.generateSearchAnswer("prompt", 512));

        when(metaspaceUsageLogger.shouldPauseAiWork()).thenReturn(false);
        properties.setApiKey(" ");
        assertThrows(AnthropicAssistantException.class, () -> client.generateSearchAnswer("prompt", 512));

        assertEquals(0, requestCount.get());
    }

    private void respond(int status, String body) {
        responseStatus = status;
        responseBody = body;
    }

    private String message(String stopReason, String text) {
        try {
            return "{\"id\":\"msg_test\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-haiku-5-5\","
                    + "\"content\":[{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"sig\"},"
                    + "{\"type\":\"text\",\"text\":" + objectMapper.writeValueAsString(text) + "}],"
                    + "\"stop_reason\":\"" + stopReason + "\",\"stop_sequence\":null,"
                    + "\"usage\":{\"input_tokens\":10,\"output_tokens\":20}}";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
