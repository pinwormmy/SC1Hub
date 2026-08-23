package com.sc1hub.content.service;

import com.sc1hub.file.dto.PostImageResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentPostComposerTest {

    private final ContentPostComposer composer = new ContentPostComposer();

    @Test
    void compose_placesImageAtTopAndYoutubeAtBottom() {
        PostImageResponse image = new PostImageResponse(
                "sample.jpg", "/uploadedImg/sample.jpg", "image/jpeg", 700, 394, 123_000);

        String html = composer.compose("제목", "<p>첫 단락</p><p>둘째 단락</p>", image,
                "웃긴 참고 이미지", "이미지 설명", "https://youtu.be/vi36jGm_cgw", "추천 영상");

        assertTrue(html.startsWith("<figure class=\"sc-post-image\">"));
        assertTrue(html.contains("alt=\"웃긴 참고 이미지\""));
        assertTrue(html.contains("width=\"700\" height=\"394\""));
        assertTrue(html.contains("<figcaption>이미지 설명</figcaption>"));
        assertTrue(html.contains("<p>첫 단락</p><p>둘째 단락</p>"));
        assertTrue(html.contains("src=\"https://www.youtube-nocookie.com/embed/vi36jGm_cgw\""));
        assertTrue(html.contains("width=\"100%\""));
        assertTrue(html.contains("class=\"sc-video-source\""));
        assertTrue(html.contains("href=\"https://www.youtube.com/watch?v=vi36jGm_cgw\""));
        assertTrue(html.indexOf("sc-post-image") < html.indexOf("<p>첫 단락</p>"));
        assertTrue(html.indexOf("<p>둘째 단락</p>") < html.indexOf("sc-video-embed"));
        assertTrue(html.indexOf("sc-video-embed") < html.indexOf("sc-video-source"));
        assertTrue(html.endsWith("</div>"));
    }

    @Test
    void toYoutubeEmbedUrl_supportsWatchShortsAndEmbedUrls() {
        assertEquals("https://www.youtube-nocookie.com/embed/vi36jGm_cgw",
                composer.toYoutubeEmbedUrl("https://www.youtube.com/watch?v=vi36jGm_cgw"));
        assertEquals("https://www.youtube-nocookie.com/embed/vi36jGm_cgw",
                composer.toYoutubeEmbedUrl("https://www.youtube.com/shorts/vi36jGm_cgw"));
        assertEquals("https://www.youtube-nocookie.com/embed/vi36jGm_cgw",
                composer.toYoutubeEmbedUrl("https://www.youtube-nocookie.com/embed/vi36jGm_cgw"));
        assertEquals("https://www.youtube.com/watch?v=vi36jGm_cgw",
                composer.toYoutubeWatchUrl("https://youtu.be/vi36jGm_cgw?feature=shared"));
    }

    @Test
    void toYoutubeEmbedUrl_rejectsNonYoutubeHosts() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> composer.toYoutubeEmbedUrl("https://example.com/watch?v=vi36jGm_cgw"));

        assertEquals("올바른 유튜브 주소를 입력해주세요.", error.getMessage());
    }
}
