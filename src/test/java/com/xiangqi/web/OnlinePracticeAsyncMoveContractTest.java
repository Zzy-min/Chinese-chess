package com.xiangqi.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlinePracticeAsyncMoveContractTest {

    @Test
    void practiceHumanMoveDoesNotTriggerImmediatePollAndKeepsAsyncMessaging() throws Exception {
        String js = readResource("/online/app.js");

        assertTrue(js.contains("function applyOptimisticPracticeMove"));
        assertTrue(js.contains("function playOptimisticPracticeMoveSound"));
        assertTrue(js.contains("playOnlineSound(onlineMoveAudio)"));
        assertFalse(js.contains("startPracticePolling(state.game.gameId, true)"));
        assertTrue(js.contains("startPracticePolling(state.game.gameId, false)"));
        assertTrue(js.contains("AI 思考中..."));
        assertFalse(js.contains("后端会立刻返回 AI 应手"));
    }

    private String readResource(String path) throws IOException {
        try (InputStream input = OnlinePracticeAsyncMoveContractTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("missing resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
