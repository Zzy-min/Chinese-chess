package com.xiangqi.online.practice;

import com.xiangqi.online.game.GameType;

public final class CreatePracticeGameRequest {
    private final GameType gameType;
    private final String difficulty;
    private final boolean humanFirst;
    private final String preferredEngine;
    private final String initialFen;

    public CreatePracticeGameRequest(GameType gameType, String difficulty, boolean humanFirst, String preferredEngine, String initialFen) {
        this.gameType = gameType;
        this.difficulty = difficulty;
        this.humanFirst = humanFirst;
        this.preferredEngine = preferredEngine;
        this.initialFen = initialFen;
    }

    public GameType gameType() {
        return gameType;
    }

    public String difficulty() {
        return difficulty;
    }

    public boolean humanFirst() {
        return humanFirst;
    }

    public String preferredEngine() {
        return preferredEngine;
    }

    public String initialFen() {
        return initialFen;
    }
}
