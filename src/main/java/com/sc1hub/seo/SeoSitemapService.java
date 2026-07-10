package com.sc1hub.seo;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.board.support.BoardTitleNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class SeoSitemapService {

    private static final long CACHE_MILLIS = 60L * 60L * 1000L;
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private final BoardService boardService;
    private final Clock clock;
    private volatile CachedSitemap cachedSitemap;

    @Autowired
    public SeoSitemapService(BoardService boardService) {
        this(boardService, Clock.systemDefaultZone());
    }

    SeoSitemapService(BoardService boardService, Clock clock) {
        this.boardService = boardService;
        this.clock = clock;
    }

    public String getSitemapXml() {
        long now = clock.millis();
        CachedSitemap current = cachedSitemap;
        if (current != null && now - current.createdAtMillis < CACHE_MILLIS) {
            return current.xml;
        }

        synchronized (this) {
            current = cachedSitemap;
            if (current != null && now - current.createdAtMillis < CACHE_MILLIS) {
                return current.xml;
            }
            String xml = buildSitemapXml();
            cachedSitemap = new CachedSitemap(now, xml);
            return xml;
        }
    }

    private String buildSitemapXml() {
        List<SitemapEntry> entries = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        addEntry(entries, seenUrls, SeoUrlPolicy.CANONICAL_ORIGIN + "/", null);
        addEntry(entries, seenUrls, SeoUrlPolicy.CANONICAL_ORIGIN + "/guidelines", null);
        addEntry(entries, seenUrls, SeoUrlPolicy.CANONICAL_ORIGIN + "/strategy-tips", null);

        List<BoardListDTO> boards = boardService.getBoardList();
        if (boards == null) {
            boards = Collections.emptyList();
        }
        for (BoardListDTO board : boards) {
            addBoardEntries(entries, seenUrls, board);
        }
        return render(entries);
    }

    private void addBoardEntries(List<SitemapEntry> entries, Set<String> seenUrls, BoardListDTO board) {
        if (board == null) {
            return;
        }

        final String boardTitle;
        try {
            boardTitle = BoardTitleNormalizer.requireValid(board.getBoardTitle());
        } catch (IllegalArgumentException e) {
            log.warn("사이트맵에서 올바르지 않은 게시판 식별자를 제외합니다: {}", board.getBoardTitle());
            return;
        }

        String boardUrl = SeoUrlPolicy.CANONICAL_ORIGIN + "/boards/" + boardTitle;
        addEntry(entries, seenUrls, boardUrl, null);
        try {
            List<BoardDTO> posts = boardService.getSitemapPosts(boardTitle);
            if (posts == null) {
                return;
            }
            for (BoardDTO post : posts) {
                if (post == null || post.getPostNum() <= 0) {
                    continue;
                }
                String postUrl = boardUrl + "/readPost?postNum=" + post.getPostNum();
                addEntry(entries, seenUrls, postUrl, toLocalDate(post.getRegDate()));
            }
        } catch (Exception e) {
            log.warn("사이트맵 게시글 조회에 실패했습니다. boardTitle={}", boardTitle, e);
        }
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(clock.getZone()).toLocalDate();
    }

    private void addEntry(List<SitemapEntry> entries, Set<String> seenUrls, String url, LocalDate lastModified) {
        if (seenUrls.add(url)) {
            entries.add(new SitemapEntry(url, lastModified));
        }
    }

    private String render(List<SitemapEntry> entries) {
        StringBuilder xml = new StringBuilder(XML_HEADER);
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (SitemapEntry entry : entries) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(escapeXml(entry.url)).append("</loc>\n");
            if (entry.lastModified != null) {
                xml.append("    <lastmod>").append(entry.lastModified).append("</lastmod>\n");
            }
            xml.append("  </url>\n");
        }
        xml.append("</urlset>\n");
        return xml.toString();
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static final class CachedSitemap {
        private final long createdAtMillis;
        private final String xml;

        private CachedSitemap(long createdAtMillis, String xml) {
            this.createdAtMillis = createdAtMillis;
            this.xml = xml;
        }
    }

    private static final class SitemapEntry {
        private final String url;
        private final LocalDate lastModified;

        private SitemapEntry(String url, LocalDate lastModified) {
            this.url = url;
            this.lastModified = lastModified;
        }
    }
}
