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

class OpenAiStrategyTipClientTest {

    private static final String API_URL = "https://api.openai.com/v1/responses";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private StrategyTipAiProperties properties;
    private OpenAiStrategyTipClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        properties = new StrategyTipAiProperties();
        properties.setEnabled(true);
        properties.setAllowLiveCalls(true);
        properties.setApiKey("test-openai-key");
        properties.setBaseUrl(API_URL);
        client = new OpenAiStrategyTipClient(restTemplate, properties, objectMapper);
    }

    @Test
    void generate_sendsOneCheckpointOnlyBatchWithoutToolsAndParsesUsage() {
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-openai-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("gpt-5.6-luna"))
                .andExpect(jsonPath("$.input[0].role").value("system"))
                .andExpect(jsonPath("$.input[0].content").value("system rules"))
                .andExpect(jsonPath("$.input[1].role").value("user"))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.reasoning.effort").value("high"))
                .andExpect(jsonPath("$.max_output_tokens").value(6000))
                .andExpect(jsonPath("$.input[1].content").value(
                        org.hamcrest.Matchers.containsString(
                                "\"checkpointKnowledgeOnly\":true")))
                .andExpect(jsonPath("$.input[1].content").value(
                        org.hamcrest.Matchers.containsString("\"toolUseAllowed\":false")))
                .andExpect(jsonPath("$.input[1].content").value(
                        org.hamcrest.Matchers.containsString(
                                "\"sourceMaterialProvided\":false")))
                .andExpect(jsonPath("$.input[1].content").value(
                        org.hamcrest.Matchers.containsString(
                                "\"preciseNumbersAllowed\":false")))
                .andExpect(jsonPath("$.input[1].content").value(
                        org.hamcrest.Matchers.containsString(
                                "built-in checkpoint knowledge")))
                .andExpect(jsonPath("$.text.verbosity").value("low"))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.name").value("strategy_tip_batch"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.schema.properties.drafts.minItems")
                        .value(3))
                .andExpect(jsonPath("$.text.format.schema.properties.drafts.maxItems")
                        .value(3))
                .andExpect(jsonPath("$.text.format.schema.properties.drafts.items"
                        + ".properties.sourceId").doesNotExist())
                .andExpect(jsonPath("$.text.format.schema.properties.drafts.items"
                        + ".properties.evidenceSummary").doesNotExist())
                .andRespond(withSuccess(completedResponse(threeDraftsJson(), 4100, 700),
                        MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = client.generate(
                "system rules", "source data", 3,
                Arrays.asList("zvt", "pvz", "team_play"));

        assertEquals("gpt-5.6-luna", batch.getModel());
        assertEquals(4100, batch.getInputTokens());
        assertEquals(700, batch.getOutputTokens());
        assertEquals(3, batch.getDrafts().size());
        assertEquals("team_play", batch.getDrafts().get(2).getCategory());
        assertEquals("팀원과 입구를 나눠 막아 초반을 버틴다",
                batch.getDrafts().get(2).getContent());
        server.verify();
    }

    @Test
    void generate_clampsOutputTokenBudgetAndInvalidReasoningEffort() {
        properties.setMaxOutputTokens(99999);
        properties.setReasoningEffort("invalid");
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.max_output_tokens").value(6000))
                .andExpect(jsonPath("$.reasoning.effort").value("high"))
                .andRespond(withSuccess(completedResponse(oneDraftJson(), 10, 10),
                        MediaType.APPLICATION_JSON));

        generateOne();
        server.verify();
    }

    @Test
    void generate_rejectsDisabledLiveCallsAndMissingKeyWithoutCallingApi() {
        properties.setEnabled(false);
        assertThrows(StrategyTipAiClientException.class, this::generateOne);

        properties.setEnabled(true);
        properties.setAllowLiveCalls(false);
        assertThrows(StrategyTipAiClientException.class, this::generateOne);

        properties.setAllowLiveCalls(true);
        properties.setApiKey(" ");
        assertThrows(StrategyTipAiClientException.class, this::generateOne);
        server.verify();
    }

    @Test
    void generate_rejectsUntrustedBaseUrlBeforeSendingApiKey() {
        String[] untrustedUrls = {
                "http://api.openai.com/v1/responses",
                "https://api.openai.com.evil.example/v1/responses",
                "https://user@api.openai.com/v1/responses",
                "https://api.openai.com:8443/v1/responses",
                "https://api.openai.com/v1/responses?key=leak",
                "https://api.openai.com/v1/chat/completions"
        };

        for (String untrustedUrl : untrustedUrls) {
            properties.setBaseUrl(untrustedUrl);
            StrategyTipAiClientException exception = assertThrows(
                    StrategyTipAiClientException.class, this::generateOne);
            assertTrue(exception.getMessage().contains("trusted OpenAI HTTPS endpoint"));
        }
        server.verify();
    }

    @Test
    void generate_rejectsIncompleteResponseWithUsage() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("{\"status\":\"incomplete\",\"usage\":{"
                                + "\"input_tokens\":12,\"output_tokens\":7}}",
                        MediaType.APPLICATION_JSON));

        StrategyTipAiClientException exception = assertThrows(
                StrategyTipAiClientException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("incomplete"));
        assertTrue(exception.hasUsage());
        assertEquals(12, exception.getInputTokens());
        assertEquals(7, exception.getOutputTokens());
    }

    @Test
    void generate_rejectsActualShapedIncompleteBatchAndPreservesBilledUsage() {
        String partialOutput = "{\"drafts\":["
                + "{\"category\":\"zvt\",\"content\":\"뮤탈로 이동 동선을 먼저 확인한다\"},"
                + "{\"category\":\"pvz\",\"content\":\"질럿을 좁은 길목에 세운다\"";
        String response = "{\"status\":\"incomplete\","
                + "\"incomplete_details\":{\"reason\":\"max_output_tokens\"},"
                + "\"output\":[{\"type\":\"message\",\"content\":["
                + "{\"type\":\"output_text\",\"text\":" + quote(partialOutput) + "}]}],"
                + "\"usage\":{\"input_tokens\":3293,\"output_tokens\":2978}}";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        StrategyTipAiClientException exception = assertThrows(
                StrategyTipAiClientException.class,
                () -> client.generate("system rules", "source data", 3,
                        Arrays.asList("zvt", "pvz", "team_play")));

        assertTrue(exception.getMessage().contains("incomplete"));
        assertTrue(exception.getMessage().contains("max_output_tokens"));
        assertTrue(exception.hasUsage());
        assertEquals(3293, exception.getInputTokens());
        assertEquals(2978, exception.getOutputTokens());
        server.verify();
    }

    @Test
    void generate_rejectsRefusalContent() {
        String response = "{\"status\":\"completed\",\"output\":[{\"type\":\"message\","
                + "\"content\":[{\"type\":\"refusal\",\"refusal\":\"cannot help\"}]}]}";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        StrategyTipAiClientException exception = assertThrows(
                StrategyTipAiClientException.class, this::generateOne);
        assertTrue(exception.getMessage().contains("refused"));
    }

    @Test
    void generate_rejectsUnexpectedToolUse() {
        String response = "{\"status\":\"completed\",\"output\":["
                + "{\"type\":\"web_search_call\"},"
                + "{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":"
                + quote(oneDraftJson()) + "}]}]}";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        StrategyTipAiClientException exception = assertThrows(
                StrategyTipAiClientException.class, this::generateOne);
        assertTrue(exception.getMessage().contains("external tool use"));
    }

    @Test
    void generate_rejectsMalformedStructuredOutput() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse("not-json", 1, 1),
                        MediaType.APPLICATION_JSON));

        StrategyTipAiClientException exception = assertThrows(
                StrategyTipAiClientException.class, this::generateOne);
        assertTrue(exception.getMessage().contains("invalid JSON"));
    }

    @Test
    void generate_rejectsUnexpectedDraftCount() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse("{\"drafts\":[]}", 1, 1),
                        MediaType.APPLICATION_JSON));

        StrategyTipAiClientException exception = assertThrows(
                StrategyTipAiClientException.class, this::generateOne);
        assertTrue(exception.getMessage().contains("unexpected draft count"));
    }

    @Test
    void generate_rejectsDraftOutsideAllowedScope() {
        String output = "{\"drafts\":[{\"category\":\"pvt\","
                + "\"content\":\"질럿은 입구에서 길을 막아 시간을 번다\"}]}";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(output, 1, 1),
                        MediaType.APPLICATION_JSON));

        StrategyTipAiClientException exception = assertThrows(
                StrategyTipAiClientException.class, this::generateOne);
        assertTrue(exception.getMessage().contains("allowed category scope"));
    }

    @Test
    void generate_rejectsDuplicateCategories() {
        String output = "{\"drafts\":["
                + "{\"category\":\"zvt\",\"content\":\"뮤탈로 이동 동선을 먼저 확인한다\"},"
                + "{\"category\":\"zvt\",\"content\":\"러커로 진입 경로를 좁혀 수비한다\"}]}";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(output, 1, 1),
                        MediaType.APPLICATION_JSON));

        StrategyTipAiClientException exception = assertThrows(
                StrategyTipAiClientException.class,
                () -> client.generate("system", "source", 2,
                        Arrays.asList("zvt", "pvz")));
        assertTrue(exception.getMessage().contains("duplicate"));
    }

    @Test
    void generate_reportsSanitizedHttpError() {
        server.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"bad request test-openai-key\"}}"));

        StrategyTipAiClientException exception = assertThrows(
                StrategyTipAiClientException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("[redacted]"));
        assertTrue(!exception.getMessage().contains("test-openai-key"));
    }

    @Test
    void generate_saturatesTokenUsage() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(oneDraftJson(),
                                Integer.MAX_VALUE, Integer.MAX_VALUE),
                        MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = generateOne();
        assertEquals(Integer.MAX_VALUE, batch.getInputTokens());
        assertEquals(Integer.MAX_VALUE, batch.getOutputTokens());
    }

    private StrategyTipAiGeneratedBatch generateOne() {
        return client.generate("system rules", "source data", 1,
                Collections.singletonList("zvt"));
    }

    private String oneDraftJson() {
        return "{\"drafts\":[{\"category\":\"zvt\","
                + "\"content\":\"뮤탈로 이동 동선을 먼저 확인한다\"}]}";
    }

    private String threeDraftsJson() {
        return "{\"drafts\":["
                + "{\"category\":\"zvt\",\"content\":\"뮤탈로 이동 동선을 먼저 확인한다\"},"
                + "{\"category\":\"pvz\",\"content\":\"질럿을 좁은 길목에 세워 저글링을 막는다\"},"
                + "{\"category\":\"team_play\",\"content\":\"팀원과 입구를 나눠 막아 초반을 버틴다\"}]}";
    }

    private String completedResponse(String output, int inputTokens, int outputTokens) {
        return "{\"status\":\"completed\",\"model\":\"gpt-5.6-luna\","
                + "\"output\":[{\"type\":\"reasoning\"},"
                + "{\"type\":\"message\",\"content\":["
                + "{\"type\":\"output_text\",\"text\":" + quote(output) + "}]}],"
                + "\"usage\":{\"input_tokens\":" + inputTokens
                + ",\"output_tokens\":" + outputTokens + "}}";
    }

    private String quote(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AssertionError(e);
        }
    }
}
