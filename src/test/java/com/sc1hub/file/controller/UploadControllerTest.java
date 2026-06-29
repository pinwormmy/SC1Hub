package com.sc1hub.file.controller;

import com.sc1hub.file.util.UploadedImageFileNameUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void ckSubmit_recoversUtf8FileNameFromRawQueryString() throws Exception {
        String uid = "81469f62-87c3-4b41-8832-eb089fa50414";
        String fileName = "ssb 사건.webp";
        String storedFileName = UploadedImageFileNameUtil.toStoredFileName(fileName);
        byte[] imageBytes = new byte[] { 1, 2, 3, 4 };
        Files.write(tempDir.resolve(uid + "_" + storedFileName), imageBytes);

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
    void ckSubmit_recoversGarbledFileNameFromUidWhenOnlyOneUploadMatches() throws Exception {
        String uid = "81469f62-87c3-4b41-8832-eb089fa50414";
        byte[] imageBytes = new byte[] { 5, 6, 7, 8 };
        Files.write(tempDir.resolve(uid + "_간호사도 궁금해하는 럴커.jpg"), imageBytes);

        UploadController controller = new UploadController();
        ReflectionTestUtils.setField(controller, "uploadPath", tempDir.toString());
        ReflectionTestUtils.setField(controller, "imageUploadPath", "");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ckImgSubmit");
        request.setQueryString("uid=" + uid + "&fileName=broken.jpg");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ckSubmit(uid, "broken.jpg", request, response);

        assertEquals(200, response.getStatus());
        assertArrayEquals(imageBytes, response.getContentAsByteArray());
    }

    @Test
    void imgSubmit_usesSameRecoveryAsCkSubmitForLegacyImgLinks() throws Exception {
        String uid = "81469f62-87c3-4b41-8832-eb089fa50414";
        byte[] imageBytes = new byte[] { 9, 10, 11, 12 };
        Files.write(tempDir.resolve(uid + "_드라군 심시티.jpg"), imageBytes);

        UploadController controller = new UploadController();
        ReflectionTestUtils.setField(controller, "uploadPath", tempDir.toString());
        ReflectionTestUtils.setField(controller, "imageUploadPath", "");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/img/" + uid + "_broken.jpg");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.imgSubmit(uid + "_broken.jpg", request, response);

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

    @Test
    void imageUpload_storesAsciiSafeFileName() throws Exception {
        UploadController controller = new UploadController();
        ReflectionTestUtils.setField(controller, "uploadPath", tempDir.toString());
        ReflectionTestUtils.setField(controller, "imageUploadPath", "");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/imageUpload");
        MockMultipartFile upload = new MockMultipartFile(
                "upload",
                "뮤탈과 바이오닉 심슨버전.jpg",
                "image/jpeg",
                new byte[] { 9, 8, 7 }
        );

        Map<String, Object> body = controller.imageUpload(request, upload).getBody();

        assertNotNull(body);
        assertTrue(body.get("fileName").toString().matches("[A-Za-z0-9_-]+\\.jpg"));
        assertTrue(body.get("url").toString().contains("/ckImgSubmit?uid="));
        try (Stream<Path> stream = Files.list(tempDir)) {
            assertEquals(1L, stream.count());
        }
        try (Stream<Path> stream = Files.list(tempDir)) {
            String savedName = stream.findFirst().get().getFileName().toString();
            assertTrue(savedName.matches("[0-9a-f-]{36}_[A-Za-z0-9_-]+\\.jpg"));
        }
    }

    @Test
    void ckSubmit_redirectsWithoutDoubleEncodingStoredFileName() throws Exception {
        String uid = "81469f62-87c3-4b41-8832-eb089fa50414";
        String storedFileName = UploadedImageFileNameUtil.toStoredFileName("뮤탈과 바이오닉 심슨버전.jpg");

        UploadController controller = new UploadController();
        ReflectionTestUtils.setField(controller, "uploadPath", tempDir.toString());
        ReflectionTestUtils.setField(controller, "imageUploadPath", "");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ckImgSubmit");
        request.setQueryString("uid=" + uid + "&fileName=" + encode(storedFileName));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ckSubmit(uid, storedFileName, request, response);

        assertEquals(302, response.getStatus());
        assertEquals("/uploadedImg/" + uid + "_" + storedFileName, response.getRedirectedUrl());
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }
}
