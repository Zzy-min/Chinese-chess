package com.xiangqi.model.gomoku;

import com.xiangqi.ai.MinimaxAI;

public final class BuiltinGomokuEngine implements GomokuEngine {
    private final GomokuAI ai = new GomokuAI();

    @Override
    public int[] findBestMove(GomokuBoard board, GomokuStone aiStone, MinimaxAI.Difficulty difficulty) {
        return ai.findBestMove(board, aiStone, difficulty);
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

