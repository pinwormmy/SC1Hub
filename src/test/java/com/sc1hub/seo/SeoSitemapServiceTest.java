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
        assertFalse(first.contains("/login"));
        assertTrue(first == second);
        verify(boardService, times(1)).getBoardList();
        verify(boardService, times(1)).getSitemapPosts("tvszboard");
    }
}
