package com.sc1hub.board.controller;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.member.dto.MemberDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    void submitModifyPost_returnsAlert_whenMemberDoesNotMatchWriter() throws Exception {
        BoardDTO post = new BoardDTO();
        post.setPostNum(3);
        post.setWriter("Alice");

        MemberDTO member = new MemberDTO();
        member.setId("user");
        member.setNickName("Bob");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("member", member);
        Model model = new ExtendedModelMap();

        String view = boardController.submitModifyPost("freeBoard", post, request, model);

        assertEquals("alert", view);
        assertNotNull(model.asMap().get("msg"));
        verify(boardService, never()).submitModifyPost(anyString(), any(BoardDTO.class));
    }
}