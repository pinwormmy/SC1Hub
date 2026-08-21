package com.sc1hub.content.controller;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.board.service.PostContentSanitizer;
import com.sc1hub.content.dto.ContentPostRequest;
import com.sc1hub.content.dto.ContentPostResponse;
import com.sc1hub.file.service.PostImageService;
import com.sc1hub.member.dto.MemberDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
        ContentAdminController controller = new ContentAdminController(boardService, sanitizer, postImageService);
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
}
