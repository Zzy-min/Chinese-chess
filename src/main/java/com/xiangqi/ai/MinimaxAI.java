package com.xiangqi.ai;

import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.Piece;
import com.xiangqi.model.PieceColor;
import com.xiangqi.model.PieceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 中国象棋AI - 迭代加深 + Alpha-Beta + 置换表 + 启发式排序
 */
public class MinimaxAI {
    private static final Random RANDOM = new Random();
    private static final int MATE_SCORE = 200000;

    private static final int TT_EXACT = 0;
    private static final int TT_LOWER = 1;
    private static final int TT_UPPER = 2;

    private static final int MAX_PLY = 64;
    private static final int ASPIRATION_WINDOW = 80;
    private static final int TIME_CHECK_MASK = 1023;
    private static final int TT_MAX_ENTRIES = 220000;
    private static final int ROOT_PARALLEL_MIN_DEPTH = 4;
    private static final int ROOT_PARALLEL_MIN_MOVES = 3;
    private static final int ROOT_PARALLEL_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    private static final int RESULT_CACHE_MAX_ENTRIES = 50000;
    private static final long RESULT_CACHE_TTL_MS = 3 * 60 * 1000L;
    private static final long ZOBRIST_TURN_KEY = 0x9E3779B97F4A7C15L;
    private static final long[][][] ZOBRIST = initZobrist();
    private static final ExecutorService ROOT_EXECUTOR = Executors.newFixedThreadPool(ROOT_PARALLEL_THREADS, new ThreadFactory() {
        private int idx = 0;
        @Override
        public synchronized Thread newThread(Runnable r) {
            Thread t = new Thread(r, "xq-root-" + (++idx));
            t.setDaemon(true);
            return t;
        }
    });
    private static final ConcurrentHashMap<Long, CachedBestMove> RESULT_CACHE = new ConcurrentHashMap<Long, CachedBestMove>();

    public enum Difficulty {
        EASY("简单", 2, 1200, 0.35),
        MEDIUM("中等", 4, 6000, 0.12),
        HARD("困难", 6, 15000, 0.0);

        private final String displayName;
        private final int maxDepth;
        private final int timeLimitMs;
        private final double randomPickChance;

