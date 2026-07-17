package com.sc1hub.member.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginKeepAliveViewCoverageTest {

    private static final Path VIEW_ROOT = Paths.get("src/main/webapp/WEB-INF/views");

    @Test
    void loginSensitivePages_includeSharedHeaderKeepAliveScript() throws IOException {
        List<String> viewPaths = Arrays.asList(
                "board/postList.jsp",
                "board/readPost.jsp",
                "board/writePost.jsp",
                "board/modifyPost.jsp",
                "strategyTip/list.jsp",
                "myPage.jsp",
                "modifyMyInfo.jsp",
                "adminPage.jsp",
                "adminOps.jsp",
                "adminAliasDictionary.jsp"
        );

        for (String viewPath : viewPaths) {
            String source = new String(
                    Files.readAllBytes(VIEW_ROOT.resolve(viewPath)), StandardCharsets.UTF_8);
            assertTrue(source.contains("header.jspf"),
                    () -> viewPath + " must include the shared header login keep-alive script");
        }
    }

    @Test
    void sharedHeader_loadsLoginKeepAliveScript() throws IOException {
        String source = new String(
                Files.readAllBytes(VIEW_ROOT.resolve("include/header.jspf")), StandardCharsets.UTF_8);

        assertTrue(source.contains("/js/site-header.js"));
    }

    @Test
    void loginKeepAliveTracksCkeditorActivityForLongPostEditing() throws IOException {
        Path scriptPath = Paths.get("src/main/resources/static/js/site-header.js");
        String source = new String(Files.readAllBytes(scriptPath), StandardCharsets.UTF_8);

        assertTrue(source.contains("CKEDITOR"));
        assertTrue(source.contains("instanceReady"));
    }
}
