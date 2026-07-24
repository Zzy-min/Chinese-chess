package com.xiangqi.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineSiteResourceContractTest {

    @Test
    void onlineSiteTreatsHomeAsUnifiedEntryAndAnalysisUsesSharedBoardRenderer() throws Exception {
        String js = readResource("/online/app.js");

        assertTrue(js.contains("function renderHomePageGuofeng"));
        assertTrue(js.contains("象棋对局"));
        assertTrue(js.contains("五子棋对局"));
        assertTrue(js.contains("data-action=\"quick-start-ai-practice\""));
        assertTrue(js.contains("function quickStartPublicMatch"));
        assertTrue(js.contains("/rooms/quick-match"));
        assertTrue(js.contains("data-action=\"quick-start-public-match\""));
        assertTrue(js.contains("data-nav=\"play/xiangqi\""));
        assertTrue(js.contains("data-nav=\"play/gomoku\""));
        assertFalse(js.contains("<span>AI 象棋练习</span>"));
        assertFalse(js.contains("首页承接 AI 对局与在线对局两条入口"));
        assertFalse(js.contains("米色大厅语言"));
        assertFalse(js.contains("返回学习页"));
        assertTrue(js.contains("function renderPlayableBoardByGameType"));
        assertTrue(js.contains("function renderAnalysisBoardByGameType"));
        assertTrue(js.contains("function renderUnsupportedBoard"));
        assertTrue(js.contains("function leaderboardRowsHtml"));
        assertTrue(js.contains("暂无榜单数据"));
        assertFalse(js.contains("棋逢对手"));
        assertFalse(js.contains("江湖小虾"));
        assertFalse(js.contains("棋圣无名"));
        assertFalse(js.contains("清风徐来"));
    }

    @Test
    void onlineSiteProvidesDedicatedMobileShellForEveryNonBoardRoute() throws Exception {
        String js = readResource("/online/app.js");
        String css = readResource("/online/mobile.css");

        assertTrue(js.contains("function renderMobileHomePage"));
        assertTrue(js.contains("function renderMobileLobby"));
        assertTrue(js.contains("function renderMobileContentPage"));
        assertTrue(js.contains("function renderMobileModePage"));
        assertTrue(js.contains("function renderMobileQuickStartSheet"));
        assertTrue(js.contains("function renderMobileBottomNav"));
        assertTrue(js.contains("data-action=\"open-mobile-quick-start\""));
        assertTrue(js.contains("data-mobile-nav=\"home\""));
        assertTrue(js.contains("data-mobile-nav=\"play\""));
        assertTrue(js.contains("data-mobile-nav=\"learn\""));
        assertTrue(js.contains("data-mobile-nav=\"watch\""));
        assertTrue(js.contains("data-mobile-nav=\"me\""));

        assertTrue(css.contains(".mobileHome"));
        assertTrue(css.contains(".mobileBottomNav"));
        assertTrue(css.contains(".mobileQuickStartSheet"));
        assertTrue(css.contains(".mobileContentPage"));
        assertTrue(css.contains(".mobileModePage"));
        assertTrue(css.contains("padding-bottom: env(safe-area-inset-bottom"));
        assertTrue(css.contains("@media (max-width: 768px)"));
        assertFalse(css.contains("overflow-x: clip"));
        assertTrue(css.contains("touch-action: manipulation"));
    }

    @Test
    void boardRoutesDoNotRenderMobileBottomNavigation() throws Exception {
        String js = readResource("/online/app.js");

        assertTrue(js.contains("if (isBoardRoutePage(activePage) || activePage === 'welcome')"));
        assertTrue(js.contains("return '';"));
    }

    @Test
    void learnRoomAndBoardLayoutsExposeMobileAndReplayFixes() throws Exception {
        String html = readResource("/online/index.html");
        String js = readResource("/online/app.js");
        String css = readResource("/online/app.css");
        String mobileCss = readResource("/online/mobile.css");

        assertTrue(html.contains("app.css?v=20260724m3"));
        assertTrue(html.contains("mobile.css?v=20260724m3"));
        assertTrue(html.contains("app.js?v=20260724m3"));
        assertTrue(js.contains("data-nav=\"learn/puzzles/ENDGAME_FEN\""));
        assertTrue(js.contains("data-action=\"close-room\""));
        assertTrue(js.contains("method: 'DELETE'"));
        assertTrue(js.contains("data.type === 'room_closed'"));
        assertTrue(css.contains(".site.route-analysis .boardPane--analysis"));
        assertTrue(css.contains(".site.route-analysis .playbackControls"));
        assertTrue(mobileCss.contains(".boardStage"));
        assertTrue(mobileCss.contains("order: 1"));
        assertTrue(mobileCss.contains(".boardPlayerCard"));
        assertTrue(mobileCss.contains("font-size: 18px"));
    }

    @Test
    void mobileWebBridgeIsOptionalAndSupportsLifecycleShareAndHaptics() throws Exception {
        String js = readResource("/online/app.js");

        assertTrue(js.contains("function notifyNative(type, payload = {})"));
        assertTrue(js.contains("notifyNative('appReady'"));
        assertTrue(js.contains("notifyNative('gameStateChanged'"));
        assertTrue(js.contains("notifyNative('shareRoom'"));
        assertTrue(js.contains("notifyNative('haptic'"));
        assertTrue(js.contains("notifyNative('versionTap')"));
        assertTrue(js.contains("window.QingQijuApp.postMessage"));
        assertTrue(js.contains("data-action=\"mobile-version-tap\""));
    }

    private String readResource(String path) throws IOException {
        try (InputStream input = OnlineSiteResourceContractTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("missing resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
