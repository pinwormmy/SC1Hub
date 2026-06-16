package com.sc1hub.file.util;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class UploadedImageFileNameUtil {

    private static final Base64.Encoder URL_SAFE_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private UploadedImageFileNameUtil() {
    }

    public static String normalizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        return Normalizer.normalize(fileName.trim(), Normalizer.Form.NFC);
    }

    public static String sanitizeLegacyFileName(String fileName) {
        String normalized = normalizeFileName(fileName);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.replace("/", "_").replace("\\", "_");
    }

    public static String toStoredFileName(String fileName) {
        String legacyFileName = sanitizeLegacyFileName(fileName);
        if (!StringUtils.hasText(legacyFileName)) {
            return "";
        }

        String extension = StringUtils.getFilenameExtension(legacyFileName);
        String baseName = StringUtils.stripFilenameExtension(legacyFileName);
        if (!StringUtils.hasText(baseName)) {
            baseName = legacyFileName;
        }

        String encodedBaseName = URL_SAFE_ENCODER.encodeToString(baseName.getBytes(StandardCharsets.UTF_8));
        if (StringUtils.hasText(extension)) {
            return encodedBaseName + "." + extension;
        }
        return encodedBaseName;
    }

    public static List<String> candidateFileNames(String fileName) {
        String legacyFileName = sanitizeLegacyFileName(fileName);
        if (!StringUtils.hasText(legacyFileName)) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(legacyFileName);
        candidates.add(toStoredFileName(legacyFileName));
        return new ArrayList<>(candidates);
    }
}
