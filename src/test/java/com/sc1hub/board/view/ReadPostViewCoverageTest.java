package com.sc1hub.board.view;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadPostViewCoverageTest {

    private static final Path READ_POST_SCRIPT = Paths.get("src/main/resources/static/js/readPost.js");

    @Test
    void commentsRenderUntrustedFieldsAsTextAndUseServerDeletePermissions() throws Exception {
        String source = new String(Files.readAllBytes(READ_POST_SCRIPT), StandardCharsets.UTF_8);

        assertTrue(source.contains("document.createDocumentFragment()"));
        assertTrue(source.contains("content.textContent = comment.content || \"\""));
        assertTrue(source.contains("document.createTextNode(resolveCommentNickname(comment)"));
        assertTrue(source.contains("commentList.replaceChildren(fragment)"));
        assertTrue(source.contains("if (comment.deletable)"));
        assertTrue(source.contains("comment.passwordRequired"));
        assertFalse(source.contains("comment.guestComment"));
        assertTrue(source.contains("formBody.set(\"password\", password)"));
        assertTrue(source.contains("application/x-www-form-urlencoded"));
        assertTrue(source.contains("body: formBody"));
        assertFalse(source.contains("deleteComment?"));
        assertFalse(source.contains("commentListHtml"));
        assertFalse(source.contains("javascript:pageSettingAndLoadComment"));
        assertFalse(source.contains("id: memberId"));
    }
}
