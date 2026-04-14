package com.xiangqi.online.practice;

import com.xiangqi.online.game.GameType;

public final class CreatePracticeGameRequest {
    private final GameType gameType;
    private final String difficulty;
    private final boolean humanFirst;
    private final String preferredEngine;
    private final String fen;
    private final String endgameId;
    private final String endgameName;

    public CreatePracticeGameRequest(GameType gameType, String difficulty, boolean humanFirst, String preferredEngine) {
        this(gameType, difficulty, humanFirst, preferredEngine, null, null, null);
    }

    public CreatePracticeGameRequest(GameType gameType, String difficulty, boolean humanFirst, String preferredEngine, String fen, String endgameName) {
        this(gameType, difficulty, humanFirst, preferredEngine, fen, null, endgameName);
    }

    public CreatePracticeGameRequest(GameType gameType, String difficulty, boolean humanFirst, String preferredEngine, String fen, String endgameId, String endgameName) {
        this.gameType = gameType;
        this.difficulty = difficulty;
        this.humanFirst = humanFirst;
        this.preferredEngine = preferredEngine;
        this.fen = fen;
        this.endgameId = endgameId;
        this.endgameName = endgameName;
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

    public String fen() {
        return fen;
    }

    public String endgameId() {
        return endgameId;
    }

    public String endgameName() {
        return endgameName;
    }
}
