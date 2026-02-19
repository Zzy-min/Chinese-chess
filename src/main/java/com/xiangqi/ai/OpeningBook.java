package com.xiangqi.ai;

import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.PieceColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 开局库（轻量）
 * 参考常见开局思路：中炮、屏风马、顺炮、挺兵/飞相等。
 */
public final class OpeningBook {
    private static final Random RANDOM = new Random();

    private OpeningBook() {
    }

    public static Move findOpeningMove(Board board, PieceColor aiColor, List<Move> validMoves) {
        if (board == null || aiColor == null || validMoves == null || validMoves.isEmpty()) {
            return null;
        }
        int ply = board.getMoveCount();
        if (ply >= 10) {
            return null;
        }

        List<Move> candidates = getCandidates(ply, aiColor);
        List<Move> matched = new ArrayList<>();
        for (Move cand : candidates) {
            Move m = findMatching(validMoves, cand);
            if (m != null) {
                matched.add(m);
            }
        }

        if (matched.isEmpty()) {
            return null;
        }

        // 给开局库少量变化，避免每局固定完全一致。
        int top = Math.min(2, matched.size());
        return matched.get(RANDOM.nextInt(top));
    }

    private static List<Move> getCandidates(int ply, PieceColor color) {
        if (color == PieceColor.RED) {
            switch (ply) {
                case 0:
                    return Arrays.asList(
                        m(7, 1, 7, 4), // 炮二平五
                        m(9, 1, 7, 2), // 马二进三
                        m(9, 7, 7, 6), // 马八进七
                        m(6, 2, 5, 2)  // 兵七进一
                    );
                case 2:
                    return Arrays.asList(
                        m(9, 1, 7, 2),
                        m(9, 7, 7, 6),
                        m(6, 2, 5, 2),
                        m(7, 7, 7, 4)
                    );
                case 4:
                case 6:
                case 8:
                    return Arrays.asList(
                        m(9, 7, 7, 6),
                        m(9, 1, 7, 2),
                        m(7, 7, 7, 4),
                        m(6, 6, 5, 6),
                        m(9, 2, 7, 4)
                    );
                default:
                    return new ArrayList<Move>();
            }
        }

        switch (ply) {
            case 1:
                return Arrays.asList(
                    m(0, 7, 2, 6), // 马8进7
                    m(2, 7, 2, 4), // 炮8平5
                    m(0, 1, 2, 2), // 马2进3
                    m(3, 2, 4, 2)  // 卒7进1
                );
            case 3:
                return Arrays.asList(
                    m(0, 1, 2, 2),
                    m(0, 7, 2, 6),
                    m(2, 1, 2, 4),
                    m(3, 6, 4, 6)
                );
            case 5:
            case 7:
            case 9:
                return Arrays.asList(
                    m(2, 1, 2, 4),
                    m(3, 4, 4, 4),
                    m(0, 2, 2, 4),
                    m(3, 2, 4, 2)
                );
            default:
                return new ArrayList<Move>();
        }
    }

    private static Move findMatching(List<Move> validMoves, Move target) {
        for (Move m : validMoves) {
            if (m.getFromRow() == target.getFromRow()
                && m.getFromCol() == target.getFromCol()
                && m.getToRow() == target.getToRow()
                && m.getToCol() == target.getToCol()) {
                return m;
            }
        }
        return null;
    }

    private static Move m(int fr, int fc, int tr, int tc) {
        return new Move(fr, fc, tr, tc);
    }
}

