package com.xiangqi.model.gomoku;

import com.xiangqi.ai.MinimaxAI;

public interface GomokuEngine {
    int[] findBestMove(GomokuBoard board, GomokuStone aiStone, MinimaxAI.Difficulty difficulty);

    String getEngineId();

    String getEngineText();

    default void close() {
        // no-op
    }
}

