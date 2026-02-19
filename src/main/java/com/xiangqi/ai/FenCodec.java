package com.xiangqi.ai;

import com.xiangqi.model.Board;
import com.xiangqi.model.Piece;
import com.xiangqi.model.PieceColor;
import com.xiangqi.model.PieceType;

public final class FenCodec {
    private FenCodec() {
    }

    public static String toFen(Board board) {
        if (board == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(96);
        for (int row = 0; row < Board.ROWS; row++) {
            if (row > 0) {
                sb.append('/');
            }
            int empty = 0;
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece == null) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    sb.append(empty);
                    empty = 0;
                }
                sb.append(toFenChar(piece));
            }
            if (empty > 0) {
                sb.append(empty);
            }
        }
        sb.append(' ');
        sb.append(board.getCurrentTurn() == PieceColor.RED ? 'w' : 'b');
        return sb.toString();
    }

    private static char toFenChar(Piece piece) {
        PieceType type = piece.getType();
        boolean red = piece.getColor() == PieceColor.RED;
        char c;
        switch (type) {
            case JIANG:
            case SHUAI:
                c = 'k';
                break;
            case SHI:
            case SHI_RED:
                c = 'a';
                break;
            case XIANG:
            case XIANG_RED:
                c = 'b';
                break;
            case MA:
            case MA_RED:
                c = 'n';
                break;
            case CHE:
            case CHE_RED:
                c = 'r';
                break;
            case PAO:
            case PAO_RED:
                c = 'c';
                break;
            case ZU:
            case ZU_RED:
                c = 'p';
                break;
            default:
                c = '1';
                break;
        }
        return red ? Character.toUpperCase(c) : c;
    }
}
