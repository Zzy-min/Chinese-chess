package com.xiangqi.model;

/**
 * 棋子类 - 表示棋盘上的单个棋子
 */
public class Piece {
    private PieceType type;
    private PieceColor color;
    private int row;
    private int col;

    public Piece(PieceType type, PieceColor color, int row, int col) {
        this.type = type;
        this.color = color;
        this.row = row;
        this.col = col;
    }

    public PieceType getType() {
        return type;
    }

    public PieceColor getColor() {
        return color;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public void setPosition(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public Piece copy() {
        return new Piece(type, color, row, col);
    }

    @Override
    public String toString() {
        return color.toString() + type.toString();
    }
}
