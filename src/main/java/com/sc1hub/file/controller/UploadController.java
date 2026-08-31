package com.sc1hub.file.controller;

import com.sc1hub.file.dto.PostImageResponse;
import com.sc1hub.file.service.PostImageService;
import com.sc1hub.file.util.UploadedImageFileNameUtil;
import com.sc1hub.file.util.UploadedImagePathResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
public class UploadController {

    private static final String IMMUTABLE_IMAGE_CACHE_CONTROL = "public, max-age=31536000, immutable";

    // 슬래시 포함 여부가 또 중요해서 설정 경로 따로 만듬
    private String uploadPath;

    private String imageUploadPath;

    private final PostImageService postImageService;

    private volatile Path resolvedUploadPath;
    private volatile Path resolvedImageUploadPath;

    @Autowired
    public UploadController(
            @Value("${path.upload.ck}") String uploadPath,
            @Value("${path.upload.img:}") String imageUploadPath,
            PostImageService postImageService) {
        this.uploadPath = uploadPath;
        this.imageUploadPath = imageUploadPath;
        this.postImageService = postImageService;
    }

    UploadController() {
        this("", "", null);
    }

    @PostMapping(value = "/imageUpload", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> imageUpload(HttpServletRequest request,
            @RequestParam("upload") MultipartFile upload) {
        try {
            PostImageService service = postImageService == null
                    ? new PostImageService(uploadPath, imageUploadPath)
                    : postImageService;
            PostImageResponse image = service.store(upload, request.getContextPath());
            return ResponseEntity.ok(buildSuccessResponse(image));
        } catch (IllegalArgumentException e) {
            return errorResponse(e.getMessage());
        } catch (IOException | IllegalStateException e) {
            log.error("Image upload failed.", e);
            return errorResponse("이미지 업로드에 실패했습니다.");
        }
    }

    // 서버로 전송된 이미지 뿌려주기
    @GetMapping(value="/ckImgSubmit")
    public void ckSubmit(@RequestParam(value="uid") String uid
            , @RequestParam(value="fileName") String fileName
            , HttpServletRequest request, HttpServletResponse response) {

        List<Path> basePaths = getUploadBasePaths();
        if (basePaths.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String decodedFileName = decodeRequestParameter(request, "fileName", fileName);
        Path targetPath = UploadedImagePathResolver.findUploadedFile(basePaths, uid, decodedFileName);
        if (targetPath == null) {
            log.warn("Image not found on filesystem. uid={}, fileName={}, basePaths={}. Redirecting to /img.", uid, decodedFileName, basePaths);
            if (redirectToImg(request, response, uid, decodedFileName)) {
                return;
            }
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        log.debug("img upload path for submit: {}", targetPath);
        writeImageResponse(targetPath, request, response, uid, decodedFileName);
    }

    @GetMapping(value={"/img/{imageName:.+}", "/uploadedImg/{imageName:.+}"})
    public void imgSubmit(@PathVariable("imageName") String imageName,
            HttpServletRequest request, HttpServletResponse response) {

        UploadedImageRef ref = parseImgPath(imageName);
        if (ref == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        List<Path> basePaths = getUploadBasePaths();
        if (basePaths.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path targetPath = UploadedImagePathResolver.findUploadedFile(basePaths, ref.uid, ref.fileName);
        if (targetPath == null) {
            log.warn("Image not found on filesystem from /img. uid={}, fileName={}, basePaths={}",
                    ref.uid, ref.fileName, basePaths);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        log.debug("img upload path for /img: {}", targetPath);
        writeImageResponse(targetPath, request, response, ref.uid, ref.fileName);
    }

    private void writeImageResponse(Path targetPath, HttpServletRequest request, HttpServletResponse response,
            String uid, String decodedFileName) {
        if (Files.isRegularFile(targetPath)) {
            try {
                long fileSize = Files.size(targetPath);
                long lastModified = Files.getLastModifiedTime(targetPath).toMillis();
                String contentType = Files.probeContentType(targetPath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
                response.setContentType(contentType);
                response.setHeader("Cache-Control", IMMUTABLE_IMAGE_CACHE_CONTROL);
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setDateHeader("Last-Modified", lastModified);
                if (isNotModified(request, lastModified)) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return;
                }
                if (fileSize <= Integer.MAX_VALUE) {
                    response.setContentLength((int) fileSize);
                }
                try (ServletOutputStream out = response.getOutputStream()) {
                    Files.copy(targetPath, out);
                    out.flush();
                }
            } catch (IOException e) {
                log.error("Failed to load uploaded image. path={}", targetPath, e);
            }
        } else {
            if (redirectToImg(request, response, uid, decodedFileName)) {
                return;
            }
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private boolean isNotModified(HttpServletRequest request, long lastModified) {
        if (request == null) {
            return false;
        }
        try {
            long ifModifiedSince = request.getDateHeader("If-Modified-Since");
            return ifModifiedSince >= 0 && ifModifiedSince >= (lastModified / 1000L) * 1000L;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private UploadedImageRef parseImgPath(String imageName) {
        String decodedImageName = decodeFileName(imageName);
        int separator = decodedImageName.indexOf('_');
        if (separator <= 0 || separator >= decodedImageName.length() - 1) {
            return null;
        }
        String uid = decodedImageName.substring(0, separator).trim();
        String fileName = UploadedImageFileNameUtil.sanitizeLegacyFileName(decodedImageName.substring(separator + 1));
        if (uid.isEmpty() || fileName.isEmpty()) {
            return null;
        }
        return new UploadedImageRef(uid, fileName);
    }

    static String decodeRequestParameter(HttpServletRequest request, String parameterName, String fallbackValue) {
        String rawQuery = request == null ? null : request.getQueryString();
        String rawValue = findRawQueryValue(rawQuery, parameterName);
        if (rawValue != null) {
            return decodeFileName(rawValue);
        }
        return decodeFileName(fallbackValue);
    }

    private static String findRawQueryValue(String rawQuery, String parameterName) {
        if (rawQuery == null || rawQuery.trim().isEmpty() || parameterName == null || parameterName.trim().isEmpty()) {
            return null;
        }
        String normalized = rawQuery.replace("&amp;", "&");
        String expectedKey = parameterName.toLowerCase();
        for (String token : normalized.split("&")) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            String[] pair = token.split("=", 2);
            String key = decodeFileName(pair[0]).toLowerCase();
            if (expectedKey.equals(key)) {
                return pair.length > 1 ? pair[1] : "";
            }
        }
        return null;
    }

    private Path getPrimaryUploadBasePath() {
        Path ckPath = resolveBasePath(uploadPath, true);
        if (ckPath != null) {
            return ckPath;
        }
        return resolveBasePath(imageUploadPath, false);
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
            if (rawPath == null || rawPath.trim().isEmpty()) {
                return null;
            }
            String normalized = normalizeFilePath(rawPath.trim());
            Path base = Paths.get(normalized);
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

    private String normalizeFilePath(String path) {
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

    private static String decodeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        try {
            return URLDecoder.decode(fileName, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return fileName;
        }
    }

    private boolean redirectToImg(HttpServletRequest request, HttpServletResponse response, String uid, String fileName) {
        if (uid == null || uid.trim().isEmpty()) {
            return false;
        }
        try {
            String redirectFileName = toRedirectFileName(fileName);
            if (redirectFileName.isEmpty()) {
                return false;
            }
            String encodedName = URLEncoder.encode(redirectFileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
            String redirectUrl = request.getContextPath() + "/uploadedImg/" + uid + "_" + encodedName;
            response.sendRedirect(redirectUrl);
            return true;
        } catch (IOException e) {
            log.warn("Failed to redirect image request. uid={}, fileName={}", uid, fileName, e);
            return false;
        }
    }

    private ResponseEntity<Map<String, Object>> errorResponse(String message) {
        return ResponseEntity.ok(buildErrorResponse(message));
    }

    private Map<String, Object> buildSuccessResponse(PostImageResponse image) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uploaded", 1);
        body.put("fileName", image.getFileName());
        body.put("url", image.getUrl());
        body.put("mimeType", image.getMimeType());
        body.put("width", image.getWidth());
        body.put("height", image.getHeight());
        body.put("bytes", image.getBytes());
        return body;
    }

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("uploaded", 0);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("message", message);
        error.put("error", detail);
        return error;
    }

    private static String toRedirectFileName(String fileName) {
        String sanitizedFileName = UploadedImageFileNameUtil.sanitizeLegacyFileName(fileName);
        if (sanitizedFileName.isEmpty()) {
            return "";
        }
        return isAsciiFileName(sanitizedFileName)
                ? sanitizedFileName
                : UploadedImageFileNameUtil.toStoredFileName(sanitizedFileName);
    }

    private static boolean isAsciiFileName(String fileName) {
        for (int i = 0; i < fileName.length(); i++) {
            if (fileName.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static final class UploadedImageRef {
        private final String uid;
        private final String fileName;

        private UploadedImageRef(String uid, String fileName) {
            this.uid = uid;
            this.fileName = fileName;
        }
    }
}
