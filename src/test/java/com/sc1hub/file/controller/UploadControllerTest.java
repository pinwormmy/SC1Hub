package com.sc1hub.file.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void ckSubmit_recoversUtf8FileNameFromRawQueryString() throws Exception {
        String uid = "81469f62-87c3-4b41-8832-eb089fa50414";
        String fileName = "ssb 사건.webp";
        byte[] imageBytes = new byte[] { 1, 2, 3, 4 };
        Files.write(tempDir.resolve(uid + "_" + fileName), imageBytes);

        UploadController controller = new UploadController();
        ReflectionTestUtils.setField(controller, "uploadPath", tempDir.toString());
        ReflectionTestUtils.setField(controller, "imageUploadPath", "");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ckImgSubmit");
        request.setQueryString("uid=" + uid + "&fileName=" + encode(fileName));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ckSubmit(uid, "ssb ???.webp", request, response);

        assertEquals(200, response.getStatus());
        assertArrayEquals(imageBytes, response.getContentAsByteArray());
    }

    @Test
    void decodeRequestParameter_decodesRawQueryBeforeFallback() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ckImgSubmit");
        request.setQueryString("uid=u1&fileName=" + encode("간호사도 궁금해하는 럴커.jpg"));

        String result = UploadController.decodeRequestParameter(request, "fileName", "broken");

        assertEquals("간호사도 궁금해하는 럴커.jpg", result);
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }
}
