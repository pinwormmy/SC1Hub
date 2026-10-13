package com.sc1hub.seo;

import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.board.dto.BoardListDTO;
import com.sc1hub.board.service.BoardService;
import com.sc1hub.board.support.BoardTitleNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 새 글을 검색엔진에 빨리 알리기 위한 RSS 2.0 피드. 네이버 서치어드바이저는 사이트맵과 별도로
 * RSS 제출을 받아 새 문서 수집에 쓴다.
 */
@Slf4j
@Service
public class SeoFeedService {

    static final long CACHE_MINUTES = 10L;
    static final int MAX_ITEMS = 30;
    private static final long CACHE_MILLIS = TimeUnit.MINUTES.toMillis(CACHE_MINUTES);
    private static final int DESCRIPTION_MAX_LENGTH = 300;
    // 꿀잼놀이터는 비회원도 쓰는 잡담 게시판이라 공략 사이트의 새 글 피드에 싣지 않는다.
    private static final Set<String> EXCLUDED_BOARDS = Set.of("funboard");
    private static final ZoneId FEED_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter RFC_822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    private final BoardService boardService;
    private final Clock clock;
    private volatile CachedFeed cachedFeed;

    @Autowired
    public SeoFeedService(BoardService boardService) {
        this(boardService, Clock.systemDefaultZone());
    }

    SeoFeedService(BoardService boardService, Clock clock) {
        this.boardService = boardService;
        this.clock = clock;
    }

    public String getRssXml() {
        long now = clock.millis();
        CachedFeed current = cachedFeed;
        if (current != null && now - current.createdAtMillis < CACHE_MILLIS) {
            return current.xml;
        }

        synchronized (this) {
            current = cachedFeed;
            if (current != null && now - current.createdAtMillis < CACHE_MILLIS) {
                return current.xml;
            }
            String xml = render(collectItems());
            cachedFeed = new CachedFeed(now, xml);
            return xml;
        }
    }

    private List<FeedItem> collectItems() {
        List<BoardListDTO> boards = boardService.getBoardList();
        if (boards == null) {
            return Collections.emptyList();
        }

        List<FeedItem> items = new ArrayList<>();
        for (BoardListDTO board : boards) {
            if (board == null) {
                continue;
            }
            final String boardTitle;
            try {
                boardTitle = BoardTitleNormalizer.requireValid(board.getBoardTitle());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (EXCLUDED_BOARDS.contains(boardTitle)) {
                continue;
            }
            try {
                List<BoardDTO> posts = boardService.getFeedPosts(boardTitle, MAX_ITEMS);
                if (posts == null) {
                    continue;
                }
                for (BoardDTO post : posts) {
                    if (post != null && post.getPostNum() > 0 && post.getRegDate() != null) {
                        items.add(new FeedItem(boardTitle, board.getKoreanTitle(), post));
                    }
                }
            } catch (Exception e) {
                log.warn("RSS 게시글 조회에 실패했습니다. boardTitle={}", boardTitle, e);
            }
        }
        items.sort(Comparator.comparing((FeedItem item) -> item.post.getRegDate()).reversed());
        return items.size() > MAX_ITEMS ? items.subList(0, MAX_ITEMS) : items;
    }

    private String render(List<FeedItem> items) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\">\n<channel>\n");
        xml.append("  <title>SC1Hub - 스타크래프트1 빌드·전략 커뮤니티</title>\n");
        xml.append("  <link>").append(SeoUrlPolicy.CANONICAL_ORIGIN).append("/</link>\n");
        xml.append("  <description>스타크래프트1 빌드오더, 종족별 운영법과 실전 전략을 공유하는 공략 커뮤니티</description>\n");
        xml.append("  <language>ko</language>\n");
        if (!items.isEmpty()) {
            xml.append("  <lastBuildDate>").append(formatDate(items.get(0).post.getRegDate())).append("</lastBuildDate>\n");
        }
        for (FeedItem item : items) {
            String url = SeoUrlPolicy.CANONICAL_ORIGIN + "/boards/" + item.boardTitle
                    + "/readPost?postNum=" + item.post.getPostNum();
            xml.append("  <item>\n");
            xml.append("    <title>").append(escapeXml(item.post.getTitle())).append("</title>\n");
            xml.append("    <link>").append(escapeXml(url)).append("</link>\n");
            xml.append("    <guid isPermaLink=\"true\">").append(escapeXml(url)).append("</guid>\n");
            xml.append("    <description>").append(escapeXml(excerpt(item.post.getContent()))).append("</description>\n");
            if (StringUtils.hasText(item.boardName)) {
                xml.append("    <category>").append(escapeXml(item.boardName)).append("</category>\n");
            }
            xml.append("    <pubDate>").append(formatDate(item.post.getRegDate())).append("</pubDate>\n");
            xml.append("  </item>\n");
        }
        xml.append("</channel>\n</rss>\n");
        return xml.toString();
    }

    private String excerpt(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String text = Jsoup.parseBodyFragment(html).text().trim();
        if (text.length() <= DESCRIPTION_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, DESCRIPTION_MAX_LENGTH - 1).trim() + "…";
    }

    private String formatDate(Date date) {
        return RFC_822.format(date.toInstant().atZone(FEED_ZONE));
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        value.codePoints().forEach(cp -> {
            // XML 1.0 이 허용하지 않는 제어문자는 피드 전체를 깨뜨리므로 버린다.
            if (cp < 0x20 && cp != '\t' && cp != '\n' && cp != '\r') {
                return;
            }
            switch (cp) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.appendCodePoint(cp);
            }
        });
        return out.toString();
    }

    private static final class CachedFeed {
        private final long createdAtMillis;
        private final String xml;

        private CachedFeed(long createdAtMillis, String xml) {
            this.createdAtMillis = createdAtMillis;
            this.xml = xml;
        }
    }

    private static final class FeedItem {
        private final String boardTitle;
        private final String boardName;
        private final BoardDTO post;

        private FeedItem(String boardTitle, String boardName, BoardDTO post) {
            this.boardTitle = boardTitle;
            this.boardName = boardName;
            this.post = post;
        }
    }
}
