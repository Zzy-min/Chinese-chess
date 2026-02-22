package com.xiangqi.model.gomoku;

public enum GomokuStone {
    EMPTY,
    BLACK,
    WHITE;

    public GomokuStone opposite() {
        if (this == BLACK) {
            return WHITE;
        }
        if (this == WHITE) {
            return BLACK;
        }
        return EMPTY;
    }
}

