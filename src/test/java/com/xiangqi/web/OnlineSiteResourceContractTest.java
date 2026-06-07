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
        assertTrue(js.contains("data-action=\"quick-match-xiangqi\""));
        assertTrue(js.contains("data-nav=\"play/xiangqi\""));
        assertTrue(js.contains("data-nav=\"play/gomoku\""));
        assertFalse(js.contains("<span>AI 象棋练习</span>"));
        assertFalse(js.contains("首页承接 AI 对局与在线对局两条入口"));
        assertFalse(js.contains("米色大厅语言"));
        assertFalse(js.contains("返回学习页"));
        assertTrue(js.contains("function renderPlayableBoardByGameType"));
        assertTrue(js.contains("function renderAnalysisBoardByGameType"));
        assertTrue(js.contains("function renderUnsupportedBoard"));
        assertTrue(js.contains("function renderLeaderboardRows"));
        assertTrue(js.contains("暂无真实榜单数据"));
        assertFalse(js.contains("棋逢对手"));
        assertFalse(js.contains("江湖小虾"));
        assertFalse(js.contains("棋圣无名"));
        assertFalse(js.contains("清风徐来"));
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
