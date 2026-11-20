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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeoSitemapService {

    static final long CACHE_MINUTES = 10L;
    private static final long CACHE_MILLIS = TimeUnit.MINUTES.toMillis(CACHE_MINUTES);
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final ZoneId SITEMAP_ZONE = ZoneId.of("Asia/Seoul");

    private final BoardService boardService;
    private final Clock clock;
    private volatile CachedSitemap cachedSitemap;

    @Autowired
    public SeoSitemapService(BoardService boardService) {
        this(boardService, Clock.system(SITEMAP_ZONE));
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
            BuildResult built = buildSitemapXml();
            if (built.hadError) {
                if (current != null) {
                    return current.xml;
                }
                return built.xml;
            }
            cachedSitemap = new CachedSitemap(now, built.xml);
            return built.xml;
        }
    }

    private BuildResult buildSitemapXml() {
        List<SitemapEntry> boardEntries = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        String homeUrl = SeoUrlPolicy.CANONICAL_ORIGIN + "/";
        String guidelinesUrl = SeoUrlPolicy.CANONICAL_ORIGIN + "/guidelines";
        String buildTimerUrl = SeoUrlPolicy.CANONICAL_ORIGIN + "/build-timer";
        String keyboardTestUrl = SeoUrlPolicy.CANONICAL_ORIGIN + "/keyboard-test";
        seenUrls.add(homeUrl);
        seenUrls.add(guidelinesUrl);
        seenUrls.add(buildTimerUrl);
        seenUrls.add(keyboardTestUrl);

        List<BoardListDTO> boards = boardService.getBoardList();
        if (boards == null) {
            boards = Collections.emptyList();
        }
        // 홈은 최신 글 목록을 싣고 있으므로 사이트 전체에서 가장 최근 글의 날짜를 따라간다.
        LocalDate latest = null;
        boolean hadError = false;
        for (BoardListDTO board : boards) {
            BoardResult result = addBoardEntries(boardEntries, seenUrls, board);
            latest = later(latest, result.latest);
            if (result.hadError) {
                hadError = true;
            }
        }

        List<SitemapEntry> entries = new ArrayList<>(boardEntries.size() + 4);
        entries.add(new SitemapEntry(homeUrl, latest));
        entries.add(new SitemapEntry(guidelinesUrl, null));
        entries.add(new SitemapEntry(buildTimerUrl, null));
        entries.add(new SitemapEntry(keyboardTestUrl, null));
        entries.addAll(boardEntries);
        return new BuildResult(render(entries), hadError);
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        return b != null && b.isAfter(a) ? b : a;
    }

    /** 게시판 목록과 글 URL을 더하고, 그 게시판에서 가장 최근 글의 날짜를 돌려준다(없으면 null). */
    private BoardResult addBoardEntries(List<SitemapEntry> entries, Set<String> seenUrls, BoardListDTO board) {
        if (board == null) {
            return new BoardResult(null, false);
        }

        final String boardTitle;
        try {
            boardTitle = BoardTitleNormalizer.requireValid(board.getBoardTitle());
        } catch (IllegalArgumentException e) {
            log.warn("사이트맵에서 올바르지 않은 게시판 식별자를 제외합니다: {}", board.getBoardTitle());
            return new BoardResult(null, false);
        }

        String boardUrl = SeoUrlPolicy.CANONICAL_ORIGIN + "/boards/" + boardTitle;
        List<BoardDTO> posts = null;
        boolean hadError = false;
        try {
            posts = boardService.getSitemapPosts(boardTitle);
        } catch (Exception e) {
            hadError = true;
            log.warn("사이트맵 게시글 조회에 실패했습니다. boardTitle={}", boardTitle, e);
        }
        if (posts == null) {
            posts = Collections.emptyList();
        }

        LocalDate latest = null;
        for (BoardDTO post : posts) {
            if (post != null && post.getPostNum() > 0) {
                latest = later(latest, lastChanged(post));
            }
        }
        // 게시판 목록은 새 글이 올라올 때마다 바뀌므로 가장 최근 글의 날짜를 lastmod 로 쓴다.
        addEntry(entries, seenUrls, boardUrl, latest);
        for (BoardDTO post : posts) {
            if (post == null || post.getPostNum() <= 0) {
                continue;
            }
            String postUrl = boardUrl + "/readPost?postNum=" + post.getPostNum();
            addEntry(entries, seenUrls, postUrl, lastChanged(post));
        }
        return new BoardResult(latest, hadError);
    }

    /** 글을 고친 적이 있으면 수정일, 없으면 작성일. */
    private LocalDate lastChanged(BoardDTO post) {
        return later(toLocalDate(post.getRegDate()), toLocalDate(post.getModDate()));
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

    private record BuildResult(String xml, boolean hadError) {
    }

    private record BoardResult(LocalDate latest, boolean hadError) {
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
