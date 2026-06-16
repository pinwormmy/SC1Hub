package com.sc1hub.file.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadedImageFileNameUtilTest {

    @Test
    void toStoredFileName_convertsToAsciiSafeName() {
        String stored = UploadedImageFileNameUtil.toStoredFileName("뮤탈과 바이오닉 심슨버전.jpg");

        assertTrue(stored.matches("[A-Za-z0-9_-]+\\.jpg"));
    }

    @Test
    void candidateFileNames_keepsLegacyAndStoredVariants() {
        List<String> candidates = UploadedImageFileNameUtil.candidateFileNames("ssb 사건.webp");

        assertEquals(2, candidates.size());
        assertEquals("ssb 사건.webp", candidates.get(0));
        assertTrue(candidates.get(1).matches("[A-Za-z0-9_-]+\\.webp"));
    }
}
