package com.xiangqi.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebUiResourceTest {
    @Test
    void gomokuRendererSkipsStaleXiangqiRowsDuringGameSwitch() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/web/app.js")) {
            if (input == null) {
                throw new IOException("Missing /web/app.js");
            }
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(script.contains("state.gameType===GAME_GOMOKU&&Array.isArray(state.board)"));
            assertTrue(script.contains("const boardRow=state.board[r];if(!Array.isArray(boardRow))continue;"));
            assertTrue(script.contains("const p=boardRow[c];"));
        }
    }
}
