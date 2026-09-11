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

    @Test
    void sanitize_allowsSoopLiveAndVodPlayersAndWrapsBareIframes() {
        String html = "<iframe src=\"https://play.sooplive.co.kr/afreeca/embed\" width=\"640\" height=\"360\" frameborder=\"0\"></iframe>"
                + "<p><iframe src=\"https://vod.sooplive.co.kr/player/79858588/embed?autoPlay=false&amp;showChat=true\"></iframe></p>"
                + "<iframe src=\"https://vod.afreecatv.com/player/12345/embed\"></iframe>"
                + "<iframe src=\"http://play.sooplive.co.kr/afreeca/embed\"></iframe>"
                + "<iframe src=\"https://play.sooplive.co.kr.evil.com/afreeca/embed\"></iframe>";

        String result = sanitizer.sanitize(html);

        assertTrue(result.contains("<div class=\"sc-video-embed\"><iframe src=\"https://play.sooplive.co.kr/afreeca/embed\""));
        assertTrue(result.contains("vod.sooplive.co.kr/player/79858588/embed"));
        assertTrue(result.contains("vod.afreecatv.com/player/12345/embed"));
        assertFalse(result.contains("width=\"640\""));
        assertFalse(result.contains("http://play.sooplive.co.kr"));
        assertFalse(result.contains("evil.com"));
        assertTrue(result.split("sc-video-embed", -1).length - 1 == 3);
    }

    @Test
    void sanitize_doesNotDoubleWrapEditorEmbeds() {
        String html = "<div class=\"sc-video-embed\"><iframe src=\"https://www.youtube-nocookie.com/embed/abc\"></iframe></div>";

        String result = sanitizer.sanitize(html);

        assertTrue(result.split("sc-video-embed", -1).length - 1 == 1);
        assertTrue(result.contains("loading=\"lazy\""));
    }
}
