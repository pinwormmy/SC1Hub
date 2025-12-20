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
import java.io.*;
import java.util.UUID;

import static java.nio.file.Files.newOutputStream;

// 에디터 첨부자료 통째로 들고와서 괜히 무거워짐. 나중에 필요없는 파일 다 삭제하기
// 컨트롤러에 연산 이렇게 두는거 정리해야하지않을까?
@Slf4j
@Controller
public class UploadController {

    // 슬래시 포함 여부가 또 중요해서 설정 경로 따로 만듬
    @Value("${path.upload.ck}")
    private String uploadPath;

    @PostMapping(value="/imageUpload")
    public void imageUpload(HttpServletRequest request,
                            HttpServletResponse response, MultipartHttpServletRequest multiFile
            , @RequestParam MultipartFile upload) {
        // 랜덤 문자 생성
        UUID uid = UUID.randomUUID();
        OutputStream out = null;
        PrintWriter printWriter = null;
        //인코딩
        response.setCharacterEncoding("utf-8");
        response.setContentType("text/html;charset=utf-8");
        try{
            //파일 이름 가져오기
            String fileName = upload.getOriginalFilename();
            byte[] bytes = upload.getBytes();

            String path = uploadPath;
            String ckUploadPath = path + uid + "_" + fileName;
            File folder = new File(path);
            log.info("img upload path:"+path);	// 이미지 저장경로 console에 확인
            if(!folder.exists()){
                try{
                    boolean isCreated = folder.mkdirs(); // 폴더 생성
                    if (!isCreated) {
                        log.error("Failed to create directory");
                    }
                }catch(Exception e){
                    log.error("Failed to create upload directory. path={}", path, e);
                }
            }
            out = newOutputStream(new File(ckUploadPath).toPath());
            out.write(bytes);
            out.flush(); // outputStram에 저장된 데이터를 전송하고 초기화
            request.getParameter("CKEditorFuncNum");
            printWriter = response.getWriter();
            String fileUrl = "/ckImgSubmit?uid=" + uid + "&fileName=" + fileName; // 작성화면
            // 업로드시 메시지 출력
            printWriter.println("{\"filename\" : \""+fileName+"\", \"uploaded\" : 1, \"url\":\""+fileUrl+"\"}");
            printWriter.flush();
        }catch(IOException e){
            log.error("Image upload failed.", e);
        } finally {
            try {
                if(out != null) { out.close(); }
                if(printWriter != null) { printWriter.close(); }
            } catch(IOException e) {
                log.warn("Failed to close upload stream.", e);
            }
        }
    }

    // 서버로 전송된 이미지 뿌려주기
    @GetMapping(value="/ckImgSubmit")
    public void ckSubmit(@RequestParam(value="uid") String uid
            , @RequestParam(value="fileName") String fileName
            , HttpServletRequest request, HttpServletResponse response) {

        //서버에 저장된 이미지 경로
        String path = uploadPath;	// 저장된 이미지 경로
        log.info("img upload path for submit:"+path);
        String sDirPath = path + uid + "_" + fileName;

        File imgFile = new File(sDirPath);

        //사진 이미지 찾지 못하는 경우 예외처리로 빈 이미지 파일을 설정한다.
        if(imgFile.isFile()){
            byte[] buf = new byte[1024];
            int readByte;
            int length;
            byte[] imgBuf;

            try (FileInputStream fileInputStream = new FileInputStream(imgFile); ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 ServletOutputStream out = response.getOutputStream()) {

                while ((readByte = fileInputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, readByte);
                }

                imgBuf = outputStream.toByteArray();
                length = imgBuf.length;
                out.write(imgBuf, 0, length);
                out.flush();

            } catch (IOException e) {
                log.error("Failed to load uploaded image. path={}", sDirPath, e);
            }
        }
    }
}
