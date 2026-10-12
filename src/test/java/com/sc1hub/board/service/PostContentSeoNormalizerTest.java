package com.sc1hub.board.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostContentSeoNormalizerTest {

    private final PostContentSeoNormalizer normalizer = new PostContentSeoNormalizer();

    @Test
    void internalLinksAreRewrittenToTheCanonicalForm() {
        assertEquals("https://sc1hub.com/boards/pvszboard/readPost?postNum=4",
                PostContentSeoNormalizer.normalizeInternalHref("http://www.sc1hub.com/boards/pVsZBoard/readPost?postNum=4"));
        assertEquals("/boards/tvszboard?recentPage=2",
                PostContentSeoNormalizer.normalizeInternalHref("/boards/tVsZBoard/?recentPage=2"));
        assertEquals("https://sc1hub.com/boards/funboard",
                PostContentSeoNormalizer.normalizeInternalHref("https://sc1hub.com/boards/funBoard/"));
        assertEquals("https://sc1hub.com/",
                PostContentSeoNormalizer.normalizeInternalHref("http://sc1hub.com"));
    }

    @Test
    void externalAndLookalikeLinksAreLeftAlone() {
        assertEquals("https://youtu.be/boards/ABC",
                PostContentSeoNormalizer.normalizeInternalHref("https://youtu.be/boards/ABC"));
        assertEquals("https://sc1hub.com.evil.example/boards/X",
                PostContentSeoNormalizer.normalizeInternalHref("https://sc1hub.com.evil.example/boards/X"));
        assertEquals("#top", PostContentSeoNormalizer.normalizeInternalHref("#top"));
    }

    @Test
    void normalizeRewritesOnlyAnchorHrefsAndKeepsOtherMarkup() {
        String html = "<p>https://sc1hub.com/boards/pVsZBoard 참고</p>"
                + "<a class=\"x\" href='https://sc1hub.com/boards/pVsZBoard/readPost?postNum=8&amp;a=1' target=\"_blank\">글</a>";

        assertEquals("<p>https://sc1hub.com/boards/pVsZBoard 참고</p>"
                        + "<a class=\"x\" href='https://sc1hub.com/boards/pvszboard/readPost?postNum=8&amp;a=1' target=\"_blank\">글</a>",
                normalizer.normalize(html, null));
    }

    @Test
    void missingOrBlankAltIsFilledFromThePostTitle() {
        String html = "<img src=\"/a.png\"><img src=\"/b.png\" alt=\"\"/><img alt=\"직접 쓴 설명\" src=\"/c.png\">";

        assertEquals("<img src=\"/a.png\" alt=\"973 &quot;빌드&quot;\">"
                        + "<img src=\"/b.png\" alt=\"973 &quot;빌드&quot; 2\"/>"
                        + "<img alt=\"직접 쓴 설명\" src=\"/c.png\">",
                normalizer.normalize(html, "973 \"빌드\""));
    }

    @Test
    void imagesAreUntouchedWithoutATitle() {
        assertEquals("<img src=\"x\">", normalizer.normalize("<img src=\"x\">", " "));
    }
}
