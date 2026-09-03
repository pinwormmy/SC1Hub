package com.sc1hub.common.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentApiTokenAuthenticatorTest {

    @Test
    void acceptsMatchingBearerTokenOnAnyPath() {
        ContentApiTokenAuthenticator authenticator = new ContentApiTokenAuthenticator("secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/assistant/index/reindex");
        request.addHeader("Authorization", "Bearer secret-token");

        assertTrue(authenticator.hasValidToken(request));
    }

    @Test
    void rejectsWrongTokenAndMissingHeader() {
        ContentApiTokenAuthenticator authenticator = new ContentApiTokenAuthenticator("secret-token");
        MockHttpServletRequest wrong = new MockHttpServletRequest("POST", "/api/assistant/index/reindex");
        wrong.addHeader("Authorization", "Bearer other-token");

        assertFalse(authenticator.hasValidToken(wrong));
        assertFalse(authenticator.hasValidToken(new MockHttpServletRequest("POST", "/api/assistant/index/reindex")));
    }

    @Test
    void rejectsEverythingWhenNoTokenIsConfigured() {
        ContentApiTokenAuthenticator authenticator = new ContentApiTokenAuthenticator("  ");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/assistant/index/reindex");
        request.addHeader("Authorization", "Bearer ");

        assertFalse(authenticator.hasValidToken(request));
    }
}
