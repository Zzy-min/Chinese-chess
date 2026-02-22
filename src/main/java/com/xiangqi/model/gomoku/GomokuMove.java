package com.xiangqi.model.gomoku;

public final class GomokuMove {
    private final int row;
    private final int col;
    private final GomokuStone stone;

    public GomokuMove(int row, int col, GomokuStone stone) {
        this.row = row;
        this.col = col;
        this.stone = stone;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public GomokuStone getStone() {
        return stone;
    }
}

