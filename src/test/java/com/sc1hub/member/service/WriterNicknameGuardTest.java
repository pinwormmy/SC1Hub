package com.sc1hub.member.service;

import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WriterNicknameGuardTest {

    @Mock
    private BoardMapper boardMapper;

    private BoardListDTO board(String title) {
        BoardListDTO board = new BoardListDTO();
        board.setBoardTitle(title);
        return board;
    }

    @Test
    void detectsNicknameUsedAsWriterOnAnyBoard() {
        when(boardMapper.getBoardList()).thenReturn(List.of(board("funBoard"), board("tipboard")));
        when(boardMapper.countMemberPostsByWriter("funboard", "old-author")).thenReturn(0);
        when(boardMapper.countMemberPostsByWriter("tipboard", "old-author")).thenReturn(2);

        assertTrue(new WriterNicknameGuard(boardMapper).hasAuthoredPosts(" old-author "));
    }

    @Test
    void unusedNicknameIsAvailable() {
        when(boardMapper.getBoardList()).thenReturn(List.of(board("funboard")));
        when(boardMapper.countMemberPostsByWriter("funboard", "fresh")).thenReturn(0);

        assertFalse(new WriterNicknameGuard(boardMapper).hasAuthoredPosts("fresh"));
    }

    @Test
    void blankNicknameNeverQueries() {
        assertFalse(new WriterNicknameGuard(boardMapper).hasAuthoredPosts("  "));
        verify(boardMapper, never()).getBoardList();
        verify(boardMapper, never()).countMemberPostsByWriter(anyString(), any());
    }

    @Test
    void oneBrokenBoardDoesNotHideMatchesOnOthers() {
        when(boardMapper.getBoardList()).thenReturn(List.of(board("noticeboard"), board("tipboard")));
        when(boardMapper.countMemberPostsByWriter("noticeboard", "x")).thenThrow(new IllegalStateException("synthetic"));
        when(boardMapper.countMemberPostsByWriter("tipboard", "x")).thenReturn(1);

        assertTrue(new WriterNicknameGuard(boardMapper).hasAuthoredPosts("x"));
    }
}
