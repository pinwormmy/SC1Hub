package com.sc1hub.board.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadedImageDimensionInjectorTest {

    @TempDir
    Path tempDir;

    @Test
    void injectMissingDimensions_readsSizeFromUploadedFile() throws Exception {
        String uid = "uid123";
        String fileName = "map.jpg";
        Path imagePath = tempDir.resolve(uid + "_" + fileName);

        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", imagePath.toFile());

        UploadedImageDimensionInjector injector = new UploadedImageDimensionInjector(tempDir.toString(), "");
        String html = "<p><img src=\"/ckImgSubmit?uid=uid123&fileName=map.jpg\"></p>";

        String result = injector.injectMissingDimensions(html);

        assertTrue(result.contains("width=\"320\""));
        assertTrue(result.contains("height=\"240\""));
    }

    @Test
    void injectMissingDimensions_keepsExistingWidthHeight() {
        UploadedImageDimensionInjector injector = new UploadedImageDimensionInjector(tempDir.toString(), "");
        String html = "<p><img src=\"/ckImgSubmit?uid=uid123&fileName=map.jpg\" width=\"111\" height=\"222\"></p>";

        String result = injector.injectMissingDimensions(html);

        assertEquals(html, result);
    }

    @Test
    void injectMissingDimensions_promotesStyleWidthHeightToAttributes() {
        UploadedImageDimensionInjector injector = new UploadedImageDimensionInjector(tempDir.toString(), "");
        String html = "<p><img src=\"/ckImgSubmit?uid=uid123&fileName=map.jpg\" style=\"height:700px; width:800px\"></p>";

        String result = injector.injectMissingDimensions(html);

        assertTrue(result.contains("width=\"800\""));
        assertTrue(result.contains("height=\"700\""));
    }
}
