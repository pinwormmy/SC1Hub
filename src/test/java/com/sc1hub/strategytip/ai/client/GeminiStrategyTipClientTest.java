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
        client = new GeminiStrategyTipClient(restTemplate, properties, new ObjectMapper(),
                new GroundingCitationUrlResolver());
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
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString(
                        "-site:sc1hub.com -site:www.sc1hub.com")))
                .andExpect(jsonPath("$.tools[0].type").value("google_search"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.generation_config.thinking_level").value("minimal"))
                .andExpect(jsonPath("$.generation_config.thinking_summaries").value("none"))
                .andExpect(jsonPath("$.generation_config.max_output_tokens").value(600))
                .andExpect(jsonPath("$.response_format.length()").value(1))
                .andExpect(jsonPath("$.response_format[0].type").value("text"))
                .andExpect(jsonPath("$.response_format[0].mime_type").value("application/json"))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.minItems").value(1))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.maxItems").value(1))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.items"
                        + ".properties.category.enum[0]").value("zvp"))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.items"
                        + ".properties.sourceId.enum[0]").value("zvp:10"))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.items"
                        + ".properties.content.description")
                        .value(org.hamcrest.Matchers.containsString("96")))
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.items"
                        + ".properties.content.minLength").doesNotExist())
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.items"
                        + ".properties.content.maxLength").doesNotExist())
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.items"
                        + ".properties.evidenceSummary.maxLength").doesNotExist())
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.items"
                        + ".properties.externalEvidenceSummary.maxLength").doesNotExist())
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.items"
                        + ".properties.externalSourceUrl").doesNotExist())
                .andExpect(jsonPath("$.response_format[0].schema.properties.drafts.items"
                        + ".properties.externalSourceTitle").doesNotExist())
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
        assertEquals("Trusted Strategy Guide",
                batch.getDrafts().get(0).getExternalSourceTitle());
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
    void generate_rejectsDraftWithoutNativeCitationAnnotation() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponseWithoutCitation(),
                        MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("exactly one native Google Search citation"));
        assertTrue(exception.hasUsage());
        assertEquals(123, exception.getInputTokens());
        assertEquals(52, exception.getOutputTokens());
        assertEquals(1, exception.getSearchQueryCount());
        server.verify();
    }

    @Test
    void generate_usesCitationUrlHostWhenNativeTitleIsMissing() {
        String response = completedResponse(CITATION_URL)
                .replace("\"title\":\"Trusted Strategy Guide\",", "");
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = generateOne();

        assertEquals("example.com", batch.getDrafts().get(0).getExternalSourceTitle());
        server.verify();
    }

    @Test
    void generate_acceptsBroadFullOutputCitationForSingleDraft() {
        String escapedOutput = escapedStructuredOutput();
        String plainOutput = escapedOutput.replace("\\\"", "\"");
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(
                        CITATION_URL, 0, plainOutput.getBytes(StandardCharsets.UTF_8).length,
                        escapedOutput), MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = generateOne();

        assertEquals(CITATION_URL, batch.getDrafts().get(0).getExternalSourceUrl());
        server.verify();
    }

    @Test
    void generate_acceptsSingleDraftCitationWithoutOffsets() {
        String response = completedResponseWithAnnotations(
                "zvp opening -site:sc1hub.com -site:www.sc1hub.com",
                escapedStructuredOutput(),
                citationJsonWithoutOffsets(CITATION_URL, "Trusted Strategy Guide"));
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = generateOne();

        assertEquals(CITATION_URL, batch.getDrafts().get(0).getExternalSourceUrl());
        server.verify();
    }

    @Test
    void generate_acceptsSingleDraftMalformedAndOutOfRangeOffsetsWhenUrlIsUnique() {
        String malformed = "{\"type\":\"url_citation\",\"url\":\"" + CITATION_URL
                + "\",\"title\":\"Trusted Strategy Guide\","
                + "\"start_index\":\"not-a-number\",\"end_index\":{}}";
        String outOfRange = citationJson(
                CITATION_URL, "Trusted Strategy Guide", 999_999, 1_000_000);
        String response = completedResponseWithAnnotations(
                "zvp opening -site:sc1hub.com -site:www.sc1hub.com",
                escapedStructuredOutput(), malformed + "," + outOfRange);
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = generateOne();

        assertEquals(1, batch.getCitationTitlesByUrl().size());
        assertEquals(CITATION_URL, batch.getDrafts().get(0).getExternalSourceUrl());
        server.verify();
    }

    @Test
    void generate_deduplicatesRepeatedSingleDraftCitationsToSameDestination() {
        String escapedOutput = escapedStructuredOutput();
        String plainOutput = escapedOutput.replace("\\\"", "\"");
        int outputBytes = plainOutput.getBytes(StandardCharsets.UTF_8).length;
        String first = citationJson(CITATION_URL, "Trusted Strategy Guide", 0, outputBytes);
        String response = completedResponseWithAnnotations(
                "zvp opening -site:sc1hub.com -site:www.sc1hub.com",
                escapedOutput, first + "," + first);
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = generateOne();

        assertEquals(1, batch.getCitationTitlesByUrl().size());
        assertEquals(CITATION_URL, batch.getDrafts().get(0).getExternalSourceUrl());
        server.verify();
    }

    @Test
    void generate_rejectsTwoDistinctExternalDestinationsForSingleDraft() {
        String escapedOutput = escapedStructuredOutput();
        String plainOutput = escapedOutput.replace("\\\"", "\"");
        int outputBytes = plainOutput.getBytes(StandardCharsets.UTF_8).length;
        String annotations = citationJson(CITATION_URL, "First guide", 0, outputBytes)
                + "," + citationJson("https://liquipedia.net/starcraft/Strategy",
                "Second guide", 0, outputBytes);
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponseWithAnnotations(
                        "zvp opening -site:sc1hub.com -site:www.sc1hub.com",
                        escapedOutput, annotations), MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("exactly one native Google Search citation"));
        server.verify();
    }

    @Test
    void generate_rejectsTwoDistinctExternalDestinationsWithoutOffsetsForSingleDraft() {
        String annotations = citationJsonWithoutOffsets(CITATION_URL, "First guide")
                + "," + citationJsonWithoutOffsets(
                "https://liquipedia.net/starcraft/Strategy", "Second guide");
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponseWithAnnotations(
                        "zvp opening -site:sc1hub.com -site:www.sc1hub.com",
                        escapedStructuredOutput(), annotations), MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("exactly one native Google Search citation"));
        server.verify();
    }

    @Test
    void generate_rejectsSearchQueryWithoutBothSc1hubExclusions() {
        String response = completedResponse(CITATION_URL)
                .replace("zvp opening defense -site:sc1hub.com -site:www.sc1hub.com",
                        "zvp opening defense -site:sc1hub.com");
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("did not exclude SC1Hub"));
        assertEquals(1, exception.getSearchQueryCount());
        server.verify();
    }

    @Test
    void generate_rejectsMoreThanOneActualSearchQueryForSingleDraft() {
        String safeQuery = "zvp opening defense -site:sc1hub.com -site:www.sc1hub.com";
        String response = completedResponse(CITATION_URL)
                .replace("\"queries\":[\"" + safeQuery + "\"]",
                        "\"queries\":[\"" + safeQuery + "\",\"" + safeQuery + "\"]");
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("exactly one safe Google Search query"));
        assertEquals(2, exception.getSearchQueryCount());
        server.verify();
    }

    @Test
    void generate_rejectsDirectClaimThatSc1hubIsExternal() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(
                        "https://sc1hub.com/boards/pvspboard/readPost?postNum=2"),
                        MediaType.APPLICATION_JSON));
        GeminiStrategyTipException direct = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);
        assertTrue(direct.getMessage().contains("unsafe external citation URL"));
        server.verify();
    }

    @Test
    void generate_rejectsTitleClaimThatSc1hubIsExternal() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(CITATION_URL)
                        .replace("Trusted Strategy Guide", "guide.sc1hub.com"),
                        MediaType.APPLICATION_JSON));
        GeminiStrategyTipException title = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);
        assertTrue(title.getMessage().contains("cited SC1Hub"));
        server.verify();
    }

    @Test
    void generate_resolvesTrustedGoogleRedirectAndDoesNotFetchExternalDestination() {
        final int[] fetchCount = {0};
        GroundingCitationUrlResolver resolver = new GroundingCitationUrlResolver(uri -> {
            fetchCount[0]++;
            assertEquals("vertexaisearch.cloud.google.com", uri.getHost());
            return new GroundingCitationUrlResolver.RedirectResponse(
                    302, "https://liquipedia.net/starcraft/Strategy");
        });
        client = new GeminiStrategyTipClient(restTemplate, properties, new ObjectMapper(), resolver);
        String groundingUrl = "https://vertexaisearch.cloud.google.com/"
                + "grounding-api-redirect/test-token";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(groundingUrl), MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = generateOne();

        assertEquals("https://liquipedia.net/starcraft/Strategy",
                batch.getDrafts().get(0).getExternalSourceUrl());
        assertEquals(1, fetchCount[0]);
        server.verify();
    }

    @Test
    void generate_rejectsTrustedGoogleRedirectThatEndsAtSc1hub() {
        GroundingCitationUrlResolver resolver = new GroundingCitationUrlResolver(uri ->
                new GroundingCitationUrlResolver.RedirectResponse(
                        302, "https://www.sc1hub.com/boards/tipboard/readPost?postNum=4"));
        client = new GeminiStrategyTipClient(restTemplate, properties, new ObjectMapper(), resolver);
        String groundingUrl = "https://vertexaisearch.cloud.google.com/"
                + "grounding-api-redirect/test-token";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(groundingUrl), MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class, this::generateOne);

        assertTrue(exception.getMessage().contains("unsafe external citation URL"));
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
        String firstEvidence = "외부 가이드도 같은 초반 대응을 설명합니다.";
        String secondEvidence = "외부 가이드도 방어 위치 조정을 설명합니다.";
        int firstStart = utf8IndexOf(plainOutput, firstEvidence, 0);
        int secondStart = utf8IndexOf(plainOutput, secondEvidence, 0);
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
    void generate_acceptsSingleDraftCitationOutsideTheDraftOutputRange() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(CITATION_URL, 0, 1),
                        MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = generateOne();

        assertEquals(CITATION_URL, batch.getDrafts().get(0).getExternalSourceUrl());
        server.verify();
    }

    @Test
    void generate_acceptsSingleDraftCitationBoundAnywhereInsideThatDraft() {
        String escapedOutput = escapedStructuredOutput();
        String plainOutput = escapedOutput.replace("\\\"", "\"");
        String content = "질럿 압박을 확인하면 입구 수비 병력을 먼저 보강하세요.";
        int citationStart = utf8IndexOf(plainOutput, content, 0);
        int citationEnd = citationStart + content.getBytes(StandardCharsets.UTF_8).length;
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedResponse(
                        CITATION_URL, citationStart, citationEnd, escapedOutput),
                        MediaType.APPLICATION_JSON));

        StrategyTipAiGeneratedBatch batch = generateOne();

        assertEquals(CITATION_URL, batch.getDrafts().get(0).getExternalSourceUrl());
        server.verify();
    }

    @Test
    void generate_rejectsBroadFullOutputCitationForMultipleDrafts() {
        String escapedOutput = escapedTwoDraftStructuredOutput();
        String plainOutput = escapedOutput.replace("\\\"", "\"");
        int citationStart = 0;
        int citationEnd = plainOutput.getBytes(StandardCharsets.UTF_8).length;
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedTwoDraftResponseWithSingleCitation(
                        escapedOutput, citationStart, citationEnd), MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class,
                () -> client.generate("system rules", "source data", 2,
                        Arrays.asList("zvp", "tvp"), Arrays.asList("zvp:10", "tvp:20")));

        assertTrue(exception.getMessage().contains("exactly one native Google Search citation"));
        server.verify();
    }

    @Test
    void generate_rejectsCitationWithoutOffsetsForMultipleDrafts() {
        String escapedOutput = escapedTwoDraftStructuredOutput();
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(completedTwoDraftResponseWithAnnotations(
                        escapedOutput,
                        citationJsonWithoutOffsets(CITATION_URL, "Trusted Strategy Guide")),
                        MediaType.APPLICATION_JSON));

        GeminiStrategyTipException exception = assertThrows(
                GeminiStrategyTipException.class,
                () -> client.generate("system rules", "source data", 2,
                        Arrays.asList("zvp", "tvp"), Arrays.asList("zvp:10", "tvp:20")));

        assertTrue(exception.getMessage().contains("exactly one native Google Search citation"));
        server.verify();
    }

    @Test
    void generate_clampsConfiguredOutputTokenLimit() {
        properties.setMaxOutputTokens(999_999);
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.generation_config.max_output_tokens").value(600))
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
        String citedEvidence = "외부 가이드도 같은 초반 대응을 설명합니다.";
        int citationCharIndex = plainOutput.indexOf(citedEvidence);
        int citationStart = plainOutput.substring(0, citationCharIndex)
                .getBytes(StandardCharsets.UTF_8).length;
        int citationEnd = citationStart + citedEvidence.getBytes(StandardCharsets.UTF_8).length;
        return completedResponse(citationUrl, citationStart, citationEnd, escapedOutput);
    }

    private String completedResponse(String citationUrl, int citationStart, int citationEnd) {
        return completedResponse(citationUrl, citationStart, citationEnd, escapedStructuredOutput());
    }

    private String completedResponseWithoutCitation() {
        String response = completedResponse(CITATION_URL);
        int annotationsStart = response.indexOf("\"annotations\":[");
        int annotationsEnd = response.indexOf("]}]}]", annotationsStart);
        return response.substring(0, annotationsStart)
                + "\"annotations\":[]"
                + response.substring(annotationsEnd + 1);
    }

    private String escapedStructuredOutput() {
        return "{\\\"drafts\\\":[{"
                + "\\\"category\\\":\\\"zvp\\\","
                + "\\\"content\\\":\\\"질럿 압박을 확인하면 입구 수비 병력을 먼저 보강하세요.\\\","
                + "\\\"sourceId\\\":\\\"zvp:10\\\","
                + "\\\"evidenceSummary\\\":\\\"내부 글의 초반 수비 대응을 근거로 삼았습니다.\\\","
                + "\\\"externalEvidenceSummary\\\":\\\"외부 가이드도 같은 초반 대응을 설명합니다.\\\"}]}";
    }

    private String escapedTwoDraftStructuredOutput() {
        return "{\\\"drafts\\\":[{"
                + "\\\"category\\\":\\\"zvp\\\","
                + "\\\"content\\\":\\\"질럿 압박을 확인하면 입구 수비 병력을 먼저 보강하세요.\\\","
                + "\\\"sourceId\\\":\\\"zvp:10\\\","
                + "\\\"evidenceSummary\\\":\\\"내부 글의 초반 수비 대응을 근거로 삼았습니다.\\\","
                + "\\\"externalEvidenceSummary\\\":\\\"외부 가이드도 같은 초반 대응을 설명합니다.\\\"},{"
                + "\\\"category\\\":\\\"tvp\\\","
                + "\\\"content\\\":\\\"상대 병력 이동을 확인한 뒤 방어 위치를 먼저 조정하세요.\\\","
                + "\\\"sourceId\\\":\\\"tvp:20\\\","
                + "\\\"evidenceSummary\\\":\\\"내부 글의 방어 위치 조정을 근거로 삼았습니다.\\\","
                + "\\\"externalEvidenceSummary\\\":\\\"외부 가이드도 방어 위치 조정을 설명합니다.\\\"}]}";
    }

    private int utf8IndexOf(String value, String target, int fromIndex) {
        int charIndex = value.indexOf(target, fromIndex);
        return value.substring(0, charIndex).getBytes(StandardCharsets.UTF_8).length;
    }

    private String completedTwoDraftResponse(String escapedOutput, int firstStart, int secondStart) {
        int firstEvidenceBytes = "외부 가이드도 같은 초반 대응을 설명합니다."
                .getBytes(StandardCharsets.UTF_8).length;
        int secondEvidenceBytes = "외부 가이드도 방어 위치 조정을 설명합니다."
                .getBytes(StandardCharsets.UTF_8).length;
        return "{"
                + "\"status\":\"completed\","
                + "\"model\":\"gemini-3.6-flash\","
                + "\"steps\":["
                + "{\"type\":\"google_search_call\",\"arguments\":{\"queries\":["
                + "\"zvp defense -site:sc1hub.com -site:www.sc1hub.com\","
                + "\"tvp defense -site:sc1hub.com -site:www.sc1hub.com\"]}},"
                + "{\"type\":\"model_output\",\"content\":[{\"type\":\"text\","
                + "\"text\":\"" + escapedOutput + "\","
                + "\"annotations\":["
                + "{\"type\":\"url_citation\",\"url\":\"" + CITATION_URL
                + "\",\"title\":\"Trusted Strategy Guide\",\"start_index\":" + firstStart
                + ",\"end_index\":" + (firstStart + firstEvidenceBytes) + "},"
                + "{\"type\":\"url_citation\",\"url\":\"" + CITATION_URL
                + "\",\"title\":\"Trusted Strategy Guide\",\"start_index\":" + secondStart
                + ",\"end_index\":" + (secondStart + secondEvidenceBytes) + "}]}]}],"
                + "\"usage\":{\"total_input_tokens\":246,\"total_output_tokens\":90,"
                + "\"total_thought_tokens\":14}"
                + "}";
    }

    private String completedTwoDraftResponseWithSingleCitation(
            String escapedOutput, int citationStart, int citationEnd) {
        return completedTwoDraftResponseWithAnnotations(escapedOutput,
                citationJson(CITATION_URL, "Trusted Strategy Guide", citationStart, citationEnd));
    }

    private String completedTwoDraftResponseWithAnnotations(
            String escapedOutput, String annotationsJson) {
        return "{"
                + "\"status\":\"completed\","
                + "\"model\":\"gemini-3.6-flash\","
                + "\"steps\":["
                + "{\"type\":\"google_search_call\",\"arguments\":{\"queries\":["
                + "\"zvp defense -site:sc1hub.com -site:www.sc1hub.com\","
                + "\"tvp defense -site:sc1hub.com -site:www.sc1hub.com\"]}},"
                + "{\"type\":\"model_output\",\"content\":[{\"type\":\"text\","
                + "\"text\":\"" + escapedOutput + "\","
                + "\"annotations\":[" + annotationsJson + "]}]}],"
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
                + "{\"type\":\"google_search_call\",\"arguments\":{\"queries\":["
                + "\"zvp opening defense -site:sc1hub.com -site:www.sc1hub.com\"]}},"
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

    private String completedResponseWithAnnotations(String query, String escapedOutput,
                                                    String annotationsJson) {
        return "{"
                + "\"status\":\"completed\","
                + "\"model\":\"gemini-3.6-flash\","
                + "\"steps\":["
                + "{\"type\":\"google_search_call\",\"arguments\":{\"queries\":[\""
                + query + "\"]}},"
                + "{\"type\":\"model_output\",\"content\":[{\"type\":\"text\","
                + "\"text\":\"" + escapedOutput + "\","
                + "\"annotations\":[" + annotationsJson + "]}]}],"
                + "\"usage\":{\"total_input_tokens\":123,\"total_output_tokens\":45,"
                + "\"total_thought_tokens\":7}"
                + "}";
    }

    private String citationJson(String url, String title, int start, int end) {
        return "{\"type\":\"url_citation\",\"url\":\"" + url
                + "\",\"title\":\"" + title + "\",\"start_index\":" + start
                + ",\"end_index\":" + end + "}";
    }

    private String citationJsonWithoutOffsets(String url, String title) {
        return "{\"type\":\"url_citation\",\"url\":\"" + url
                + "\",\"title\":\"" + title + "\"}";
    }
}
