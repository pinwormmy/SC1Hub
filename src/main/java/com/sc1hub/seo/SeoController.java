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

    private final SeoSitemapService sitemapService;

    public SeoController(SeoSitemapService sitemapService) {
        this.sitemapService = sitemapService;
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok()
                .contentType(SITEMAP_MEDIA_TYPE)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(sitemapService.getSitemapXml());
    }
}
