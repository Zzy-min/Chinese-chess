package com.xiangqi.ai;

import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.PieceColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 开局库（优先走法）
 * 参考常见开局谱系：中炮、屏风马、顺炮、飞相、仙人指路等。
 * 目标：优先主线，减少开局阶段无谓长考。
 */
public final class OpeningBook {
    private OpeningBook() {
    }

    private static final class WeightedMove {
        private final Move move;
        private final int priority;

        private WeightedMove(Move move, int priority) {
            this.move = move;
            this.priority = priority;
        }
    }

    public static Move findOpeningMove(Board board, PieceColor aiColor, List<Move> validMoves) {
        if (board == null || aiColor == null || validMoves == null || validMoves.isEmpty()) {
            return null;
        }
        int ply = board.getMoveCount();
        if (ply >= 10) {
            return null;
        }

        List<WeightedMove> candidates = getCandidates(ply, aiColor);
        Move best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (WeightedMove cand : candidates) {
            Move m = findMatching(validMoves, cand.move);
            if (m != null) {
                if (cand.priority > bestPriority) {
                    best = m;
                    bestPriority = cand.priority;
                }
            }
        }
        return best;
    }

    private static List<WeightedMove> getCandidates(int ply, PieceColor color) {
        if (color == PieceColor.RED) {
            switch (ply) {
                case 0:
                    return Arrays.asList(
                        wm(7, 1, 7, 4, 100), // 炮二平五（中炮主线）
                        wm(9, 1, 7, 2, 82),  // 马二进三（屏风马体系）
                        wm(9, 7, 7, 6, 81),  // 马八进七（柔性开局）
                        wm(6, 2, 5, 2, 78),  // 兵七进一（仙人指路）
                        wm(9, 6, 7, 4, 75)   // 相七进五（飞相局）
                    );
                case 2:
                    return Arrays.asList(
                        wm(9, 1, 7, 2, 98),  // 中炮配马二进三
                        wm(9, 7, 7, 6, 96),  // 中炮配马八进七
                        wm(6, 2, 5, 2, 90),  // 挺七兵稳固中腹
                        wm(7, 7, 7, 4, 86),  // 炮八平五（双炮）
                        wm(9, 2, 7, 4, 80)   // 车九进一预备出车
                    );
                case 4:
                case 6:
                case 8:
                    return Arrays.asList(
                        wm(9, 7, 7, 6, 95),
                        wm(9, 1, 7, 2, 94),
                        wm(7, 7, 7, 4, 90),
                        wm(6, 6, 5, 6, 88),  // 挺三兵争先
                        wm(9, 2, 7, 4, 86),  // 车九进一
                        wm(7, 4, 4, 4, 84)   // 炮五进四（中路压制）
                    );
                default:
                    return new ArrayList<WeightedMove>();
            }
        }

        switch (ply) {
            case 1:
                return Arrays.asList(
                    wm(0, 1, 2, 2, 100), // 马2进3（屏风马主应）
                    wm(0, 7, 2, 6, 99),  // 马8进7（对称屏风）
                    wm(2, 1, 2, 4, 94),  // 炮2平5（顺炮）
                    wm(2, 7, 2, 4, 92),  // 炮8平5（列炮）
                    wm(3, 2, 4, 2, 88),  // 卒7进1（挺卒争先）
                    wm(3, 4, 4, 4, 86),  // 卒5进1（抢中）
                    wm(0, 6, 2, 4, 83)   // 象7进5（稳健过渡）
                );
            case 3:
                return Arrays.asList(
                    wm(0, 7, 2, 6, 98),
                    wm(0, 1, 2, 2, 97),
                    wm(2, 1, 2, 4, 92),
                    wm(3, 6, 4, 6, 88),  // 卒3进1
                    wm(0, 6, 2, 4, 86),  // 象7进5（稳健）
                    wm(2, 7, 2, 4, 84),  // 炮8平5
                    wm(3, 4, 4, 4, 82)
                );
            case 5:
            case 7:
            case 9:
                return Arrays.asList(
                    wm(2, 1, 2, 4, 95),
                    wm(3, 4, 4, 4, 91),  // 卒5进1争中
                    wm(0, 2, 2, 4, 88),  // 车1进1预备横车
                    wm(3, 2, 4, 2, 86),
                    wm(3, 6, 4, 6, 85),
                    wm(0, 8, 1, 8, 83),  // 车9进1出动
                    wm(0, 0, 1, 0, 82),  // 车1进1出动
                    wm(0, 5, 1, 4, 80)   // 士6进5稳固中路
                );
            default:
                return new ArrayList<WeightedMove>();
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

    private static WeightedMove wm(int fr, int fc, int tr, int tc, int priority) {
        return new WeightedMove(m(fr, fc, tr, tc), priority);
    }
}

