package com.xiangqi.model;

import java.util.Random;

/**
 * Zobrist 哈希 - 用于棋局状态的高效指纹计算
 *
 * 使用 XOR 操作实现增量更新：落子时 XOR 棋子位置键，提子时再次 XOR 移除。
 * 用于置换表、三次重复检测等场景。
 */
public final class Zobrist {
    private static final int NUM_PIECES = 14;
    private static final int NUM_SQUARES = 90; // 10 rows × 9 cols

    private static final long[][] PIECE_KEYS = new long[NUM_PIECES][NUM_SQUARES];
    private static final long TURN_KEY;
    private static final long[] ENCODING;

    static {
        Random rng = new Random(0xC0FFEE123456789L); // 固定种子保证可重现
        for (int p = 0; p < NUM_PIECES; p++) {
            for (int sq = 0; sq < NUM_SQUARES; sq++) {
                PIECE_KEYS[p][sq] = rng.nextLong();
            }
        }
        TURN_KEY = rng.nextLong();

        // 构建 PieceType → 索引映射
        ENCODING = new long[PieceType.values().length];
        PieceType[] types = PieceType.values();
        for (int i = 0; i < types.length; i++) {
            ENCODING[i] = i;
        }
    }

    private Zobrist() {}

    /**
     * 计算整个棋盘的 Zobrist 哈希
     */
    public static long compute(Board board) {
        long hash = 0;
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece != null) {
                    hash ^= pieceKey(piece, row, col);
                }
            }
        }
        if (board.getCurrentTurn() == PieceColor.BLACK) {
            hash ^= TURN_KEY;
        }
        return hash;
    }

    /**
     * 增量更新：放置棋子
     */
    public static long place(long hash, Piece piece, int row, int col) {
        return hash ^ pieceKey(piece, row, col);
    }

    /**
     * 增量更新：移除棋子
     */
    public static long remove(long hash, Piece piece, int row, int col) {
        return hash ^ pieceKey(piece, row, col);
    }

    /**
     * 增量更新：移动棋子（从 from 到 to）
     */
    public static long move(long hash, Piece piece, int fromRow, int fromCol, int toRow, int toCol) {
        hash ^= pieceKey(piece, fromRow, fromCol);
        hash ^= pieceKey(piece, toRow, toCol);
        return hash;
    }

    /**
     * 增量更新：切换走棋方
     */
    public static long flipTurn(long hash) {
        return hash ^ TURN_KEY;
    }

    private static long pieceKey(Piece piece, int row, int col) {
        int pieceIndex = piece.getType().ordinal();
        int squareIndex = row * Board.COLS + col;
        return PIECE_KEYS[pieceIndex][squareIndex];
    }
}
