package com.xiangqi.model.gomoku;

import com.xiangqi.ai.MinimaxAI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class GomokuAI {
    private static final long WIN_SCORE = 1_000_000_000L;
    private final Random random = new Random();

    public int[] findBestMove(GomokuBoard board, GomokuStone aiStone, MinimaxAI.Difficulty difficulty) {
        List<int[]> candidates = collectCandidates(board);
        if (candidates.isEmpty()) {
            return new int[] {GomokuBoard.SIZE / 2, GomokuBoard.SIZE / 2};
        }

        int[] immediateWin = findImmediateWin(board, aiStone, candidates);
        if (immediateWin != null) {
            return immediateWin;
        }

        GomokuStone opp = aiStone.opposite();
        int[] urgentBlock = findImmediateBlock(board, aiStone, opp, candidates);
        if (urgentBlock != null) {
            return urgentBlock;
        }

        if (difficulty != MinimaxAI.Difficulty.EASY) {
            int depth = difficulty == MinimaxAI.Difficulty.HARD ? 3 : 2;
            int width = difficulty == MinimaxAI.Difficulty.HARD ? 12 : 8;
            int[] deep = findBestByAlphaBeta(board, aiStone, depth, width, difficulty);
            if (deep != null) {
                return deep;
            }
        }

        List<ScoredMove> scored = new ArrayList<>(candidates.size());
        for (int[] cand : candidates) {
            long score = scoreMove(board, cand[0], cand[1], aiStone, difficulty);
            scored.add(new ScoredMove(cand[0], cand[1], score));
        }
        scored.sort((a, b) -> Long.compare(b.score, a.score));
        if (scored.isEmpty()) {
            return null;
        }

        if (difficulty == MinimaxAI.Difficulty.EASY) {
            int k = Math.min(5, scored.size());
            ScoredMove pick = scored.get(random.nextInt(k));
            return new int[] {pick.row, pick.col};
        }
        return new int[] {scored.get(0).row, scored.get(0).col};
    }

    private int[] findBestByAlphaBeta(GomokuBoard board, GomokuStone aiStone, int depth, int width, MinimaxAI.Difficulty difficulty) {
        List<int[]> topMoves = topCandidates(board, aiStone, width, difficulty);
        long alpha = Long.MIN_VALUE / 4;
        long beta = Long.MAX_VALUE / 4;
        long best = Long.MIN_VALUE / 4;
        int[] bestMove = null;
        for (int[] move : topMoves) {
            GomokuBoard next = new GomokuBoard(board);
            next.setCurrentTurnForSearch(aiStone);
            GomokuPlaceResult pr = next.place(move[0], move[1], true);
            if (!pr.isSuccess()) {
                continue;
            }
            long score = -negamax(next, aiStone.opposite(), aiStone, depth - 1, -beta, -alpha, width, difficulty);
            if (score > best) {
                best = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, score);
        }
        return bestMove;
    }

    private long negamax(GomokuBoard board, GomokuStone sideToMove, GomokuStone rootStone,
                         int depth, long alpha, long beta, int width, MinimaxAI.Difficulty difficulty) {
        if (board.getWinner() != GomokuStone.EMPTY) {
            return board.getWinner() == rootStone ? WIN_SCORE - (3 - depth) * 1000L : -WIN_SCORE + (3 - depth) * 1000L;
        }
        if (board.isGameOver()) {
            return 0L;
        }
        if (depth <= 0) {
            return staticEvaluate(board, rootStone);
        }

        List<int[]> moves = topCandidates(board, sideToMove, width, difficulty);
        if (moves.isEmpty()) {
            return staticEvaluate(board, rootStone);
        }

        long best = Long.MIN_VALUE / 4;
        for (int[] move : moves) {
            GomokuBoard next = new GomokuBoard(board);
            next.setCurrentTurnForSearch(sideToMove);
            GomokuPlaceResult pr = next.place(move[0], move[1], true);
            if (!pr.isSuccess()) {
                continue;
            }
            long val = -negamax(next, sideToMove.opposite(), rootStone, depth - 1, -beta, -alpha, width, difficulty);
            if (val > best) {
                best = val;
            }
            if (val > alpha) {
                alpha = val;
            }
            if (alpha >= beta) {
                break;
            }
        }
        return best == Long.MIN_VALUE / 4 ? staticEvaluate(board, rootStone) : best;
    }

    private List<int[]> topCandidates(GomokuBoard board, GomokuStone side, int width, MinimaxAI.Difficulty difficulty) {
        List<int[]> all = collectCandidates(board);
        List<ScoredMove> scored = new ArrayList<>(all.size());
        for (int[] m : all) {
            long s = scoreMove(board, m[0], m[1], side, difficulty);
            scored.add(new ScoredMove(m[0], m[1], s));
        }
        scored.sort((a, b) -> Long.compare(b.score, a.score));
        int k = Math.min(Math.max(1, width), scored.size());
        List<int[]> top = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            top.add(new int[] {scored.get(i).row, scored.get(i).col});
        }
        return top;
    }

    private long staticEvaluate(GomokuBoard board, GomokuStone rootStone) {
        GomokuStone opp = rootStone.opposite();
        long me = bestOnePly(board, rootStone);
        long enemy = bestOnePly(board, opp);
        long tension = hasImmediateWin(board, opp) ? 90_000_000L : 0L;
        return me - enemy - tension;
    }

    private int[] findImmediateWin(GomokuBoard board, GomokuStone aiStone, List<int[]> candidates) {
        for (int[] cand : candidates) {
            GomokuBoard copy = new GomokuBoard(board);
            copy.setCurrentTurnForSearch(aiStone);
            GomokuPlaceResult pr = copy.place(cand[0], cand[1], true);
            if (pr.isSuccess() && copy.getWinner() == aiStone) {
                return cand;
            }
        }
        return null;
    }

    private int[] findImmediateBlock(GomokuBoard board, GomokuStone aiStone, GomokuStone opp, List<int[]> candidates) {
        for (int[] cand : candidates) {
            GomokuBoard oppTry = new GomokuBoard(board);
            oppTry.setCurrentTurnForSearch(opp);
            GomokuPlaceResult oppRes = oppTry.place(cand[0], cand[1], true);
            if (!(oppRes.isSuccess() && oppTry.getWinner() == opp)) {
                continue;
            }
            GomokuBoard aiTry = new GomokuBoard(board);
            aiTry.setCurrentTurnForSearch(aiStone);
            GomokuPlaceResult aiRes = aiTry.place(cand[0], cand[1], true);
            if (aiRes.isSuccess()) {
                return cand;
            }
        }
        return null;
    }

    private long scoreMove(GomokuBoard board, int row, int col, GomokuStone aiStone, MinimaxAI.Difficulty difficulty) {
        GomokuStone opp = aiStone.opposite();
        GomokuBoard me = new GomokuBoard(board);
        me.setCurrentTurnForSearch(aiStone);
        GomokuPlaceResult meRes = me.place(row, col, true);
        if (!meRes.isSuccess()) {
            return Long.MIN_VALUE / 2;
        }
        if (me.getWinner() == aiStone) {
            return 1_000_000_000L;
        }

        long own = localPatternScore(me, row, col, aiStone);
        long den = localPatternScore(me, row, col, opp);
        long risk = hasImmediateWin(me, opp) ? 80_000_000L : 0L;
        long score = own * 5 + den * 3 - risk;

        if (difficulty == MinimaxAI.Difficulty.HARD) {
            long oppBest = bestOnePly(me, opp);
            score -= oppBest / 2;
        }
        return score;
    }

    private long bestOnePly(GomokuBoard board, GomokuStone side) {
        List<int[]> candidates = collectCandidates(board);
        long best = Long.MIN_VALUE / 4;
        for (int[] c : candidates) {
            GomokuBoard copy = new GomokuBoard(board);
            copy.setCurrentTurnForSearch(side);
            GomokuPlaceResult pr = copy.place(c[0], c[1], true);
            if (!pr.isSuccess()) {
                continue;
            }
            if (copy.getWinner() == side) {
                return 900_000_000L;
            }
            long s = localPatternScore(copy, c[0], c[1], side);
            if (s > best) {
                best = s;
            }
        }
        return Math.max(0L, best);
    }

    private boolean hasImmediateWin(GomokuBoard board, GomokuStone side) {
        List<int[]> candidates = collectCandidates(board);
        for (int[] c : candidates) {
            GomokuBoard copy = new GomokuBoard(board);
            copy.setCurrentTurnForSearch(side);
            GomokuPlaceResult pr = copy.place(c[0], c[1], true);
            if (pr.isSuccess() && copy.getWinner() == side) {
                return true;
            }
        }
        return false;
    }

    private long localPatternScore(GomokuBoard board, int row, int col, GomokuStone color) {
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        long total = 0L;
        for (int[] d : dirs) {
            int lenA = countDir(board, row, col, d[0], d[1], color);
            int lenB = countDir(board, row, col, -d[0], -d[1], color);
            int len = 1 + lenA + lenB;
            int open = 0;
            if (isOpen(board, row + (lenA + 1) * d[0], col + (lenA + 1) * d[1])) {
                open++;
            }
            if (isOpen(board, row - (lenB + 1) * d[0], col - (lenB + 1) * d[1])) {
                open++;
            }
            total += patternWeight(len, open);
        }
        return total;
    }

    private int countDir(GomokuBoard board, int row, int col, int dr, int dc, GomokuStone color) {
        int n = 0;
        int r = row + dr;
        int c = col + dc;
        while (board.isInside(r, c) && board.getStone(r, c) == color) {
            n++;
            r += dr;
            c += dc;
        }
        return n;
    }

    private boolean isOpen(GomokuBoard board, int row, int col) {
        return board.isInside(row, col) && board.getStone(row, col) == GomokuStone.EMPTY;
    }

    private long patternWeight(int len, int openEnds) {
        if (len >= 5) {
            return 100_000_000L;
        }
        if (len == 4) {
            return openEnds == 2 ? 5_000_000L : 700_000L;
        }
        if (len == 3) {
            return openEnds == 2 ? 120_000L : 16_000L;
        }
        if (len == 2) {
            return openEnds == 2 ? 3_000L : 500L;
        }
        return openEnds == 2 ? 60L : 10L;
    }

    private List<int[]> collectCandidates(GomokuBoard board) {
        Set<Integer> set = new HashSet<>();
        boolean hasStone = false;
        for (int r = 0; r < GomokuBoard.SIZE; r++) {
            for (int c = 0; c < GomokuBoard.SIZE; c++) {
                if (board.getStone(r, c) == GomokuStone.EMPTY) {
                    continue;
                }
                hasStone = true;
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        int nr = r + dr;
                        int nc = c + dc;
                        if (!board.isInside(nr, nc) || board.getStone(nr, nc) != GomokuStone.EMPTY) {
                            continue;
                        }
                        set.add(nr * GomokuBoard.SIZE + nc);
                    }
                }
            }
        }

        List<int[]> out = new ArrayList<>();
        if (!hasStone) {
            out.add(new int[] {GomokuBoard.SIZE / 2, GomokuBoard.SIZE / 2});
            return out;
        }
        for (Integer v : set) {
            out.add(new int[] {v / GomokuBoard.SIZE, v % GomokuBoard.SIZE});
        }
        return out;
    }

    private static final class ScoredMove {
        private final int row;
        private final int col;
        private final long score;

        private ScoredMove(int row, int col, long score) {
            this.row = row;
            this.col = col;
            this.score = score;
        }
    }
}
