package com.sc1hub.board;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.mapper.BoardMapper;
import com.sc1hub.member.MemberDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceImplTest {

    @Mock
    private BoardMapper boardMapper;

    @InjectMocks
    private BoardServiceImpl boardService;

    @Test
    void canWrite_returnsFalse_whenMemberNull() {
        assertFalse(boardService.canWrite("freeBoard", null));
    }

    @Test
    void canWrite_requiresAdminGrade_forAdminOnlyBoards() {
        MemberDTO admin = new MemberDTO();
        admin.setGrade(3);

        MemberDTO user = new MemberDTO();
        user.setGrade(2);

        assertTrue(boardService.canWrite("tVsTBoard", admin));
        assertFalse(boardService.canWrite("tvstboard", user));
    }

    @Test
    void canWrite_allowsNonAdminBoards_evenLowGrade() {
        MemberDTO user = new MemberDTO();
        user.setGrade(0);

        assertTrue(boardService.canWrite("freeBoard", user));
    }

    @Test
    void deletePost_throwsIllegalArgumentException_whenPostMissing() throws Exception {
        int postNum = 123;

        MemberDTO member = new MemberDTO();
        member.setId("user");
        member.setNickName("Nick");

        when(boardMapper.readPost(eq("freeboard"), eq(postNum))).thenReturn(null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> boardService.deletePost("FreeBoard", postNum, member)
        );
        assertTrue(ex.getMessage().contains("존재하지"));

        verify(boardMapper, never()).deletePost(anyString(), anyInt());
    }

    @Test
    void deletePost_throwsAccessDeniedException_whenNotWriterOrAdmin() throws Exception {
        int postNum = 123;

        BoardDTO post = new BoardDTO();
        post.setWriter("Alice");

        MemberDTO member = new MemberDTO();
        member.setId("user");
        member.setNickName("Bob");

        when(boardMapper.readPost(eq("freeboard"), eq(postNum))).thenReturn(post);

        assertThrows(AccessDeniedException.class, () -> boardService.deletePost("FreeBoard", postNum, member));

        verify(boardMapper, never()).deletePost(anyString(), anyInt());
    }

    @Test
    void deletePost_allowsAdmin() throws Exception {
        int postNum = 123;

        BoardDTO post = new BoardDTO();
        post.setWriter("Alice");

        MemberDTO admin = new MemberDTO();
        admin.setId("admin");
        admin.setNickName("Someone");

        when(boardMapper.readPost(eq("freeboard"), eq(postNum))).thenReturn(post);

        boardService.deletePost("FreeBoard", postNum, admin);

        verify(boardMapper).deletePost("freeboard", postNum);
    }

    @Test
    void deletePost_allowsWriter() throws Exception {
        int postNum = 123;

        BoardDTO post = new BoardDTO();
        post.setWriter("Alice");

        MemberDTO member = new MemberDTO();
        member.setId("user");
        member.setNickName("Alice");

        when(boardMapper.readPost(eq("freeboard"), eq(postNum))).thenReturn(post);

        boardService.deletePost("FreeBoard", postNum, member);

        verify(boardMapper).deletePost("freeboard", postNum);
    }

    @Test
    void pageSetting_setsDefaults_andCalculates() throws Exception {
        PageDTO page = new PageDTO();
        page.setRecentPage(0);
        page.setSearchType(null);
        page.setKeyword(null);

        when(boardMapper.countTotalPost(eq("freeboard"), any(PageDTO.class))).thenReturn(30);

        PageDTO result = boardService.pageSetting("FreeBoard", page);

        assertSame(page, result);
        assertEquals(1, result.getRecentPage());
        assertEquals("title", result.getSearchType());
        assertEquals("", result.getKeyword());
        assertEquals(15, result.getDisplayPostLimit());
        assertEquals(2, result.getTotalPage());
        assertEquals(1, result.getPageBeginPoint());
        assertEquals(2, result.getPageEndPoint());
        assertEquals(0, result.getPostBeginPoint());
        assertEquals(15, result.getPostEndPoint());
    }

    @Test
    void increaseViewCount_updatesWhenIpNotSeen() throws Exception {
        int postNum = 10;
        String ip = "127.0.0.1";

        when(boardMapper.checkViewUserIp("freeboard", postNum, ip)).thenReturn(0);

        boardService.increaseViewCount("FreeBoard", postNum, ip);

        verify(boardMapper).saveViewUserIp("freeboard", postNum, ip);
        verify(boardMapper).updateViews("freeboard", postNum);
    }

    @Test
    void increaseViewCount_doesNothingWhenIpSeen() throws Exception {
        int postNum = 10;
        String ip = "127.0.0.1";

        when(boardMapper.checkViewUserIp("freeboard", postNum, ip)).thenReturn(1);

        boardService.increaseViewCount("FreeBoard", postNum, ip);

        verify(boardMapper, never()).saveViewUserIp(anyString(), anyInt(), anyString());
        verify(boardMapper, never()).updateViews(anyString(), anyInt());
    }

    @Test
    void insertRecommendation_insertsAndSyncsTotalCount_whenNotAlreadyRecommended() {
        RecommendDTO dto = new RecommendDTO();
        dto.setPostNum(55);
        dto.setUserId("user");

        when(boardMapper.checkRecommendation("freeboard", dto)).thenReturn(0);
        when(boardMapper.getActualRecommendCount("freeboard", 55)).thenReturn(1);
        when(boardMapper.getRecommendCount("freeboard", 55)).thenReturn(0);

        boardService.insertRecommendation("FreeBoard", dto);

        verify(boardMapper).insertRecommendation("freeboard", dto);
        verify(boardMapper).updateTotalRecommendCount("freeboard", 55);
    }

    @Test
    void insertRecommendation_throws_whenAlreadyRecommended() {
        RecommendDTO dto = new RecommendDTO();
        dto.setPostNum(55);
        dto.setUserId("user");

        when(boardMapper.checkRecommendation("freeboard", dto)).thenReturn(1);

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> boardService.insertRecommendation("FreeBoard", dto)
        );
        assertTrue(ex.getMessage().contains("이미 추천"));

        verify(boardMapper, never()).insertRecommendation(anyString(), any(RecommendDTO.class));
    }
}

