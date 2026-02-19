package com.xiangqi.model;

/**
 * 移动类 - 表示一次棋子移动
 */
public class Move {
    private int fromRow;
    private int fromCol;
    private int toRow;
    private int toCol;
    private Piece capturedPiece; // 被吃掉的棋子

    public Move(int fromRow, int fromCol, int toRow, int toCol) {
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
        this.capturedPiece = null;
    }

    public void setCapturedPiece(Piece piece) {
        this.capturedPiece = piece;
    }

    public Piece getCapturedPiece() {
        return capturedPiece;
    }

    public int getFromRow() {
        return fromRow;
    }

    public int getFromCol() {
        return fromCol;
    }

    public int getToRow() {
        return toRow;
    }

    public int getToCol() {
        return toCol;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Move move = (Move) obj;
        return fromRow == move.fromRow && fromCol == move.fromCol &&
                toRow == move.toRow && toCol == move.toCol;
    }

    @Override
    public String toString() {
        return String.format("(%d,%d) -> (%d,%d)", fromRow, fromCol, toRow, toCol);
    }
}
