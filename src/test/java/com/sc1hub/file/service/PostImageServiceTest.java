package com.sc1hub.file.service;

import com.sc1hub.file.dto.PostImageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostImageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void store_resizesAndCompressesImageForPosts() throws Exception {
        PostImageService service = new PostImageService(tempDir.toString(), "");
        MockMultipartFile upload = new MockMultipartFile(
                "upload", "전략 이미지.png", "image/png", noisyPng(1400, 788));

        PostImageResponse response = service.store(upload, "");

        assertTrue(response.getWidth() <= 700);
        assertTrue(response.getHeight() <= 2000);
        assertTrue(response.getBytes() <= PostImageService.TARGET_OUTPUT_BYTES);
        assertTrue(response.getUrl().startsWith("/uploadedImg/"));
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(1L, files.count());
        }
    }

    @Test
    void store_rejectsNonImageBytes() {
        PostImageService service = new PostImageService(tempDir.toString(), "");
        MockMultipartFile upload = new MockMultipartFile(
                "upload", "not-image.jpg", "image/jpeg", "hello".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.store(upload, ""));
    }

    private byte[] noisyPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)).getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
