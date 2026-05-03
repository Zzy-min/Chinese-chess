package com.xiangqi.model;

import java.util.Objects;

/**
 * 移动类 - 表示棋子的移动
 */
public class Move {
    public static final int FLAG_CAPTURE   = 1;
    public static final int FLAG_CHECK     = 2;
    public static final int FLAG_CHECKMATE = 4;

    private int fromRow;
    private int fromCol;
    private int toRow;
    private int toCol;
    private Piece capturedPiece;
    private int flags;
    private String notation;
    private long hashBefore;

    public Move(int fromRow, int fromCol, int toRow, int toCol) {
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
        this.capturedPiece = null;
        this.flags = 0;
    }

    public Move(int fromRow, int fromCol, int toRow, int toCol, int flags) {
        this(fromRow, fromCol, toRow, toCol);
        this.flags = flags;
    }

    public void setCapturedPiece(Piece piece) {
        this.capturedPiece = piece;
    }

    public Piece getCapturedPiece() {
        return capturedPiece;
    }

    public int getFromRow() { return fromRow; }
    public int getFromCol() { return fromCol; }
    public int getToRow() { return toRow; }
    public int getToCol() { return toCol; }

    public int getFlags() { return flags; }
    public void setFlags(int flags) { this.flags = flags; }

    public String getNotation() { return notation; }
    public void setNotation(String notation) { this.notation = notation; }

    public long getHashBefore() { return hashBefore; }
    public void setHashBefore(long hashBefore) { this.hashBefore = hashBefore; }

    public boolean isCapture() { return (flags & FLAG_CAPTURE) != 0; }
    public boolean isCheck() { return (flags & FLAG_CHECK) != 0; }
    public boolean isCheckmate() { return (flags & FLAG_CHECKMATE) != 0; }

    public void setCapture() { flags |= FLAG_CAPTURE; }
    public void setCheck() { flags |= FLAG_CHECK; }
    public void setCheckmate() { flags |= FLAG_CHECKMATE; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Move move = (Move) obj;
        return fromRow == move.fromRow && fromCol == move.fromCol &&
                toRow == move.toRow && toCol == move.toCol;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromRow, fromCol, toRow, toCol);
    }

    public Move copy() {
        Move copy = new Move(fromRow, fromCol, toRow, toCol, flags);
        if (capturedPiece != null) {
            copy.capturedPiece = capturedPiece.copy();
        }
        copy.notation = notation;
        copy.hashBefore = hashBefore;
        return copy;
    }

    @Override
    public String toString() {
        return String.format("(%d,%d) -> (%d,%d)", fromRow, fromCol, toRow, toCol);
    }
}
