package com.sc1hub.board.view;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostEditorViewCoverageTest {

    private static final Path VIEW_ROOT = Paths.get("src/main/webapp/WEB-INF/views");
    private static final Path STATIC_ROOT = Paths.get("src/main/resources/static");

    @Test
    void writeAndModifyViewsLoadNativeEditorAssetsWithoutCkeditor() throws Exception {
        String writeView = read(VIEW_ROOT.resolve("board/writePost.jsp"));
        String modifyView = read(VIEW_ROOT.resolve("board/modifyPost.jsp"));

        assertTrue(writeView.contains("/css/post-editor.css"));
        assertTrue(writeView.contains("/js/post-editor.js"));
        assertTrue(modifyView.contains("/css/post-editor.css"));
        assertTrue(modifyView.contains("/js/post-editor.js"));
        assertFalse(writeView.toLowerCase().contains("ckeditor"));
        assertFalse(modifyView.toLowerCase().contains("ckeditor"));
    }

    @Test
    void sharedEditorProvidesVisualSourcePreviewImageAndDraftControls() throws Exception {
        String fragment = read(VIEW_ROOT.resolve("include/postEditorContent.jspf"));
        String script = read(STATIC_ROOT.resolve("js/post-editor.js"));

        assertTrue(fragment.contains("contenteditable=\"true\""));
        assertTrue(fragment.contains("data-editor-mode=\"source\""));
        assertTrue(fragment.contains("data-editor-mode=\"preview\""));
        assertTrue(fragment.contains("data-editor-image-input"));
        assertTrue(script.contains("localStorage"));
        assertTrue(script.contains("uploadImage"));
        assertTrue(script.contains("youtubeEmbedUrl"));
        assertTrue(script.contains("safePreviewHtml"));
    }

    private String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
