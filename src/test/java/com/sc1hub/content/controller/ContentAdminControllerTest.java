package com.sc1hub.content.controller;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.board.service.PostContentSanitizer;
import com.sc1hub.common.exception.ResourceNotFoundException;
import com.sc1hub.content.dto.ContentPostRequest;
import com.sc1hub.content.dto.ContentPostResponse;
import com.sc1hub.content.dto.ContentPostForm;
import com.sc1hub.content.service.ContentPostComposer;
import com.sc1hub.file.dto.PostImageResponse;
import com.sc1hub.file.service.PostImageService;
import com.sc1hub.member.dto.MemberDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentAdminControllerTest {

    @Mock
    private BoardService boardService;

    @Mock
    private PostImageService postImageService;

    @Test
    void publishPost_usesAuthenticatedAdminWriterAndReturnsLocation() throws Exception {
        PostContentSanitizer sanitizer = new PostContentSanitizer();
        ContentAdminController controller = controller(sanitizer);
        when(boardService.getKoreanTitle("tvspboard")).thenReturn("테프전");
        doAnswer(invocation -> {
            BoardDTO post = invocation.getArgument(1);
            post.setPostNum(42);
            return null;
        }).when(boardService).submitPost(eq("tvspboard"), any(BoardDTO.class));

        MemberDTO admin = new MemberDTO();
        admin.setGrade(3);
        admin.setNickName("운영자");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("member", admin);

        ContentPostRequest payload = new ContentPostRequest();
        payload.setTitle("공1업 6팩");
        payload.setWriter("위조 작성자");
        payload.setContent("<p onclick=\"bad()\">본문</p>");

        ResponseEntity<ContentPostResponse> response = controller.publishPost("tVsPBoard", payload, request);

        assertEquals(201, response.getStatusCodeValue());
        assertEquals(42, response.getBody().getPostNum());
        assertEquals("/boards/tvspboard/readPost?postNum=42", response.getHeaders().getLocation().toString());
        verify(boardService).submitPost(eq("tvspboard"), any(BoardDTO.class));
    }

    @Test
    void listAndReadPosts_returnExistingBoardContentWithoutIncreasingViews() throws Exception {
        ContentAdminController controller = controller(new PostContentSanitizer());
        BoardDTO summary = new BoardDTO();
        summary.setPostNum(12);
        summary.setTitle("헌터 팀플");
        BoardDTO detail = new BoardDTO();
        detail.setPostNum(12);
        detail.setContent("<p>본문</p>");
        when(boardService.getKoreanTitle("teamplayguideboard")).thenReturn("팀플 게시판");
        when(boardService.getRecentPosts("teamplayguideboard", 20))
                .thenReturn(Collections.singletonList(summary));
        when(boardService.readPost("teamplayguideboard", 12)).thenReturn(detail);

        assertEquals("헌터 팀플", controller.listPosts("TEAMPLAYGUIDEBOARD", 20).get(0).getTitle());
        assertEquals("<p>본문</p>", controller.readPost("teamplayguideboard", 12).getContent());
    }

    @Test
    void listPosts_rejectsExcessiveLimit() {
        ContentAdminController controller = controller(new PostContentSanitizer());
        when(boardService.getKoreanTitle("tvspboard")).thenReturn("테프전");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.listPosts("tvspboard", 101));

        assertEquals("조회 개수는 1개 이상 100개 이하로 입력해주세요.", error.getMessage());
    }

    @Test
    void publishPostWithMedia_placesOptimizedImageFirstAndYoutubeLast() throws Exception {
        PostContentSanitizer sanitizer = new PostContentSanitizer();
        ContentAdminController controller = controller(sanitizer);
        when(boardService.getKoreanTitle("teamplayguideboard")).thenReturn("팀플 게시판");
        when(postImageService.store(any(), eq(""))).thenReturn(new PostImageResponse(
                "teamplay.jpg", "/uploadedImg/teamplay.jpg", "image/jpeg", 700, 394, 123_000));
        doAnswer(invocation -> {
            BoardDTO post = invocation.getArgument(1);
            post.setPostNum(13);
            assertEquals("운영자", post.getWriter());
            assertTrue(post.getContent().startsWith("<figure class=\"sc-post-image\">"));
            assertTrue(post.getContent().contains("width=\"700\""));
            assertTrue(post.getContent().contains("합류냐 역공이냐"));
            assertTrue(post.getContent().contains("https://www.youtube-nocookie.com/embed/vi36jGm_cgw"));
            assertTrue(post.getContent().contains("width=\"100%\""));
            assertTrue(post.getContent().contains("class=\"sc-video-source\""));
            assertTrue(post.getContent().contains("href=\"https://www.youtube.com/watch?v=vi36jGm_cgw\""));
            assertTrue(post.getContent().endsWith("</div>"));
            return null;
        }).when(boardService).submitPost(eq("teamplayguideboard"), any(BoardDTO.class));

        ContentPostForm form = new ContentPostForm();
        form.setTitle("헌터 팀플");
        form.setContent("<p>합류냐 역공이냐</p>");
        form.setImageCaption("3초 안에 결정");
        form.setYoutubeUrl("https://www.youtube.com/watch?v=vi36jGm_cgw");
        MockMultipartFile image = new MockMultipartFile(
                "upload", "teamplay.jpg", "image/jpeg", new byte[] { 1, 2, 3 });

        ResponseEntity<ContentPostResponse> response = controller.publishPostWithMedia(
                "teamplayguideboard", form, image, new MockHttpServletRequest());

        assertEquals(201, response.getStatusCodeValue());
        assertEquals(13, response.getBody().getPostNum());
    }

    @Test
    void updatePost_preservesWriterSanitizesContentAndReturnsLocation() throws Exception {
        ContentAdminController controller = controller(new PostContentSanitizer());
        BoardDTO existing = new BoardDTO();
        existing.setPostNum(12);
        existing.setWriter("기존 작성자");
        existing.setGuestPassword("guest-secret");
        when(boardService.getKoreanTitle("tvspboard")).thenReturn("테프전");
        when(boardService.readPost("tvspboard", 12)).thenReturn(existing);
        doAnswer(invocation -> {
            BoardDTO post = invocation.getArgument(1);
            assertEquals(12, post.getPostNum());
            assertEquals("수정 제목", post.getTitle());
            assertEquals("<p>수정 본문</p>", post.getContent());
            assertEquals("기존 작성자", post.getWriter());
            assertEquals("guest-secret", post.getGuestPassword());
            assertEquals(1, post.getNotice());
            return null;
        }).when(boardService).submitModifyPost(eq("tvspboard"), any(BoardDTO.class));

        ContentPostRequest payload = new ContentPostRequest();
        payload.setTitle(" 수정 제목 ");
        payload.setContent("<p onclick=\"bad()\">수정 본문</p>");
        payload.setWriter("바꾸려는 작성자");
        payload.setNotice(true);

        ResponseEntity<ContentPostResponse> response = controller.updatePost(
                "tVsPBoard", 12, payload, new MockHttpServletRequest());

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(12, response.getBody().getPostNum());
        assertEquals("/boards/tvspboard/readPost?postNum=12",
                response.getHeaders().getLocation().toString());
    }

    @Test
    void updatePostWithMedia_placesImageFirstAndYoutubeLast() throws Exception {
        ContentAdminController controller = controller(new PostContentSanitizer());
        BoardDTO existing = new BoardDTO();
        existing.setPostNum(12);
        existing.setWriter("운영자");
        when(boardService.getKoreanTitle("pvstboard")).thenReturn("프테전");
        when(boardService.readPost("pvstboard", 12)).thenReturn(existing);
        when(postImageService.store(any(), eq(""))).thenReturn(new PostImageResponse(
                "arbiter.jpg", "/uploadedImg/arbiter.jpg", "image/jpeg", 700, 394, 123_000));
        doAnswer(invocation -> {
            BoardDTO post = invocation.getArgument(1);
            assertTrue(post.getContent().startsWith("<figure class=\"sc-post-image\">"));
            assertTrue(post.getContent().contains("<p>23넥 본문</p>"));
            assertTrue(post.getContent().contains("https://www.youtube-nocookie.com/embed/csIPbJ719iw"));
            assertTrue(post.getContent().endsWith("</div>"));
            return null;
        }).when(boardService).submitModifyPost(eq("pvstboard"), any(BoardDTO.class));

        ContentPostForm form = new ContentPostForm();
        form.setTitle("23넥 아비터");
        form.setContent("<p>23넥 본문</p>");
        form.setImageAlt("23넥 참고 이미지");
        form.setYoutubeUrl("https://www.youtube.com/watch?v=csIPbJ719iw");
        MockMultipartFile image = new MockMultipartFile(
                "upload", "arbiter.jpg", "image/jpeg", new byte[] { 1, 2, 3 });

        ResponseEntity<ContentPostResponse> response = controller.updatePostWithMedia(
                "pvstboard", 12, form, image, new MockHttpServletRequest());

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(12, response.getBody().getPostNum());
    }

    @Test
    void updatePost_returnsNotFoundBeforeWritingMissingPost() throws Exception {
        ContentAdminController controller = controller(new PostContentSanitizer());
        when(boardService.getKoreanTitle("pvstboard")).thenReturn("프테전");
        when(boardService.readPost("pvstboard", 999)).thenReturn(null);

        ContentPostRequest payload = new ContentPostRequest();
        payload.setTitle("제목");
        payload.setContent("<p>본문</p>");

        assertThrows(ResourceNotFoundException.class,
                () -> controller.updatePost("pvstboard", 999, payload, new MockHttpServletRequest()));
        verify(boardService, never()).submitModifyPost(eq("pvstboard"), any(BoardDTO.class));
    }

    @Test
    void deletePost_returnsNoContentForExistingPost() throws Exception {
        ContentAdminController controller = controller(new PostContentSanitizer());
        BoardDTO existing = new BoardDTO();
        existing.setPostNum(12);
        when(boardService.getKoreanTitle("pvstboard")).thenReturn("프테전");
        when(boardService.readPost("pvstboard", 12)).thenReturn(existing);

        ResponseEntity<Void> response = controller.deletePost("pvstboard", 12);

        assertEquals(204, response.getStatusCodeValue());
        verify(boardService).deletePost("pvstboard", 12);
    }

    private ContentAdminController controller(PostContentSanitizer sanitizer) {
        return new ContentAdminController(boardService, sanitizer, postImageService, new ContentPostComposer());
    }
}
