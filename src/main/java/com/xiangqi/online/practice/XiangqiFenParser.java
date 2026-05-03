package com.xiangqi.online.practice;

import com.xiangqi.model.Board;
import com.xiangqi.model.Piece;
import com.xiangqi.model.PieceColor;
import com.xiangqi.model.PieceType;

public final class XiangqiFenParser {
    private XiangqiFenParser() {
    }

    public static Board parse(String fen) {
        String trimmed = fen == null ? "" : fen.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("initial FEN is empty");
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) {
            throw new IllegalArgumentException("initial FEN is empty");
        }

        String[] rows = parts[0].split("/");
        if (rows.length != Board.ROWS) {
            throw new IllegalArgumentException("invalid initial FEN: expected 10 rows");
        }

        Board board = new Board();
        clearBoard(board);
        for (int row = 0; row < Board.ROWS; row++) {
            parseRow(board, rows[row], row);
        }
        ensureGeneralsPresent(board);
        board.setCurrentTurn(parseTurn(parts));
        return board;
    }

    private static void clearBoard(Board board) {
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                board.setPiece(row, col, null);
            }
        }
    }

    private static void parseRow(Board board, String rowText, int row) {
        int col = 0;
        for (int i = 0; i < rowText.length(); i++) {
            char ch = rowText.charAt(i);
            if (Character.isDigit(ch)) {
                int empty = ch - '0';
                if (empty <= 0 || empty > Board.COLS) {
                    throw new IllegalArgumentException("invalid initial FEN: invalid empty count");
                }
                col += empty;
                if (col > Board.COLS) {
                    throw new IllegalArgumentException("invalid initial FEN: row width overflow");
                }
                continue;
            }
            if (col >= Board.COLS) {
                throw new IllegalArgumentException("invalid initial FEN: row width overflow");
            }
            Piece piece = createPiece(ch, row, col);
            if (piece == null) {
                throw new IllegalArgumentException("invalid initial FEN: unsupported piece char");
            }
            board.setPiece(row, col, piece);
            col++;
        }
        if (col != Board.COLS) {
            throw new IllegalArgumentException("invalid initial FEN: each row must have 9 columns");
        }
    }

    private static void ensureGeneralsPresent(Board board) {
        int redGeneral = 0;
        int blackGeneral = 0;
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece == null || !piece.getType().isGeneral()) {
                    continue;
                }
                if (piece.getColor() == PieceColor.RED) {
                    redGeneral++;
                } else {
                    blackGeneral++;
                }
            }
        }
        if (redGeneral != 1 || blackGeneral != 1) {
            throw new IllegalArgumentException("invalid initial FEN: must contain exactly one red and one black general");
        }
    }

    private static PieceColor parseTurn(String[] parts) {
        if (parts.length < 2 || parts[1] == null || parts[1].trim().isEmpty()) {
            return PieceColor.RED;
        }
        String turn = parts[1].trim().toLowerCase();
        if ("w".equals(turn)) {
            return PieceColor.RED;
        }
        if ("b".equals(turn)) {
            return PieceColor.BLACK;
        }
        throw new IllegalArgumentException("invalid initial FEN: turn must be w or b");
    }

    private static Piece createPiece(char ch, int row, int col) {
        boolean red = Character.isUpperCase(ch);
        char code = Character.toLowerCase(ch);
        PieceType type;
        switch (code) {
            case 'k':
                type = red ? PieceType.SHUAI : PieceType.JIANG;
                break;
            case 'a':
                type = red ? PieceType.SHI_RED : PieceType.SHI;
                break;
            case 'b':
                type = red ? PieceType.XIANG_RED : PieceType.XIANG;
                break;
            case 'n':
                type = red ? PieceType.MA_RED : PieceType.MA;
                break;
            case 'r':
                type = red ? PieceType.CHE_RED : PieceType.CHE;
                break;
            case 'c':
                type = red ? PieceType.PAO_RED : PieceType.PAO;
                break;
            case 'p':
                type = red ? PieceType.ZU_RED : PieceType.ZU;
                break;
            default:
                return null;
        }
        return new Piece(type, red ? PieceColor.RED : PieceColor.BLACK, row, col);
    }
}
