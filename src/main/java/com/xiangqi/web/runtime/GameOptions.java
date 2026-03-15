package com.xiangqi.web.runtime;

import com.xiangqi.ai.MinimaxAI;

public final class GameOptions {
    private final boolean pvcMode;
    private final MinimaxAI.Difficulty difficulty;
    private final boolean humanFirst;

    public GameOptions(boolean pvcMode, MinimaxAI.Difficulty difficulty, boolean humanFirst) {
        this.pvcMode = pvcMode;
        this.difficulty = difficulty == null ? MinimaxAI.Difficulty.MEDIUM : difficulty;
        this.humanFirst = humanFirst;
    }

    public boolean isPvcMode() {
        return pvcMode;
    }

    public MinimaxAI.Difficulty getDifficulty() {
        return difficulty;
    }

    public boolean isHumanFirst() {
        return humanFirst;
    }
}
