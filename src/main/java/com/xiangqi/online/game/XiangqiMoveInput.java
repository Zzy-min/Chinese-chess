package com.xiangqi.online.game;

public final class XiangqiMoveInput {
    private final int fromRow;
    private final int fromCol;
    private final int toRow;
    private final int toCol;

    public XiangqiMoveInput(int fromRow, int fromCol, int toRow, int toCol) {
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
    }

    public int fromRow() {
        return fromRow;
    }

    public int fromCol() {
        return fromCol;
    }

    public int toRow() {
        return toRow;
    }

    public int toCol() {
        return toCol;
    }
}
