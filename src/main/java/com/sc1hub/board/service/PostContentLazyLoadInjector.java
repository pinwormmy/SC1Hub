package com.sc1hub.board.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 게시글 본문의 embed/이미지에 렌더링 시점에 로딩 힌트를 붙인다.
 *
 * <p>{@link PostContentSanitizer}는 새로 저장되는 글에만 적용되므로, 그 이전에 작성된 글의
 * 유튜브 iframe은 페이지 진입 즉시 플레이어 전체를 내려받아 {@code load} 이벤트를 1초 이상
 * 붙잡고 광고 스케줄까지 뒤로 민다. 여기서는 저장 데이터를 건드리지 않고 읽을 때마다
 * {@code loading} 속성을 보정해 페이지 전환 직후의 네트워크·CPU 경쟁을 줄인다.</p>
 *
 * <ul>
 *   <li>모든 {@code <iframe>}: {@code loading} 속성이 없으면 {@code lazy}.</li>
 *   <li>첫 번째 {@code <img>}: 상단 대표 이미지이므로 {@code eager}로 강제해 첫 화면에 바로 뜨게 한다.</li>
 *   <li>그 뒤의 {@code <img>}: {@code loading} 속성이 없으면 {@code lazy}.</li>
 * </ul>
 */
@Component
public class PostContentLazyLoadInjector {

    private static final Pattern IFRAME_TAG_PATTERN = Pattern.compile("(?i)<iframe\\b[^>]*>");
    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("(?i)<img\\b[^>]*>");
    private static final Pattern LOADING_ATTRIBUTE_PATTERN =
            Pattern.compile("(?i)\\sloading\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s\"'>]+)");

    public String injectLazyLoading(String html) {
        if (!StringUtils.hasText(html)) {
            return html;
        }
        String lowerCased = html.toLowerCase(Locale.ROOT);
        String result = html;
        if (lowerCased.contains("<iframe")) {
            result = rewriteTags(result, IFRAME_TAG_PATTERN, false);
        }
        if (lowerCased.contains("<img")) {
            result = rewriteTags(result, IMG_TAG_PATTERN, true);
        }
        return result;
    }

    private static String rewriteTags(String html, Pattern tagPattern, boolean firstTagEager) {
        Matcher matcher = tagPattern.matcher(html);
        StringBuffer out = new StringBuffer(html.length() + 64);
        boolean first = true;
        while (matcher.find()) {
            String tag = matcher.group();
            String updated = first && firstTagEager
                    ? setLoading(tag, "eager")
                    : addLoadingIfMissing(tag, "lazy");
            first = false;
            matcher.appendReplacement(out, Matcher.quoteReplacement(updated));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String addLoadingIfMissing(String tag, String value) {
        if (LOADING_ATTRIBUTE_PATTERN.matcher(tag).find()) {
            return tag;
        }
        return insertAttribute(tag, value);
    }

    private static String setLoading(String tag, String value) {
        Matcher matcher = LOADING_ATTRIBUTE_PATTERN.matcher(tag);
        if (matcher.find()) {
            return matcher.replaceFirst(Matcher.quoteReplacement(" loading=\"" + value + "\""));
        }
        return insertAttribute(tag, value);
    }

    private static String insertAttribute(String tag, String value) {
        int insertPos = tag.endsWith("/>") ? tag.length() - 2 : tag.length() - 1;
        return tag.substring(0, insertPos) + " loading=\"" + value + "\"" + tag.substring(insertPos);
    }
}
