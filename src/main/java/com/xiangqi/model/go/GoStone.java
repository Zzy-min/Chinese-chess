package com.xiangqi.model.go;

public enum GoStone {
    EMPTY("."),
    BLACK("黑"),
    WHITE("白");

    private final String displayText;

    GoStone(String displayText) {
        this.displayText = displayText;
    }

    public GoStone opposite() {
        if (this == BLACK) {
            return WHITE;
        }
        if (this == WHITE) {
            return BLACK;
        }
        return EMPTY;
    }

    public String getDisplayText() {
        return displayText;
    }
}