        Difficulty(String displayName, int maxDepth, int timeLimitMs, double randomPickChance) {
            this.displayName = displayName;
            this.maxDepth = maxDepth;
            this.timeLimitMs = timeLimitMs;
            this.randomPickChance = randomPickChance;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public int getTimeLimitMs() {
            return timeLimitMs;
        }

        public double getRandomPickChance() {
            return randomPickChance;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final class TTEntry {
        private final int depth;
        private final int score;
        private final int flag;
        private final Move bestMove;

        private TTEntry(int depth, int score, int flag, Move bestMove) {
            this.depth = depth;
            this.score = score;
            this.flag = flag;
            this.bestMove = bestMove;
        }
    }

    private static final class EndgameCurve {
        private final int depthDelta;
        private final int timeDeltaMs;
        private final boolean forceDeterministic;

        private EndgameCurve(int depthDelta, int timeDeltaMs, boolean forceDeterministic) {
            this.depthDelta = depthDelta;
            this.timeDeltaMs = timeDeltaMs;
            this.forceDeterministic = forceDeterministic;
        }
    }

    private static final class SearchBudget {
        private final int maxDepth;
        private final int timeLimitMs;

        private SearchBudget(int maxDepth, int timeLimitMs) {
            this.maxDepth = maxDepth;
            this.timeLimitMs = timeLimitMs;
        }
    }

    private static final class CachedBestMove {
        private final Move move;
        private final long expiresAt;

        private CachedBestMove(Move move, long expiresAt) {
            this.move = move;
            this.expiresAt = expiresAt;
        }
    }

    private Difficulty difficulty = Difficulty.MEDIUM;

    private long searchStartTime;
    private int searchTimeLimitMs;
    private long searchDeadlineMs;
    private boolean timeUp;
    private int timeCheckCounter;

    private final Map<Long, TTEntry> transpositionTable = new HashMap<Long, TTEntry>(1 << 15);
    private final int[][][] historyHeuristic = new int[2][90][90];
    private final Move[][] killerMoves = new Move[MAX_PLY][2];

    public void setDifficulty(Difficulty difficulty) {
        if (difficulty != null) {
            this.difficulty = difficulty;
        }
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public Move findBestMove(Board board, PieceColor aiColor) {
        List<Move> validMoves = board.getAllValidMoves(aiColor);
        if (validMoves.isEmpty()) {
            return null;
        }

        sortMovesByCaptureValue(validMoves, board);

        Move openingMove = OpeningBook.findOpeningMove(board, aiColor, validMoves);
        if (openingMove != null) {
            return openingMove;
        }

        EndgameStudySet.Tier studyTier = EndgameStudySet.getTier(board);
        boolean inStudySet = studyTier != null;
        boolean inLearnedSet = XqipuLearnedSet.contains(board);
        boolean inEventSet = EventLearnedSet.contains(board);
        EndgameCurve endgameCurve = inStudySet ? curveFor(studyTier, difficulty) : new EndgameCurve(0, 0, false);

        if (!endgameCurve.forceDeterministic && !inStudySet && !inLearnedSet && !inEventSet
            && difficulty.getRandomPickChance() > 0 && RANDOM.nextDouble() < difficulty.getRandomPickChance()) {
            int topN = Math.max(1, Math.min(4, validMoves.size()));
            return validMoves.get(RANDOM.nextInt(topN));
        }

        int maxDepth = difficulty.getMaxDepth();
        int extraTime = 0;
        if (inStudySet) {
            maxDepth = Math.min(maxDepth + studyTier.getDepthBonus() + endgameCurve.depthDelta, 10);
            extraTime += studyTier.getTimeBonusMs() + endgameCurve.timeDeltaMs;
        }
        if (inLearnedSet) {
            if (difficulty == Difficulty.HARD) {
                maxDepth = Math.min(maxDepth + 1, 9);
                extraTime += 1500;
            } else if (difficulty == Difficulty.MEDIUM) {
                extraTime += 1000;
            } else {
                extraTime += 600;
            }
        }
        if (inEventSet) {
            // 赛事局面更偏实战，给中高难度更多预算，提高中后盘质量。
            if (difficulty == Difficulty.HARD) {
                maxDepth = Math.min(maxDepth + 1, 10);
                extraTime += 1800;
            } else if (difficulty == Difficulty.MEDIUM) {
                maxDepth = Math.min(maxDepth + 1, 9);
                extraTime += 1200;
            } else {
                extraTime += 700;
            }
        }

        SearchBudget budget = tuneBudget(board, validMoves, maxDepth, difficulty.getTimeLimitMs() + extraTime, inStudySet, inLearnedSet, inEventSet);
        maxDepth = budget.maxDepth;
        searchStartTime = System.currentTimeMillis();
        searchTimeLimitMs = Math.max(450, budget.timeLimitMs);
        searchDeadlineMs = searchStartTime + searchTimeLimitMs;
        timeUp = false;
        timeCheckCounter = 0;
        transpositionTable.clear();

        long cacheKey = buildResultCacheKey(board, aiColor, difficulty);
        Move cached = loadCachedBestMove(cacheKey, validMoves);
        if (cached != null) {
            return cached;
        }

        Move bestMove = validMoves.get(0);
        Move pvMove = null;
        int prevScore = 0;

        for (int depth = 1; depth <= maxDepth && !timeUp; depth++) {
            SearchResult result;
            if (depth >= 3) {
                int alpha = prevScore - ASPIRATION_WINDOW;
                int beta = prevScore + ASPIRATION_WINDOW;
                result = searchRoot(board, aiColor, validMoves, depth, pvMove, alpha, beta);
                if (!timeUp && result.bestMove != null && (result.score <= alpha || result.score >= beta)) {
                    // aspiration 失败时回退到全窗口
                    result = searchRoot(board, aiColor, validMoves, depth, pvMove, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
                }
            } else {
                result = searchRoot(board, aiColor, validMoves, depth, pvMove, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
            }
            if (!timeUp && result.bestMove != null) {
                bestMove = result.bestMove;
                pvMove = result.bestMove;
                prevScore = result.score;
            }
        }

        cacheBestMove(cacheKey, bestMove);
        return bestMove;
    }

    private SearchResult searchRoot(Board board, PieceColor aiColor, List<Move> rootMoves, int depth, Move pvMove, int alpha, int beta) {
        List<Move> ordered = new ArrayList<Move>(rootMoves);
        orderMoves(ordered, board, pvMove, 0);

        if (depth >= ROOT_PARALLEL_MIN_DEPTH && ordered.size() >= ROOT_PARALLEL_MIN_MOVES && ROOT_PARALLEL_THREADS > 1) {
            SearchResult parallel = searchRootParallel(board, aiColor, ordered, depth);
            if (parallel.bestMove != null || timeUp) {
                return parallel;
            }
        }

        int localAlpha = alpha;
        int bestScore = Integer.MIN_VALUE;
        Move bestMove = null;

        for (int i = 0; i < ordered.size(); i++) {
            Move move = ordered.get(i);
            if (isTimeUp()) {
                break;
            }
            Board testBoard = new Board(board);
            testBoard.movePiece(move);

            int score;
            if (i == 0) {
                score = -negamax(testBoard, depth - 1, -beta, -localAlpha, 1, aiColor);
            } else {
                // PVS: 先进行零窗口试探，提升速度
                score = -negamax(testBoard, depth - 1, -localAlpha - 1, -localAlpha, 1, aiColor);
                if (!timeUp && score > localAlpha && score < beta) {
                    score = -negamax(testBoard, depth - 1, -beta, -localAlpha, 1, aiColor);
                }
            }
            if (timeUp) {
                break;
            }
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            if (score > localAlpha) {
                localAlpha = score;
            }
            if (localAlpha >= beta) {
                break;
            }
        }

        return new SearchResult(bestMove, bestScore);
    }

    private SearchResult searchRootParallel(Board board, PieceColor aiColor, List<Move> ordered, int depth) {
        List<Future<SearchResult>> futures = new ArrayList<Future<SearchResult>>(ordered.size());
        for (Move move : ordered) {
            if (isTimeUp()) {
                break;
            }
            final Move rootMove = copyMove(move);
            final Board rootBoard = new Board(board);
            rootBoard.movePiece(rootMove);
            futures.add(ROOT_EXECUTOR.submit(new Callable<SearchResult>() {
                @Override
                public SearchResult call() {
                    MinimaxAI worker = new MinimaxAI();
                    worker.setDifficulty(difficulty);
                    worker.searchStartTime = searchStartTime;
                    worker.searchTimeLimitMs = searchTimeLimitMs;
                    worker.searchDeadlineMs = searchDeadlineMs;
                    worker.timeUp = false;
                    worker.timeCheckCounter = 0;
                    int score = -worker.negamax(rootBoard, depth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE, 1, aiColor);
                    return new SearchResult(rootMove, score);
                }
            }));
        }

        int bestScore = Integer.MIN_VALUE;
        Move bestMove = null;
        for (Future<SearchResult> future : futures) {
            long remainMs = searchDeadlineMs - System.currentTimeMillis();
            if (remainMs <= 0) {
                timeUp = true;
                break;
            }
            try {
                SearchResult result = future.get(Math.max(1L, remainMs), TimeUnit.MILLISECONDS);
                if (result != null && result.bestMove != null && result.score > bestScore) {
                    bestScore = result.score;
                    bestMove = result.bestMove;
                }
            } catch (TimeoutException e) {
                timeUp = true;
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                timeUp = true;
                break;
            } catch (ExecutionException e) {
                // 忽略个别任务失败，继续汇总其他根节点结果
            }
        }

        for (Future<SearchResult> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
        return new SearchResult(bestMove, bestScore);
    }

    private EndgameCurve curveFor(EndgameStudySet.Tier tier, Difficulty difficulty) {
        switch (tier) {
            case BASIC:
                // 初级残局：更快应手，优先速度与实用。
                if (difficulty == Difficulty.HARD) {
                    return new EndgameCurve(0, -600, true);
                }
                if (difficulty == Difficulty.MEDIUM) {
                    return new EndgameCurve(0, -900, true);
                }
                return new EndgameCurve(0, -1000, true);
            case MEDIUM:
                if (difficulty == Difficulty.HARD) {
                    return new EndgameCurve(1, 1000, true);
                }
                if (difficulty == Difficulty.MEDIUM) {
                    return new EndgameCurve(1, 700, true);
                }
                return new EndgameCurve(0, 500, true);
            case ADVANCED:
                // 高级残局：更稳求解，提升深度与预算，关闭随机性。
                if (difficulty == Difficulty.HARD) {
                    return new EndgameCurve(2, 2600, true);
                }
                if (difficulty == Difficulty.MEDIUM) {
                    return new EndgameCurve(2, 1800, true);
                }
                return new EndgameCurve(1, 1200, true);
            default:
                return new EndgameCurve(0, 0, false);
        }
    }

    private int negamax(Board board, int depth, int alpha, int beta, int ply, PieceColor aiColor) {
        if (isTimeUp()) {
            return evaluate(board, aiColor);
        }

        if (board.isGameOver()) {
            PieceColor winner = board.getWinner();
            if (winner == aiColor) {
                return MATE_SCORE - ply;
            }
            if (winner == aiColor.opposite()) {
                return -MATE_SCORE + ply;
            }
            return 0;
        }

        PieceColor sideToMove = board.getCurrentTurn();
        if (depth <= 0) {
            // 将军局面补一层，避免浅层漏算强制将杀。
            if (board.isInCheck(sideToMove)) {
                depth = 1;
            } else {
                return evaluate(board, aiColor);
            }
        }

        long hash = computeHash(board);
        int originalAlpha = alpha;
        TTEntry entry = transpositionTable.get(hash);
        Move ttMove = null;
        if (entry != null) {
            ttMove = entry.bestMove;
            if (entry.depth >= depth) {
                if (entry.flag == TT_EXACT) {
                    return entry.score;
                }
                if (entry.flag == TT_LOWER) {
                    alpha = Math.max(alpha, entry.score);
                } else if (entry.flag == TT_UPPER) {
                    beta = Math.min(beta, entry.score);
                }
                if (alpha >= beta) {
                    return entry.score;
                }
            }
        }

        List<Move> validMoves = board.getAllValidMoves(sideToMove);
        if (validMoves.isEmpty()) {
            return evaluate(board, aiColor);
        }
        orderMoves(validMoves, board, ttMove, ply);

        int bestScore = Integer.MIN_VALUE;
        Move bestMove = null;
        boolean firstMove = true;
        for (Move move : validMoves) {
            if (isTimeUp()) {
                break;
            }

            boolean isCapture = board.getPiece(move.getToRow(), move.getToCol()) != null;
            Board next = new Board(board);
            next.movePiece(move);
            int nextPly = Math.min(MAX_PLY - 1, ply + 1);
            int score;
            if (firstMove) {
                score = -negamax(next, depth - 1, -beta, -alpha, nextPly, aiColor);
                firstMove = false;
            } else {
                score = -negamax(next, depth - 1, -alpha - 1, -alpha, nextPly, aiColor);
                if (!timeUp && score > alpha && score < beta) {
                    score = -negamax(next, depth - 1, -beta, -alpha, nextPly, aiColor);
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                if (!isCapture) {
                    recordKiller(move, ply);
                    recordHistory(sideToMove, move, depth);
                }
                break;
            }
        }

        if (!timeUp && bestMove != null) {
            int flag = TT_EXACT;
            if (bestScore <= originalAlpha) {
                flag = TT_UPPER;
            } else if (bestScore >= beta) {
                flag = TT_LOWER;
            }
            if (transpositionTable.size() >= TT_MAX_ENTRIES) {
                transpositionTable.clear();
            }
            transpositionTable.put(hash, new TTEntry(depth, bestScore, flag, copyMove(bestMove)));
        }
        return bestScore;
    }

    private boolean isTimeUp() {
        if (timeUp) {
            return true;
        }
        timeCheckCounter++;
        if ((timeCheckCounter & TIME_CHECK_MASK) != 0) {
            return false;
        }
        if (System.currentTimeMillis() >= searchDeadlineMs) {
            timeUp = true;
            return true;
        }
        return false;
    }

    private SearchBudget tuneBudget(Board board, List<Move> rootMoves, int baseDepth, int baseTimeMs,
                                    boolean inStudySet, boolean inLearnedSet, boolean inEventSet) {
        int depth = baseDepth;
        int timeMs = baseTimeMs;
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int branching = rootMoves == null ? 0 : rootMoves.size();

        if (cores >= 8) {
            timeMs += 1800;
            depth += 1;
        } else if (cores <= 2) {
            timeMs -= 900;
            depth -= 1;
        }

        if (branching >= 42) {
            timeMs -= 1200;
            depth -= 1;
        } else if (branching <= 14) {
            timeMs += 900;
            if (difficulty != Difficulty.EASY) {
                depth += 1;
            }
        }

        if (board != null && board.isInCheck(board.getCurrentTurn())) {
            timeMs += 600;
            depth += 1;
        }

        int hardCap = difficulty == Difficulty.HARD ? 11 : (difficulty == Difficulty.MEDIUM ? 9 : 7);
        if (inStudySet || inLearnedSet || inEventSet) {
            hardCap += 1;
        }
        depth = Math.max(1, Math.min(depth, hardCap));
        timeMs = Math.max(450, timeMs);
        return new SearchBudget(depth, timeMs);
    }

    private long buildResultCacheKey(Board board, PieceColor aiColor, Difficulty diff) {
        long h = computeHash(board);
        h ^= ((long) diff.ordinal() & 0xFFL) << 56;
        h ^= aiColor == PieceColor.RED ? 0x13579BDF2468ACE0L : 0x2468ACE013579BDFL;
        return h;
    }

    private Move loadCachedBestMove(long key, List<Move> validMoves) {
        CachedBestMove cached = RESULT_CACHE.get(key);
        if (cached == null) {
            return null;
        }
        if (System.currentTimeMillis() > cached.expiresAt) {
            RESULT_CACHE.remove(key);
            return null;
        }
        if (cached.move == null || validMoves == null) {
            return null;
        }
        for (Move move : validMoves) {
            if (isSameMove(move, cached.move)) {
                return move;
            }
        }
        return null;
    }

    private void cacheBestMove(long key, Move move) {
        if (move == null) {
            return;
        }
        if (RESULT_CACHE.size() >= RESULT_CACHE_MAX_ENTRIES) {
            RESULT_CACHE.clear();
        }
        RESULT_CACHE.put(key, new CachedBestMove(copyMove(move), System.currentTimeMillis() + RESULT_CACHE_TTL_MS));
    }

    private void orderMoves(List<Move> moves, Board board, Move pvMove, int ply) {
        List<MoveOrder> scored = new ArrayList<MoveOrder>(moves.size());
        PieceColor side = board.getCurrentTurn();
        int colorIdx = side == PieceColor.RED ? 0 : 1;
        Move killer1 = killerMoves[ply][0];
        Move killer2 = killerMoves[ply][1];

        for (Move move : moves) {
            int score = 0;
            if (pvMove != null && isSameMove(move, pvMove)) {
                score += 3_000_000;
            }

            Piece captured = board.getPiece(move.getToRow(), move.getToCol());
            Piece attacker = board.getPiece(move.getFromRow(), move.getFromCol());
            if (captured != null && attacker != null) {
                score += 2_000_000 + getPieceValue(captured) * 16 - getPieceValue(attacker);
            } else {
                if (killer1 != null && isSameMove(move, killer1)) {
                    score += 1_500_000;
                } else if (killer2 != null && isSameMove(move, killer2)) {
                    score += 1_200_000;
                }
                int from = move.getFromRow() * 9 + move.getFromCol();
                int to = move.getToRow() * 9 + move.getToCol();
                score += historyHeuristic[colorIdx][from][to];
            }
            scored.add(new MoveOrder(move, score));
        }

        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        moves.clear();
        for (MoveOrder moveOrder : scored) {
            moves.add(moveOrder.move);
        }
    }

    private void recordKiller(Move move, int ply) {
        if (move == null || ply < 0 || ply >= MAX_PLY) {
            return;
        }
        if (killerMoves[ply][0] == null || !isSameMove(killerMoves[ply][0], move)) {
            killerMoves[ply][1] = killerMoves[ply][0];
            killerMoves[ply][0] = copyMove(move);
        }
    }

    private void recordHistory(PieceColor side, Move move, int depth) {
        int colorIdx = side == PieceColor.RED ? 0 : 1;
        int from = move.getFromRow() * 9 + move.getFromCol();
        int to = move.getToRow() * 9 + move.getToCol();
        historyHeuristic[colorIdx][from][to] += depth * depth;
        if (historyHeuristic[colorIdx][from][to] > 2_000_000) {
            historyHeuristic[colorIdx][from][to] /= 2;
        }
    }

    private long computeHash(Board board) {
        long h = 0L;
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece == null) {
                    continue;
                }
                int t = piece.getType().ordinal();
                h ^= ZOBRIST[t][row][col];
            }
        }
        if (board.getCurrentTurn() == PieceColor.RED) {
            h ^= ZOBRIST_TURN_KEY;
        }
        return h;
    }

    private static long[][][] initZobrist() {
        PieceType[] types = PieceType.values();
        long[][][] keys = new long[types.length][Board.ROWS][Board.COLS];
        Random r = new Random(20260219L);
        for (int t = 0; t < types.length; t++) {
            for (int row = 0; row < Board.ROWS; row++) {
                for (int col = 0; col < Board.COLS; col++) {
                    long v = r.nextLong();
                    if (v == 0L) {
                        v = 1L;
                    }
                    keys[t][row][col] = v;
                }
            }
        }
        return keys;
    }

    private int evaluate(Board board, PieceColor aiColor) {
        int score = 0;
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece == null) {
                    continue;
                }
                int pieceValue = getPieceValue(piece);
                int positionValue = getPositionValue(piece, row, col);
                if (piece.getColor() == aiColor) {
                    score += pieceValue + positionValue;
                } else {
                    score -= pieceValue + positionValue;
                }
            }
        }

        if (board.isInCheck(aiColor.opposite())) {
            score += 30;
        }
        if (board.isInCheck(aiColor)) {
            score -= 45;
        }

        if (board.isGameOver()) {
            PieceColor winner = board.getWinner();
            if (winner == aiColor) {
                return MATE_SCORE;
            }
            if (winner == aiColor.opposite()) {
                return -MATE_SCORE;
            }
            return 0;
        }
        return score;
    }

    private int getPieceValue(Piece piece) {
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

    private void sortMovesByCaptureValue(List<Move> moves, Board board) {
        List<Move> ordered = new ArrayList<Move>(moves);
        ordered.sort((move1, move2) -> {
            boolean isCapture1 = board.getPiece(move1.getToRow(), move1.getToCol()) != null;
            boolean isCapture2 = board.getPiece(move2.getToRow(), move2.getToCol()) != null;

            if (isCapture1 && !isCapture2) {
                return -1;
            }
            if (!isCapture1 && isCapture2) {
                return 1;
            }
            if (isCapture1) {
                Piece captured1 = board.getPiece(move1.getToRow(), move1.getToCol());
                Piece captured2 = board.getPiece(move2.getToRow(), move2.getToCol());
                return Integer.compare(getPieceValue(captured2), getPieceValue(captured1));
            }
            return 0;
        });

        moves.clear();
        moves.addAll(ordered);
    }

    private int getPositionValue(Piece piece, int row, int col) {
        PieceType type = piece.getType();
        PieceColor color = piece.getColor();

        if (type == PieceType.ZU || type == PieceType.ZU_RED) {
            if (color == PieceColor.RED && row <= 4) {
                return (4 - row) * 24;
            }
            if (color == PieceColor.BLACK && row >= 5) {
                return (row - 4) * 24;
            }
            return 4;
        }

        if (type == PieceType.MA || type == PieceType.MA_RED || type == PieceType.CHE || type == PieceType.CHE_RED) {
            int centerRow = 4;
            int centerCol = 4;
            int distance = Math.abs(row - centerRow) + Math.abs(col - centerCol);
            return (8 - distance) * 10;
        }

        if (type == PieceType.PAO || type == PieceType.PAO_RED) {
            if (row >= 2 && row <= 7 && col >= 2 && col <= 6) {
                return 34;
            }
        }

        return 0;
    }

    private boolean isSameMove(Move a, Move b) {
        return a != null && b != null
            && a.getFromRow() == b.getFromRow()
            && a.getFromCol() == b.getFromCol()
            && a.getToRow() == b.getToRow()
            && a.getToCol() == b.getToCol();
    }

    private Move copyMove(Move m) {
        return new Move(m.getFromRow(), m.getFromCol(), m.getToRow(), m.getToCol());
    }

    private static final class MoveOrder {
        private final Move move;
        private final int score;

        private MoveOrder(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }

    private static final class SearchResult {
        private final Move bestMove;
        private final int score;

        private SearchResult(Move bestMove, int score) {
            this.bestMove = bestMove;
            this.score = score;
        }
    }
}
