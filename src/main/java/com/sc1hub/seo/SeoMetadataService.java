package com.sc1hub.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc1hub.board.dto.BoardDTO;
import com.sc1hub.common.dto.PageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SeoMetadataService {

    public static final String DEFAULT_SOCIAL_IMAGE =
            SeoUrlPolicy.CANONICAL_ORIGIN + "/images/home-wallpaper-dark.jpg";
    private static final String SITE_NAME = "SC1Hub";
    private static final String SITE_DESCRIPTION =
            "스타크래프트1 빌드오더, 종족별 운영법과 실전 전략을 공유하는 공략 커뮤니티";
    private static final int META_DESCRIPTION_MAX_LENGTH = 160;
    private static final int TITLE_MAX_LENGTH = 70;
    private static final Pattern HTML_TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final DateTimeFormatter ARTICLE_DATE_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ObjectMapper objectMapper;

    public SeoMetadataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void applyHome(Model model, HttpServletRequest request) {
        String title = "SC1Hub - 스타크래프트1 빌드·전략 커뮤니티";
        String canonical = canonicalFrom(request);
        applyCommon(model, title, SITE_DESCRIPTION, buildWebsiteStructuredData(title, canonical));
    }

    public void applyContentPage(Model model, HttpServletRequest request, String title, String description) {
        String canonical = canonicalFrom(request);
        applyCommon(model, title, truncateMeta(description),
                buildWebPageStructuredData(title, truncateMeta(description), canonical));
    }

    public void applyBoardList(Model model, HttpServletRequest request, String koreanTitle, PageDTO page) {
        String normalizedTitle = defaultIfBlank(koreanTitle, "스타크래프트1 공략 게시판");
        String pageSuffix = paginationSuffix(page);
        String title = truncateTitle(normalizedTitle + pageSuffix + " | 스타크래프트1 빌드·전략 - SC1Hub");
        String description = buildBoardDescription(normalizedTitle) + pageSuffix;
        String canonical = canonicalFrom(request);
        applyCommon(model, title, description,
                buildCollectionStructuredData(title, description, canonical));
    }

    public void applyPost(Model model, HttpServletRequest request, String koreanTitle, BoardDTO post) {
        String boardName = defaultIfBlank(koreanTitle, "스타크래프트1 공략");
        String postTitle = post == null ? boardName : defaultIfBlank(post.getTitle(), boardName);
        String title = truncateTitle(postTitle + " | " + boardName + " - SC1Hub");
        String description = buildPostDescription(boardName, post);
        String canonical = canonicalFrom(request);
        applyCommon(model, title, description,
                buildArticleStructuredData(post, title, description, canonical));
    }

    public void applyStrategyTips(Model model, HttpServletRequest request,
                                  String categoryName, PageDTO page) {
        String categoryPrefix = StringUtils.hasText(categoryName) ? categoryName.trim() + " " : "";
        String pageSuffix = paginationSuffix(page);
        String title = truncateTitle(categoryPrefix + "한줄 공략" + pageSuffix
                + " | 스타크래프트1 실전 팁 - SC1Hub");
        String description = truncateMeta(categoryPrefix
                + "스타크래프트1 빌드, 운영, 타이밍과 실전 팁을 짧고 빠르게 확인하세요."
                + pageSuffix);
        String canonical = canonicalFrom(request);
        applyCommon(model, title, description,
                buildCollectionStructuredData(title, description, canonical));
    }

    String buildBoardDescription(String koreanTitle) {
        return truncateMeta(koreanTitle
                + "의 스타크래프트1 빌드오더, 운영법, 공격 타이밍과 실전 공략을 확인하세요.");
    }

    String buildPostDescription(String koreanTitle, BoardDTO post) {
        if (post == null) {
            return buildBoardDescription(koreanTitle);
        }
        String text = stripHtmlToText(post.getContent());
        if (!StringUtils.hasText(text)) {
            text = defaultIfBlank(post.getTitle(), koreanTitle);
        }
        return truncateMeta(koreanTitle + " - " + text);
    }

    private void applyCommon(Model model, String title, String description, Map<String, Object> structuredData) {
        model.addAttribute("pageTitle", title);
        model.addAttribute("metaDescription", description);
        model.addAttribute("socialImage", DEFAULT_SOCIAL_IMAGE);
        String json = serializeSafely(structuredData);
        if (json != null) {
            model.addAttribute("structuredDataJson", json);
        }
    }

    private Map<String, Object> buildWebsiteStructuredData(String title, String canonical) {
        Map<String, Object> website = new LinkedHashMap<>();
        website.put("@type", "WebSite");
        website.put("@id", SeoUrlPolicy.CANONICAL_ORIGIN + "/#website");
        website.put("url", SeoUrlPolicy.CANONICAL_ORIGIN + "/");
        website.put("name", SITE_NAME);
        website.put("alternateName", "스타크래프트1 허브");
        website.put("description", SITE_DESCRIPTION);
        website.put("inLanguage", "ko-KR");

        Map<String, Object> webPage = new LinkedHashMap<>();
        webPage.put("@type", "WebPage");
        webPage.put("@id", canonical + "#webpage");
        webPage.put("url", canonical);
        webPage.put("name", title);
        webPage.put("isPartOf", reference(SeoUrlPolicy.CANONICAL_ORIGIN + "/#website"));
        webPage.put("inLanguage", "ko-KR");

        List<Map<String, Object>> graph = new ArrayList<>();
        graph.add(website);
        graph.add(webPage);

        Map<String, Object> root = contextRoot();
        root.put("@graph", graph);
        return root;
    }

    private Map<String, Object> buildCollectionStructuredData(String title, String description, String canonical) {
        Map<String, Object> root = contextRoot();
        root.put("@type", "CollectionPage");
        root.put("@id", canonical + "#collection");
        root.put("url", canonical);
        root.put("name", title);
        root.put("description", description);
        root.put("isPartOf", reference(SeoUrlPolicy.CANONICAL_ORIGIN + "/#website"));
        root.put("inLanguage", "ko-KR");
        return root;
    }

    private Map<String, Object> buildWebPageStructuredData(String title, String description, String canonical) {
        Map<String, Object> root = contextRoot();
        root.put("@type", "WebPage");
        root.put("@id", canonical + "#webpage");
        root.put("url", canonical);
        root.put("name", title);
        root.put("description", description);
        root.put("isPartOf", reference(SeoUrlPolicy.CANONICAL_ORIGIN + "/#website"));
        root.put("inLanguage", "ko-KR");
        return root;
    }

    private Map<String, Object> buildArticleStructuredData(BoardDTO post, String title,
                                                            String description, String canonical) {
        Map<String, Object> root = contextRoot();
        root.put("@type", "Article");
        root.put("@id", canonical + "#article");
        root.put("url", canonical);
        root.put("mainEntityOfPage", reference(canonical));
        root.put("headline", title);
        root.put("description", description);
        root.put("image", DEFAULT_SOCIAL_IMAGE);
        root.put("inLanguage", "ko-KR");

        if (post != null) {
            String authorName = defaultIfBlank(post.getWriter(), "SC1Hub 사용자");
            Map<String, Object> author = new LinkedHashMap<>();
            author.put("@type", "Person");
            author.put("name", authorName);
            root.put("author", author);
            String published = formatArticleDate(post.getRegDate());
            if (published != null) {
                root.put("datePublished", published);
                root.put("dateModified", published);
            }
        }

        Map<String, Object> publisher = new LinkedHashMap<>();
        publisher.put("@type", "Organization");
        publisher.put("name", SITE_NAME);
        publisher.put("url", SeoUrlPolicy.CANONICAL_ORIGIN);
        root.put("publisher", publisher);
        return root;
    }

    private Map<String, Object> contextRoot() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        return root;
    }

    private Map<String, Object> reference(String id) {
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("@id", id);
        return reference;
    }

    private String canonicalFrom(HttpServletRequest request) {
        Object canonical = request == null ? null : request.getAttribute("canonical");
        return canonical == null ? SeoUrlPolicy.CANONICAL_ORIGIN + "/" : canonical.toString();
    }

    private String serializeSafely(Map<String, Object> structuredData) {
        try {
            return objectMapper.writeValueAsString(structuredData)
                    .replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("&", "\\u0026");
        } catch (JsonProcessingException e) {
            log.warn("SEO 구조화 데이터 생성에 실패했습니다.", e);
            return null;
        }
    }

    private String stripHtmlToText(String html) {
        if (html == null) {
            return "";
        }
        String text = HTML_TAG.matcher(html).replaceAll(" ");
        text = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    private String truncateMeta(String text) {
        String normalized = normalizeText(text);
        if (normalized.length() <= META_DESCRIPTION_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, META_DESCRIPTION_MAX_LENGTH - 1).trim() + "…";
    }

    private String truncateTitle(String title) {
        String normalized = normalizeText(title);
        if (normalized.length() <= TITLE_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, TITLE_MAX_LENGTH - 1).trim() + "…";
    }

    private String normalizeText(String text) {
        return text == null ? "" : WHITESPACE.matcher(text.trim()).replaceAll(" ");
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String paginationSuffix(PageDTO page) {
        return page != null && page.getRecentPage() > 1 ? " - " + page.getRecentPage() + "페이지" : "";
    }

    private String formatArticleDate(Date date) {
        if (date == null) {
            return null;
        }
        return ARTICLE_DATE_FORMAT.format(date.toInstant().atZone(ZoneId.of("Asia/Seoul")));
    }
}
