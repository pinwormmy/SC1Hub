package com.sc1hub.seo;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
public class SeoController {

    private static final MediaType SITEMAP_MEDIA_TYPE = MediaType.parseMediaType("application/xml;charset=UTF-8");

    private static final MediaType RSS_MEDIA_TYPE = MediaType.parseMediaType("application/rss+xml;charset=UTF-8");

    private final SeoSitemapService sitemapService;
    private final SeoFeedService feedService;

    public SeoController(SeoSitemapService sitemapService, SeoFeedService feedService) {
        this.sitemapService = sitemapService;
        this.feedService = feedService;
    }

    @GetMapping(value = "/rss.xml", produces = "application/rss+xml;charset=UTF-8")
    public ResponseEntity<String> rss() {
        return ResponseEntity.ok()
                .contentType(RSS_MEDIA_TYPE)
                .cacheControl(CacheControl.maxAge(SeoFeedService.CACHE_MINUTES, TimeUnit.MINUTES).cachePublic())
                .body(feedService.getRssXml());
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok()
                .contentType(SITEMAP_MEDIA_TYPE)
                .cacheControl(CacheControl.maxAge(SeoSitemapService.CACHE_MINUTES, TimeUnit.MINUTES).cachePublic())
                .body(sitemapService.getSitemapXml());
    }
}
