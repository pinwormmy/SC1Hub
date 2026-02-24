package com.sc1hub.board.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class UploadedImageDimensionInjector {

    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("(?i)<img\\b[^>]*>");
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("(?i)([a-z_:][-a-z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))");
    private static final Pattern PIXEL_VALUE_PATTERN = Pattern.compile("(?i)^\\s*(\\d+)\\s*(px)?\\s*$");
    private static final Pattern STYLE_DECLARATION_PATTERN = Pattern.compile("(?i)^\\s*(width|height)\\s*:\\s*(\\d+)\\s*(px)?\\s*$");
    private static final Pattern IMG_PATH_PATTERN = Pattern.compile("(?i)/img/([^/?#]+)");

    private final String uploadPath;
    private final String imageUploadPath;

    private volatile Path resolvedUploadPath;
    private volatile Path resolvedImageUploadPath;

    public UploadedImageDimensionInjector(
            @Value("${path.upload.ck:}") String uploadPath,
            @Value("${path.upload.img:}") String imageUploadPath) {
        this.uploadPath = uploadPath;
        this.imageUploadPath = imageUploadPath;
    }

    public String injectMissingDimensions(String html) {
        if (!StringUtils.hasText(html) || !containsImgTag(html)) {
            return html;
        }

        try {
            Matcher tagMatcher = IMG_TAG_PATTERN.matcher(html);
            StringBuffer out = new StringBuffer(html.length() + 128);
            while (tagMatcher.find()) {
                String originalTag = tagMatcher.group();
                String updatedTag = injectDimensionsIntoTag(originalTag);
                tagMatcher.appendReplacement(out, Matcher.quoteReplacement(updatedTag));
            }
            tagMatcher.appendTail(out);
            return out.toString();
        } catch (Exception e) {
            log.warn("Failed to inject image dimensions into post content.", e);
            return html;
        }
    }

    private String injectDimensionsIntoTag(String imgTag) {
        Map<String, String> attrs = extractAttributes(imgTag);
        String src = attrs.get("src");
        if (!StringUtils.hasText(src)) {
            return imgTag;
        }

        int widthFromAttr = parsePixelValue(attrs.get("width"));
        int heightFromAttr = parsePixelValue(attrs.get("height"));
        if (widthFromAttr > 0 && heightFromAttr > 0) {
            return imgTag;
        }

        int[] styleDimensions = parseStyleDimensions(attrs.get("style"));
        int width = widthFromAttr > 0 ? widthFromAttr : styleDimensions[0];
        int height = heightFromAttr > 0 ? heightFromAttr : styleDimensions[1];

        if (width <= 0 || height <= 0) {
            UploadedImageRef ref = parseUploadedImageRef(src);
            if (ref != null) {
                ImageDimension dimension = readImageDimension(ref);
                if (dimension != null) {
                    if (width <= 0) {
                        width = dimension.width;
                    }
                    if (height <= 0) {
                        height = dimension.height;
                    }
                }
            }
        }

        if (width <= 0 || height <= 0) {
            return imgTag;
        }

        String updated = imgTag;
        if (widthFromAttr <= 0) {
            updated = upsertAttribute(updated, "width", Integer.toString(width));
        }
        if (heightFromAttr <= 0) {
            updated = upsertAttribute(updated, "height", Integer.toString(height));
        }
        return updated;
    }

    private static boolean containsImgTag(String html) {
        return html.toLowerCase(Locale.ROOT).contains("<img");
    }

    private static Map<String, String> extractAttributes(String imgTag) {
        Map<String, String> attrs = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE_PATTERN.matcher(imgTag);
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = firstNonNull(matcher.group(3), matcher.group(4), matcher.group(5));
            if (name != null) {
                attrs.put(name.toLowerCase(Locale.ROOT), value == null ? "" : value);
            }
        }
        return attrs;
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static int parsePixelValue(String value) {
        if (!StringUtils.hasText(value)) {
            return -1;
        }
        Matcher matcher = PIXEL_VALUE_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int[] parseStyleDimensions(String style) {
        int[] result = new int[] { -1, -1 };
        if (!StringUtils.hasText(style)) {
            return result;
        }
        String[] declarations = style.split(";");
        for (String declaration : declarations) {
            if (!StringUtils.hasText(declaration)) {
                continue;
            }
            Matcher matcher = STYLE_DECLARATION_PATTERN.matcher(declaration);
            if (!matcher.matches()) {
                continue;
            }
            String name = matcher.group(1);
            int value;
            try {
                value = Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            if ("width".equalsIgnoreCase(name)) {
                result[0] = value;
            } else if ("height".equalsIgnoreCase(name)) {
                result[1] = value;
            }
        }
        return result;
    }

    private UploadedImageRef parseUploadedImageRef(String src) {
        if (!StringUtils.hasText(src) || src.startsWith("data:")) {
            return null;
        }
        String normalizedSrc = src.trim().replace("&amp;", "&");
        URI uri;
        try {
            uri = URI.create(normalizedSrc);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String path = uri.getPath();
        if (StringUtils.hasText(path) && path.contains("/ckImgSubmit")) {
            Map<String, String> query = parseQuery(uri.getRawQuery());
            String uid = query.get("uid");
            String fileName = sanitizeFileName(normalizeFileName(query.get("filename")));
            if (StringUtils.hasText(uid) && StringUtils.hasText(fileName)) {
                return new UploadedImageRef(uid.trim(), fileName);
            }
        }
        if (StringUtils.hasText(path)) {
            Matcher matcher = IMG_PATH_PATTERN.matcher(path);
            if (matcher.find()) {
                String rawToken = decodeUrlComponent(matcher.group(1));
                int separator = rawToken.indexOf('_');
                if (separator > 0 && separator < rawToken.length() - 1) {
                    String uid = rawToken.substring(0, separator).trim();
                    String fileName = sanitizeFileName(normalizeFileName(rawToken.substring(separator + 1)));
                    if (StringUtils.hasText(uid) && StringUtils.hasText(fileName)) {
                        return new UploadedImageRef(uid, fileName);
                    }
                }
            }
        }
        return null;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new LinkedHashMap<>();
        String normalized = rawQuery.replace("&amp;", "&");
        for (String token : normalized.split("&")) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String[] pair = token.split("=", 2);
            String key = decodeUrlComponent(pair[0]).toLowerCase(Locale.ROOT);
            String value = pair.length > 1 ? decodeUrlComponent(pair[1]) : "";
            map.put(key, value);
        }
        return map;
    }

    private static String decodeUrlComponent(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }

    private static String normalizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String trimmed = fileName.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return Normalizer.normalize(trimmed, Normalizer.Form.NFC);
    }

    private static String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        return fileName.trim().replace("/", "_").replace("\\", "_");
    }

    private ImageDimension readImageDimension(UploadedImageRef ref) {
        Path imagePath = findUploadedImagePath(ref.uid, ref.fileName);
        if (imagePath == null || !Files.isRegularFile(imagePath)) {
            return null;
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(imagePath.toFile())) {
            if (stream == null) {
                return null;
            }
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > 0 && height > 0) {
                    return new ImageDimension(width, height);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.debug("Failed to read image dimension. path={}", imagePath, e);
        }
        return null;
    }

    private Path findUploadedImagePath(String uid, String fileName) {
        if (!StringUtils.hasText(uid) || !StringUtils.hasText(fileName)) {
            return null;
        }
        for (Path basePath : getUploadBasePaths()) {
            Path found = findUploadedFile(basePath, uid, fileName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private List<Path> getUploadBasePaths() {
        List<Path> paths = new ArrayList<>();
        Path ckPath = resolveBasePath(uploadPath, true);
        if (ckPath != null) {
            paths.add(ckPath);
        }
        Path imgPath = resolveBasePath(imageUploadPath, false);
        if (imgPath != null && paths.stream().noneMatch(p -> p.equals(imgPath))) {
            paths.add(imgPath);
        }
        return paths;
    }

    private Path resolveBasePath(String rawPath, boolean cacheCk) {
        Path cached = cacheCk ? resolvedUploadPath : resolvedImageUploadPath;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            Path currentCached = cacheCk ? resolvedUploadPath : resolvedImageUploadPath;
            if (currentCached != null) {
                return currentCached;
            }
            if (!StringUtils.hasText(rawPath)) {
                return null;
            }
            Path base = Paths.get(normalizeFilePath(rawPath.trim()));
            if (!base.isAbsolute()) {
                base = base.toAbsolutePath();
            }
            Path resolved = base.normalize();
            if (cacheCk) {
                resolvedUploadPath = resolved;
            } else {
                resolvedImageUploadPath = resolved;
            }
            return resolved;
        }
    }

    private static String normalizeFilePath(String path) {
        if (!path.startsWith("file:")) {
            return path;
        }
        try {
            URI uri = URI.create(path);
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return Paths.get(uri).toString();
            }
        } catch (Exception ignored) {
        }
        return path.replaceFirst("^file:(//)?", "");
    }

    private Path findUploadedFile(Path basePath, String uid, String fileName) {
        if (basePath == null || !StringUtils.hasText(uid) || !StringUtils.hasText(fileName)) {
            return null;
        }
        String normalizedName = normalizeFileName(fileName);
        Path direct = basePath.resolve(uid + "_" + normalizedName);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        if (!Files.isDirectory(basePath)) {
            return null;
        }
        String prefix = uid + "_";
        try (Stream<Path> stream = Files.list(basePath)) {
            for (Path candidate : (Iterable<Path>) stream::iterator) {
                String name = candidate.getFileName().toString();
                if (!name.startsWith(prefix)) {
                    continue;
                }
                String actualName = name.substring(prefix.length());
                if (normalizeFileName(actualName).equals(normalizedName)) {
                    return candidate;
                }
            }
        } catch (IOException e) {
            log.debug("Failed to scan upload directory. path={}", basePath, e);
        }
        return null;
    }

    private static String upsertAttribute(String imgTag, String attributeName, String value) {
        Pattern pattern = Pattern.compile("(?i)\\b" + Pattern.quote(attributeName) + "\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s\"'>]+)");
        Matcher matcher = pattern.matcher(imgTag);
        if (matcher.find()) {
            return matcher.replaceFirst(attributeName + "=\"" + value + "\"");
        }
        int insertPos = imgTag.lastIndexOf("/>");
        if (insertPos < 0) {
            insertPos = imgTag.lastIndexOf('>');
        }
        if (insertPos < 0) {
            return imgTag;
        }
        return imgTag.substring(0, insertPos) + " " + attributeName + "=\"" + value + "\"" + imgTag.substring(insertPos);
    }

    private static final class UploadedImageRef {
        private final String uid;
        private final String fileName;

        private UploadedImageRef(String uid, String fileName) {
            this.uid = uid;
            this.fileName = fileName;
        }
    }

    private static final class ImageDimension {
        private final int width;
        private final int height;

        private ImageDimension(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
