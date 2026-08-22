package com.sc1hub.content.controller;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.board.service.PostContentSanitizer;
import com.sc1hub.board.support.BoardTitleNormalizer;
import com.sc1hub.common.exception.ResourceNotFoundException;
import com.sc1hub.content.dto.ContentPostForm;
import com.sc1hub.content.dto.ContentPostRequest;
import com.sc1hub.content.dto.ContentPostResponse;
import com.sc1hub.content.service.ContentPostComposer;
import com.sc1hub.file.dto.PostImageResponse;
import com.sc1hub.file.service.PostImageService;
import com.sc1hub.member.dto.MemberDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/content")
public class ContentAdminController {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_WRITER_LENGTH = 40;
    private static final int MAX_CONTENT_LENGTH = 500_000;
    private static final int MAX_POST_LIMIT = 100;
    private static final String DEFAULT_API_WRITER = "운영자";

    private final BoardService boardService;
    private final PostContentSanitizer contentSanitizer;
    private final PostImageService postImageService;
    private final ContentPostComposer contentPostComposer;

    public ContentAdminController(BoardService boardService,
            PostContentSanitizer contentSanitizer,
            PostImageService postImageService,
            ContentPostComposer contentPostComposer) {
        this.boardService = boardService;
        this.contentSanitizer = contentSanitizer;
        this.postImageService = postImageService;
        this.contentPostComposer = contentPostComposer;
    }

    @GetMapping(value = "/boards", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BoardListDTO> listBoards() {
        return boardService.getBoardList();
    }

    @GetMapping(value = "/boards/{boardTitle}/posts", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BoardDTO> listPosts(@PathVariable String boardTitle,
            @RequestParam(defaultValue = "20") int limit) throws Exception {
        String normalizedBoardTitle = requireBoard(boardTitle);
        if (limit < 1 || limit > MAX_POST_LIMIT) {
            throw new IllegalArgumentException("조회 개수는 1개 이상 100개 이하로 입력해주세요.");
        }
        return boardService.getRecentPosts(normalizedBoardTitle, limit);
    }

    @GetMapping(value = "/boards/{boardTitle}/posts/{postNum}", produces = MediaType.APPLICATION_JSON_VALUE)
    public BoardDTO readPost(@PathVariable String boardTitle, @PathVariable int postNum) throws Exception {
        String normalizedBoardTitle = requireBoard(boardTitle);
        if (postNum < 1) {
            throw new IllegalArgumentException("게시글 번호를 확인해주세요.");
        }
        BoardDTO post = boardService.readPost(normalizedBoardTitle, postNum);
        if (post == null) {
            throw new ResourceNotFoundException("존재하지 않는 게시글입니다.");
        }
        return post;
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
        return publish(requireBoard(boardTitle), payload, request);
    }

    @PostMapping(value = "/boards/{boardTitle}/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ContentPostResponse> publishPostWithMedia(@PathVariable String boardTitle,
            @ModelAttribute ContentPostForm form,
            @RequestParam(value = "upload", required = false) MultipartFile upload,
            HttpServletRequest request) throws Exception {
        String normalizedBoardTitle = requireBoard(boardTitle);
        PostImageResponse image = upload == null || upload.isEmpty()
                ? null
                : postImageService.store(upload, request.getContextPath());
        ContentPostRequest payload = new ContentPostRequest();
        payload.setTitle(form.getTitle());
        payload.setWriter(form.getWriter());
        payload.setNotice(form.isNotice());
        payload.setContent(contentPostComposer.compose(form.getTitle(), form.getContent(), image,
                form.getImageAlt(), form.getImageCaption(), form.getYoutubeUrl(), form.getYoutubeTitle()));
        return publish(normalizedBoardTitle, payload, request);
    }

    private ResponseEntity<ContentPostResponse> publish(String normalizedBoardTitle,
            ContentPostRequest payload, HttpServletRequest request) throws Exception {
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

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(404).body(Collections.singletonMap("message", e.getMessage()));
    }

    private String requireBoard(String boardTitle) {
        String normalizedBoardTitle = BoardTitleNormalizer.requireValid(boardTitle);
        if (!StringUtils.hasText(boardService.getKoreanTitle(normalizedBoardTitle))) {
            throw new IllegalArgumentException("존재하지 않는 게시판입니다.");
        }
        return normalizedBoardTitle;
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
        if (!StringUtils.hasText(writer) && member == null) {
            writer = DEFAULT_API_WRITER;
        }
        if (!StringUtils.hasText(writer) || writer.trim().length() > MAX_WRITER_LENGTH) {
            throw new IllegalArgumentException("작성자는 1자 이상 40자 이하로 입력해주세요.");
        }
        return writer.trim();
    }
}
