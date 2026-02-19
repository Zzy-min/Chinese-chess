package com.xiangqi.controller;

import com.xiangqi.model.*;
import java.util.Arrays;
import java.util.List;

/**
 * 残局加载器 - 提供经典残局
 */
public class EndgameLoader {
    private static final List<String> ENDGAME_NAMES = Arrays.asList(
        "七星聚会",
        "蚯蚓降龙",
        "千里独行",
        "野马操田",
        "梅花谱",
        "百局象棋谱",
        "适情雅趣",
        "烂柯神机",
        "梦入神机",
        "韬略元机"
    );

    public static List<String> getEndgameNames() {
        return ENDGAME_NAMES;
    }

    public static void loadEndgame(Board board, String endgameName) {
        clearBoard(board);

        switch (endgameName) {
            case "七星聚会":
                loadFromFen(board, "4rk3/3P5/4bP3/9/9/8P/9/1p2p2C1/3p1p3/4K1RR1 w");
                break;
            case "蚯蚓降龙":
                loadFromFen(board, "2rak4/4a4/b8/9/2p6/1RC5r/8P/6p2/4p4/5K2R w");
                break;
            case "千里独行":
                loadFromFen(board, "4k4/9/3aP3b/p8/9/6n2/2P3p2/4R4/3p1p3/4K4 w");
                break;
            case "野马操田":
                loadFromFen(board, "2bak4/4a4/4b4/9/6NRR/2B6/1rP1P3N/3pB4/4p4/3K5 w");
                break;
            case "梅花谱":
                loadFromFen(board, "4k4/3a1a3/6n2/4p4/9/4P4/3C5/7N1/4A4/4K4 w");
                break;
            case "百局象棋谱":
                loadFromFen(board, "4ka3/4a4/9/6b2/9/3rR3C/5N3/6B2/3K1A3/9 w");
                break;
            case "适情雅趣":
                loadFromFen(board, "2baka3/4a4/4b4/9/9/7R1/4B4/4A4/3pN4/3K5 w");
                break;
            case "烂柯神机":
                loadFromFen(board, "4k4/3Pa4/4ba3/9/9/8N/9/9/5R3/3K5 b");
                break;
            case "梦入神机":
                loadFromFen(board, "3aka3/5c3/4b4/4p4/5r3/9/9/9/4N4/4KA3 w");
                break;
            case "韬略元机":
                loadFromFen(board, "2ba5/4a4/4bk3/r8/9/6B2/9/4B4/4A4/5K3 b");
                break;
            default:
                board.initializeBoard();
        }
    }

    private static void clearBoard(Board board) {
        // 先复位以清理历史，再按残局坐标重新放子
        board.initializeBoard();
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                board.setPiece(row, col, null);
            }
        }
        board.setCurrentTurn(PieceColor.RED);
    }

    private static void loadFromFen(Board board, String fen) {
        String[] parts = fen.trim().split("\\s+");
        if (parts.length == 0) {
            return;
        }

        String[] rows = parts[0].split("/");
        for (int row = 0; row < Math.min(rows.length, Board.ROWS); row++) {
            int col = 0;
            for (char c : rows[row].toCharArray()) {
                if (Character.isDigit(c)) {
                    col += c - '0';
                    continue;
                }
                Piece piece = createPieceFromFen(c, row, col);
                if (piece != null && col >= 0 && col < Board.COLS) {
                    board.setPiece(row, col, piece);
                    col++;
                }
            }
        }

        if (parts.length > 1 && "b".equalsIgnoreCase(parts[1])) {
            board.setCurrentTurn(PieceColor.BLACK);
        } else {
            board.setCurrentTurn(PieceColor.RED);
        }
    }

    private static Piece createPieceFromFen(char ch, int row, int col) {
        boolean red = Character.isUpperCase(ch);
        char c = Character.toLowerCase(ch);

        PieceType type;
        switch (c) {
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
