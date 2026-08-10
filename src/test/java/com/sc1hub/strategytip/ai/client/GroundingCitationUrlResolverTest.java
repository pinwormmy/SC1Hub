package com.sc1hub.strategytip.ai.client;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroundingCitationUrlResolverTest {

    private static final String GROUNDING_URL = "https://vertexaisearch.cloud.google.com/"
            + "grounding-api-redirect/test-token";

    @Test
    void resolveDestination_acceptsSafeDirectHttpsUrlWithoutFetchingIt() {
        GroundingCitationUrlResolver resolver = new GroundingCitationUrlResolver(uri -> {
            throw new AssertionError("A direct external citation must not be fetched.");
        });

        GroundingCitationUrlResolver.ResolvedDestination destination =
                resolver.resolveDestination("https://liquipedia.net/starcraft/Strategy#section");

        assertEquals("https://liquipedia.net/starcraft/Strategy", destination.getUrl());
        assertEquals("Liquipedia strategy",
                resolver.safeDisplayTitle(destination, "  Liquipedia\nstrategy  "));
        assertEquals("https://example.com/a%20b",
                resolver.resolveDestination("https://example.com/a%20b").getUrl());
    }

    @Test
    void resolveDestination_rejectsUnsafeDirectHostsAndSchemes() {
        GroundingCitationUrlResolver resolver = new GroundingCitationUrlResolver(uri -> {
            throw new AssertionError("Unsafe citation must be rejected before fetching.");
        });
        List<String> unsafeUrls = Arrays.asList(
                "http://example.com/guide",
                "https://sc1hub.com/guide",
                "https://cdn.sc1hub.com/guide",
                "https://localhost/guide",
                "https://service.local/guide",
                "https://127.0.0.1/guide",
                "https://2130706433/guide",
                "https://[::1]/guide",
                "https://user@example.com/guide",
                "https://example.com:8443/guide");

        for (String unsafeUrl : unsafeUrls) {
            assertThrows(GeminiStrategyTipException.class,
                    () -> resolver.resolveDestination(unsafeUrl), unsafeUrl);
        }
    }

    @Test
    void resolveDestination_followsOnlyTrustedGoogleRedirectHops() {
        List<URI> fetched = new ArrayList<>();
        GroundingCitationUrlResolver resolver = new GroundingCitationUrlResolver(uri -> {
            fetched.add(uri);
            if (fetched.size() == 1) {
                return new GroundingCitationUrlResolver.RedirectResponse(302,
                        "/grounding-api-redirect/second-token");
            }
            return new GroundingCitationUrlResolver.RedirectResponse(307,
                    "https://liquipedia.net/starcraft/Strategy");
        });

        GroundingCitationUrlResolver.ResolvedDestination destination =
                resolver.resolveDestination(GROUNDING_URL);

        assertEquals("https://liquipedia.net/starcraft/Strategy", destination.getUrl());
        assertEquals(2, fetched.size());
        assertEquals("vertexaisearch.cloud.google.com", fetched.get(0).getHost());
        assertEquals("vertexaisearch.cloud.google.com", fetched.get(1).getHost());
    }

    @Test
    void resolveDestination_rejectsBadMissingAndLoopingGoogleRedirects() {
        GroundingCitationUrlResolver badStatus = new GroundingCitationUrlResolver(uri ->
                new GroundingCitationUrlResolver.RedirectResponse(200,
                        "https://example.com/guide"));
        GroundingCitationUrlResolver missingLocation = new GroundingCitationUrlResolver(uri ->
                new GroundingCitationUrlResolver.RedirectResponse(302, " "));
        GroundingCitationUrlResolver loop = new GroundingCitationUrlResolver(uri ->
                new GroundingCitationUrlResolver.RedirectResponse(302, GROUNDING_URL));

        assertThrows(GeminiStrategyTipException.class,
                () -> badStatus.resolveDestination(GROUNDING_URL));
        assertThrows(GeminiStrategyTipException.class,
                () -> missingLocation.resolveDestination(GROUNDING_URL));
        assertThrows(GeminiStrategyTipException.class,
                () -> loop.resolveDestination(GROUNDING_URL));
    }

    @Test
    void resolveDestination_rejectsUntrustedGooglePathAndUnsafeFinalDestination() {
        GroundingCitationUrlResolver resolver = new GroundingCitationUrlResolver(uri ->
                new GroundingCitationUrlResolver.RedirectResponse(302,
                        "https://admin.sc1hub.com/private"));

        assertThrows(GeminiStrategyTipException.class,
                () -> resolver.resolveDestination(
                        "https://vertexaisearch.cloud.google.com/not-grounding/token"));
        assertThrows(GeminiStrategyTipException.class,
                () -> resolver.resolveDestination(GROUNDING_URL));
    }

    @Test
    void resolveDestination_rejectsGoogleRedirectOrSearchPageAsFinalSource() {
        GroundingCitationUrlResolver finalGoogleRedirect = new GroundingCitationUrlResolver(uri ->
                new GroundingCitationUrlResolver.RedirectResponse(302,
                        "https://www.google.com/url?q=https%3A%2F%2Fexample.com%2Fguide"));

        assertThrows(GeminiStrategyTipException.class,
                () -> finalGoogleRedirect.resolveDestination(GROUNDING_URL));
        assertThrows(GeminiStrategyTipException.class,
                () -> finalGoogleRedirect.resolveDestination(
                        "https://www.google.com/search?q=starcraft+guide"));
    }
}
