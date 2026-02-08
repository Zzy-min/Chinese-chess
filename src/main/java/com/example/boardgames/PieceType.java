package com.example.boardgames;

public enum PieceType {
    GENERAL("帅", "将"),
    ADVISOR("仕", "士"),
    ELEPHANT("相", "象"),
    HORSE("马", "马"),
    ROOK("车", "车"),
    CANNON("炮", "炮"),
    SOLDIER("兵", "卒");

    private final String redLabel;
    private final String blackLabel;

    PieceType(String redLabel, String blackLabel) {
        this.redLabel = redLabel;
        this.blackLabel = blackLabel;
    }

    public String getLabel(boolean red) {
        return red ? redLabel : blackLabel;
    }
}
