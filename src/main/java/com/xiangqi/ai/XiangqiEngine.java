package com.xiangqi.ai;

import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.PieceColor;

public interface XiangqiEngine {
    Move findBestMove(Board board, PieceColor aiColor, MinimaxAI.Difficulty difficulty);

    String getEngineId();

    String getEngineText();

    default void close() {
        // no-op
    }
}

