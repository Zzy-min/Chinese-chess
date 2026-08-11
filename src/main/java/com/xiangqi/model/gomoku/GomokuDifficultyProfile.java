package com.xiangqi.model.gomoku;

import com.xiangqi.ai.MinimaxAI;

import java.util.Locale;

/** Human-facing Gomoku difficulty independent from Xiangqi's three-level search enum. */
public enum GomokuDifficultyProfile {
    NOVICE("入门", true, 120, 3, 0.45, 10, MinimaxAI.Difficulty.EASY),
    EASY("简单", true, 220, 5, 0.18, 8, MinimaxAI.Difficulty.EASY),
    MEDIUM("中等", true, 480, 7, 0.04, 5, MinimaxAI.Difficulty.MEDIUM),
    HARD("困难", false, 1_200, 11, 0.0, 3, MinimaxAI.Difficulty.HARD),
    MASTER("大师", false, 2_500, 15, 0.0, 1, MinimaxAI.Difficulty.HARD);

    private final String label;
    private final boolean preferBuiltin;
    private final int timeoutTurnMs;
    private final int maxDepth;
    private final double blunderRate;
    private final int candidateWidth;
    private final MinimaxAI.Difficulty builtinDifficulty;

    GomokuDifficultyProfile(String label, boolean preferBuiltin, int timeoutTurnMs, int maxDepth,
                            double blunderRate, int candidateWidth, MinimaxAI.Difficulty builtinDifficulty) {
        this.label = label;
        this.preferBuiltin = preferBuiltin;
        this.timeoutTurnMs = timeoutTurnMs;
        this.maxDepth = maxDepth;
        this.blunderRate = blunderRate;
        this.candidateWidth = candidateWidth;
        this.builtinDifficulty = builtinDifficulty;
    }

    public String label() { return label; }
    public boolean preferBuiltin() { return preferBuiltin; }
    public int timeoutTurnMs() { return timeoutTurnMs; }
    public int maxDepth() { return maxDepth; }
    public double blunderRate() { return blunderRate; }
    public int candidateWidth() { return candidateWidth; }
    public MinimaxAI.Difficulty builtinDifficulty() { return builtinDifficulty; }

    public static GomokuDifficultyProfile from(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if ("NOVICE".equals(value)) return NOVICE;
        if ("MEDIUM".equals(value)) return MEDIUM;
        if ("HARD".equals(value)) return HARD;
        if ("MASTER".equals(value) || "EXPERT".equals(value)) return MASTER;
        return EASY;
    }

    public static GomokuDifficultyProfile fromLegacy(MinimaxAI.Difficulty difficulty) {
        if (difficulty == MinimaxAI.Difficulty.HARD) return HARD;
        if (difficulty == MinimaxAI.Difficulty.MEDIUM) return MEDIUM;
        return EASY;
    }
}
