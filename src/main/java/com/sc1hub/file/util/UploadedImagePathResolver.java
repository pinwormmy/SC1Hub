package com.sc1hub.file.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
public final class UploadedImagePathResolver {

    private UploadedImagePathResolver() {
    }

    public static Path findUploadedFile(List<Path> basePaths, String uid, String fileName) {
        if (basePaths == null || basePaths.isEmpty()) {
            return null;
        }
        for (Path basePath : basePaths) {
            Path found = findUploadedFile(basePath, uid, fileName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static Path findUploadedFile(Path basePath, String uid, String fileName) {
        if (basePath == null || !hasSafeUid(uid) || !StringUtils.hasText(fileName)) {
            return null;
        }
        List<String> candidateFileNames = UploadedImageFileNameUtil.candidateFileNames(fileName);

        for (String candidateFileName : candidateFileNames) {
            Path targetPath = resolveUploadTarget(basePath, uid, candidateFileName);
            if (targetPath == null) {
                continue;
            }
            try {
                if (Files.isRegularFile(targetPath)) {
                    return targetPath;
                }
            } catch (InvalidPathException e) {
                log.debug("Skipping invalid uploaded image path. basePath={}, fileName={}", basePath, candidateFileName, e);
            }
        }
        return findUniqueFileByUid(basePath, uid, fileName, candidateFileNames);
    }

    public static Path resolveUploadTarget(Path basePath, String uid, String fileName) {
        if (basePath == null || !hasSafeUid(uid)) {
            return null;
        }
        String normalizedName = UploadedImageFileNameUtil.sanitizeLegacyFileName(fileName);
        if (!StringUtils.hasText(normalizedName)) {
            return null;
        }
        try {
            return basePath.resolve(uid + "_" + normalizedName);
        } catch (InvalidPathException e) {
            log.debug("Failed to resolve uploaded image path. basePath={}, fileName={}", basePath, normalizedName, e);
            return null;
        }
    }

    private static Path findUniqueFileByUid(Path basePath, String uid, String fileName, List<String> candidateFileNames) {
        if (basePath == null || !hasSafeUid(uid) || !Files.isDirectory(basePath)) {
            return null;
        }

        String prefix = uid + "_";
        String requestedExtension = getExtension(fileName);
        Set<String> normalizedCandidates = normalizeCandidates(candidateFileNames);
        List<Path> uidMatches = new ArrayList<>();
        List<Path> extensionMatches = new ArrayList<>();

        try (Stream<Path> stream = Files.list(basePath)) {
            java.util.Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext()) {
                Path candidate = iterator.next();
                String name = candidate.getFileName() == null ? "" : candidate.getFileName().toString();
                if (!name.startsWith(prefix) || !Files.isRegularFile(candidate)) {
                    continue;
                }

                String actualFileName = name.substring(prefix.length());
                if (normalizedCandidates.contains(UploadedImageFileNameUtil.normalizeFileName(actualFileName))) {
                    return candidate;
                }

                uidMatches.add(candidate);
                if (StringUtils.hasText(requestedExtension)
                        && requestedExtension.equalsIgnoreCase(getExtension(actualFileName))) {
                    extensionMatches.add(candidate);
                }
            }
        } catch (IOException | InvalidPathException e) {
            log.debug("Failed to scan upload directory. path={}, uid={}", basePath, uid, e);
            return null;
        }

        if (extensionMatches.size() == 1) {
            return extensionMatches.get(0);
        }
        if (uidMatches.size() == 1) {
            return uidMatches.get(0);
        }
        if (uidMatches.size() > 1) {
            log.warn("Multiple uploaded image files matched uid. basePath={}, uid={}, requestedFileName={}, matches={}",
                    basePath, uid, fileName, uidMatches.size());
        }
        return null;
    }

    private static Set<String> normalizeCandidates(List<String> candidateFileNames) {
        Set<String> normalized = new LinkedHashSet<>();
        if (candidateFileNames == null) {
            return normalized;
        }
        for (String candidateFileName : candidateFileNames) {
            String value = UploadedImageFileNameUtil.normalizeFileName(candidateFileName);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private static String getExtension(String fileName) {
        String safeFileName = UploadedImageFileNameUtil.sanitizeLegacyFileName(fileName);
        String extension = StringUtils.getFilenameExtension(safeFileName);
        if (!StringUtils.hasText(extension)) {
            return "";
        }
        return extension.toLowerCase(Locale.ROOT);
    }

    private static boolean hasSafeUid(String uid) {
        return StringUtils.hasText(uid) && uid.matches("[A-Za-z0-9._-]+");
    }
}
