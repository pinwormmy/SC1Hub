package com.sc1hub.file.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

// 에디터 첨부자료 통째로 들고와서 괜히 무거워짐. 나중에 필요없는 파일 다 삭제하기
// 컨트롤러에 연산 이렇게 두는거 정리해야하지않을까?
@Slf4j
@Controller
public class UploadController {

    // 슬래시 포함 여부가 또 중요해서 설정 경로 따로 만듬
    @Value("${path.upload.ck}")
    private String uploadPath;

    @Value("${path.upload.img:}")
    private String imageUploadPath;

    private volatile Path resolvedUploadPath;
    private volatile Path resolvedImageUploadPath;

    @PostMapping(value="/imageUpload")
    public void imageUpload(HttpServletRequest request,
                            HttpServletResponse response, MultipartHttpServletRequest multiFile
            , @RequestParam MultipartFile upload) {
        // 랜덤 문자 생성
        UUID uid = UUID.randomUUID();
        //인코딩
        response.setCharacterEncoding("utf-8");
        response.setContentType("text/html;charset=utf-8");
        PrintWriter printWriter = null;
        try {
            Path basePath = getPrimaryUploadBasePath();
            if (basePath == null) {
                log.error("Upload path is not configured.");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
            //파일 이름 가져오기
            String fileName = upload.getOriginalFilename();
            String safeFileName = sanitizeFileName(normalizeFileName(fileName));
            byte[] bytes = upload.getBytes();

            Files.createDirectories(basePath);
            Path targetPath = resolveUploadTarget(basePath, uid.toString(), safeFileName);
            if (targetPath == null) {
                log.error("Failed to resolve upload path. basePath={}, fileName={}", basePath, safeFileName);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
            log.info("img upload path: {}", targetPath);
            try (OutputStream out = Files.newOutputStream(targetPath)) {
                out.write(bytes);
                out.flush();
            }

            request.getParameter("CKEditorFuncNum");
            printWriter = response.getWriter();
            String encodedFileName = URLEncoder.encode(safeFileName, StandardCharsets.UTF_8.name());
            String fileUrl = request.getContextPath() + "/ckImgSubmit?uid=" + uid + "&fileName=" + encodedFileName; // 작성화면
            // 업로드시 메시지 출력
            printWriter.println("{\"filename\" : \"" + safeFileName + "\", \"uploaded\" : 1, \"url\":\"" + fileUrl + "\"}");
            printWriter.flush();
        } catch (IOException e) {
            log.error("Image upload failed.", e);
        } finally {
            try {
                if (printWriter != null) {
                    printWriter.close();
                }
            } catch (Exception e) {
                log.warn("Failed to close upload stream.", e);
            }
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
        String decodedFileName = decodeFileName(fileName);
        Path targetPath = null;
        for (Path candidateBase : basePaths) {
            targetPath = findUploadedFile(candidateBase, uid, decodedFileName);
            if (targetPath != null) {
                break;
            }
        }
        if (targetPath == null) {
            log.warn("Image not found on filesystem. uid={}, fileName={}, basePaths={}. Redirecting to /img.", uid, decodedFileName, basePaths);
            if (redirectToImg(request, response, uid, decodedFileName)) {
                return;
            }
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        File imgFile = targetPath.toFile();
        log.info("img upload path for submit: {}", targetPath);

        //사진 이미지 찾지 못하는 경우 예외처리로 빈 이미지 파일을 설정한다.
        if (imgFile.isFile()) {
            byte[] buf = new byte[1024];
            int readByte;
            int length;
            byte[] imgBuf;

            try (FileInputStream fileInputStream = new FileInputStream(imgFile);
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 ServletOutputStream out = response.getOutputStream()) {

                String contentType = Files.probeContentType(targetPath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
                response.setContentType(contentType);

                while ((readByte = fileInputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, readByte);
                }

                imgBuf = outputStream.toByteArray();
                length = imgBuf.length;
                out.write(imgBuf, 0, length);
                out.flush();

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

    private String decodeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        try {
            return URLDecoder.decode(fileName, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return fileName;
        }
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String trimmed = fileName.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return Normalizer.normalize(trimmed, Normalizer.Form.NFC);
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "upload";
        }
        return fileName.trim().replace("/", "_").replace("\\", "_");
    }

    private Path findUploadedFile(Path basePath, String uid, String fileName) {
        if (basePath == null || uid == null || uid.trim().isEmpty()) {
            return null;
        }
        String normalizedName = normalizeFileName(fileName);
        String safeName = sanitizeFileName(normalizedName);
        Path direct = basePath.resolve(uid + "_" + safeName);
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
            log.warn("Failed to scan upload directory. path={}", basePath, e);
        }
        return null;
    }

    private Path resolveUploadTarget(Path basePath, String uid, String fileName) {
        if (basePath == null || uid == null || uid.trim().isEmpty()) {
            return null;
        }
        String normalizedName = normalizeFileName(fileName);
        String safeName = sanitizeFileName(normalizedName);
        return basePath.resolve(uid + "_" + safeName);
    }

    private boolean redirectToImg(HttpServletRequest request, HttpServletResponse response, String uid, String fileName) {
        if (uid == null || uid.trim().isEmpty()) {
            return false;
        }
        try {
            String safeName = sanitizeFileName(normalizeFileName(fileName));
            String encodedName = URLEncoder.encode(safeName, StandardCharsets.UTF_8.name()).replace("+", "%20");
            String redirectUrl = request.getContextPath() + "/img/" + uid + "_" + encodedName;
            response.sendRedirect(redirectUrl);
            return true;
        } catch (IOException e) {
            log.warn("Failed to redirect image request. uid={}, fileName={}", uid, fileName, e);
            return false;
        }
    }
}
