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
    void loginKeepAliveTracksNativeEditorActivityForLongPostEditing() throws IOException {
        Path scriptPath = Paths.get("src/main/resources/static/js/site-header.js");
        String source = new String(Files.readAllBytes(scriptPath), StandardCharsets.UTF_8);

        assertTrue(source.contains("'keydown'"));
        assertTrue(source.contains("recordActivity"));
        assertFalse(source.contains("CKEDITOR"));
    }

    @Test
    void sharedPageKeepsInternalNavigationLightweightAndLoadsAdsenseAsynchronously() throws IOException {
        String headSource = new String(
                Files.readAllBytes(VIEW_ROOT.resolve("include/head.jspf")), StandardCharsets.UTF_8);
        String footerSource = new String(
                Files.readAllBytes(VIEW_ROOT.resolve("include/footer.jspf")), StandardCharsets.UTF_8);

        assertTrue(headSource.contains("scriptEl.async = true"));
        assertTrue(headSource.contains("scriptEl.crossOrigin = 'anonymous'"));
        assertTrue(headSource.contains("window.addEventListener('load', loadAdsense, { once: true })"));
        assertTrue(headSource.contains("document.head.appendChild(scriptEl)"));
        assertTrue(headSource.contains("data-google-vignette"));
        assertTrue(headSource.contains("url.origin === window.location.origin"));
        assertTrue(headSource.contains("document.addEventListener('click', markActivatedInternalLink"));
        assertTrue(headSource.contains("DOMContentLoaded', markExistingInternalLinks, { once: true }"));
        assertTrue(headSource.contains("font-display: swap"));
        assertFalse(headSource.contains("requestIdleCallback"));
        assertFalse(headSource.contains("cancelIdleCallback"));
        assertFalse(headSource.contains("ADSENSE_DELAY_MS"));
        assertFalse(headSource.contains("window.setTimeout"));
        assertFalse(headSource.contains("document.addEventListener('pointerdown'"));
        assertFalse(headSource.contains("window.stop()"));
        assertFalse(headSource.contains("MutationObserver"));
        assertFalse(headSource.contains("data-sc-adsense"));
        assertFalse(footerSource.contains("jquery"));
    }

    @Test
    void sharedChatKeepsOnlyLatestPartnerAdAndCancelsPreviousLazyLoad() throws IOException {
        Path scriptPath = Paths.get("src/main/resources/static/js/sc-chat.js");
        String source = new String(Files.readAllBytes(scriptPath), StandardCharsets.UTF_8);

        int insertAdStart = source.indexOf("function insertAdLine(config, afterMessageEl, afterMessageId)");
        int removePreviousAd = source.indexOf("removePreviousChatAd();", insertAdStart);
        int appendNewAd = source.indexOf("afterMessageEl.insertAdjacentElement('afterend', lineEl)", insertAdStart);
        int createIframe = source.indexOf("document.createElement('iframe')");

        assertTrue(source.contains("adObserver.unobserve(currentAdLineEl)"));
        assertTrue(source.contains("currentAdLineEl.remove()"));
        assertTrue(source.contains("lineEl !== currentAdLineEl || !lineEl.isConnected"));
        assertTrue(insertAdStart >= 0);
        assertTrue(removePreviousAd > insertAdStart);
        assertTrue(appendNewAd > removePreviousAd);
        assertTrue(source.contains("function refreshLatestChatAd()"));
        assertTrue(source.contains("candidate = { messageEl, messageId }"));
        assertTrue(source.contains("lineEl.dataset.role = message.role || ''"));
        assertTrue(source.contains("document.createDocumentFragment()"));
        assertTrue(source.contains("DEFAULT_MAX_RENDERED_MESSAGES = 50"));
        assertTrue(source.contains("self.historySize"));
        assertTrue(source.contains("messageEls.length - maxRenderedMessages"));
        assertTrue(createIframe >= 0 && createIframe == source.lastIndexOf("document.createElement('iframe')"));
    }

    @Test
    void sharedChatLoadsPartnerAdOnlyAfterChatExpansion() throws IOException {
        Path chatScriptPath = Paths.get("src/main/resources/static/js/sc-chat.js");
        Path terminalScriptPath = Paths.get("src/main/resources/static/js/sc-terminal.js");
        Path stylePath = Paths.get("src/main/resources/static/css/style.css");
        String chatSource = new String(Files.readAllBytes(chatScriptPath), StandardCharsets.UTF_8);
        String terminalSource = new String(Files.readAllBytes(terminalScriptPath), StandardCharsets.UTF_8);
        String styleSource = new String(Files.readAllBytes(stylePath), StandardCharsets.UTF_8);
        int insertAdStart = chatSource.indexOf("function insertAdLine(config, afterMessageEl, afterMessageId)");
        int insertAdEnd = chatSource.indexOf("function refreshLatestChatAd()", insertAdStart);
        String insertAdSource = chatSource.substring(insertAdStart, insertAdEnd);

        assertTrue(terminalSource.contains("new CustomEvent('sc:chat-expanded'"));
        assertTrue(terminalSource.contains("detail: { expanded }"));
        assertTrue(insertAdStart >= 0);
        assertTrue(insertAdEnd > insertAdStart);
        assertTrue(insertAdSource.contains("currentAdSrc = adSrc"));
        assertTrue(insertAdSource.contains("observePendingChatAd();"));
        assertFalse(insertAdSource.contains("document.createElement('iframe')"));
        assertFalse(insertAdSource.contains("iframeEl.src = adSrc"));
        assertTrue(chatSource.contains("function createCurrentChatAdIframe()"));
        assertTrue(chatSource.contains("iframeEl.src = currentAdSrc"));
        assertTrue(chatSource.contains("window.addEventListener('sc:chat-expanded'"));
        assertTrue(chatSource.contains("expanded && isChatExpanded()"));
        assertTrue(chatSource.contains("setChatExpanded(Boolean(event.detail && event.detail.expanded))"));
        assertTrue(chatSource.contains("currentAdIframeEl.remove()"));
        assertFalse(chatSource.contains("CHAT_AD_LOAD_QUIET_MILLIS"));
        assertFalse(chatSource.contains("requestIdleCallback"));
        assertFalse(chatSource.contains("postponeChatAdForUserInput"));
        assertFalse(chatSource.contains("navigator.scheduling.isInputPending()"));
        assertFalse(chatSource.contains("fetchpriority"));
        assertTrue(styleSource.contains(".sc-chat__ad {\n    display: none;"));
        assertTrue(styleSource.contains("body.sc-chat-fullscreen .sc-chat__ad {\n    display: flex;"));
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
        assertTrue(source.contains("rootMargin: '300px 0px'"));
        assertTrue(source.contains("iframeEl.setAttribute('loading', 'lazy')"));
        assertTrue(source.contains("iframeEl.title = '쿠팡 파트너스 상품 광고'"));
        assertTrue(source.contains("window.matchMedia('(max-width: 768px)')"));
        assertFalse(source.contains("AD_LOAD_QUIET_MILLIS"));
        assertFalse(source.contains("requestIdleCallback"));
        assertFalse(source.contains("postponeAdForUserInput"));
        assertFalse(source.contains("navigator.scheduling.isInputPending()"));
        assertFalse(source.contains("fetchpriority"));
    }

    @Test
    void sharedUiAvoidsContinuousPaintAndSyntheticGlobalResizeWork() throws IOException {
        String styleSource = new String(
                Files.readAllBytes(Paths.get("src/main/resources/static/css/style.css")),
                StandardCharsets.UTF_8);
        String latestPostsSource = new String(
                Files.readAllBytes(VIEW_ROOT.resolve("include/latestPosts.jspf")),
                StandardCharsets.UTF_8);
        String terminalSource = new String(
                Files.readAllBytes(Paths.get("src/main/resources/static/js/sc-terminal.js")),
                StandardCharsets.UTF_8);

        assertTrue(styleSource.contains("background-attachment: scroll"));
        assertFalse(styleSource.contains("background-attachment: fixed"));
        assertFalse(styleSource.contains("mix-blend-mode"));
        assertFalse(styleSource.contains("text-shadow: 0 0 6px"));
        assertFalse(styleSource.contains("body::before"));
        assertTrue(latestPostsSource.contains("window.scUpdateTitleSlides(containerEl)"));
        assertFalse(latestPostsSource.contains("dispatchEvent(new Event('resize'))"));
        assertTrue(terminalSource.contains("function updateTitleSlideOverflow(rootEl = document)"));
    }
}
