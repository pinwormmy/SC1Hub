package com.sc1hub.board.service;

import com.sc1hub.assistant.config.AssistantBotProperties;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.dto.CommentDTO;
import com.sc1hub.board.mapper.BoardMapper;
import com.sc1hub.board.support.GuestPasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestPasswordMigrationTest {

    @Mock
    private BoardMapper boardMapper;

    private final GuestPasswordHasher hasher = new GuestPasswordHasher(new BCryptPasswordEncoder(4));

    private GuestPasswordMigration migration() {
        AssistantBotProperties botProperties = new AssistantBotProperties();
        botProperties.setPersonaName("프징징봇");
        AssistantBotProperties.PersonaProperties persona = new AssistantBotProperties.PersonaProperties();
        persona.setName("고수봇");
        botProperties.setPersonas(List.of(persona));
        return new GuestPasswordMigration(boardMapper, hasher, botProperties, true);
    }

    private static BoardListDTO board(String title) {
        BoardListDTO board = new BoardListDTO();
        board.setBoardTitle(title);
        return board;
    }

    @Test
    void hashesPlaintextRowsRekeysBotRowsAndLeavesHashedRowsAlone() throws Exception {
        BoardDTO guestPost = new BoardDTO();
        guestPost.setPostNum(1);
        guestPost.setWriter("손님");
        guestPost.setGuestPassword("1234");
        BoardDTO botPost = new BoardDTO();
        botPost.setPostNum(2);
        botPost.setWriter("고수봇");
        botPost.setGuestPassword("shared-bot-password");
        BoardDTO alreadyHashed = new BoardDTO();
        alreadyHashed.setPostNum(3);
        alreadyHashed.setWriter("손님");
        alreadyHashed.setGuestPassword(hasher.hash("x"));

        CommentDTO guestComment = new CommentDTO();
        guestComment.setCommentNum(10);
        guestComment.setNickname("손님");
        guestComment.setPassword("pw");
        CommentDTO botComment = new CommentDTO();
        botComment.setCommentNum(11);
        botComment.setNickname("프징징봇");
        botComment.setPassword("shared-bot-password");
        CommentDTO memberCommentNamedLikeBot = new CommentDTO();
        memberCommentNamedLikeBot.setCommentNum(12);
        memberCommentNamedLikeBot.setId("member1");
        memberCommentNamedLikeBot.setNickname("프징징봇");
        memberCommentNamedLikeBot.setPassword("keep");

        when(boardMapper.getBoardList()).thenReturn(List.of(board("funboard"), board("tipboard")));
        when(boardMapper.selectLegacyGuestPostPasswords(eq("funboard"), anyInt()))
                .thenReturn(List.of(guestPost, botPost, alreadyHashed));
        when(boardMapper.selectLegacyCommentPasswords(eq("funboard"), anyInt()))
                .thenReturn(List.of(guestComment, botComment, memberCommentNamedLikeBot));
        when(boardMapper.selectLegacyCommentPasswords(eq("tipboard"), anyInt())).thenReturn(List.of());
        when(boardMapper.updateGuestPostPasswordIfUnchanged(anyString(), anyInt(), anyString(), anyString())).thenReturn(1);
        when(boardMapper.updateCommentPasswordIfUnchanged(anyString(), anyInt(), anyString(), anyString())).thenReturn(1);

        GuestPasswordMigration.Summary summary = migration().run();

        ArgumentCaptor<String> postHash = ArgumentCaptor.forClass(String.class);
        verify(boardMapper).updateGuestPostPasswordIfUnchanged(eq("funboard"), eq(1), eq("1234"), postHash.capture());
        assertTrue(hasher.matches("1234", postHash.getValue()));

        ArgumentCaptor<String> botHash = ArgumentCaptor.forClass(String.class);
        verify(boardMapper).updateGuestPostPasswordIfUnchanged(eq("funboard"), eq(2), eq("shared-bot-password"), botHash.capture());
        assertFalse(hasher.matches("shared-bot-password", botHash.getValue()), "봇 글은 공유 비밀번호로 더 이상 열리지 않는다");
        verify(boardMapper, never()).updateGuestPostPasswordIfUnchanged(eq("funboard"), eq(3), anyString(), anyString());

        ArgumentCaptor<String> commentHash = ArgumentCaptor.forClass(String.class);
        verify(boardMapper).updateCommentPasswordIfUnchanged(eq("funboard"), eq(10), eq("pw"), commentHash.capture());
        assertTrue(hasher.matches("pw", commentHash.getValue()));
        ArgumentCaptor<String> botCommentHash = ArgumentCaptor.forClass(String.class);
        verify(boardMapper).updateCommentPasswordIfUnchanged(eq("funboard"), eq(11), eq("shared-bot-password"), botCommentHash.capture());
        assertFalse(hasher.matches("shared-bot-password", botCommentHash.getValue()));
        ArgumentCaptor<String> memberHash = ArgumentCaptor.forClass(String.class);
        verify(boardMapper).updateCommentPasswordIfUnchanged(eq("funboard"), eq(12), eq("keep"), memberHash.capture());
        assertTrue(hasher.matches("keep", memberHash.getValue()), "회원 댓글(id 있음)은 별명이 봇과 같아도 재잠금하지 않는다");

        // guest_password 컬럼은 funboard 에만 있으므로 다른 게시판의 글은 조회하지 않는다.
        verify(boardMapper, never()).selectLegacyGuestPostPasswords(eq("tipboard"), anyInt());

        assertEquals(6, summary.candidates());
        assertEquals(5, summary.upgraded());
        assertEquals(2, summary.rekeyedBotRows());
        assertEquals(1, summary.skipped());
        assertEquals(0, summary.failed());
        assertFalse(summary.truncated());
        assertNull(summary.error());
    }

    @Test
    void neverFailsStartupWhenTheDatabaseIsUnavailable() throws Exception {
        when(boardMapper.getBoardList()).thenThrow(new IllegalStateException("db down"));

        GuestPasswordMigration.Summary summary = migration().run();

        assertEquals("IllegalStateException", summary.error());
        assertEquals(0, summary.upgraded());
    }
}
