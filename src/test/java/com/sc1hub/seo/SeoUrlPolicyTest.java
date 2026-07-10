package com.sc1hub.seo;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SeoUrlPolicyTest {

    private final SeoUrlPolicy policy = new SeoUrlPolicy();

    @Test
    void resolve_keepsOnlyPostIdentityInCanonicalQuery() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/boards/zvspboard/readPost");
        request.addParameter("postNum", "002");
        request.addParameter("utm_source", "newsletter");

        SeoUrlPolicy.ResolvedUrl resolved = policy.resolve(request);

        assertEquals("https://sc1hub.com/boards/zvspboard/readPost?postNum=2", resolved.getCanonical());
        assertNull(resolved.getRedirectLocation());
    }

    @Test
    void resolve_preservesCategoryAndNonFirstPagination() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/strategy-tips");
        request.addParameter("category", "Z_VS_T");
        request.addParameter("recentPage", "3");

        SeoUrlPolicy.ResolvedUrl resolved = policy.resolve(request);

        assertEquals("https://sc1hub.com/strategy-tips?category=z_vs_t&recentPage=3",
                resolved.getCanonical());
    }

    @Test
    void resolve_stripsFirstPageParameterFromBoardCanonical() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boards/tipboard");
        request.addParameter("recentPage", "1");

        assertEquals("https://sc1hub.com/boards/tipboard", policy.resolve(request).getCanonical());
    }
}
