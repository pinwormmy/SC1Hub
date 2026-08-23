package com.sc1hub.board.controller;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDataDTO;
import com.sc1hub.board.dto.CommentDTO;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.common.exception.ResourceNotFoundException;
import com.sc1hub.member.dto.MemberDTO;
import com.sc1hub.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Collections;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BoardControllerTest {

    @Mock
    private BoardService boardService;

    @Mock
    private MemberService memberService;

    @InjectMocks
    private BoardController boardController;

    @Test
    void submitPost_returnsAlert_whenMemberMissing() throws Exception {
        BoardDTO post = new BoardDTO();
        post.setWriter("Alice");

        MockHttpServletRequest request = new MockHttpServletRequest();
        Model model = new ExtendedModelMap();

        String view = boardController.submitPost("freeBoard", post, request, model);

        assertEquals("alert", view);
        assertNotNull(model.asMap().get("msg"));
        verify(boardService, never()).submitPost(anyString(), any(BoardDTO.class));
    }

    @Test
    void submitPost_allowsGuestWriter_whenFunBoardAndPasswordProvided() throws Exception {
        BoardDTO post = new BoardDTO();
        post.setWriter("비회원작성자");
        post.setGuestPassword("1234");

        MockHttpServletRequest request = new MockHttpServletRequest();
        Model model = new ExtendedModelMap();

        String view = boardController.submitPost("funBoard", post, request, model);

        assertEquals("redirect:/boards/funboard", view);
        verify(boardService).submitPost("funboard", post);
    }

    @Test
    void submitPost_returnsAlert_whenFunBoardGuestPasswordMissing() throws Exception {
        BoardDTO post = new BoardDTO();
        post.setWriter("비회원작성자");

        MockHttpServletRequest request = new MockHttpServletRequest();
        Model model = new ExtendedModelMap();

        String view = boardController.submitPost("funBoard", post, request, model);

        assertEquals("alert", view);
        assertEquals("이름과 비밀번호를 확인해주세요", model.asMap().get("msg"));
        assertEquals("/boards/funboard/writePost", model.asMap().get("url"));
        verify(boardService, never()).submitPost(anyString(), any(BoardDTO.class));
    }

    @Test
    void listData_allowsGuestWrite_whenBoardIsFunBoard() throws Exception {
        PageDTO page = new PageDTO();
        MockHttpSession session = new MockHttpSession();

        when(boardService.getKoreanTitle("funboard")).thenReturn("꿀잼놀이터");
        when(boardService.pageSetting("funboard", page)).thenReturn(page);
        when(boardService.showSelfNoticeList("funboard")).thenReturn(Collections.emptyList());
        when(boardService.showPostList("funboard", page)).thenReturn(Collections.emptyList());

        BoardListDataDTO response = boardController.listData("funBoard", page, session);

        assertTrue(response.isCanWrite());
        verify(boardService, never()).canWrite(anyString(), any(MemberDTO.class));
    }

    @Test
    void readPost_returnsRealNotFoundBeforeUpdatingViews() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boards/tipboard/readPost");
        when(boardService.getKoreanTitle("tipboard")).thenReturn("꿀팁보급고");
        when(boardService.readPost("tipboard", 999)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> boardController.readPost("tipBoard", 999, new ExtendedModelMap(), request));

        verify(boardService, never()).increaseViewCount(anyString(), anyInt(), anyString());
    }

    @Test
    void modifyPost_allowsGuestAccess_whenPasswordMatches() throws Exception {
        BoardDTO post = new BoardDTO();
        post.setPostNum(7);
        post.setWriter("비회원작성자");
        post.setGuestPassword("1234");

        MockHttpSession session = new MockHttpSession();
        Model model = new ExtendedModelMap();

        when(boardService.readPost("funboard", 7)).thenReturn(post);
        when(boardService.getKoreanTitle("funboard")).thenReturn("꿀잼놀이터");

        String view = boardController.modifyPost("funBoard", model, 7, "1234", session);

        assertEquals("board/modifyPost", view);
        assertEquals(post, model.asMap().get("post"));
    }

    @Test
    void submitModifyPost_allowsAuthorizedGuestSession() throws Exception {
        BoardDTO existingPost = new BoardDTO();
        existingPost.setPostNum(7);
        existingPost.setWriter("비회원작성자");
        existingPost.setGuestPassword("1234");

        BoardDTO modifiedPost = new BoardDTO();
        modifiedPost.setPostNum(7);
        modifiedPost.setTitle("수정 제목");
        modifiedPost.setContent("수정 내용");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        Set<String> authorized = new HashSet<>();
        authorized.add("funboard:7");
        session.setAttribute("authorizedGuestPostKeys", authorized);
        request.setSession(session);
        Model model = new ExtendedModelMap();

        when(boardService.readPost("funboard", 7)).thenReturn(existingPost);

        String view = boardController.submitModifyPost("funBoard", modifiedPost, request, model);

        assertEquals("redirect:/boards/funboard/readPost?postNum=7", view);
        assertEquals("비회원작성자", modifiedPost.getWriter());
        assertEquals("1234", modifiedPost.getGuestPassword());
        verify(boardService).submitModifyPost("funboard", modifiedPost);
    }

    @Test
    void deletePost_allowsGuestDelete_whenPasswordMatches() throws Exception {
        BoardDTO existingPost = new BoardDTO();
        existingPost.setPostNum(7);
        existingPost.setWriter("비회원작성자");
        existingPost.setGuestPassword("1234");

        BoardDTO requestPost = new BoardDTO();
        requestPost.setPostNum(7);
        requestPost.setGuestPassword("1234");

        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        when(boardService.readPost("funboard", 7)).thenReturn(existingPost);

        String view = boardController.deletePost("funBoard", requestPost, request, redirectAttributes);

        assertEquals("redirect:/boards/funboard", view);
        verify(boardService).deletePost("funboard", 7);
        verify(boardService, never()).deletePost(anyString(), anyInt(), any(MemberDTO.class));
    }

    @Test
    void submitModifyPost_returnsAlert_whenMemberDoesNotMatchWriter() throws Exception {
        BoardDTO post = new BoardDTO();
        post.setPostNum(3);
        post.setWriter("Alice");

        BoardDTO existingPost = new BoardDTO();
        existingPost.setPostNum(3);
        existingPost.setWriter("Alice");

        MemberDTO member = new MemberDTO();
        member.setId("user");
        member.setNickName("Bob");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("member", member);
        request.setSession(session);
        Model model = new ExtendedModelMap();

        when(boardService.readPost("freeboard", 3)).thenReturn(existingPost);

        String view = boardController.submitModifyPost("freeBoard", post, request, model);

        assertEquals("alert", view);
        assertNotNull(model.asMap().get("msg"));
        verify(boardService, never()).submitModifyPost(anyString(), any(BoardDTO.class));
    }

    @Test
    void addComment_usesSessionIdentityAndClearsForgedGuestFields() throws Exception {
        MemberDTO member = new MemberDTO();
        member.setId("owner");
        member.setNickName("회원");
        CommentDTO comment = new CommentDTO();
        comment.setPostNum(7);
        comment.setId("forged");
        comment.setNickname("사칭");
        comment.setPassword("secret");
        comment.setContent("정상 댓글");

        ResponseEntity<Map<String, String>> response =
                boardController.addComment("FunBoard", comment, member);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("owner", comment.getId());
        assertEquals(null, comment.getNickname());
        assertEquals(null, comment.getPassword());
        verify(boardService).addComment("funboard", comment);
    }

    @Test
    void addComment_requiresGuestNicknameAndPasswordAndClearsForgedId() throws Exception {
        CommentDTO comment = new CommentDTO();
        comment.setPostNum(7);
        comment.setId("forged");
        comment.setNickname("비회원");
        comment.setContent("정상 댓글");

        ResponseEntity<Map<String, String>> response =
                boardController.addComment("FunBoard", comment, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(null, comment.getId());
        verify(boardService, never()).addComment(anyString(), any(CommentDTO.class));
    }

    @Test
    void addComment_acceptsTrimmedGuestCredentialsWithoutClientIdentity() throws Exception {
        CommentDTO comment = new CommentDTO();
        comment.setPostNum(7);
        comment.setId("forged");
        comment.setNickname(" 비회원 ");
        comment.setPassword(" secret ");
        comment.setContent("정상 댓글");

        ResponseEntity<Map<String, String>> response =
                boardController.addComment("FunBoard", comment, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(null, comment.getId());
        assertEquals("비회원", comment.getNickname());
        assertEquals("secret", comment.getPassword());
        verify(boardService).addComment("funboard", comment);
    }

    @Test
    void addComment_acceptsCommentColumnLengthBoundaries() throws Exception {
        CommentDTO comment = new CommentDTO();
        comment.setPostNum(7);
        comment.setNickname(repeat("닉", 50));
        comment.setPassword(repeat("p", 100));
        comment.setContent(repeat("댓", 500));

        ResponseEntity<Map<String, String>> response =
                boardController.addComment("FunBoard", comment, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(boardService).addComment("funboard", comment);
    }

    @Test
    void addComment_rejectsContentLongerThanDatabaseColumn() throws Exception {
        CommentDTO comment = guestComment(repeat("댓", 501), "비회원", "secret");

        ResponseEntity<Map<String, String>> response =
                boardController.addComment("FunBoard", comment, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(boardService, never()).addComment(anyString(), any(CommentDTO.class));
    }

    @Test
    void addComment_rejectsGuestNicknameLongerThanDatabaseColumn() throws Exception {
        CommentDTO comment = guestComment("댓글", repeat("닉", 51), "secret");

        ResponseEntity<Map<String, String>> response =
                boardController.addComment("FunBoard", comment, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(boardService, never()).addComment(anyString(), any(CommentDTO.class));
    }

    @Test
    void addComment_rejectsGuestPasswordLongerThanDatabaseColumn() throws Exception {
        CommentDTO comment = guestComment("댓글", "비회원", repeat("p", 101));

        ResponseEntity<Map<String, String>> response =
                boardController.addComment("FunBoard", comment, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(boardService, never()).addComment(anyString(), any(CommentDTO.class));
    }

    @Test
    void showCommentList_exposesOnlyServerComputedDeletePermissions() throws Exception {
        CommentDTO owned = new CommentDTO();
        owned.setId("owner");
        CommentDTO other = new CommentDTO();
        other.setId("other");
        CommentDTO guest = new CommentDTO();
        guest.setPassword("secret");
        CommentDTO legacyGuest = new CommentDTO();
        List<CommentDTO> comments = Arrays.asList(owned, other, guest, legacyGuest);
        PageDTO page = new PageDTO();
        MemberDTO member = new MemberDTO();
        member.setId("owner");
        when(boardService.showCommentList("funboard", page)).thenReturn(comments);

        List<CommentDTO> result = boardController.showCommentList("FunBoard", page, member);

        assertEquals(comments, result);
        assertTrue(owned.isDeletable());
        assertFalse(other.isDeletable());
        assertTrue(guest.isGuestComment());
        assertTrue(guest.isDeletable());
        assertTrue(guest.isPasswordRequired());
        assertTrue(legacyGuest.isGuestComment());
        assertFalse(legacyGuest.isDeletable());
        assertFalse(legacyGuest.isPasswordRequired());
    }

    @Test
    void showCommentList_doesNotPromptAdministratorForGuestPassword() throws Exception {
        CommentDTO guest = new CommentDTO();
        guest.setPassword("secret");
        CommentDTO legacyGuest = new CommentDTO();
        List<CommentDTO> comments = Arrays.asList(guest, legacyGuest);
        PageDTO page = new PageDTO();
        MemberDTO admin = new MemberDTO();
        admin.setGrade(3);
        when(boardService.showCommentList("funboard", page)).thenReturn(comments);

        boardController.showCommentList("FunBoard", page, admin);

        assertTrue(guest.isDeletable());
        assertFalse(guest.isPasswordRequired());
        assertTrue(legacyGuest.isDeletable());
        assertFalse(legacyGuest.isPasswordRequired());
    }

    @Test
    void showCommentList_doesNotCreateSessionForAnonymousReader() throws Exception {
        when(boardService.showCommentList(eq("funboard"), any(PageDTO.class)))
                .thenReturn(Collections.emptyList());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(boardController).build();

        mockMvc.perform(post("/boards/FunBoard/showCommentList")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertNull(result.getRequest().getSession(false)));
    }

    @Test
    void deleteComment_returnsForbiddenWhenServiceRejectsCaller() throws Exception {
        doThrow(new java.nio.file.AccessDeniedException("denied"))
                .when(boardService).deleteComment("funboard", 9, null, null);

        ResponseEntity<Map<String, String>> response =
                boardController.deleteComment("FunBoard", 9, null, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("댓글 삭제 권한이 없습니다.", response.getBody().get("message"));
    }

    @Test
    void deleteComment_returnsNotFoundWhenCommentDoesNotExist() throws Exception {
        doThrow(new IllegalArgumentException("missing"))
                .when(boardService).deleteComment("funboard", 404, null, null);

        ResponseEntity<Map<String, String>> response =
                boardController.deleteComment("FunBoard", 404, null, null);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("존재하지 않는 댓글입니다.", response.getBody().get("message"));
    }

    @Test
    void deleteComment_bindsGuestPasswordFromFormBody() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(boardController).build();

        mockMvc.perform(post("/boards/FunBoard/deleteComment")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("commentNum=9&password=secret"))
                .andExpect(status().isOk());

        verify(boardService).deleteComment("funboard", 9, null, "secret");
    }

    private CommentDTO guestComment(String content, String nickname, String password) {
        CommentDTO comment = new CommentDTO();
        comment.setPostNum(7);
        comment.setContent(content);
        comment.setNickname(nickname);
        comment.setPassword(password);
        return comment;
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
