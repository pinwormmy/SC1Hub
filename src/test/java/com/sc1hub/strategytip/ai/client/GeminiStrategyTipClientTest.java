package com.sc1hub.strategytip.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.strategytip.ai.config.StrategyTipAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;

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

class GeminiStrategyTipClientTest {

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/interactions";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private StrategyTipAiProperties properties;
    private GeminiStrategyTipClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        properties = new StrategyTipAiProperties();
        properties.setEnabled(true);
        properties.setAllowLiveCalls(true);
        properties.setApiKey("test-gemini-key");
        properties.setBaseUrl(API_URL);
        client = new GeminiStrategyTipClient(restTemplate, properties, objectMapper);
    }

    @Test
    void generate_sendsOneInternalOnlyBatchWithoutSearchAndParsesUsage() {
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-gemini-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("gemini-3.6-flash"))
                .andExpect(jsonPath("$.system_instruction").value("system rules"))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.generation_config.thinking_level").value("high"))
                .andExpect(jsonPath("$.generation_config.thinking_summaries").value("none"))
                .andExpect(jsonPath("$.generation_config.max_output_tokens").value(3000))
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString(
                        "\"externalKnowledgeAllowed\":false")))
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString(
                        "\"toolUseAllowed\":false")))
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString(
                        "evidenceMustBeVerbatimFromSelectedExcerpt")))
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString(
                        "contentMustContainEvidenceSummaryVerbatim")))
                .andExpect(jsonPath("$.response_format.length()").value(1))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.minItems")
                        .value(3))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.maxItems")
                        .value(3))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.items"
                        + ".properties.externalEvidenceSummary").doesNotExist())
                .andRespond(withSuccess(completedResponse(threeDraftsJson(), 4100, 480, 220),
                        MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = client.generate(
                "system rules", "source data", 3,
                Arrays.asList("zvt", "pvz", "team_play"),
                Arrays.asList("zvstboard:10", "pvszboard:20", "teamplayguideboard:30"));

        assertEquals("gemini-3.6-flash", batch.getModel());
        assertEquals(4100, batch.getInputTokens());
        assertEquals(700, batch.getOutputTokens());
        assertEquals(3, batch.getDrafts().size());
        assertEquals("team_play", batch.getDrafts().get(2).getCategory());
        assertEquals("팀원과 입구를 나눠 막는다", batch.getDrafts().get(2).getEvidenceSummary());
        server.verify();
    }

    @Test
    void generate_clampsOutputTokenBudgetAndInvalidThinkingLevel() {
        properties.setMaxOutputTokens(99999);
        properties.setThinkingLevel("invalid");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.generation_config.max_output_tokens").value(3000))
                .andExpect(jsonPath("$.generation_config.thinking_level").value("high"))
                .andRespond(withSuccess(completedResponse(oneDraftJson(), 10, 10, 0),
                        MediaType.APPLICATION_JSON));

        generateOne();
        server.verify();
    }

    @Test
    void generate_rejectsDisabledLiveCallsAndMissingKeyWithoutCallingApi() {
        properties.setEnabled(false);
        assertThrows(GeminiStrategyTipException.class, this::generateOne);

        properties.setEnabled(true);
        properties.setAllowLiveCalls(false);
        assertThrows(GeminiStrategyTipException.class, this::generateOne);

        properties.setAllowLiveCalls(true);
        properties.setApiKey(" ");
        assertThrows(GeminiStrategyTipException.class, this::generateOne);
        server.verify();
    }

    @Test
    void generate_rejectsUntrustedBaseUrlBeforeSendingApiKey() {
        String[] untrustedUrls = {
                "http://generativelanguage.googleapis.com/v1beta/interactions",
                "https://generativelanguage.googleapis.com.evil.example/v1beta/interactions",
                "https://user@generativelanguage.googleapis.com/v1beta/interactions",
                "https://generativelanguage.googleapis.com:8443/v1beta/interactions"
        };

        for (String untrustedUrl : untrustedUrls) {
            properties.setBaseUrl(untrustedUrl);
            GeminiStrategyTipException exception = assertThrows(
                    GeminiStrategyTipException.class, this::generateOne);
            assertTrue(exception.getMessage().contains("trusted Google HTTPS endpoint"));
        }
        server.verify();
    }

    @Test
    void generate_rejectsIncompleteInteractionWithUsage() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("{\"status\":\"incomplete\",\"usage\":{"
                                + "\"total_input_tokens\":12,\"total_output_tokens\":7}}",
                        MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("incomplete"));
        assertTrue(exception.hasUsage());
        assertEquals(12, exception.getInputTokens());
        assertEquals(7, exception.getOutputTokens());
        assertEquals(0, exception.getSearchQueryCount());
    }

    @Test
    void generate_rejectsRefusalContent() {
        String response = "{\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\","
                + "\"content\":[{\"type\":\"refusal\",\"text\":\"cannot help\"}]}]}";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);
        assertTrue(exception.getMessage().contains("refused"));
    }

    @Test
    void generate_rejectsUnexpectedSearchOrUrlToolUse() {
        String response = "{\"status\":\"completed\",\"steps\":["
                + "{\"type\":\"google_search_call\"},"
                + "{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":"
                + quote(oneDraftJson()) + "}]}]}";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);
        assertTrue(exception.getMessage().contains("external tool use"));
    }

    @Test
    void generate_rejectsMalformedStructuredOutput() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse("not-json", 1, 1, 0),
                        MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);
        assertTrue(exception.getMessage().contains("invalid JSON"));
    }

    @Test
    void generate_rejectsUnexpectedDraftCount() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse("{\"drafts\":[]}", 1, 1, 0),
                        MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);
        assertTrue(exception.getMessage().contains("unexpected draft count"));
    }

    @Test
    void generate_rejectsDraftOutsideAllowedScope() {
        String output = "{\"drafts\":[{\"category\":\"pvt\","
                + "\"content\":\"질럿은 입구에서 길을 막아 시간을 번다\","
                + "\"sourceId\":\"pvstboard:999\","
                + "\"evidenceSummary\":\"질럿으로 입구를 막아 시간을 번다\"}]}";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(output, 1, 1, 0),
                        MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);
        assertTrue(exception.getMessage().contains("allowed source scope"));
    }

    @Test
    void generate_rejectsDuplicateCategoriesOrSources() {
        String output = "{\"drafts\":["
                + "{\"category\":\"zvt\",\"content\":\"뮤탈로 이동 동선을 먼저 확인한다\","
                + "\"sourceId\":\"zvstboard:10\",\"evidenceSummary\":\"뮤탈로 이동 동선을 먼저 확인한다\"},"
                + "{\"category\":\"zvt\",\"content\":\"러커로 진입 경로를 좁혀 수비한다\","
                + "\"sourceId\":\"zvstboard:10\",\"evidenceSummary\":\"러커로 진입 경로를 좁혀 수비한다\"}]}";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(output, 1, 1, 0),
                        MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class,
                () -> client.generate("system", "source", 2,
                        Arrays.asList("zvt", "pvz"),
                        Arrays.asList("zvstboard:10", "pvszboard:20")));
        assertTrue(exception.getMessage().contains("duplicate"));
    }

    @Test
    void generate_reportsSanitizedHttpError() {
        server.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"bad request test-gemini-key\"}}"));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("[redacted]"));
        assertTrue(!exception.getMessage().contains("test-gemini-key"));
    }

    @Test
    void generate_saturatesTokenUsage() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(oneDraftJson(),
                                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
                        MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = generateOne();
        assertEquals(Integer.MAX_VALUE, batch.getInputTokens());
        assertEquals(Integer.MAX_VALUE, batch.getOutputTokens());
    }

    private StrategyTipAiGeneratedBatch generateOne() {
        return client.generate("system rules", "source data", 1,
                Collections.singletonList("zvt"),
                Collections.singletonList("zvstboard:10"));
    }

    private String oneDraftJson() {
        return "{\"drafts\":[{\"category\":\"zvt\","
                + "\"content\":\"뮤탈로 이동 동선을 먼저 확인한다\","
                + "\"sourceId\":\"zvstboard:10\","
                + "\"evidenceSummary\":\"뮤탈로 이동 동선을 먼저 확인한다\"}]}";
    }

    private String threeDraftsJson() {
        return "{\"drafts\":["
                + "{\"category\":\"zvt\",\"content\":\"뮤탈로 이동 동선을 먼저 확인한다\","
                + "\"sourceId\":\"zvstboard:10\",\"evidenceSummary\":\"뮤탈로 이동 동선을 먼저 확인한다\"},"
                + "{\"category\":\"pvz\",\"content\":\"질럿을 좁은 길목에 세워 저글링을 막는다\","
                + "\"sourceId\":\"pvszboard:20\",\"evidenceSummary\":\"질럿을 좁은 길목에 세운다\"},"
                + "{\"category\":\"team_play\",\"content\":\"팀원과 입구를 나눠 막아 초반을 버틴다\","
                + "\"sourceId\":\"teamplayguideboard:30\",\"evidenceSummary\":\"팀원과 입구를 나눠 막는다\"}]}";
    }

    private String completedResponse(String output, int inputTokens,
                                     int outputTokens, int thoughtTokens) {
        return "{\"status\":\"completed\",\"model\":\"gemini-3.6-flash\","
                + "\"steps\":[{\"type\":\"model_output\",\"content\":["
                + "{\"type\":\"text\",\"text\":" + quote(output) + "}]}],"
                + "\"usage\":{\"total_input_tokens\":" + inputTokens
                + ",\"total_output_tokens\":" + outputTokens
                + ",\"total_thought_tokens\":" + thoughtTokens + "}}";
    }

    private String quote(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AssertionError(e);
        }
    }
}
