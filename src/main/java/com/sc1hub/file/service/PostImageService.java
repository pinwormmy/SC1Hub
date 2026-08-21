package com.sc1hub.file.service;

import com.sc1hub.file.dto.PostImageResponse;
import com.sc1hub.file.util.UploadedImageFileNameUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class PostImageService {

    static final long MAX_INPUT_BYTES = 10L * 1024L * 1024L;
    static final int MAX_WIDTH = 700;
    static final int MAX_HEIGHT = 2000;
    static final long TARGET_OUTPUT_BYTES = 400L * 1024L;
    private static final long MAX_PIXELS = 40_000_000L;

    private final String uploadPath;
    private final String imageUploadPath;

    @Autowired
    public PostImageService(
            @Value("${path.upload.ck:}") String uploadPath,
            @Value("${path.upload.img:}") String imageUploadPath) {
        this.uploadPath = uploadPath;
        this.imageUploadPath = imageUploadPath;
    }

    public PostImageResponse store(MultipartFile upload, String contextPath) throws IOException {
        if (upload == null || upload.isEmpty()) {
            throw new IllegalArgumentException("업로드할 이미지가 없습니다.");
        }
        if (upload.getSize() > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("이미지는 10MB 이하만 업로드할 수 있습니다.");
        }

        DecodedImage decoded = decode(upload.getBytes());
        BufferedImage resized = resizeToBounds(decoded.image);
        EncodedImage encoded = encodeWithinTarget(resized, decoded.format);
        Path basePath = resolvePrimaryUploadPath();
        if (basePath == null) {
            throw new IllegalStateException("업로드 경로가 설정되어 있지 않습니다.");
        }

        Files.createDirectories(basePath);
        String uid = UUID.randomUUID().toString();
        String fileName = buildStoredFileName(upload.getOriginalFilename(), encoded.extension);
        Path targetPath = basePath.resolve(uid + "_" + fileName).normalize();
        if (!targetPath.startsWith(basePath)) {
            throw new IllegalArgumentException("올바르지 않은 파일 이름입니다.");
        }
        Files.write(targetPath, encoded.bytes);

        String prefix = contextPath == null ? "" : contextPath;
        String url = prefix + "/uploadedImg/" + uid + "_" + fileName;
        return new PostImageResponse(fileName, url, encoded.mimeType,
                encoded.image.getWidth(), encoded.image.getHeight(), encoded.bytes.length);
    }

    private DecodedImage decode(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!"jpeg".equals(format) && !"jpg".equals(format) && !"png".equals(format)) {
                    throw new IllegalArgumentException("JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("이미지 크기가 너무 큽니다.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.");
                }
                return new DecodedImage(image, format);
            } finally {
                reader.dispose();
            }
        }
    }

    private BufferedImage resizeToBounds(BufferedImage source) {
        double scale = Math.min(1.0d, Math.min(
                MAX_WIDTH / (double) source.getWidth(),
                MAX_HEIGHT / (double) source.getHeight()));
        if (scale >= 1.0d) {
            return source;
        }
        return resize(source,
                Math.max(1, (int) Math.round(source.getWidth() * scale)),
                Math.max(1, (int) Math.round(source.getHeight() * scale)));
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private EncodedImage encodeWithinTarget(BufferedImage source, String originalFormat) throws IOException {
        boolean transparent = source.getColorModel().hasAlpha();
        BufferedImage candidate = source;

        for (int resizeAttempt = 0; resizeAttempt < 7; resizeAttempt++) {
            if (transparent) {
                byte[] png = encodePng(candidate);
                if (png.length <= TARGET_OUTPUT_BYTES) {
                    return new EncodedImage(candidate, png, "png", "image/png");
                }
            } else {
                float[] qualities = { 0.88f, 0.82f, 0.76f, 0.70f, 0.64f };
                for (float quality : qualities) {
                    byte[] jpeg = encodeJpeg(candidate, quality);
                    if (jpeg.length <= TARGET_OUTPUT_BYTES) {
                        return new EncodedImage(candidate, jpeg, "jpg", "image/jpeg");
                    }
                }
            }
            if (candidate.getWidth() <= 240 && candidate.getHeight() <= 240) {
                break;
            }
            int nextWidth = Math.max(1, (int) Math.round(candidate.getWidth() * 0.88d));
            int nextHeight = Math.max(1, (int) Math.round(candidate.getHeight() * 0.88d));
            if (nextWidth == candidate.getWidth() && nextHeight == candidate.getHeight()) {
                break;
            }
            candidate = resize(candidate, nextWidth, nextHeight);
        }

        if (!transparent && "png".equals(originalFormat)) {
            byte[] jpeg = encodeJpeg(toOpaque(candidate), 0.60f);
            if (jpeg.length <= TARGET_OUTPUT_BYTES) {
                return new EncodedImage(candidate, jpeg, "jpg", "image/jpeg");
            }
        }
        throw new IllegalArgumentException("이미지를 400KB 이하로 최적화할 수 없습니다.");
    }

    private byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("PNG 인코더를 찾을 수 없습니다.");
        }
        return output.toByteArray();
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        BufferedImage opaque = toOpaque(image);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG 인코더를 찾을 수 없습니다.");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new IIOImage(opaque, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private BufferedImage toOpaque(BufferedImage source) {
        if (!source.getColorModel().hasAlpha() && source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private String buildStoredFileName(String originalFileName, String extension) {
        String safeName = UploadedImageFileNameUtil.sanitizeLegacyFileName(originalFileName);
        String baseName = StringUtils.stripFilenameExtension(safeName);
        if (!StringUtils.hasText(baseName)) {
            baseName = "post-image";
        }
        return UploadedImageFileNameUtil.toStoredFileName(baseName + "." + extension);
    }

    private Path resolvePrimaryUploadPath() {
        Path ckPath = resolvePath(uploadPath);
        return ckPath != null ? ckPath : resolvePath(imageUploadPath);
    }

    private Path resolvePath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return null;
        }
        String normalized = rawPath.trim();
        if (normalized.startsWith("file:")) {
            try {
                normalized = Paths.get(URI.create(normalized)).toString();
            } catch (Exception ignored) {
                normalized = normalized.replaceFirst("^file:(//)?", "");
            }
        }
        Path path = Paths.get(normalized);
        if (!path.isAbsolute()) {
            path = path.toAbsolutePath();
        }
        return path.normalize();
    }

    private static final class DecodedImage {
        private final BufferedImage image;
        private final String format;

        private DecodedImage(BufferedImage image, String format) {
            this.image = image;
            this.format = format;
        }
    }

    private static final class EncodedImage {
        private final BufferedImage image;
        private final byte[] bytes;
        private final String extension;
        private final String mimeType;

        private EncodedImage(BufferedImage image, byte[] bytes, String extension, String mimeType) {
            this.image = image;
            this.bytes = bytes;
            this.extension = extension;
            this.mimeType = mimeType;
        }
    }
}
