package com.xiangqi.ai;

import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.Piece;
import com.xiangqi.model.PieceColor;
import com.xiangqi.model.PieceType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
        List<MoveScore> available = new ArrayList<MoveScore>();
        for (WeightedMove cand : candidates) {
            Move m = findMatching(validMoves, cand.move);
            if (m != null) {
                int score = evaluateOpeningMove(board, m, aiColor, cand.priority);
                available.add(new MoveScore(m, score));
            }
        }
        if (available.isEmpty()) {
            return null;
        }

        int bestScore = Integer.MIN_VALUE;
        for (MoveScore moveScore : available) {
            bestScore = Math.max(bestScore, moveScore.score);
        }
        int threshold = 36;
        List<MoveScore> nearBest = new ArrayList<MoveScore>();
        int weightSum = 0;
        for (MoveScore moveScore : available) {
            if (moveScore.score >= bestScore - threshold) {
                nearBest.add(moveScore);
                weightSum += Math.max(1, moveScore.score - (bestScore - threshold) + 1);
            }
        }
        if (nearBest.isEmpty()) {
            return available.get(0).move;
        }
        int pick = ThreadLocalRandom.current().nextInt(Math.max(1, weightSum));
        int acc = 0;
        for (MoveScore moveScore : nearBest) {
            acc += Math.max(1, moveScore.score - (bestScore - threshold) + 1);
            if (pick < acc) {
                return moveScore.move;
            }
        }
        return nearBest.get(0).move;
    }

    private static int evaluateOpeningMove(Board board, Move move, PieceColor side, int basePriority) {
        int score = basePriority * 10;
        Piece mover = board.getPiece(move.getFromRow(), move.getFromCol());
        Piece captured = board.getPiece(move.getToRow(), move.getToCol());

        if (captured != null) {
            score += pieceValue(captured) * 8;
        }

        int fromCenterDist = Math.abs(move.getFromRow() - 4) + Math.abs(move.getFromCol() - 4);
        int toCenterDist = Math.abs(move.getToRow() - 4) + Math.abs(move.getToCol() - 4);
        score += (fromCenterDist - toCenterDist) * 10;

        if (mover != null) {
            PieceType type = mover.getType();
            if (type == PieceType.MA || type == PieceType.MA_RED) {
                score += 55;
            } else if (type == PieceType.PAO || type == PieceType.PAO_RED) {
                score += 48;
                if (move.getToCol() == 4) {
                    score += 36;
                }
            } else if (type == PieceType.CHE || type == PieceType.CHE_RED) {
                score += 38;
            } else if (type == PieceType.ZU || type == PieceType.ZU_RED) {
                score += 18;
            }
        }

        if (isForward(side, move)) {
            score += 20;
        }

        Board next = new Board(board);
        next.movePiece(move);
        if (next.isInCheck(side.opposite())) {
            score += 32;
        }

        int riskPenalty = landingRiskPenalty(next, move, side);
        score -= riskPenalty;
        return score;
    }

    private static int landingRiskPenalty(Board nextBoard, Move move, PieceColor side) {
        int penalty = 0;
        List<Move> replies = nextBoard.getAllValidMoves(side.opposite());
        Piece landed = nextBoard.getPiece(move.getToRow(), move.getToCol());
        for (Move reply : replies) {
            if (reply.getToRow() == move.getToRow() && reply.getToCol() == move.getToCol()) {
                penalty += 40;
                if (landed != null) {
                    penalty += pieceValue(landed) / 4;
                }
                break;
            }
        }
        return penalty;
    }

    private static boolean isForward(PieceColor side, Move move) {
        if (side == PieceColor.RED) {
            return move.getToRow() < move.getFromRow();
        }
        return move.getToRow() > move.getFromRow();
    }

    private static int pieceValue(Piece piece) {
        if (piece == null) {
            return 0;
        }
        switch (piece.getType()) {
            case JIANG:
            case SHUAI:
                return 10000;
            case CHE:
            case CHE_RED:
                return 900;
            case MA:
            case MA_RED:
                return 430;
            case PAO:
            case PAO_RED:
                return 460;
            case XIANG:
            case XIANG_RED:
            case SHI:
            case SHI_RED:
                return 210;
            case ZU:
            case ZU_RED:
                return 100;
            default:
                return 0;
        }
    }

    private static final class MoveScore {
        private final Move move;
        private final int score;

        private MoveScore(Move move, int score) {
            this.move = move;
            this.score = score;
        }
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

