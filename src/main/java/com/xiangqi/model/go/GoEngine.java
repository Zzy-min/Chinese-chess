package com.xiangqi.model.go;

import com.xiangqi.ai.MinimaxAI;

public interface GoEngine extends AutoCloseable {
    boolean isAvailable();

    GoEngineMove genMove(GoBoard board, GoStone aiStone, MinimaxAI.Difficulty difficulty);

    GoScoreSummary score(GoBoard board);

    String getEngineName();

    @Override
    void close();
}
