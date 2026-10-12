package com.sc1hub.board.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 게시글 본문을 읽을 때 검색엔진이 보는 링크와 이미지 설명을 보정한다. 저장 데이터는 건드리지 않는다.
 *
 * <ul>
 *   <li>본문 안의 사이트 내부 링크를 정규 URL 모양으로 맞춘다(https, www 없음, 게시판 이름 소문자,
 *       게시판 경로 끝 슬래시 없음). 옛 글에는 {@code /boards/pVsZBoard/...} 같은 대소문자 섞인 링크가
 *       남아 있어 클릭과 크롤링마다 301을 한 번씩 거쳤다.</li>
 *   <li>{@code alt}가 없거나 빈 {@code <img>}에 글 제목으로 대체 텍스트를 채운다. 같은 글의 두 번째
 *       이미지부터는 순번을 붙여 서로 구분한다.</li>
 * </ul>
 */
@Component
public class PostContentSeoNormalizer {

    private static final String CANONICAL_ORIGIN = "https://sc1hub.com";
    private static final Pattern ANCHOR_TAG_PATTERN = Pattern.compile("(?i)<a\\b[^>]*>");
    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("(?i)<img\\b[^>]*>");
    private static final Pattern HREF_ATTRIBUTE_PATTERN =
            Pattern.compile("(?i)(\\shref\\s*=\\s*)(\"([^\"]*)\"|'([^']*)')");
    private static final Pattern ALT_ATTRIBUTE_PATTERN =
            Pattern.compile("(?i)\\salt\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]*))");
    private static final Pattern SITE_URL_PATTERN =
            Pattern.compile("(?i)^(?:https?:)?//(?:www\\.)?sc1hub\\.com(?::\\d+)?(/[^\\s]*)?$");
    private static final Pattern BOARD_PATH_PATTERN = Pattern.compile("^/boards/([A-Za-z0-9_]+)(/?)(.*)$", Pattern.DOTALL);

    public String normalize(String html, String postTitle) {
        if (!StringUtils.hasText(html)) {
            return html;
        }
        String lowerCased = html.toLowerCase(Locale.ROOT);
        String result = html;
        if (lowerCased.contains("<a")) {
            result = rewriteInternalLinks(result);
        }
        if (StringUtils.hasText(postTitle) && lowerCased.contains("<img")) {
            result = fillMissingAlt(result, postTitle.trim());
        }
        return result;
    }

    private static String rewriteInternalLinks(String html) {
        Matcher tagMatcher = ANCHOR_TAG_PATTERN.matcher(html);
        StringBuffer out = new StringBuffer(html.length());
        while (tagMatcher.find()) {
            String tag = tagMatcher.group();
            Matcher hrefMatcher = HREF_ATTRIBUTE_PATTERN.matcher(tag);
            if (hrefMatcher.find()) {
                boolean doubleQuoted = hrefMatcher.group(3) != null;
                String href = doubleQuoted ? hrefMatcher.group(3) : hrefMatcher.group(4);
                String normalized = normalizeInternalHref(href);
                if (!normalized.equals(href)) {
                    String quote = doubleQuoted ? "\"" : "'";
                    tag = tag.substring(0, hrefMatcher.start())
                            + hrefMatcher.group(1) + quote + normalized + quote
                            + tag.substring(hrefMatcher.end());
                }
            }
            tagMatcher.appendReplacement(out, Matcher.quoteReplacement(tag));
        }
        tagMatcher.appendTail(out);
        return out.toString();
    }

    static String normalizeInternalHref(String href) {
        if (href == null) {
            return null;
        }
        String trimmed = href.trim();
        Matcher siteMatcher = SITE_URL_PATTERN.matcher(trimmed);
        if (siteMatcher.matches()) {
            String path = siteMatcher.group(1);
            return CANONICAL_ORIGIN + (path == null ? "/" : normalizeBoardPath(path));
        }
        if (trimmed.startsWith("/boards/")) {
            return normalizeBoardPath(trimmed);
        }
        return href;
    }

    private static String normalizeBoardPath(String path) {
        Matcher matcher = BOARD_PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            return path;
        }
        String board = matcher.group(1).toLowerCase(Locale.ROOT);
        String rest = matcher.group(3);
        // "/boards/x/" 와 "/boards/x/?recentPage=2" 의 끝 슬래시만 뗀다. "/boards/x/readPost" 의 슬래시는 그대로.
        boolean boardRootOnly = rest.isEmpty() || rest.startsWith("?") || rest.startsWith("#");
        return "/boards/" + board + (boardRootOnly ? "" : matcher.group(2)) + rest;
    }

    private static String fillMissingAlt(String html, String postTitle) {
        Matcher tagMatcher = IMG_TAG_PATTERN.matcher(html);
        StringBuffer out = new StringBuffer(html.length() + 128);
        int imageIndex = 0;
        while (tagMatcher.find()) {
            imageIndex++;
            String tag = tagMatcher.group();
            String alt = escapeAttribute(imageIndex == 1 ? postTitle : postTitle + " " + imageIndex);
            Matcher altMatcher = ALT_ATTRIBUTE_PATTERN.matcher(tag);
            if (!altMatcher.find()) {
                int insertPos = tag.endsWith("/>") ? tag.length() - 2 : tag.length() - 1;
                tag = tag.substring(0, insertPos) + " alt=\"" + alt + "\"" + tag.substring(insertPos);
            } else if (!StringUtils.hasText(firstNonNull(altMatcher.group(2), altMatcher.group(3), altMatcher.group(4)))) {
                tag = tag.substring(0, altMatcher.start()) + " alt=\"" + alt + "\"" + tag.substring(altMatcher.end());
            }
            tagMatcher.appendReplacement(out, Matcher.quoteReplacement(tag));
        }
        tagMatcher.appendTail(out);
        return out.toString();
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
