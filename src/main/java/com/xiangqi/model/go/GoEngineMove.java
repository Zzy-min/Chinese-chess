package com.xiangqi.model.go;

public final class GoEngineMove {
    private final int row;
    private final int col;
    private final boolean pass;

    public GoEngineMove(int row, int col, boolean pass) {
        this.row = row;
        this.col = col;
        this.pass = pass;
    }

    public static GoEngineMove pass() {
        return new GoEngineMove(-1, -1, true);
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public boolean isPass() {
        return pass;
    }
}
