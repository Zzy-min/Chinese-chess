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

        assertTrue(js.contains("现在开始下棋"));
        assertTrue(js.contains("AI 对局"));
        assertTrue(js.contains("在线对局"));
        assertTrue(js.indexOf("AI 对局") < js.indexOf("在线对局"));
        assertTrue(js.contains("data-action=\"play-practice-again\""));
        assertTrue(js.contains("返回首页"));
        assertFalse(js.contains("首页承接 AI 对局与在线对局两条入口"));
        assertFalse(js.contains("米色大厅语言"));
        assertFalse(js.contains("返回学习页"));
        assertTrue(js.contains("function renderReadonlyBoard("));
        assertTrue(js.contains("renderReadonlyBoard(analysis.gameType, board)"));
        assertFalse(js.contains("function renderStaticXiangqiBoard("));
        assertFalse(js.contains("function renderStaticGomokuBoard("));
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
