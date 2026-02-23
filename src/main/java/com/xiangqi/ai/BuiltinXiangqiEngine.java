package com.xiangqi.ai;

import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.PieceColor;

public final class BuiltinXiangqiEngine implements XiangqiEngine {
    @Override
    public Move findBestMove(Board board, PieceColor aiColor, MinimaxAI.Difficulty difficulty) {
        MinimaxAI ai = new MinimaxAI();
        ai.setDifficulty(difficulty);
        return ai.findBestMove(board, aiColor);
    }

    @Override
    public String getEngineId() {
        return "builtin";
    }

    @Override
    public String getEngineText() {
        return "内置AI";
    }
}

