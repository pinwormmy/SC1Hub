package com.sc1hub.member.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginKeepAliveViewCoverageTest {

    private static final Path VIEW_ROOT = Paths.get("src/main/webapp/WEB-INF/views");

    @Test
    void loginSensitivePages_includeSharedHeaderKeepAliveScript() throws IOException {
        List<String> viewPaths = Arrays.asList(
                "board/postList.jsp",
                "board/readPost.jsp",
                "board/writePost.jsp",
                "board/modifyPost.jsp",
                "strategyTip/list.jsp",
                "myPage.jsp",
                "modifyMyInfo.jsp",
                "adminPage.jsp",
                "adminOps.jsp",
                "adminAliasDictionary.jsp",
                "adminStrategyTipAi.jsp"
        );

        for (String viewPath : viewPaths) {
            String source = new String(
                    Files.readAllBytes(VIEW_ROOT.resolve(viewPath)), StandardCharsets.UTF_8);
            assertTrue(source.contains("header.jspf"),
                    () -> viewPath + " must include the shared header login keep-alive script");
        }
    }

    @Test
    void sharedHeader_loadsLoginKeepAliveScript() throws IOException {
        String source = new String(
                Files.readAllBytes(VIEW_ROOT.resolve("include/header.jspf")), StandardCharsets.UTF_8);

        assertTrue(source.contains("/js/site-header.js"));
    }

    @Test
    void strategyTipAiAdminView_hasCsrfProtectedManualGenerationForm() throws IOException {
        String source = new String(
                Files.readAllBytes(VIEW_ROOT.resolve("adminStrategyTipAi.jsp")),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("action=\"/adminPage/strategy-tips/ai/generate\""));
        assertTrue(source.contains("name=\"csrfToken\""));
        assertTrue(source.contains("초안 3건 생성"));
        assertTrue(source.contains("API 요금이 청구될 수 있습니다"));
        assertTrue(source.contains("not aiStatus.enabled"));
        assertTrue(source.contains("생성 중…"));
        assertTrue(source.contains("aria-busy"));
        assertFalse(source.contains("generationBlocked"));
        assertFalse(source.contains("apiCallLimitReached"));
        assertFalse(source.contains("사이트 내부 근거"));
        assertFalse(source.contains("원문 근거 구절"));
        assertFalse(source.contains("Google Search"));
    }

    @Test
    void loginKeepAliveTracksCkeditorActivityForLongPostEditing() throws IOException {
        Path scriptPath = Paths.get("src/main/resources/static/js/site-header.js");
        String source = new String(Files.readAllBytes(scriptPath), StandardCharsets.UTF_8);

        assertTrue(source.contains("CKEDITOR"));
        assertTrue(source.contains("instanceReady"));
    }

    @Test
    void sharedPagePrioritizesInternalNavigationOverAdsAndDoesNotLoadGlobalJquery() throws IOException {
        String headSource = new String(
                Files.readAllBytes(VIEW_ROOT.resolve("include/head.jspf")), StandardCharsets.UTF_8);
        String footerSource = new String(
                Files.readAllBytes(VIEW_ROOT.resolve("include/footer.jspf")), StandardCharsets.UTF_8);

        assertTrue(headSource.contains("requestIdleCallback"));
        assertTrue(headSource.contains("cancelIdleCallback"));
        assertTrue(headSource.contains("window.addEventListener('load'"));
        assertTrue(headSource.contains("document.addEventListener('pointerdown'"));
        assertTrue(headSource.contains("window.stop()"));
        assertTrue(headSource.contains("fetchPriority = 'low'"));
        assertTrue(headSource.contains("document.head.appendChild(scriptEl)"));
        assertFalse(headSource.contains("<script async src=\"https://pagead2.googlesyndication.com"));
        assertTrue(headSource.contains("data-google-vignette"));
        assertTrue(headSource.contains("url.origin === window.location.origin"));
        assertTrue(headSource.contains("MutationObserver"));
        assertFalse(headSource.contains("data-sc-adsense"));
        assertFalse(footerSource.contains("jquery"));
    }

    @Test
    void sharedChatKeepsOnlyLatestPartnerAdAndCancelsPreviousLazyLoad() throws IOException {
        Path scriptPath = Paths.get("src/main/resources/static/js/sc-chat.js");
        String source = new String(Files.readAllBytes(scriptPath), StandardCharsets.UTF_8);

        int insertAdStart = source.indexOf("function insertAdLine(config)");
        int removePreviousAd = source.indexOf("removePreviousChatAd();", insertAdStart);
        int appendNewAd = source.indexOf("logEl.appendChild(lineEl);", insertAdStart);

        assertTrue(source.contains("adObserver.unobserve(currentAdIframeEl)"));
        assertTrue(source.contains("currentAdLineEl.remove()"));
        assertTrue(source.contains("iframeEl !== currentAdIframeEl || !iframeEl.isConnected"));
        assertTrue(insertAdStart >= 0);
        assertTrue(removePreviousAd > insertAdStart);
        assertTrue(appendNewAd > removePreviousAd);
    }

    @Test
    void postCoupangAdLoadsDirectIframeOnlyAfterPageLoadAndNearViewport() throws IOException {
        String source = new String(
                Files.readAllBytes(VIEW_ROOT.resolve("include/coupangDynamicAd.jspf")),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("ads-partners.coupang.com/g.js"));
        assertTrue(source.contains("https://ads-partners.coupang.com/widgets.html"));
        assertTrue(source.contains("window.addEventListener('load'"));
        assertTrue(source.contains("IntersectionObserver"));
        assertTrue(source.contains("!pageLoaded || !nearViewport"));
        assertTrue(source.contains("iframeEl.setAttribute('loading', 'lazy')"));
        assertTrue(source.contains("iframeEl.title = '쿠팡 파트너스 상품 광고'"));
        assertTrue(source.contains("window.matchMedia('(max-width: 768px)')"));
    }
}
