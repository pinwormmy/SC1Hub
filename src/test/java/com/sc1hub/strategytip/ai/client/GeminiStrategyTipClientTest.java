package com.sc1hub.strategytip.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.strategytip.ai.config.StrategyTipAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/interactions";
    private static final String CITATION_URL = "https://example.com/strategy";

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
        client = new GeminiStrategyTipClient(restTemplate, properties, new ObjectMapper());
    }

    @Test
    void generate_sendsGroundedStructuredRequestAndParsesNativeCitationsAndUsage() {
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-gemini-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("gemini-3.6-flash"))
                .andExpect(jsonPath("$.system_instruction").value("system rules"))
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString(
                        "minimumGoogleSearchQueries\":1")))
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString(
                        "maximumGoogleSearchQueries\":1")))
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString(
                        "execute exactly 1 queries total")))
                .andExpect(jsonPath("$.tools[0].type").value("google_search"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.generation_config.thinking_level").value("low"))
                .andExpect(jsonPath("$.generation_config.max_output_tokens").value(1200))
                .andExpect(jsonPath("$.response_format.length()").value(1))
                .andExpect(jsonPath("$.response_format[0].type").value("text"))
                .andExpect(jsonPath("$.response_format[0].mime_type").value("application/json"))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.minItems").value(1))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.maxItems").value(1))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.items"
                        + ".properties.category.enum[0]").value("zvp"))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.items"
                        + ".properties.sourceId.enum[0]").value("zvp:10"))
                .andRespond(withSuccess(completedResponse(CITATION_URL), MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = client.generate(
                "system rules", "source data", 1,
                Collections.singletonList("zvp"), Collections.singletonList("zvp:10"));

        assertEquals("gemini-3.6-flash", batch.getModel());
        assertEquals(123, batch.getInputTokens());
        assertEquals(52, batch.getOutputTokens());
        assertEquals(1, batch.getSearchQueryCount());
        assertTrue(batch.hasCitation(CITATION_URL));
        assertEquals("Trusted Strategy Guide", batch.citationTitle(CITATION_URL));
        assertEquals(1, batch.getDrafts().size());
        assertEquals("zvp", batch.getDrafts().get(0).getCategory());
        assertEquals(CITATION_URL, batch.getDrafts().get(0).getExternalSourceUrl());
        server.verify();
    }

    @Test
    void generate_rejectsDisabledOrMissingKeyWithoutCallingApi() {
        properties.setEnabled(false);
        assertThrows(GeminiStrategyTipException.class, () -> generateOne());

        properties.setEnabled(true);
        properties.setAllowLiveCalls(false);
        assertThrows(GeminiStrategyTipException.class, () -> generateOne());

        properties.setAllowLiveCalls(true);
        properties.setApiKey(" ");
        assertThrows(GeminiStrategyTipException.class, () -> generateOne());
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
    void generate_rejectsIncompleteInteraction() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("{\"status\":\"incomplete\"}", MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("incomplete"));
        server.verify();
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
        server.verify();
    }

    @Test
    void generate_rejectsExternalUrlThatIsNotInNativeCitationAnnotations() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse("https://different.example/source"),
                        MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("without a native Google Search citation"));
        assertTrue(exception.hasUsage());
        assertEquals(123, exception.getInputTokens());
        assertEquals(52, exception.getOutputTokens());
        assertEquals(1, exception.getSearchQueryCount());
        server.verify();
    }

    @Test
    void generate_preservesUsageWhenStructuredOutputCannotBeParsed() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(
                        CITATION_URL, 0, 1, "{not-json"), MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("invalid JSON"));
        assertTrue(exception.hasUsage());
        assertEquals(123, exception.getInputTokens());
        assertEquals(52, exception.getOutputTokens());
        assertEquals(1, exception.getSearchQueryCount());
        server.verify();
    }

    @Test
    void generate_saturatesBilledOutputTokensAtIntegerMaximum() {
        String response = completedResponse(CITATION_URL)
                .replace("\"total_output_tokens\":45,\"total_thought_tokens\":7",
                        "\"total_output_tokens\":2147483647,\"total_thought_tokens\":1");
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = generateOne();

        assertEquals(Integer.MAX_VALUE, batch.getOutputTokens());
        server.verify();
    }

    @Test
    void generate_allowsSameExternalUrlWhenEachDraftHasItsOwnNativeCitation() {
        String escapedOutput = escapedTwoDraftStructuredOutput();
        String plainOutput = escapedOutput.replace("\\\"", "\"");
        int firstStart = utf8IndexOf(plainOutput, CITATION_URL, 0);
        int secondStart = utf8IndexOf(plainOutput, CITATION_URL,
                plainOutput.indexOf(CITATION_URL) + CITATION_URL.length());
        String response = completedTwoDraftResponse(escapedOutput, firstStart, secondStart);
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = client.generate(
                "system rules", "source data", 2,
                Arrays.asList("zvp", "tvp"), Arrays.asList("zvp:10", "tvp:20"));

        assertEquals(2, batch.getDrafts().size());
        assertEquals(CITATION_URL, batch.getDrafts().get(0).getExternalSourceUrl());
        assertEquals(CITATION_URL, batch.getDrafts().get(1).getExternalSourceUrl());
        server.verify();
    }

    @Test
    void generate_rejectsCitationOutsideTheDraftOutputRange() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(CITATION_URL, 0, 1),
                        MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("bound to that draft"));
        server.verify();
    }

    @Test
    void generate_clampsConfiguredOutputTokenLimit() {
        properties.setMaxOutputTokens(999_999);
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.generation_config.max_output_tokens").value(1200))
                .andRespond(withSuccess(completedResponse(CITATION_URL), MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = generateOne();

        assertEquals(1, batch.getDrafts().size());
        server.verify();
    }

    @Test
    void generate_truncatesAndRedactsHttpErrorDetails() {
        String longMessage = "test-gemini-key system rules source data "
                + String.join("", Collections.nCopies(600, "x"));
        String body = "{\"error\":{\"message\":\"" + longMessage + "\"}}";
        server.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().length() < 400);
        assertTrue(exception.getMessage().contains("..."));
        assertFalse(exception.getMessage().contains("test-gemini-key"));
        assertFalse(exception.getMessage().contains("system rules"));
        assertFalse(exception.getMessage().contains("source data"));
        server.verify();
    }

    private StrategyTipAiGeneratedBatch generateOne() {
        return client.generate("system rules", "source data", 1,
                Collections.singletonList("zvp"), Collections.singletonList("zvp:10"));
    }

    private String completedResponse(String citationUrl) {
        String escapedOutput = escapedStructuredOutput();
        String plainOutput = escapedOutput.replace("\\\"", "\"");
        int citationCharIndex = plainOutput.indexOf(CITATION_URL);
        int citationStart = plainOutput.substring(0, citationCharIndex)
                .getBytes(StandardCharsets.UTF_8).length;
        int citationEnd = citationStart + CITATION_URL.getBytes(StandardCharsets.UTF_8).length;
        return completedResponse(citationUrl, citationStart, citationEnd, escapedOutput);
    }

    private String completedResponse(String citationUrl, int citationStart, int citationEnd) {
        return completedResponse(citationUrl, citationStart, citationEnd, escapedStructuredOutput());
    }

    private String escapedStructuredOutput() {
        return "{\\\"drafts\\\":[{"
                + "\\\"category\\\":\\\"zvp\\\","
                + "\\\"content\\\":\\\"질럿 압박을 확인하면 입구 수비 병력을 먼저 보강하세요.\\\","
                + "\\\"sourceId\\\":\\\"zvp:10\\\","
                + "\\\"evidenceSummary\\\":\\\"내부 글의 초반 수비 대응을 근거로 삼았습니다.\\\","
                + "\\\"externalSourceUrl\\\":\\\"" + CITATION_URL + "\\\","
                + "\\\"externalSourceTitle\\\":\\\"Trusted Strategy Guide\\\","
                + "\\\"externalEvidenceSummary\\\":\\\"외부 가이드도 같은 초반 대응을 설명합니다.\\\"}]}";
    }

    private String escapedTwoDraftStructuredOutput() {
        return "{\\\"drafts\\\":[{"
                + "\\\"category\\\":\\\"zvp\\\","
                + "\\\"content\\\":\\\"질럿 압박을 확인하면 입구 수비 병력을 먼저 보강하세요.\\\","
                + "\\\"sourceId\\\":\\\"zvp:10\\\","
                + "\\\"evidenceSummary\\\":\\\"내부 글의 초반 수비 대응을 근거로 삼았습니다.\\\","
                + "\\\"externalSourceUrl\\\":\\\"" + CITATION_URL + "\\\","
                + "\\\"externalSourceTitle\\\":\\\"Trusted Strategy Guide\\\","
                + "\\\"externalEvidenceSummary\\\":\\\"외부 가이드도 같은 초반 대응을 설명합니다.\\\"},{"
                + "\\\"category\\\":\\\"tvp\\\","
                + "\\\"content\\\":\\\"상대 병력 이동을 확인한 뒤 방어 위치를 먼저 조정하세요.\\\","
                + "\\\"sourceId\\\":\\\"tvp:20\\\","
                + "\\\"evidenceSummary\\\":\\\"내부 글의 방어 위치 조정을 근거로 삼았습니다.\\\","
                + "\\\"externalSourceUrl\\\":\\\"" + CITATION_URL + "\\\","
                + "\\\"externalSourceTitle\\\":\\\"Trusted Strategy Guide\\\","
                + "\\\"externalEvidenceSummary\\\":\\\"외부 가이드도 방어 위치 조정을 설명합니다.\\\"}]}";
    }

    private int utf8IndexOf(String value, String target, int fromIndex) {
        int charIndex = value.indexOf(target, fromIndex);
        return value.substring(0, charIndex).getBytes(StandardCharsets.UTF_8).length;
    }

    private String completedTwoDraftResponse(String escapedOutput, int firstStart, int secondStart) {
        int urlBytes = CITATION_URL.getBytes(StandardCharsets.UTF_8).length;
        return "{"
                + "\"status\":\"completed\","
                + "\"model\":\"gemini-3.6-flash\","
                + "\"steps\":["
                + "{\"type\":\"google_search_call\",\"arguments\":{\"queries\":[\"zvp defense\",\"tvp defense\"]}},"
                + "{\"type\":\"model_output\",\"content\":[{\"type\":\"text\","
                + "\"text\":\"" + escapedOutput + "\","
                + "\"annotations\":["
                + "{\"type\":\"url_citation\",\"url\":\"" + CITATION_URL
                + "\",\"title\":\"Trusted Strategy Guide\",\"start_index\":" + firstStart
                + ",\"end_index\":" + (firstStart + urlBytes) + "},"
                + "{\"type\":\"url_citation\",\"url\":\"" + CITATION_URL
                + "\",\"title\":\"Trusted Strategy Guide\",\"start_index\":" + secondStart
                + ",\"end_index\":" + (secondStart + urlBytes) + "}]}]}],"
                + "\"usage\":{\"total_input_tokens\":246,\"total_output_tokens\":90,"
                + "\"total_thought_tokens\":14}"
                + "}";
    }

    private String completedResponse(String citationUrl, int citationStart,
                                     int citationEnd, String escapedOutput) {
        return "{"
                + "\"status\":\"completed\","
                + "\"model\":\"gemini-3.6-flash\","
                + "\"steps\":["
                + "{\"type\":\"google_search_call\",\"arguments\":{\"queries\":[\"zvp opening defense\"]}},"
                + "{\"type\":\"model_output\",\"content\":[{\"type\":\"text\","
                + "\"text\":\"" + escapedOutput + "\","
                + "\"annotations\":[{\"type\":\"url_citation\","
                + "\"url\":\"" + citationUrl + "\",\"title\":\"Trusted Strategy Guide\","
                + "\"start_index\":" + citationStart + ",\"end_index\":" + citationEnd
                + "}]}]}],"
                + "\"usage\":{\"total_input_tokens\":123,\"total_output_tokens\":45,"
                + "\"total_thought_tokens\":7}"
                + "}";
    }
}
