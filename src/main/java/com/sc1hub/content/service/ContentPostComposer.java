package com.sc1hub.content.service;

import com.sc1hub.file.dto.PostImageResponse;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ContentPostComposer {

    private static final Pattern YOUTUBE_VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{6,20}");
    private static final Set<String> YOUTUBE_HOSTS = new HashSet<>(Arrays.asList(
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
            "youtube-nocookie.com", "www.youtube-nocookie.com"
    ));

    public String compose(String title, String content, PostImageResponse image,
            String imageAlt, String imageCaption, String youtubeUrl, String youtubeTitle) {
        StringBuilder html = new StringBuilder();
        if (image != null) {
            appendImage(html, title, image, imageAlt, imageCaption);
        }
        if (StringUtils.hasText(content)) {
            html.append(content.trim());
        }
        if (StringUtils.hasText(youtubeUrl)) {
            appendYoutube(html, youtubeUrl, youtubeTitle);
        }
        return html.toString();
    }

    private void appendImage(StringBuilder html, String title, PostImageResponse image,
            String imageAlt, String imageCaption) {
        Element figure = new Element("figure").addClass("sc-post-image");
        figure.appendElement("img")
                .attr("src", image.getUrl())
                .attr("alt", StringUtils.hasText(imageAlt) ? imageAlt.trim() : defaultImageAlt(title))
                .attr("width", String.valueOf(image.getWidth()))
                .attr("height", String.valueOf(image.getHeight()))
                .attr("loading", "lazy");
        if (StringUtils.hasText(imageCaption)) {
            figure.appendElement("figcaption").text(imageCaption.trim());
        }
        html.append(figure.outerHtml()).append("<p><br></p>");
    }

    private String defaultImageAlt(String title) {
        return StringUtils.hasText(title) ? title.trim() : "게시글 이미지";
    }

    private void appendYoutube(StringBuilder html, String youtubeUrl, String youtubeTitle) {
        String embedUrl = toYoutubeEmbedUrl(youtubeUrl);
        if (html.length() > 0) {
            html.append("<p><br></p>");
        }
        Element container = new Element("div").addClass("sc-video-embed");
        container.appendElement("iframe")
                .attr("src", embedUrl)
                .attr("width", "100%")
                .attr("title", StringUtils.hasText(youtubeTitle) ? youtubeTitle.trim() : "유튜브 영상")
                .attr("frameborder", "0")
                .attr("allow", "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture")
                .attr("allowfullscreen", "")
                .attr("loading", "lazy");
        html.append(container.outerHtml());
    }

    String toYoutubeEmbedUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw invalidYoutubeUrl();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String videoId;
            if ("youtu.be".equals(host)) {
                videoId = firstPathSegment(uri.getPath());
            } else if (YOUTUBE_HOSTS.contains(host)) {
                videoId = queryParameter(uri.getRawQuery(), "v");
                if (!StringUtils.hasText(videoId)) {
                    videoId = pathVideoId(uri.getPath());
                }
            } else {
                throw invalidYoutubeUrl();
            }
            if (!StringUtils.hasText(videoId) || !YOUTUBE_VIDEO_ID.matcher(videoId).matches()) {
                throw invalidYoutubeUrl();
            }
            return "https://www.youtube-nocookie.com/embed/" + videoId;
        } catch (IllegalArgumentException e) {
            if ("올바른 유튜브 주소를 입력해주세요.".equals(e.getMessage())) {
                throw e;
            }
            throw invalidYoutubeUrl();
        }
    }

    private String pathVideoId(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String[] parts = path.split("/");
        if (parts.length >= 3 && ("shorts".equals(parts[1]) || "embed".equals(parts[1]))) {
            return parts[2];
        }
        return null;
    }

    private String firstPathSegment(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        for (String part : path.split("/")) {
            if (StringUtils.hasText(part)) {
                return part;
            }
        }
        return null;
    }

    private String queryParameter(String rawQuery, String expectedName) {
        if (!StringUtils.hasText(rawQuery)) {
            return null;
        }
        for (String token : rawQuery.split("&")) {
            String[] pair = token.split("=", 2);
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (expectedName.equals(name)) {
                return pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            }
        }
        return null;
    }

    private IllegalArgumentException invalidYoutubeUrl() {
        return new IllegalArgumentException("올바른 유튜브 주소를 입력해주세요.");
    }
}
