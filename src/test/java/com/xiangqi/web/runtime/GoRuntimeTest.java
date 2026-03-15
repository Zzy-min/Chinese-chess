package com.xiangqi.web.runtime;

import com.xiangqi.ai.MinimaxAI;
import com.xiangqi.model.go.GoEngine;
import com.xiangqi.model.go.GoEngineMove;
import com.xiangqi.model.go.GoScoreSummary;
import com.xiangqi.model.go.GoStone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoRuntimeTest {

    @Test
    void shouldFallbackToPvpWhenGoEngineUnavailable() {
        GoRuntime runtime = new GoRuntime(new UnavailableGoEngine());

        runtime.reset(new GameOptions(true, MinimaxAI.Difficulty.MEDIUM, true));

        assertFalse(runtime.isPvcMode());
        assertTrue(runtime.toJson(1).contains("\"engineAvailable\":false"));
    }

    @Test
    void shouldLoadScenarioAndExposeScenarioName() {
        GoRuntime runtime = new GoRuntime(new UnavailableGoEngine());

        runtime.loadScenario("角部提子", new GameOptions(false, MinimaxAI.Difficulty.EASY, true));

        String json = runtime.toJson(1);
        assertTrue(json.contains("\"scenarioName\":\"角部提子\""));
        assertTrue(json.contains("\"gameType\":\"GO\""));
    }

    private static final class UnavailableGoEngine implements GoEngine {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public GoEngineMove genMove(com.xiangqi.model.go.GoBoard board, GoStone aiStone, MinimaxAI.Difficulty difficulty) {
            return null;
        }

        @Override
        public GoScoreSummary score(com.xiangqi.model.go.GoBoard board) {
            return null;
        }

        @Override
        public String getEngineName() {
            return "unavailable";
        }

        @Override
        public void close() {
        }
    }
}
