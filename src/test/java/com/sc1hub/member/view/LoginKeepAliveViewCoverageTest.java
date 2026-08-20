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
                "adminAliasDictionary.jsp"
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
    void adminPage_doesNotLinkRemovedStrategyTipAiFeature() throws IOException {
        String source = new String(
                Files.readAllBytes(VIEW_ROOT.resolve("adminPage.jsp")),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("/adminPage/strategy-tips/ai"));
        assertFalse(source.contains("AI 한줄 공략 검수"));
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
    void sharedChatLoadsPartnerAdOnlyAfterChatExpansion() throws IOException {
        Path chatScriptPath = Paths.get("src/main/resources/static/js/sc-chat.js");
        Path terminalScriptPath = Paths.get("src/main/resources/static/js/sc-terminal.js");
        String chatSource = new String(Files.readAllBytes(chatScriptPath), StandardCharsets.UTF_8);
        String terminalSource = new String(Files.readAllBytes(terminalScriptPath), StandardCharsets.UTF_8);
        int insertAdStart = chatSource.indexOf("function insertAdLine(config)");
        int insertAdEnd = chatSource.indexOf("function maybeInsertAd(message)", insertAdStart);
        String insertAdSource = chatSource.substring(insertAdStart, insertAdEnd);
        int storeDeferredSource = insertAdSource.indexOf("iframeEl.dataset.src = adSrc");
        int observeDeferredAd = insertAdSource.indexOf("observePendingChatAd();", storeDeferredSource);

        assertTrue(terminalSource.contains("new CustomEvent('sc:chat-expanded'"));
        assertTrue(terminalSource.contains("detail: { expanded }"));
        assertTrue(insertAdStart >= 0);
        assertTrue(insertAdEnd > insertAdStart);
        assertTrue(storeDeferredSource >= 0);
        assertTrue(observeDeferredAd > storeDeferredSource);
        assertFalse(insertAdSource.contains("iframeEl.src = adSrc"));
        assertTrue(chatSource.contains("window.addEventListener('sc:chat-expanded'"));
        assertTrue(chatSource.contains("if (!chatExpanded || !currentAdIframeEl || !currentAdIframeEl.dataset.src)"));
        assertTrue(chatSource.contains("setChatExpanded(Boolean(event.detail && event.detail.expanded))"));
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
