package com.xiangqi.controller;

import com.xiangqi.model.*;

/**
 * 残局加载器 - 提供经典残局
 */
public class EndgameLoader {

    public static void loadEndgame(Board board, String endgameName) {
        clearBoard(board);

        switch (endgameName) {
            case "七星聚会":
                loadSevenStarsGather(board);
                break;
            case "蚯蚓降龙":
                loadWormSubduesDragon(board);
                break;
            case "千里独行":
                loadThousandLiSolo(board);
                break;
            case "野马操田":
                loadWildHorsePlowing(board);
                break;
            default:
                board.initializeBoard();
        }
    }

    private static void clearBoard(Board board) {
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                board.setPiece(row, col, null);
            }
        }
        // 设置红方先手
        board.setCurrentTurn(PieceColor.RED);
    }

    // 七星聚会 - 经典残局
    private static void loadSevenStarsGather(Board board) {
        // 红方
        board.setPiece(9, 4, new Piece(PieceType.SHUAI, PieceColor.RED, 9, 4));
        board.setPiece(9, 1, new Piece(PieceType.CHE_RED, PieceColor.RED, 9, 1));
        board.setPiece(7, 1, new Piece(PieceType.PAO_RED, PieceColor.RED, 7, 1));
        board.setPiece(6, 0, new Piece(PieceType.ZU_RED, PieceColor.RED, 6, 0));
        board.setPiece(6, 2, new Piece(PieceType.ZU_RED, PieceColor.RED, 6, 2));
        board.setPiece(6, 4, new Piece(PieceType.ZU_RED, PieceColor.RED, 6, 4));
        board.setPiece(6, 6, new Piece(PieceType.ZU_RED, PieceColor.RED, 6, 6));
        board.setPiece(6, 8, new Piece(PieceType.ZU_RED, PieceColor.RED, 6, 8));

        // 黑方
        board.setPiece(0, 4, new Piece(PieceType.JIANG, PieceColor.BLACK, 0, 4));
        board.setPiece(0, 1, new Piece(PieceType.CHE, PieceColor.BLACK, 0, 1));
        board.setPiece(2, 1, new Piece(PieceType.PAO, PieceColor.BLACK, 2, 1));
        board.setPiece(3, 0, new Piece(PieceType.ZU, PieceColor.BLACK, 3, 0));
        board.setPiece(3, 2, new Piece(PieceType.ZU, PieceColor.BLACK, 3, 2));
        board.setPiece(3, 4, new Piece(PieceType.ZU, PieceColor.BLACK, 3, 4));
        board.setPiece(3, 6, new Piece(PieceType.ZU, PieceColor.BLACK, 3, 6));
        board.setPiece(3, 8, new Piece(PieceType.ZU, PieceColor.BLACK, 3, 8));

        board.setCurrentTurn(PieceColor.RED);
    }

    // 蚯蚓降龙
    private static void loadWormSubduesDragon(Board board) {
        // 红方（简化版）
        board.setPiece(9, 4, new Piece(PieceType.SHUAI, PieceColor.RED, 9, 4));
        board.setPiece(9, 0, new Piece(PieceType.CHE_RED, PieceColor.RED, 9, 0));
        board.setPiece(7, 4, new Piece(PieceType.PAO_RED, PieceColor.RED, 7, 4));
        board.setPiece(5, 3, new Piece(PieceType.ZU_RED, PieceColor.RED, 5, 3));
        board.setPiece(5, 5, new Piece(PieceType.ZU_RED, PieceColor.RED, 5, 5));

        // 黑方
        board.setPiece(0, 4, new Piece(PieceType.JIANG, PieceColor.BLACK, 0, 4));
        board.setPiece(0, 8, new Piece(PieceType.CHE, PieceColor.BLACK, 0, 8));
        board.setPiece(1, 7, new Piece(PieceType.MA, PieceColor.BLACK, 1, 7));
        board.setPiece(3, 4, new Piece(PieceType.ZU, PieceColor.BLACK, 3, 4));

        board.setCurrentTurn(PieceColor.RED);
    }

    // 千里独行
    private static void loadThousandLiSolo(Board board) {
        // 红方
        board.setPiece(9, 4, new Piece(PieceType.SHUAI, PieceColor.RED, 9, 4));
        board.setPiece(9, 1, new Piece(PieceType.CHE_RED, PieceColor.RED, 9, 1));
        board.setPiece(8, 2, new Piece(PieceType.MA_RED, PieceColor.RED, 8, 2));
        board.setPiece(6, 4, new Piece(PieceType.ZU_RED, PieceColor.RED, 6, 4));

        // 黑方
        board.setPiece(0, 4, new Piece(PieceType.JIANG, PieceColor.BLACK, 0, 4));
        board.setPiece(2, 4, new Piece(PieceType.CHE, PieceColor.BLACK, 2, 4));
        board.setPiece(3, 3, new Piece(PieceType.ZU, PieceColor.BLACK, 3, 3));

        board.setCurrentTurn(PieceColor.RED);
    }

    // 野马操田
    private static void loadWildHorsePlowing(Board board) {
        // 红方
        board.setPiece(9, 4, new Piece(PieceType.SHUAI, PieceColor.RED, 9, 4));
        board.setPiece(9, 0, new Piece(PieceType.CHE_RED, PieceColor.RED, 9, 0));
        board.setPiece(7, 2, new Piece(PieceType.MA_RED, PieceColor.RED, 7, 2));
        board.setPiece(6, 4, new Piece(PieceType.ZU_RED, PieceColor.RED, 6, 4));

        // 黑方
        board.setPiece(0, 4, new Piece(PieceType.JIANG, PieceColor.BLACK, 0, 4));
        board.setPiece(2, 4, new Piece(PieceType.CHE, PieceColor.BLACK, 2, 4));
        board.setPiece(3, 3, new Piece(PieceType.ZU, PieceColor.BLACK, 3, 3));
        board.setPiece(3, 5, new Piece(PieceType.ZU, PieceColor.BLACK, 3, 5));

        board.setCurrentTurn(PieceColor.RED);
    }
}
