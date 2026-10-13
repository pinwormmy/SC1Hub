package com.sc1hub.seo;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.service.BoardService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeoSitemapServiceTest {

    @Test
    void getSitemapXml_containsCanonicalPublicUrlsAndUsesCache() throws Exception {
        BoardService boardService = mock(BoardService.class);
        BoardListDTO board = new BoardListDTO();
        board.setBoardTitle("tVsZBoard");
        BoardDTO post = new BoardDTO();
        post.setPostNum(3);
        post.setRegDate(Date.from(Instant.parse("2026-07-01T00:00:00Z")));
        when(boardService.getBoardList()).thenReturn(Collections.singletonList(board));
        when(boardService.getSitemapPosts("tvszboard")).thenReturn(Arrays.asList(post, post));
        Clock clock = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        SeoSitemapService service = new SeoSitemapService(boardService, clock);

        String first = service.getSitemapXml();
        String second = service.getSitemapXml();

        assertTrue(first.contains("<loc>https://sc1hub.com/boards/tvszboard</loc>"));
        assertTrue(first.contains("<loc>https://sc1hub.com/boards/tvszboard/readPost?postNum=3</loc>"));
        assertTrue(first.contains("<lastmod>2026-07-01</lastmod>"));
        // 홈과 게시판 목록은 가장 최근 글의 날짜를 lastmod 로 따라간다.
        assertTrue(first.contains("<loc>https://sc1hub.com/</loc>\n    <lastmod>2026-07-01</lastmod>"));
        assertTrue(first.contains("<loc>https://sc1hub.com/boards/tvszboard</loc>\n    <lastmod>2026-07-01</lastmod>"));
        assertTrue(first.contains("<loc>https://sc1hub.com/guidelines</loc>\n  </url>"));
        assertFalse(first.contains("/login"));
        assertTrue(first == second);
        verify(boardService, times(1)).getBoardList();
        verify(boardService, times(1)).getSitemapPosts("tvszboard");
    }

    @Test
    void getSitemapXml_usesTheModifiedDateOfEditedPosts() throws Exception {
        BoardService boardService = mock(BoardService.class);
        BoardListDTO board = new BoardListDTO();
        board.setBoardTitle("tipboard");
        BoardDTO edited = new BoardDTO();
        edited.setPostNum(7);
        edited.setRegDate(Date.from(Instant.parse("2025-01-01T00:00:00Z")));
        edited.setModDate(Date.from(Instant.parse("2026-10-13T00:00:00Z")));
        when(boardService.getBoardList()).thenReturn(Collections.singletonList(board));
        when(boardService.getSitemapPosts("tipboard")).thenReturn(Collections.singletonList(edited));
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneId.of("Asia/Seoul"));

        String xml = new SeoSitemapService(boardService, clock).getSitemapXml();

        assertTrue(xml.contains("readPost?postNum=7</loc>\n    <lastmod>2026-10-13</lastmod>"));
        assertTrue(xml.contains("<loc>https://sc1hub.com/boards/tipboard</loc>\n    <lastmod>2026-10-13</lastmod>"));
        assertFalse(xml.contains("2025-01-01"));
    }

    @Test
    void getSitemapXml_refreshesAfterTenMinutes() throws Exception {
        BoardService boardService = mock(BoardService.class);
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(
                0L,
                TimeUnit.MINUTES.toMillis(9),
                TimeUnit.MINUTES.toMillis(11));
        when(boardService.getBoardList()).thenReturn(Collections.emptyList());
        SeoSitemapService service = new SeoSitemapService(boardService, clock);

        service.getSitemapXml();
        service.getSitemapXml();
        service.getSitemapXml();

        verify(boardService, times(2)).getBoardList();
    }
}
