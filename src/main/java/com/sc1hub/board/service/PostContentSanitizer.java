package com.sc1hub.board.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class PostContentSanitizer {

    /**
     * 본문에 임베드할 수 있는 영상 플레이어 호스트. 유튜브와 SOOP(구 아프리카TV) 생방송·VOD 플레이어만 허용한다.
     * 목록을 바꾸면 post-editor.js 의 미리보기 허용 목록(ALLOWED_EMBED_HOSTS)도 함께 맞춘다.
     */
    private static final Set<String> ALLOWED_IFRAME_HOSTS = new HashSet<>(Arrays.asList(
            "youtube.com", "www.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com",
            "play.sooplive.co.kr", "vod.sooplive.co.kr", "play.sooplive.com", "vod.sooplive.com",
            "play.afreecatv.com", "vod.afreecatv.com"
    ));
    private static final String VIDEO_EMBED_CLASS = "sc-video-embed";
    private static final Set<String> ALLOWED_CLASSES = new HashSet<>(Arrays.asList(
            "sc-video-embed", "sc-video-source", "sc-post-image"
    ));

    private final Safelist safelist;

    public PostContentSanitizer() {
        safelist = Safelist.none()
                .addTags("p", "br", "h2", "h3", "h4", "strong", "b", "em", "i", "u", "s",
                        "ul", "ol", "li", "blockquote", "a", "img", "figure", "figcaption", "hr",
                        "table", "thead", "tbody", "tr", "th", "td", "div", "span", "iframe")
                .addAttributes("a", "href", "title", "target", "rel")
                .addAttributes("img", "src", "alt", "width", "height", "loading")
                .addAttributes("figure", "class")
                .addAttributes("div", "class")
                .addAttributes("th", "colspan", "rowspan", "scope")
                .addAttributes("td", "colspan", "rowspan")
                .addAttributes("iframe", "src", "width", "height", "title", "frameborder", "allow",
                        "allowfullscreen", "loading")
                .addProtocols("iframe", "src", "https");
    }

    public String sanitize(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }

        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String cleaned = Jsoup.clean(html, "", safelist, outputSettings);
        Document fragment = Jsoup.parseBodyFragment(cleaned);
        fragment.outputSettings(outputSettings);

        for (Element iframe : fragment.select("iframe")) {
            if (!isAllowedIframeSource(iframe.attr("src"))) {
                iframe.remove();
                continue;
            }
            iframe.attr("loading", "lazy");
            iframe.attr("allowfullscreen", "");
            wrapInResponsiveEmbed(iframe);
        }
        for (Element link : fragment.select("a[href]")) {
            if (!isAllowedLinkSource(link.attr("href"))) {
                link.removeAttr("href");
            } else if ("_blank".equalsIgnoreCase(link.attr("target"))) {
                link.attr("rel", "noopener noreferrer");
            }
        }
        for (Element image : fragment.select("img")) {
            if (!isAllowedImageSource(image.attr("src"))) {
                image.remove();
                continue;
            }
            image.attr("loading", "lazy");
        }
        for (Element element : fragment.select("[class]")) {
            Set<String> allowedClassNames = new HashSet<>(element.classNames());
            allowedClassNames.retainAll(ALLOWED_CLASSES);
            if (allowedClassNames.isEmpty()) {
                element.removeAttr("class");
            } else {
                element.classNames(allowedClassNames);
            }
        }
        return fragment.body().html();
    }

    /**
     * 구 에디터(CKEditor)나 HTML 편집 탭에서 그대로 붙여 넣은 고정 크기 iframe 을 에디터가 만드는
     * {@code <div class="sc-video-embed">} 로 감싸 모바일에서도 16:9 로 꽉 차게 보이도록 한다.
     * 이미 감싸져 있으면 손대지 않는다. 고정 width/height 는 래퍼 CSS 가 덮어쓰므로 제거한다.
     */
    private void wrapInResponsiveEmbed(Element iframe) {
        Element parent = iframe.parent();
        if (parent != null && parent.hasClass(VIDEO_EMBED_CLASS)) {
            return;
        }
        iframe.removeAttr("width");
        iframe.removeAttr("height");
        Element wrapper = new Element("div").addClass(VIDEO_EMBED_CLASS);
        iframe.replaceWith(wrapper);
        wrapper.appendChild(iframe);
    }

    private boolean isAllowedIframeSource(String source) {
        try {
            URI uri = URI.create(source);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && ALLOWED_IFRAME_HOSTS.contains(uri.getHost().toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isAllowedLinkSource(String source) {
        if (source.startsWith("/") || source.startsWith("#")) {
            return true;
        }
        try {
            String scheme = URI.create(source).getScheme();
            return "http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme)
                    || "mailto".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isAllowedImageSource(String source) {
        if (source.startsWith("/") && !source.startsWith("//")) {
            return true;
        }
        try {
            String scheme = URI.create(source).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
