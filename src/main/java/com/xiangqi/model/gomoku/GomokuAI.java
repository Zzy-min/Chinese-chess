package com.xiangqi.model.gomoku;

import com.xiangqi.ai.MinimaxAI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class GomokuAI {
    private static final long WIN_SCORE = 1_000_000_000L;
    private static final long LOSING_THREAT_PENALTY = 850_000_000L;
    private static final long FORK_THREAT_BONUS = 240_000_000L;
    private static final long SINGLE_THREAT_BONUS = 80_000_000L;
    private static final int ORDERING_PREFILTER_FACTOR = 4;
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

        candidates = limitCandidatesByDifficulty(board, aiStone, candidates, difficulty);

        SearchPlan plan = buildSearchPlan(board, difficulty, candidates.size());
        if (plan.depth > 1) {
            long deadlineNanos = System.nanoTime() + plan.timeBudgetMs * 1_000_000L;
            int[] deep = findBestByAlphaBeta(board, aiStone, plan.depth, plan.width, difficulty, deadlineNanos);
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

    private int[] findBestByAlphaBeta(GomokuBoard board, GomokuStone aiStone, int depth, int width,
                                      MinimaxAI.Difficulty difficulty, long deadlineNanos) {
        List<int[]> topMoves = topCandidates(board, aiStone, width, difficulty);
        long alpha = Long.MIN_VALUE / 4;
        long beta = Long.MAX_VALUE / 4;
        long best = Long.MIN_VALUE / 4;
        int[] bestMove = null;
        for (int[] move : topMoves) {
            if (System.nanoTime() >= deadlineNanos && bestMove != null) {
                break;
            }
            GomokuBoard next = new GomokuBoard(board);
            next.setCurrentTurnForSearch(aiStone);
            GomokuPlaceResult pr = next.place(move[0], move[1], true);
            if (!pr.isSuccess()) {
                continue;
            }
            long score = -negamax(next, aiStone.opposite(), aiStone, depth - 1,
                -beta, -alpha, width, difficulty, deadlineNanos);
            if (score > best) {
                best = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, score);
        }
        return bestMove;
    }

    private long negamax(GomokuBoard board, GomokuStone sideToMove, GomokuStone rootStone,
                         int depth, long alpha, long beta, int width,
                         MinimaxAI.Difficulty difficulty, long deadlineNanos) {
        if (System.nanoTime() >= deadlineNanos) {
            return staticEvaluate(board, rootStone);
        }
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
            if (System.nanoTime() >= deadlineNanos && best != Long.MIN_VALUE / 4) {
                break;
            }
            GomokuBoard next = new GomokuBoard(board);
            next.setCurrentTurnForSearch(sideToMove);
            GomokuPlaceResult pr = next.place(move[0], move[1], true);
            if (!pr.isSuccess()) {
                continue;
            }
            long val = -negamax(next, sideToMove.opposite(), rootStone, depth - 1,
                -beta, -alpha, width, difficulty, deadlineNanos);
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
        List<int[]> tactical = collectTacticalMoves(board, side, all);
        if (!tactical.isEmpty()) {
            return rankTopMoves(board, side, tactical, width, difficulty, true);
        }
        return rankTopMoves(board, side, all, width, difficulty, false);
    }

    private List<int[]> rankTopMoves(GomokuBoard board, GomokuStone side, List<int[]> moves, int width,
                                     MinimaxAI.Difficulty difficulty, boolean preciseScore) {
        if (moves.isEmpty()) {
            return moves;
        }
        List<int[]> source = maybePrefilterMoves(board, moves, width, side, preciseScore);
        List<ScoredMove> scored = new ArrayList<>(source.size());
        for (int[] m : source) {
            long s = preciseScore
                ? scoreMove(board, m[0], m[1], side, difficulty)
                : scoreMoveForOrdering(board, m[0], m[1], side);
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

    private List<int[]> maybePrefilterMoves(GomokuBoard board, List<int[]> moves, int width,
                                            GomokuStone side, boolean preciseScore) {
        if (preciseScore || moves.size() <= width + 8) {
            return moves;
        }
        int preLimit = Math.min(moves.size(), Math.max(width * ORDERING_PREFILTER_FACTOR, width + 10));
        if (preLimit >= moves.size()) {
            return moves;
        }
        List<ScoredMove> rough = new ArrayList<>(moves.size());
        for (int[] move : moves) {
            long s = quickOrderingScore(board, move[0], move[1], side);
            rough.add(new ScoredMove(move[0], move[1], s));
        }
        rough.sort((a, b) -> Long.compare(b.score, a.score));
        List<int[]> out = new ArrayList<>(preLimit);
        for (int i = 0; i < preLimit; i++) {
            ScoredMove pick = rough.get(i);
            out.add(new int[] {pick.row, pick.col});
        }
        return out;
    }

    private long quickOrderingScore(GomokuBoard board, int row, int col, GomokuStone side) {
        GomokuStone opp = side.opposite();
        int ownAdjacent = countNeighbors(board, row, col, side, 1);
        int oppAdjacent = countNeighbors(board, row, col, opp, 1);
        int nearbyAny = countNeighbors(board, row, col, null, 2);
        return centerControlBonus(row, col) + ownAdjacent * 220L + oppAdjacent * 260L + nearbyAny * 35L;
    }

    private int countNeighbors(GomokuBoard board, int row, int col, GomokuStone sideOrNull, int radius) {
        int count = 0;
        for (int dr = -radius; dr <= radius; dr++) {
            for (int dc = -radius; dc <= radius; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int nr = row + dr;
                int nc = col + dc;
                if (!board.isInside(nr, nc)) {
                    continue;
                }
                GomokuStone s = board.getStone(nr, nc);
                if (s == GomokuStone.EMPTY) {
                    continue;
                }
                if (sideOrNull == null || s == sideOrNull) {
                    count++;
                }
            }
        }
        return count;
    }

    private List<int[]> collectTacticalMoves(GomokuBoard board, GomokuStone side, List<int[]> candidates) {
        List<int[]> wins = collectImmediateWinningMoves(board, side, candidates);
        if (!wins.isEmpty()) {
            return wins;
        }
        GomokuStone opp = side.opposite();
        List<int[]> oppWins = collectImmediateWinningMoves(board, opp, candidates);
        if (oppWins.isEmpty()) {
            return new ArrayList<>();
        }

        List<int[]> forcedBlocks = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int[] w : oppWins) {
            int key = w[0] * GomokuBoard.SIZE + w[1];
            if (!seen.add(key)) {
                continue;
            }
            GomokuBoard copy = new GomokuBoard(board);
            copy.setCurrentTurnForSearch(side);
            GomokuPlaceResult pr = copy.place(w[0], w[1], true);
            if (pr.isSuccess()) {
                forcedBlocks.add(new int[] {w[0], w[1]});
            }
        }
        if (forcedBlocks.size() <= 1) {
            return forcedBlocks;
        }

        List<int[]> safeBlocks = new ArrayList<>();
        for (int[] block : forcedBlocks) {
            GomokuBoard after = new GomokuBoard(board);
            after.setCurrentTurnForSearch(side);
            GomokuPlaceResult pr = after.place(block[0], block[1], true);
            if (!pr.isSuccess()) {
                continue;
            }
            if (!hasImmediateWin(after, opp)) {
                safeBlocks.add(block);
            }
        }
        return safeBlocks.isEmpty() ? forcedBlocks : safeBlocks;
    }

    private long staticEvaluate(GomokuBoard board, GomokuStone rootStone) {
        GomokuStone opp = rootStone.opposite();
        List<int[]> candidates = collectCandidates(board);
        long me = bestOnePly(board, rootStone, candidates);
        long enemy = bestOnePly(board, opp, candidates);
        int oppImmediateWins = countImmediateWins(board, opp, candidates, 3);
        long tension = oppImmediateWins > 0 ? (280_000_000L + (oppImmediateWins - 1L) * 60_000_000L) : 0L;
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
        List<int[]> blocks = collectTacticalMoves(board, aiStone, candidates);
        if (blocks.isEmpty()) {
            return null;
        }
        if (blocks.size() == 1) {
            return blocks.get(0);
        }
        long bestScore = Long.MIN_VALUE / 4;
        int[] best = null;
        for (int[] move : blocks) {
            long s = scoreMove(board, move[0], move[1], aiStone, MinimaxAI.Difficulty.HARD);
            if (s > bestScore) {
                bestScore = s;
                best = move;
            }
        }
        return best;
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

        List<int[]> candidates = collectCandidates(me);
        long own = localPatternScore(me, row, col, aiStone);
        long den = localPatternScore(me, row, col, opp);
        int myWinCount = countImmediateWins(me, aiStone, candidates, 2);
        int oppWinCount = countImmediateWins(me, opp, candidates, 3);
        long risk = oppWinCount > 0 ? (LOSING_THREAT_PENALTY + (oppWinCount - 1L) * 55_000_000L) : 0L;
        long score = own * 5 + den * 3 - risk;
        score += centerControlBonus(row, col);
        if (myWinCount >= 2) {
            score += FORK_THREAT_BONUS;
        } else if (myWinCount == 1) {
            score += SINGLE_THREAT_BONUS;
        }

        if (difficulty == MinimaxAI.Difficulty.HARD) {
            long oppBest = bestOnePly(me, opp, candidates);
            score -= oppBest / 2;
        }
        return score;
    }

    private long scoreMoveForOrdering(GomokuBoard board, int row, int col, GomokuStone side) {
        GomokuStone opp = side.opposite();
        GomokuBoard me = new GomokuBoard(board);
        me.setCurrentTurnForSearch(side);
        GomokuPlaceResult meRes = me.place(row, col, true);
        if (!meRes.isSuccess()) {
            return Long.MIN_VALUE / 2;
        }
        if (me.getWinner() == side) {
            return WIN_SCORE;
        }
        List<int[]> candidates = collectCandidates(me);
        int oppWinCount = countImmediateWins(me, opp, candidates, 1);
        int myWinCount = countImmediateWins(me, side, candidates, 1);
        long own = localPatternScore(me, row, col, side);
        long den = localPatternScore(me, row, col, opp);
        long score = own * 4 + den * 2 + centerControlBonus(row, col);
        if (myWinCount > 0) {
            score += 35_000_000L;
        }
        if (oppWinCount > 0) {
            score -= 220_000_000L;
        }
        return score;
    }

    private long bestOnePly(GomokuBoard board, GomokuStone side, List<int[]> candidates) {
        long best = Long.MIN_VALUE / 4;
        List<int[]> use = (candidates == null || candidates.isEmpty()) ? collectCandidates(board) : candidates;
        for (int[] c : use) {
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
        return countImmediateWins(board, side) > 0;
    }

    private int countImmediateWins(GomokuBoard board, GomokuStone side) {
        return countImmediateWins(board, side, collectCandidates(board), Integer.MAX_VALUE);
    }

    private int countImmediateWins(GomokuBoard board, GomokuStone side, List<int[]> candidates, int limit) {
        return collectImmediateWinningMoves(board, side, candidates, limit).size();
    }

    private List<int[]> collectImmediateWinningMoves(GomokuBoard board, GomokuStone side, List<int[]> candidates) {
        return collectImmediateWinningMoves(board, side, candidates, Integer.MAX_VALUE);
    }

    private List<int[]> collectImmediateWinningMoves(GomokuBoard board, GomokuStone side, List<int[]> candidates, int limit) {
        List<int[]> wins = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int[] c : candidates) {
            int key = c[0] * GomokuBoard.SIZE + c[1];
            if (!seen.add(key)) {
                continue;
            }
            GomokuBoard copy = new GomokuBoard(board);
            copy.setCurrentTurnForSearch(side);
            GomokuPlaceResult pr = copy.place(c[0], c[1], true);
            if (pr.isSuccess() && copy.getWinner() == side) {
                wins.add(new int[] {c[0], c[1]});
                if (wins.size() >= limit) {
                    break;
                }
            }
        }
        return wins;
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

    private List<int[]> limitCandidatesByDifficulty(GomokuBoard board, GomokuStone side, List<int[]> candidates,
                                                    MinimaxAI.Difficulty difficulty) {
        int moveCount = board.getMoveCount();
        int cap;
        if (difficulty == MinimaxAI.Difficulty.HARD) {
            cap = moveCount <= 8 ? 24 : (moveCount <= 20 ? 34 : 40);
        } else if (difficulty == MinimaxAI.Difficulty.MEDIUM) {
            cap = moveCount <= 8 ? 18 : (moveCount <= 20 ? 26 : 32);
        } else {
            cap = moveCount <= 8 ? 12 : (moveCount <= 20 ? 16 : 20);
        }
        if (candidates.size() <= cap) {
            return candidates;
        }
        List<int[]> tactical = collectTacticalMoves(board, side, candidates);
        if (!tactical.isEmpty() && tactical.size() <= cap) {
            return tactical;
        }
        List<int[]> source = tactical.isEmpty() ? candidates : tactical;
        return rankTopMoves(board, side, source, cap, difficulty, false);
    }

    private SearchPlan buildSearchPlan(GomokuBoard board, MinimaxAI.Difficulty difficulty, int candidateCount) {
        if (difficulty == MinimaxAI.Difficulty.EASY) {
            return new SearchPlan(0, 0, 0);
        }
        int moveCount = board.getMoveCount();
        int depth;
        int width;
        long budgetMs;
        if (difficulty == MinimaxAI.Difficulty.HARD) {
            depth = 4;
            width = 16;
            budgetMs = 260L;
            if (moveCount <= 8) {
                depth = 3;
                width = 10;
                budgetMs = 120L;
            } else if (moveCount <= 20) {
                width = 13;
                budgetMs = 190L;
            } else if (moveCount >= 34) {
                depth = 5;
                width = 12;
                budgetMs = 290L;
            }
        } else {
            depth = 3;
            width = 10;
            budgetMs = 140L;
            if (moveCount <= 10) {
                depth = 2;
                width = 8;
                budgetMs = 85L;
            } else if (moveCount >= 34) {
                width = 9;
                budgetMs = 160L;
            }
        }
        if (candidateCount > 34) {
            width = Math.max(7, width - 2);
        }
        return new SearchPlan(depth, width, budgetMs);
    }

    private long centerControlBonus(int row, int col) {
        int center = GomokuBoard.SIZE / 2;
        int dist = Math.abs(row - center) + Math.abs(col - center);
        return Math.max(0L, 48L - dist * 5L);
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

    private static final class SearchPlan {
        private final int depth;
        private final int width;
        private final long timeBudgetMs;

        private SearchPlan(int depth, int width, long timeBudgetMs) {
            this.depth = depth;
            this.width = width;
            this.timeBudgetMs = timeBudgetMs;
        }
    }
}
