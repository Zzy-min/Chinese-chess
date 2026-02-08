package com.example.boardgames;

public class Piece {
    private final PieceType type;
    private final boolean red;

    public Piece(PieceType type, boolean red) {
        this.type = type;
        this.red = red;
    }

    public PieceType getType() {
        return type;
    }

    public boolean isRed() {
        return red;
    }
}
