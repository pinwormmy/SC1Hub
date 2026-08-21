package com.sc1hub.content.controller;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.board.service.PostContentSanitizer;
import com.sc1hub.board.support.BoardTitleNormalizer;
import com.sc1hub.content.dto.ContentPostRequest;
import com.sc1hub.content.dto.ContentPostResponse;
import com.sc1hub.file.dto.PostImageResponse;
import com.sc1hub.file.service.PostImageService;
import com.sc1hub.member.dto.MemberDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/content")
public class ContentAdminController {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_WRITER_LENGTH = 40;
    private static final int MAX_CONTENT_LENGTH = 500_000;

    private final BoardService boardService;
    private final PostContentSanitizer contentSanitizer;
    private final PostImageService postImageService;

    public ContentAdminController(BoardService boardService,
            PostContentSanitizer contentSanitizer,
            PostImageService postImageService) {
        this.boardService = boardService;
        this.contentSanitizer = contentSanitizer;
        this.postImageService = postImageService;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PostImageResponse uploadImage(@RequestParam("upload") MultipartFile upload,
            HttpServletRequest request) throws IOException {
        return postImageService.store(upload, request.getContextPath());
    }

    @PostMapping(value = "/boards/{boardTitle}/posts", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ContentPostResponse> publishPost(@PathVariable String boardTitle,
            @RequestBody ContentPostRequest payload, HttpServletRequest request) throws Exception {
        String normalizedBoardTitle = BoardTitleNormalizer.requireValid(boardTitle);
        if (!StringUtils.hasText(boardService.getKoreanTitle(normalizedBoardTitle))) {
            throw new IllegalArgumentException("존재하지 않는 게시판입니다.");
        }
        validate(payload);

        BoardDTO post = new BoardDTO();
        post.setTitle(payload.getTitle().trim());
        post.setContent(contentSanitizer.sanitize(payload.getContent()));
        post.setWriter(resolveWriter(payload, request.getSession(false)));
        post.setNotice(payload.isNotice() ? 1 : 0);
        boardService.submitPost(normalizedBoardTitle, post);

        String url = request.getContextPath() + "/boards/" + normalizedBoardTitle
                + "/readPost?postNum=" + post.getPostNum();
        ContentPostResponse response = new ContentPostResponse(post.getPostNum(), normalizedBoardTitle, url);
        return ResponseEntity.created(URI.create(url)).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Collections.singletonMap("message", e.getMessage()));
    }

    private void validate(ContentPostRequest payload) {
        if (payload == null) {
            throw new IllegalArgumentException("게시글 내용을 확인해주세요.");
        }
        if (!StringUtils.hasText(payload.getTitle()) || payload.getTitle().trim().length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("제목은 1자 이상 200자 이하로 입력해주세요.");
        }
        if (!StringUtils.hasText(payload.getContent()) || payload.getContent().length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("본문은 1자 이상 500,000자 이하로 입력해주세요.");
        }
    }

    private String resolveWriter(ContentPostRequest payload, HttpSession session) {
        MemberDTO member = session == null ? null : (MemberDTO) session.getAttribute("member");
        String writer = member == null ? payload.getWriter() : member.getNickName();
        if (!StringUtils.hasText(writer) || writer.trim().length() > MAX_WRITER_LENGTH) {
            throw new IllegalArgumentException("작성자는 1자 이상 40자 이하로 입력해주세요.");
        }
        return writer.trim();
    }
}
