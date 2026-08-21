package com.sc1hub.board.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostContentSanitizerTest {

    private final PostContentSanitizer sanitizer = new PostContentSanitizer();

    @Test
    void sanitize_keepsEditorMarkupAndRemovesScriptsAndEventHandlers() {
        String html = "<h2>빌드</h2><p onclick=\"alert(1)\"><strong>본문</strong>"
                + "<script>alert(2)</script></p><img src=\"/uploadedImg/a.jpg\" onerror=\"alert(3)\">";

        String result = sanitizer.sanitize(html);

        assertTrue(result.contains("<h2>빌드</h2>"));
        assertTrue(result.contains("<strong>본문</strong>"));
        assertTrue(result.contains("src=\"/uploadedImg/a.jpg\""));
        assertFalse(result.contains("script"));
        assertFalse(result.contains("onclick"));
        assertFalse(result.contains("onerror"));
    }

    @Test
    void sanitize_allowsYoutubeAndRemovesOtherIframes() {
        String html = "<div class=\"sc-video-embed bad-class\"><iframe src=\"https://www.youtube.com/embed/abc\"></iframe></div>"
                + "<iframe src=\"https://example.com/embed/abc\"></iframe>";

        String result = sanitizer.sanitize(html);

        assertTrue(result.contains("youtube.com/embed/abc"));
        assertTrue(result.contains("sc-video-embed"));
        assertFalse(result.contains("bad-class"));
        assertFalse(result.contains("example.com"));
    }
}
