package com.sc1hub.board.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PostContentLazyLoadInjectorTest {

    private final PostContentLazyLoadInjector injector = new PostContentLazyLoadInjector();

    @Test
    void injectLazyLoading_marksLegacyYoutubeIframeLazy() {
        String html = "<p>본문</p><div class=\"sc-video-embed\">"
                + "<iframe src=\"https://www.youtube.com/embed/abc\" width=\"560\" height=\"315\" frameborder=\"0\" allowfullscreen></iframe>"
                + "</div>";

        String result = injector.injectLazyLoading(html);

        assertEquals("<p>본문</p><div class=\"sc-video-embed\">"
                + "<iframe src=\"https://www.youtube.com/embed/abc\" width=\"560\" height=\"315\" frameborder=\"0\" allowfullscreen loading=\"lazy\"></iframe>"
                + "</div>", result);
    }

    @Test
    void injectLazyLoading_keepsExplicitIframeLoadingAttribute() {
        String html = "<iframe src=\"https://www.youtube.com/embed/abc\" loading=\"eager\"></iframe>"
                + "<IFRAME SRC=\"https://www.youtube-nocookie.com/embed/x\" LOADING='lazy'></IFRAME>";

        assertEquals(html, injector.injectLazyLoading(html));
    }

    @Test
    void injectLazyLoading_firstImageEagerAndFollowingImagesLazy() {
        String html = "<p><img src=\"/uploadedImg/a.jpg\" loading=\"lazy\" width=\"800\" height=\"600\"></p>"
                + "<p><img src=\"/uploadedImg/b.jpg\" width=\"800\" height=\"600\"/></p>"
                + "<p><img src=\"/uploadedImg/c.jpg\" loading=\"eager\"></p>";

        String result = injector.injectLazyLoading(html);

        assertEquals("<p><img src=\"/uploadedImg/a.jpg\" loading=\"eager\" width=\"800\" height=\"600\"></p>"
                + "<p><img src=\"/uploadedImg/b.jpg\" width=\"800\" height=\"600\" loading=\"lazy\"/></p>"
                + "<p><img src=\"/uploadedImg/c.jpg\" loading=\"eager\"></p>", result);
    }

    @Test
    void injectLazyLoading_addsEagerToFirstImageWithoutLoadingAttribute() {
        String result = injector.injectLazyLoading("<img src=\"/uploadedImg/hero.png\">");

        assertEquals("<img src=\"/uploadedImg/hero.png\" loading=\"eager\">", result);
    }

    @Test
    void injectLazyLoading_leavesContentWithoutEmbedsUntouched() {
        String html = "<p>loading=\"lazy\" 라는 글자만 있는 본문</p>";

        assertEquals(html, injector.injectLazyLoading(html));
        assertNull(injector.injectLazyLoading(null));
        assertEquals("", injector.injectLazyLoading(""));
    }
}
