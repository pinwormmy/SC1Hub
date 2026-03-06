package com.sc1hub.board.controller;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDataDTO;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.member.dto.MemberDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardControllerTest {

    @Mock
    private BoardService boardService;

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

        assertEquals("redirect:/boards/funBoard", view);
        verify(boardService).submitPost("funBoard", post);
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
        assertEquals("/boards/funBoard/writePost", model.asMap().get("url"));
        verify(boardService, never()).submitPost(anyString(), any(BoardDTO.class));
    }

    @Test
    void listData_allowsGuestWrite_whenBoardIsFunBoard() throws Exception {
        PageDTO page = new PageDTO();
        MockHttpSession session = new MockHttpSession();

        when(boardService.getKoreanTitle("funBoard")).thenReturn("꿀잼놀이터");
        when(boardService.pageSetting("funBoard", page)).thenReturn(page);
        when(boardService.showSelfNoticeList("funBoard")).thenReturn(Collections.emptyList());
        when(boardService.showPostList("funBoard", page)).thenReturn(Collections.emptyList());

        BoardListDataDTO response = boardController.listData("funBoard", page, session);

        assertTrue(response.isCanWrite());
        verify(boardService, never()).canWrite(anyString(), any(MemberDTO.class));
    }

    @Test
    void modifyPost_allowsGuestAccess_whenPasswordMatches() throws Exception {
        BoardDTO post = new BoardDTO();
        post.setPostNum(7);
        post.setWriter("비회원작성자");
        post.setGuestPassword("1234");

        MockHttpSession session = new MockHttpSession();
        Model model = new ExtendedModelMap();

        when(boardService.readPost("funBoard", 7)).thenReturn(post);
        when(boardService.getKoreanTitle("funBoard")).thenReturn("꿀잼놀이터");

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

        when(boardService.readPost("funBoard", 7)).thenReturn(existingPost);

        String view = boardController.submitModifyPost("funBoard", modifiedPost, request, model);

        assertEquals("redirect:/boards/funBoard/readPost?postNum=7", view);
        assertEquals("비회원작성자", modifiedPost.getWriter());
        assertEquals("1234", modifiedPost.getGuestPassword());
        verify(boardService).submitModifyPost("funBoard", modifiedPost);
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

        when(boardService.readPost("funBoard", 7)).thenReturn(existingPost);

        String view = boardController.deletePost("funBoard", requestPost, request, redirectAttributes);

        assertEquals("redirect:/boards/funBoard", view);
        verify(boardService).deletePost("funBoard", 7);
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

        when(boardService.readPost("freeBoard", 3)).thenReturn(existingPost);

        String view = boardController.submitModifyPost("freeBoard", post, request, model);

        assertEquals("alert", view);
        assertNotNull(model.asMap().get("msg"));
        verify(boardService, never()).submitModifyPost(anyString(), any(BoardDTO.class));
    }
}
