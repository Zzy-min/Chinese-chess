package com.xiangqi.model;

/**
 * 棋子颜色枚举
 */
public enum PieceColor {
    RED("红方"), BLACK("黑方");

    private String displayName;

    PieceColor(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PieceColor opposite() {
        return this == RED ? BLACK : RED;
    }
}
