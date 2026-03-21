package com.xiangqi.online.game;

public final class GomokuMoveInput {
    private final int row;
    private final int col;

    public GomokuMoveInput(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int row() {
        return row;
    }

    public int col() {
        return col;
    }
}
