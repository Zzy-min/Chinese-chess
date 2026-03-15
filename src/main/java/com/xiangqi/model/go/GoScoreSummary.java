package com.xiangqi.model.go;

import java.util.Locale;

public final class GoScoreSummary {
    private final int blackArea;
    private final int whiteArea;
    private final double komi;
    private final double finalScore;
    private final String winner;

    public GoScoreSummary(int blackArea, int whiteArea, double komi, double finalScore, String winner) {
        this.blackArea = blackArea;
        this.whiteArea = whiteArea;
        this.komi = komi;
        this.finalScore = finalScore;
        this.winner = winner == null ? "" : winner;
    }

    public int getBlackArea() {
        return blackArea;
    }

    public int getWhiteArea() {
        return whiteArea;
    }

    public double getKomi() {
        return komi;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public String getWinner() {
        return winner;
    }

    public String getResultText() {
        if (finalScore == 0.0d) {
            return "双方持平";
        }
        String side = finalScore > 0 ? "黑胜" : "白胜";
        return side + String.format(Locale.ROOT, "%.1f", Math.abs(finalScore));
    }
}
