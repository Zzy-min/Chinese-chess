package com.xiangqi.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 杀招/战术术语检测（规则版）。
 * 说明：术语判定采用"可稳定计算"的局面特征，优先保证提示一致性。
 */
public final class TacticDetector {
    private TacticDetector() {
    }

    public static String detect(Board board) {
        Move lastMove = board.getLastMove();
        if (lastMove == null) {
            return "";
        }

        PieceColor defender = board.getCurrentTurn();
        if (!board.isInCheck(defender)) {
            return "";
        }

        PieceColor attacker = defender.opposite();
        Piece defenderGeneral = findGeneral(board, defender);
        if (defenderGeneral == null) {
            return "绝杀";
        }

        Piece moved = board.getPiece(lastMove.getToRow(), lastMove.getToCol());
        List<Piece> attackers = getAttackers(board, attacker, defenderGeneral.getRow(), defenderGeneral.getCol());

        // 高优先级终结语
        if (board.isCheckmate(defender)) {
            return "绝杀";
        }

        if (isMuffledPalace(board, defenderGeneral)) {
            return "闷宫";
        }

        // 白脸将：将帅照面形成将军（在残局导入时可能出现）
        if (board.areGeneralsFacing()) {
            return "白脸将";
        }

        if (attackers.size() >= 2) {
            if (isDoubleCannonCheck(attackers)) {
                return "重炮";
            }
            return "双将";
        }

        if (isHorseBehindCannon(board, attacker, defenderGeneral)) {
            return "马后炮";
        }

        boolean movedDirectCheck = moved != null && board.isValidMoveForPiece(moved, defenderGeneral.getRow(), defenderGeneral.getCol());
        if (isDiscoveredCheck(board, lastMove, attacker, defenderGeneral, movedDirectCheck)) {
            return "闪将";
        }

        if (lastMove.getCapturedPiece() != null && movedDirectCheck) {
            return "抽将";
        }

        if (moved != null && isHorseTrappingCheck(moved, defenderGeneral)) {
            return "卧槽马";
        }

        if (moved != null && (moved.getType() == PieceType.CHE || moved.getType() == PieceType.CHE_RED)
            && isRookCloseCheck(lastMove, defenderGeneral)) {
            return "铁门栓";
        }

        return "将军";
    }

    private static List<Piece> getAttackers(Board board, PieceColor attacker, int targetRow, int targetCol) {
        List<Piece> result = new ArrayList<>();
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece == null || piece.getColor() != attacker) {
                    continue;
                }
                if (piece.getRow() == targetRow && piece.getCol() == targetCol) {
                    continue;
                }
                if (board.isValidMoveForPiece(piece, targetRow, targetCol)) {
                    Piece target = board.getPiece(targetRow, targetCol);
                    if (target == null || target.getColor() != attacker) {
                        result.add(piece);
                    }
                }
            }
        }
        return result;
    }

    private static boolean isDoubleCannonCheck(List<Piece> attackers) {
        int cannons = 0;
        for (Piece p : attackers) {
            if (p.getType() == PieceType.PAO || p.getType() == PieceType.PAO_RED) {
                cannons++;
            }
        }
        return cannons >= 2;
    }

    private static boolean isMuffledPalace(Board board, Piece defenderGeneral) {
        int row = defenderGeneral.getRow();
        int col = defenderGeneral.getCol();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] d : dirs) {
            int toRow = row + d[0];
            int toCol = col + d[1];
            Move move = new Move(row, col, toRow, toCol);
            if (board.isValidMove(move)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHorseBehindCannon(Board board, PieceColor attacker, Piece defenderGeneral) {
        int targetRow = defenderGeneral.getRow();
        int targetCol = defenderGeneral.getCol();

        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece cannon = board.getPiece(row, col);
                if (cannon == null || cannon.getColor() != attacker) {
                    continue;
                }
                if (!(cannon.getType() == PieceType.PAO || cannon.getType() == PieceType.PAO_RED)) {
                    continue;
                }
                if (!board.isValidMoveForPiece(cannon, targetRow, targetCol)) {
                    continue;
                }

                // 检查炮与将之间唯一隔子是否为己方马
                if (row == targetRow) {
                    int min = Math.min(col, targetCol) + 1;
                    int max = Math.max(col, targetCol);
                    Piece blocker = null;
                    for (int c = min; c < max; c++) {
                        Piece p = board.getPiece(row, c);
                        if (p != null) {
                            if (blocker != null) {
                                blocker = null;
                                break;
                            }
                            blocker = p;
                        }
                    }
                    if (isHorse(blocker, attacker)) {
                        return true;
                    }
                } else if (col == targetCol) {
                    int min = Math.min(row, targetRow) + 1;
                    int max = Math.max(row, targetRow);
                    Piece blocker = null;
                    for (int r = min; r < max; r++) {
                        Piece p = board.getPiece(r, col);
                        if (p != null) {
                            if (blocker != null) {
                                blocker = null;
                                break;
                            }
                            blocker = p;
                        }
                    }
                    if (isHorse(blocker, attacker)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isHorse(Piece piece, PieceColor color) {
        if (piece == null || piece.getColor() != color) {
            return false;
        }
        return piece.getType() == PieceType.MA || piece.getType() == PieceType.MA_RED;
    }

    private static boolean isDiscoveredCheck(Board board, Move lastMove, PieceColor attacker,
                                             Piece defenderGeneral, boolean movedDirectCheck) {
        if (movedDirectCheck) {
            return false;
        }

        int targetRow = defenderGeneral.getRow();
        int targetCol = defenderGeneral.getCol();

        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece == null || piece.getColor() != attacker) {
                    continue;
                }
                PieceType t = piece.getType();
                boolean linePiece = t == PieceType.CHE || t == PieceType.CHE_RED
                    || t == PieceType.PAO || t == PieceType.PAO_RED
                    || t == PieceType.JIANG || t == PieceType.SHUAI;
                if (!linePiece) {
                    continue;
                }

                boolean fromInLine = (lastMove.getFromRow() == targetRow && row == targetRow)
                    || (lastMove.getFromCol() == targetCol && col == targetCol);
                if (!fromInLine) {
                    continue;
                }

                if (board.isValidMoveForPiece(piece, targetRow, targetCol)) {
                    Piece target = board.getPiece(targetRow, targetCol);
                    if (target != null && target.getColor() != attacker) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isHorseTrappingCheck(Piece moved, Piece defenderGeneral) {
        PieceType type = moved.getType();
        if (type != PieceType.MA && type != PieceType.MA_RED) {
            return false;
        }

        // 典型“卧槽马”位：将帅宫门两侧
        if (defenderGeneral.getColor() == PieceColor.BLACK) {
            return moved.getRow() == 2 && (moved.getCol() == 3 || moved.getCol() == 5);
        }
        return moved.getRow() == 7 && (moved.getCol() == 3 || moved.getCol() == 5);
    }

    private static boolean isRookCloseCheck(Move move, Piece defenderGeneral) {
        return move.getToCol() == defenderGeneral.getCol()
            && Math.abs(move.getToRow() - defenderGeneral.getRow()) <= 2;
    }

    private static Piece findGeneral(Board board, PieceColor color) {
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece p = board.getPiece(row, col);
                if (p != null && p.getColor() == color && p.getType().isGeneral()) {
                    return p;
                }
            }
        }
        return null;
    }
}
