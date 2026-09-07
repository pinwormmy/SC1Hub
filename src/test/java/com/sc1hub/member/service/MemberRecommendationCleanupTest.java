package com.sc1hub.member.service;

import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.mapper.BoardMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberRecommendationCleanupTest {

    @Mock
    private BoardMapper boardMapper;

    private BoardListDTO board(String title) {
        BoardListDTO board = new BoardListDTO();
        board.setBoardTitle(title);
        return board;
    }

    @Test
    void deletesRecommendationsOnEveryBoardAndResyncsAffectedPosts() {
        when(boardMapper.getBoardList()).thenReturn(List.of(board("funBoard"), board("tipboard")));
        when(boardMapper.selectRecommendedPostNumsByUser("funboard", "leaver")).thenReturn(List.of(3, 8));
        when(boardMapper.selectRecommendedPostNumsByUser("tipboard", "leaver")).thenReturn(List.of());
        when(boardMapper.deleteRecommendationsByUser("funboard", "leaver")).thenReturn(2);

        int removed = new MemberRecommendationCleanup(boardMapper).removeRecommendationsOf("leaver");

        assertEquals(2, removed);
        verify(boardMapper).updateTotalRecommendCount("funboard", 3);
        verify(boardMapper).updateTotalRecommendCount("funboard", 8);
        verify(boardMapper, never()).deleteRecommendationsByUser("tipboard", "leaver");
        verify(boardMapper, never()).updateTotalRecommendCount(eq("tipboard"), anyInt());
    }

    @Test
    void blankMemberIdTouchesNothing() {
        assertEquals(0, new MemberRecommendationCleanup(boardMapper).removeRecommendationsOf(" "));
        verify(boardMapper, never()).getBoardList();
        verify(boardMapper, never()).deleteRecommendationsByUser(anyString(), anyString());
    }
}
