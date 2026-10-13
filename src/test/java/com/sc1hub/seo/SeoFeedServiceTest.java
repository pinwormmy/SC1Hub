package com.sc1hub.seo;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.service.BoardService;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeoFeedServiceTest {

    @Test
    void feedListsNewestPostsAcrossBoardsWithoutTheFunBoard() throws Exception {
        BoardService boardService = mock(BoardService.class);
        when(boardService.getBoardList()).thenReturn(Arrays.asList(
                board("zVsZBoard", "저저전 게시판"), board("funboard", "꿀잼놀이터"), board("tipboard", "꿀팁보급고")));
        when(boardService.getFeedPosts(eq("zvszboard"), anyInt())).thenReturn(List.of(
                post(20, "뮤탈 싸움 <판단법> & 스커지", "<h2>요약</h2><p>싸울 때\u0001와 뺄 때</p>", "2026-10-12T07:55:35Z")));
        when(boardService.getFeedPosts(eq("tipboard"), anyInt())).thenReturn(List.of(
                post(87, "장비 추천", "<p>마우스</p>", "2026-10-02T02:45:49Z")));
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        SeoFeedService service = new SeoFeedService(boardService, clock);

        String xml = service.getRssXml();

        // 잘 만들어진 XML 이어야 수집기가 통째로 버리지 않는다.
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertTrue(xml.contains("<title>뮤탈 싸움 &lt;판단법&gt; &amp; 스커지</title>"));
        assertTrue(xml.contains("<link>https://sc1hub.com/boards/zvszboard/readPost?postNum=20</link>"));
        assertTrue(xml.contains("<description>요약 싸울 때와 뺄 때</description>"));
        assertTrue(xml.contains("<category>저저전 게시판</category>"));
        assertTrue(xml.contains("<pubDate>Mon, 12 Oct 2026 16:55:35 +0900</pubDate>"));
        assertTrue(xml.indexOf("postNum=20") < xml.indexOf("postNum=87"));
        assertFalse(xml.contains("funboard"));
        verify(boardService, never()).getFeedPosts(eq("funboard"), anyInt());

        assertEquals(xml, service.getRssXml());
        verify(boardService, times(1)).getBoardList();
    }

    private static BoardListDTO board(String boardTitle, String koreanTitle) {
        BoardListDTO board = new BoardListDTO();
        board.setBoardTitle(boardTitle);
        board.setKoreanTitle(koreanTitle);
        return board;
    }

    private static BoardDTO post(int postNum, String title, String content, String instant) {
        BoardDTO post = new BoardDTO();
        post.setPostNum(postNum);
        post.setTitle(title);
        post.setContent(content);
        post.setRegDate(Date.from(Instant.parse(instant)));
        return post;
    }
}
