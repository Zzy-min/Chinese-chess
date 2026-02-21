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
    private static final int MIDGAME_PLY_FAST_CAP = 10;
    private static final int RESCUE_BOOK_MIN_PLY = 8;
    private static final int RESCUE_BOOK_MIN_BRANCHING = 24;
    private static final int RESCUE_BOOK_REPLY_SCAN = 8;
    private static final int RESULT_CACHE_MAX_ENTRIES = 50000;
    private static final long RESULT_CACHE_TTL_MS = 3 * 60 * 1000L;
    private static final int QUIESCENCE_MAX_DEPTH = 8;
    private static final int QUIESCENCE_MAX_MOVES = 16;
    private static final int QUIESCENCE_FAST_DEPTH = 5;
    private static final int QUIESCENCE_FAST_MOVES = 10;
    private static final int QUIESCENCE_DELTA_MARGIN = 120;
    private static final int NULL_MOVE_MIN_DEPTH = 4;
    private static final int NULL_MOVE_REDUCTION = 2;
    private static final int NULL_MOVE_STATIC_MARGIN = 80;
    private static final int LMR_MIN_DEPTH = 3;
    private static final int LMR_LATE_MOVE_INDEX = 4;
    private static final int FUTILITY_MAX_DEPTH = 2;
    private static final int FUTILITY_MARGIN_DEPTH_1 = 130;
    private static final int FUTILITY_MARGIN_DEPTH_2 = 300;
    private static final int SEE_MAX_DEPTH = 4;
    private static final int SEE_BAD_CAPTURE_THRESHOLD = -40;
    private static final int GUARD_WEIGHT_SHI = 22;
    private static final int GUARD_WEIGHT_XIANG = 16;
    private static final int REPETITION_DRAW_PENALTY_WINNING = -65;
    private static final int REPETITION_DRAW_BONUS_LOSING = 45;
    private static final int REPETITION_EVAL_THRESHOLD = 120;
    private static final double[] TIME_PRESSURE_EMA = new double[Difficulty.values().length];
    private static final double TIME_PRESSURE_ALPHA = 0.22;
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
        EASY("简单", 2, 380, 0.30),
        MEDIUM("中等", 5, 980, 0.03),
        HARD("困难", 9, 5200, 0.0);

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
    private boolean searchFastMode;

    private final Map<Long, TTEntry> transpositionTable = new HashMap<Long, TTEntry>(1 << 15);
    private final Map<Long, Integer> repetitionCount = new HashMap<Long, Integer>(256);
    private final Map<Long, Integer> seeCache = new HashMap<Long, Integer>(2048);
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
        if (validMoves.size() == 1) {
            return validMoves.get(0);
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
        int ply = board.getMoveCount();
        EndgameCurve endgameCurve = inStudySet ? curveFor(studyTier, difficulty) : new EndgameCurve(0, 0, false);

        Move rescueBookMove = tryRescueBookMove(board, aiColor, validMoves, ply, inLearnedSet, inEventSet);
        if (rescueBookMove != null) {
            return rescueBookMove;
        }

        Move fastEventMove = tryFastEventMove(board, aiColor, validMoves, inEventSet, ply);
        if (fastEventMove != null) {
            return fastEventMove;
        }

        Move ultraFastMove = tryUltraFastMove(board, aiColor, validMoves, ply);
        if (ultraFastMove != null) {
            return ultraFastMove;
        }

        if (ply >= MIDGAME_PLY_FAST_CAP && !endgameCurve.forceDeterministic && !inStudySet && !inLearnedSet && !inEventSet
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
        if (ply < MIDGAME_PLY_FAST_CAP && (inLearnedSet || inEventSet)) {
            // 开局前十步命中棋谱学习局面时，提升稳健性与质量。
            if (difficulty == Difficulty.HARD) {
                maxDepth = Math.min(maxDepth + 1, 10);
                extraTime += 1100;
            } else if (difficulty == Difficulty.MEDIUM) {
                maxDepth = Math.min(maxDepth + 1, 8);
                extraTime += 900;
            } else {
                extraTime += 450;
            }
            if (aiColor == PieceColor.BLACK) {
                extraTime += 250;
            }
        }

        SearchBudget budget = tuneBudget(board, validMoves, maxDepth, difficulty.getTimeLimitMs() + extraTime, inStudySet, inLearnedSet, inEventSet);
        maxDepth = budget.maxDepth;
        double pressureNow = getTimePressure(difficulty);
        int branchingNow = validMoves.size();
        searchFastMode = branchingNow >= 34
            || pressureNow >= 1.02
            || (difficulty != Difficulty.HARD && branchingNow >= 28);
        searchStartTime = System.currentTimeMillis();
        searchTimeLimitMs = Math.max(450, budget.timeLimitMs);
        searchDeadlineMs = searchStartTime + searchTimeLimitMs;
        timeUp = false;
        timeCheckCounter = 0;
        transpositionTable.clear();
        seedRepetitionHistory(board);
        seeCache.clear();

        long cacheKey = buildResultCacheKey(board, aiColor, difficulty);
        Move cached = loadCachedBestMove(cacheKey, validMoves);
        if (cached != null) {
            return cached;
        }

        Move bestMove = validMoves.get(0);
        Move pvMove = null;
        int prevScore = 0;
        int completedDepth = 0;
        int aspirationMissTrend = 0;

        for (int depth = 1; depth <= maxDepth && !timeUp; depth++) {
            SearchResult result;
            if (depth >= 3) {
                int baseWindow = computeAspirationWindow(depth, aspirationMissTrend);
                AspirationSearchOutcome outcome = searchRootWithAdaptiveAspiration(
                    board, aiColor, validMoves, depth, pvMove, prevScore, baseWindow
                );
                result = outcome.result;
                aspirationMissTrend = outcome.attempts > 1
                    ? Math.min(5, aspirationMissTrend + 1)
                    : Math.max(0, aspirationMissTrend - 1);
            } else {
                result = searchRoot(board, aiColor, validMoves, depth, pvMove, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
            }
            if (!timeUp && result.bestMove != null) {
                bestMove = result.bestMove;
                pvMove = result.bestMove;
                prevScore = result.score;
                completedDepth = depth;
            }
        }

        long elapsed = System.currentTimeMillis() - searchStartTime;
        updateTimePressure(difficulty, elapsed, searchTimeLimitMs, timeUp);
        if (shouldCacheResult(completedDepth, maxDepth, timeUp)) {
            cacheBestMove(cacheKey, bestMove);
        }
        return bestMove;
    }

    private Move tryFastEventMove(Board board, PieceColor aiColor, List<Move> validMoves, boolean inEventSet, int ply) {
        if (!inEventSet || board == null || aiColor == null || validMoves == null || validMoves.isEmpty()) {
            return null;
        }
        if (ply < MIDGAME_PLY_FAST_CAP) {
            return null;
        }
        double pressure = getTimePressure(difficulty);
        int branching = validMoves.size();
        boolean mustFast = difficulty == Difficulty.EASY
            || (difficulty == Difficulty.MEDIUM && (branching >= 28 || pressure > 0.95))
            || (difficulty == Difficulty.HARD && (branching >= 38 || pressure > 1.08));
        if (!mustFast) {
            return null;
        }
        return findFastSafeForwardMove(board, aiColor, validMoves);
    }

    private Move tryRescueBookMove(Board board, PieceColor aiColor, List<Move> validMoves,
                                   int ply, boolean inLearnedSet, boolean inEventSet) {
        if (board == null || aiColor == null || validMoves == null || validMoves.isEmpty()) {
            return null;
        }
        if (inLearnedSet || inEventSet) {
            return null;
        }
        if (!shouldUseRescueBook(board, aiColor, validMoves, ply)) {
            return null;
        }

        Move bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        int bestHitLevel = 0;

        for (Move move : validMoves) {
            Piece attacker = board.getPiece(move.getFromRow(), move.getFromCol());
            Piece captured = board.getPiece(move.getToRow(), move.getToCol());
            Board next = new Board(board);
            next.movePiece(move);

            int hitLevel = getBookHitLevel(next, aiColor);
            if (hitLevel <= 0) {
                continue;
            }

            int score = evaluate(next, aiColor) + hitLevel * 10_000;
            if (captured != null) {
                score += getPieceValue(captured) * 10;
            }
            if (next.isInCheck(aiColor.opposite())) {
                score += 120;
            }
            if (isMoveLandingSafe(board, move, aiColor)) {
                score += 140;
            } else {
                int attackerValue = attacker == null ? 0 : getPieceValue(attacker);
                score -= Math.max(140, attackerValue / 2);
            }
            if (isForwardMove(aiColor, move)) {
                score += 60;
            }

            if (hitLevel > bestHitLevel || (hitLevel == bestHitLevel && score > bestScore)) {
                bestHitLevel = hitLevel;
                bestScore = score;
                bestMove = move;
            }
        }
        return bestMove;
    }

    private Move tryUltraFastMove(Board board, PieceColor aiColor, List<Move> validMoves, int ply) {
        if (board == null || aiColor == null || validMoves == null || validMoves.isEmpty()) {
            return null;
        }
        if (ply < 4 || difficulty == Difficulty.HARD) {
            return null;
        }
        double pressure = getTimePressure(difficulty);
        int branching = validMoves.size();
        boolean mustFast = difficulty == Difficulty.EASY
            ? (branching >= 22 || pressure >= 0.9)
            : (branching >= 34 && pressure >= 1.0);
        if (!mustFast) {
            return null;
        }
        return findFastSafeForwardMove(board, aiColor, validMoves);
    }

    private boolean shouldUseRescueBook(Board board, PieceColor aiColor, List<Move> validMoves, int ply) {
        if (ply < RESCUE_BOOK_MIN_PLY) {
            return false;
        }
        int branching = validMoves.size();
        int evalNow = evaluate(board, aiColor);
        boolean inCheck = board.isInCheck(aiColor);
        boolean behind = evalNow < -260;
        boolean complex = branching >= RESCUE_BOOK_MIN_BRANCHING;
        double pressure = getTimePressure(difficulty);
        boolean underPressure = difficulty == Difficulty.HARD ? pressure > 1.03 : pressure > 0.92;
        return inCheck || behind || complex || underPressure;
    }

    private int getBookHitLevel(Board nextBoard, PieceColor aiColor) {
        int hit = 0;
        if (EventLearnedSet.contains(nextBoard)) {
            hit += 4;
        }
        if (XqipuLearnedSet.contains(nextBoard)) {
            hit += 3;
        }
        if (hit > 0) {
            return hit;
        }

        PieceColor opponent = aiColor.opposite();
        List<Move> replies = nextBoard.getAllValidMoves(opponent);
        if (replies.isEmpty()) {
            return 0;
        }
        sortMovesByCaptureValue(replies, nextBoard);
        int scan = Math.min(RESCUE_BOOK_REPLY_SCAN, replies.size());
        int bestReplyHit = 0;
        for (int i = 0; i < scan; i++) {
            Move reply = replies.get(i);
            Board afterReply = new Board(nextBoard);
            afterReply.movePiece(reply);
            int replyHit = 0;
            if (EventLearnedSet.contains(afterReply)) {
                replyHit += 2;
            }
            if (XqipuLearnedSet.contains(afterReply)) {
                replyHit += 1;
            }
            if (replyHit > bestReplyHit) {
                bestReplyHit = replyHit;
            }
        }
        return bestReplyHit;
    }

    private Move findFastSafeForwardMove(Board board, PieceColor aiColor, List<Move> validMoves) {
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Move move : validMoves) {
            Piece attacker = board.getPiece(move.getFromRow(), move.getFromCol());
            Piece captured = board.getPiece(move.getToRow(), move.getToCol());
            Board next = new Board(board);
            next.movePiece(move);

            int score = evaluate(next, aiColor);
            if (captured != null) {
                score += getPieceValue(captured) * 12;
            }
            if (isForwardMove(aiColor, move)) {
                score += 90;
            }
            if (next.isInCheck(aiColor.opposite())) {
                score += 70;
            }

            boolean safe = isMoveLandingSafe(board, move, aiColor);
            if (safe) {
                score += 140;
            } else {
                int attackerValue = attacker == null ? 0 : getPieceValue(attacker);
                score -= Math.max(120, attackerValue / 2);
            }

            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best;
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

    private AspirationSearchOutcome searchRootWithAdaptiveAspiration(Board board, PieceColor aiColor, List<Move> rootMoves,
                                                                     int depth, Move pvMove, int guessScore, int baseWindow) {
        int window = Math.max(36, baseWindow);
        int alpha = clampToSearchBound(guessScore - window);
        int beta = clampToSearchBound(guessScore + window);
        int attempts = 1;
        SearchResult result = searchRoot(board, aiColor, rootMoves, depth, pvMove, alpha, beta);

        while (!timeUp && result.bestMove != null && (result.score <= alpha || result.score >= beta) && attempts < 3) {
            window = Math.min(620, window * 2);
            if (result.score <= alpha) {
                alpha = clampToSearchBound(guessScore - window);
            } else {
                beta = clampToSearchBound(guessScore + window);
            }
            attempts++;
            result = searchRoot(board, aiColor, rootMoves, depth, pvMove, alpha, beta);
        }

        if (!timeUp && result.bestMove != null && (result.score <= alpha || result.score >= beta)) {
            attempts++;
            result = searchRoot(board, aiColor, rootMoves, depth, pvMove, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
        }
        return new AspirationSearchOutcome(result, attempts);
    }

    private int computeAspirationWindow(int depth, int missTrend) {
        double pressure = getTimePressure(difficulty);
        int window = ASPIRATION_WINDOW + depth * 9 + missTrend * 34;
        if (pressure > 1.0) {
            window += 24;
        } else if (pressure < 0.8) {
            window -= 12;
        }
        if (difficulty == Difficulty.EASY) {
            window += 20;
        } else if (difficulty == Difficulty.HARD) {
            window -= 8;
        }
        return Math.max(36, Math.min(320, window));
    }

    private int clampToSearchBound(int score) {
        if (score <= Integer.MIN_VALUE + 1) {
            return Integer.MIN_VALUE + 1;
        }
        if (score >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return score;
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
                    worker.searchFastMode = searchFastMode;
                    worker.repetitionCount.putAll(repetitionCount);
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
        long hash = computeHash(board);
        int seen = repetitionCount.getOrDefault(hash, 0) + 1;
        repetitionCount.put(hash, seen);
        boolean repetitionSensitive = seen > 1;
        try {
        if (isTimeUp()) {
            return evaluate(board, aiColor);
        }

        if (seen >= 3) {
            return repetitionScore(board, aiColor);
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
        if (ply >= MAX_PLY - 1) {
            return evaluate(board, aiColor);
        }

        PieceColor sideToMove = board.getCurrentTurn();
        boolean sideInCheck = board.isInCheck(sideToMove);
        if (depth <= 0) {
            // 将军局面补一层，避免浅层漏算强制将杀。
            if (sideInCheck) {
                depth = 1;
            } else {
                return quiescence(board, alpha, beta, aiColor, ply, 0);
            }
        }

        int originalAlpha = alpha;
        TTEntry entry = repetitionSensitive ? null : transpositionTable.get(hash);
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

        int staticEval = evaluate(board, aiColor);
        if (!repetitionSensitive
            && depth >= NULL_MOVE_MIN_DEPTH
            && !sideInCheck
            && Math.abs(beta) < MATE_SCORE / 2
            && canUseNullMove(board, sideToMove)) {
            if (staticEval >= beta - NULL_MOVE_STATIC_MARGIN) {
                Board nullBoard = new Board(board);
                nullBoard.setCurrentTurn(sideToMove.opposite());
                int reduction = depth >= 8 ? (NULL_MOVE_REDUCTION + 1) : NULL_MOVE_REDUCTION;
                int nullDepth = Math.max(0, depth - 1 - reduction);
                int nullScore = -negamax(
                    nullBoard,
                    nullDepth,
                    -beta,
                    -beta + 1,
                    Math.min(MAX_PLY - 1, ply + 1),
                    aiColor
                );
                if (!timeUp && nullScore >= beta) {
                    return nullScore;
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
        int moveIndex = 0;
        for (Move move : validMoves) {
            if (isTimeUp()) {
                break;
            }
            moveIndex++;

            boolean isCapture = board.getPiece(move.getToRow(), move.getToCol()) != null;
            Board next = new Board(board);
            next.movePiece(move);
            int nextPly = Math.min(MAX_PLY - 1, ply + 1);
            boolean givesCheck = next.isInCheck(sideToMove.opposite());
            int fullDepth = Math.max(1, depth - 1 + checkExtension(depth, givesCheck, moveIndex));
            int score;
            if (firstMove) {
                score = -negamax(next, fullDepth, -beta, -alpha, nextPly, aiColor);
                firstMove = false;
            } else {
                if (depth <= FUTILITY_MAX_DEPTH
                    && !sideInCheck
                    && !isCapture
                    && !givesCheck
                    && !isKillerMove(move, ply)
                    && staticEval + futilityMargin(depth) <= alpha) {
                    continue;
                }
                boolean reduce = depth >= LMR_MIN_DEPTH
                    && moveIndex >= LMR_LATE_MOVE_INDEX
                    && !sideInCheck
                    && !isCapture
                    && !givesCheck
                    && !isKillerMove(move, ply);
                int searchDepth = reduce ? Math.max(1, fullDepth - 1) : fullDepth;

                score = -negamax(next, searchDepth, -alpha - 1, -alpha, nextPly, aiColor);
                if (!timeUp && reduce && score > alpha) {
                    // LMR fail-high 回补：恢复原深度后再做零窗口确认。
                    score = -negamax(next, fullDepth, -alpha - 1, -alpha, nextPly, aiColor);
                }
                if (!timeUp && score > alpha && score < beta) {
                    score = -negamax(next, fullDepth, -beta, -alpha, nextPly, aiColor);
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

        if (!timeUp && bestMove != null && !repetitionSensitive) {
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
        } finally {
            int current = repetitionCount.getOrDefault(hash, 0);
            if (current <= 1) {
                repetitionCount.remove(hash);
            } else {
                repetitionCount.put(hash, current - 1);
            }
        }
    }

    private int repetitionScore(Board board, PieceColor aiColor) {
        int eval = evaluate(board, aiColor);
        if (eval > REPETITION_EVAL_THRESHOLD) {
            return REPETITION_DRAW_PENALTY_WINNING;
        }
        if (eval < -REPETITION_EVAL_THRESHOLD) {
            return REPETITION_DRAW_BONUS_LOSING;
        }
        return 0;
    }

    private int quiescence(Board board, int alpha, int beta, PieceColor aiColor, int ply, int qDepth) {
        if (isTimeUp()) {
            return evaluate(board, aiColor);
        }
        int standPat = evaluate(board, aiColor);
        if (standPat >= beta) {
            return standPat;
        }
        if (standPat > alpha) {
            alpha = standPat;
        }
        if (qDepth >= currentQuiescenceMaxDepth() || ply >= MAX_PLY - 1) {
            return standPat;
        }

        PieceColor side = board.getCurrentTurn();
        List<Move> tacticalMoves = getQuiescenceMoves(board, side);
        if (tacticalMoves.isEmpty()) {
            return standPat;
        }

        int explored = 0;
        for (Move move : tacticalMoves) {
            if (isTimeUp()) {
                break;
            }
            Piece captured = board.getPiece(move.getToRow(), move.getToCol());
            if (captured != null) {
                int optimistic = standPat + getPieceValue(captured) + QUIESCENCE_DELTA_MARGIN;
                if (optimistic < alpha) {
                    continue;
                }
            }
            Board next = new Board(board);
            next.movePiece(move);
            int score = -quiescence(
                next,
                -beta,
                -alpha,
                aiColor,
                Math.min(MAX_PLY - 1, ply + 1),
                qDepth + 1
            );
            if (score >= beta) {
                return score;
            }
            if (score > alpha) {
                alpha = score;
            }
            explored++;
            if (explored >= currentQuiescenceMaxMoves()) {
                break;
            }
        }
        return alpha;
    }

    private List<Move> getQuiescenceMoves(Board board, PieceColor side) {
        List<Move> all = board.getAllValidMoves(side);
        if (all.isEmpty()) {
            return all;
        }
        List<MoveOrder> captures = new ArrayList<MoveOrder>(all.size());
        for (Move move : all) {
            Piece captured = board.getPiece(move.getToRow(), move.getToCol());
            if (captured == null) {
                continue;
            }
            Piece attacker = board.getPiece(move.getFromRow(), move.getFromCol());
            int seeDepth = currentSeeDepthLimit();
            int see = staticExchangeEval(board, move, side, seeDepth);
            int score = getPieceValue(captured) * 20
                - (attacker == null ? 0 : getPieceValue(attacker))
                + see * 18;
            if (see < SEE_BAD_CAPTURE_THRESHOLD && getPieceValue(captured) < 430) {
                continue;
            }
            captures.add(new MoveOrder(move, score));
        }
        captures.sort((a, b) -> Integer.compare(b.score, a.score));
        int maxMoves = currentQuiescenceMaxMoves();
        List<Move> result = new ArrayList<Move>(Math.min(maxMoves, captures.size()));
        for (MoveOrder moveOrder : captures) {
            result.add(moveOrder.move);
            if (result.size() >= maxMoves) {
                break;
            }
        }
        return result;
    }

    private int currentQuiescenceMaxDepth() {
        if (searchFastMode && difficulty != Difficulty.HARD) {
            return QUIESCENCE_FAST_DEPTH;
        }
        return QUIESCENCE_MAX_DEPTH;
    }

    private int currentQuiescenceMaxMoves() {
        if (searchFastMode && difficulty != Difficulty.HARD) {
            return QUIESCENCE_FAST_MOVES;
        }
        return QUIESCENCE_MAX_MOVES;
    }

    private void seedRepetitionHistory(Board board) {
        repetitionCount.clear();
        if (board == null) {
            return;
        }
        int totalMoves = board.getMoveCount();
        if (totalMoves <= 0) {
            return;
        }
        for (int ply = 0; ply < totalMoves; ply++) {
            Board prior = board.getBoardAtMove(ply);
            if (prior == null) {
                continue;
            }
            long key = computeHash(prior);
            repetitionCount.put(key, repetitionCount.getOrDefault(key, 0) + 1);
        }
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
        double pressure = getTimePressure(difficulty);
        int ply = board == null ? 0 : board.getMoveCount();

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

        // 低分支局面给中高难度更多“质量预算”。
        if (difficulty == Difficulty.HARD && branching > 0 && branching <= 18 && pressure < 0.92) {
            depth += 1;
            timeMs += 500;
        } else if (difficulty == Difficulty.MEDIUM && branching > 0 && branching <= 16 && pressure < 0.88) {
            depth += 1;
            timeMs += 260;
        }

        if (board != null && board.isInCheck(board.getCurrentTurn())) {
            timeMs += 600;
            depth += 1;
        }

        // 前十步提高质量，尤其是 AI 后手（黑方）开局阶段。
        if (board != null && ply < MIDGAME_PLY_FAST_CAP) {
            timeMs += 500;
            if (difficulty != Difficulty.EASY) {
                depth += 1;
            }
            if (board.getCurrentTurn() == PieceColor.BLACK) {
                timeMs += 300;
            }
        }

        // 最近搜索若接近/超过预算，动态降预算以保流畅；若明显富余，则适度加深。
        if (pressure > 1.10) {
            timeMs -= 950;
            depth -= 1;
        } else if (pressure > 0.95) {
            timeMs -= 450;
        } else if (pressure < 0.72) {
            timeMs += 450;
            if (difficulty != Difficulty.EASY) {
                depth += 1;
            }
        }

        // 第10步后优先保证响应速度，避免“中后盘长考”。
        if (ply >= MIDGAME_PLY_FAST_CAP) {
            if (difficulty == Difficulty.HARD) {
                timeMs -= 1200;
            } else if (difficulty == Difficulty.MEDIUM) {
                timeMs -= 900;
            } else {
                timeMs -= 500;
            }
            if (branching >= 34) {
                timeMs -= 500;
            }
        }

        int hardCap = difficulty == Difficulty.HARD ? 11 : (difficulty == Difficulty.MEDIUM ? 9 : 7);
        if (inStudySet || inLearnedSet || inEventSet) {
            hardCap += 1;
        }
        depth = Math.max(1, Math.min(depth, hardCap));
        int timeCap = difficulty == Difficulty.HARD ? 7000 : (difficulty == Difficulty.MEDIUM ? 2800 : 1400);
        if (ply >= MIDGAME_PLY_FAST_CAP) {
            timeCap = difficulty == Difficulty.HARD ? 4200 : (difficulty == Difficulty.MEDIUM ? 1800 : 1000);
        }
        if (ply >= 26) {
            timeCap = Math.min(timeCap, difficulty == Difficulty.HARD ? 3600 : (difficulty == Difficulty.MEDIUM ? 1600 : 900));
        }
        timeMs = Math.max(450, Math.min(timeMs, timeCap));
        return new SearchBudget(depth, timeMs);
    }

    private boolean shouldCacheResult(int completedDepth, int targetDepth, boolean timedOut) {
        int minDepth;
        if (difficulty == Difficulty.HARD) {
            minDepth = 6;
        } else if (difficulty == Difficulty.MEDIUM) {
            minDepth = 4;
        } else {
            minDepth = 3;
        }
        if (!timedOut && completedDepth >= Math.max(minDepth, targetDepth - 1)) {
            return true;
        }
        return completedDepth >= minDepth + 1;
    }

    private static synchronized double getTimePressure(Difficulty difficulty) {
        double v = TIME_PRESSURE_EMA[difficulty.ordinal()];
        return v <= 0.0 ? 1.0 : v;
    }

    private static synchronized void updateTimePressure(Difficulty difficulty, long elapsedMs, int limitMs, boolean timedOut) {
        if (limitMs <= 0) {
            return;
        }
        double ratio = Math.max(0.2, Math.min(2.4, (double) elapsedMs / (double) limitMs));
        if (timedOut) {
            ratio = Math.max(ratio, 1.25);
        }
        int idx = difficulty.ordinal();
        double prev = TIME_PRESSURE_EMA[idx];
        if (prev <= 0.0) {
            TIME_PRESSURE_EMA[idx] = ratio;
            return;
        }
        TIME_PRESSURE_EMA[idx] = prev * (1.0 - TIME_PRESSURE_ALPHA) + ratio * TIME_PRESSURE_ALPHA;
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
                int seeDepth = currentSeeDepthLimit();
                int see = staticExchangeEval(board, move, side, seeDepth);
                score += 2_000_000 + getPieceValue(captured) * 16 - getPieceValue(attacker);
                score += see * 22;
                if (see < 0) {
                    score += see * 6;
                }
            } else {
                if (killer1 != null && isSameMove(move, killer1)) {
                    score += 1_500_000;
                } else if (killer2 != null && isSameMove(move, killer2)) {
                    score += 1_200_000;
                }
                int from = move.getFromRow() * 9 + move.getFromCol();
                int to = move.getToRow() * 9 + move.getToCol();
                score += historyHeuristic[colorIdx][from][to];

                if (board.getMoveCount() < MIDGAME_PLY_FAST_CAP && ply <= 1) {
                    score += openingDevelopmentScore(board, move, side);
                }

                // 根层/浅层启发：在不被直接吃掉的前提下，优先“向前压进”。
                if (ply <= 1 && isForwardMove(side, move)) {
                    score += 120;
                    if (!searchFastMode && isMoveLandingSafe(board, move, side)) {
                        score += 160;
                    } else if (attacker != null && getPieceValue(attacker) >= 430) {
                        score -= 120;
                    }
                }
            }
            scored.add(new MoveOrder(move, score));
        }

        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        moves.clear();
        for (MoveOrder moveOrder : scored) {
            moves.add(moveOrder.move);
        }
    }

    private int openingDevelopmentScore(Board board, Move move, PieceColor side) {
        Piece mover = board.getPiece(move.getFromRow(), move.getFromCol());
        if (mover == null) {
            return 0;
        }
        int score = 0;
        PieceType type = mover.getType();
        int toCenterDist = Math.abs(move.getToRow() - 4) + Math.abs(move.getToCol() - 4);
        int fromCenterDist = Math.abs(move.getFromRow() - 4) + Math.abs(move.getFromCol() - 4);
        score += (fromCenterDist - toCenterDist) * 10;

        boolean forward = isForwardMove(side, move);
        if (type == PieceType.MA || type == PieceType.MA_RED) {
            score += 120;
            if (forward) {
                score += 40;
            }
        } else if (type == PieceType.PAO || type == PieceType.PAO_RED) {
            score += 95;
            if (move.getToCol() == 4) {
                score += 55;
            }
        } else if (type == PieceType.CHE || type == PieceType.CHE_RED) {
            score += 85;
            if (forward) {
                score += 35;
            }
        } else if (type == PieceType.ZU || type == PieceType.ZU_RED) {
            score += forward ? 55 : -20;
            if (move.getToCol() == 4) {
                score += 28;
            }
        } else if (type == PieceType.XIANG || type == PieceType.XIANG_RED
            || type == PieceType.SHI || type == PieceType.SHI_RED) {
            score -= 28;
        } else if (type == PieceType.JIANG || type == PieceType.SHUAI) {
            score -= 40;
        }

        if (isMoveLandingSafe(board, move, side)) {
            score += 36;
        }
        return score;
    }

    private boolean isForwardMove(PieceColor side, Move move) {
        if (side == PieceColor.RED) {
            return move.getToRow() < move.getFromRow();
        }
        return move.getToRow() > move.getFromRow();
    }

    private boolean isMoveLandingSafe(Board board, Move move, PieceColor mover) {
        Board next = new Board(board);
        next.movePiece(move);
        PieceColor opp = mover.opposite();
        List<Move> replies = next.getAllValidMoves(opp);
        for (Move r : replies) {
            if (r.getToRow() == move.getToRow() && r.getToCol() == move.getToCol()) {
                return false;
            }
        }
        return true;
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

        score += evaluateGuardStructure(board, aiColor);
        score -= evaluateGuardStructure(board, aiColor.opposite());

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

    private int evaluateGuardStructure(Board board, PieceColor color) {
        int guardScore = 0;
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece == null || piece.getColor() != color) {
                    continue;
                }
                PieceType type = piece.getType();
                if (type == PieceType.SHI || type == PieceType.SHI_RED) {
                    guardScore += GUARD_WEIGHT_SHI;
                } else if (type == PieceType.XIANG || type == PieceType.XIANG_RED) {
                    guardScore += GUARD_WEIGHT_XIANG;
                }
            }
        }
        return guardScore;
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

    private int staticExchangeEval(Board board, Move move, PieceColor mover, int maxDepth) {
        if (board == null || move == null || mover == null) {
            return 0;
        }
        Piece captured = board.getPiece(move.getToRow(), move.getToCol());
        Piece attacker = board.getPiece(move.getFromRow(), move.getFromCol());
        if (captured == null || attacker == null) {
            return 0;
        }
        long seeKey = buildSeeKey(board, move, mover, maxDepth);
        Integer cached = seeCache.get(seeKey);
        if (cached != null) {
            return cached.intValue();
        }
        Board next = new Board(board);
        next.movePiece(move);
        int firstGain = getPieceValue(captured);
        int replyGain = seeBestCaptureGain(next, move.getToRow(), move.getToCol(), mover.opposite(), 1, maxDepth);
        int score = firstGain - replyGain;
        if (seeCache.size() >= 16_000) {
            seeCache.clear();
        }
        seeCache.put(seeKey, score);
        return score;
    }

    private int seeBestCaptureGain(Board board, int targetRow, int targetCol, PieceColor side, int depth, int maxDepth) {
        if (board == null || side == null || depth > maxDepth) {
            return 0;
        }
        List<Move> attackers = getCaptureMovesToSquare(board, side, targetRow, targetCol);
        if (attackers.isEmpty()) {
            return 0;
        }
        Move least = selectLeastValuableAttacker(board, attackers);
        if (least == null) {
            return 0;
        }
        Piece victim = board.getPiece(targetRow, targetCol);
        if (victim == null) {
            return 0;
        }
        int gainNow = getPieceValue(victim);
        Board next = new Board(board);
        next.movePiece(least);
        int gainLater = seeBestCaptureGain(next, targetRow, targetCol, side.opposite(), depth + 1, maxDepth);
        return Math.max(0, gainNow - gainLater);
    }

    private Move selectLeastValuableAttacker(Board board, List<Move> attackers) {
        Move best = null;
        int bestValue = Integer.MAX_VALUE;
        for (Move move : attackers) {
            Piece piece = board.getPiece(move.getFromRow(), move.getFromCol());
            if (piece == null) {
                continue;
            }
            int value = getPieceValue(piece);
            if (value < bestValue) {
                bestValue = value;
                best = move;
            }
        }
        return best;
    }

    private List<Move> getCaptureMovesToSquare(Board board, PieceColor side, int targetRow, int targetCol) {
        List<Move> captures = new ArrayList<Move>();
        Piece victim = board.getPiece(targetRow, targetCol);
        if (victim == null || victim.getColor() == side) {
            return captures;
        }
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece == null || piece.getColor() != side) {
                    continue;
                }
                Move candidate = new Move(piece.getRow(), piece.getCol(), targetRow, targetCol);
                if (board.isValidMove(candidate)) {
                    captures.add(candidate);
                }
            }
        }
        return captures;
    }

    private int currentSeeDepthLimit() {
        if (searchFastMode) {
            return difficulty == Difficulty.EASY ? 1 : 2;
        }
        if (difficulty == Difficulty.HARD) {
            return SEE_MAX_DEPTH + 1;
        }
        return SEE_MAX_DEPTH;
    }

    private long buildSeeKey(Board board, Move move, PieceColor mover, int maxDepth) {
        long h = computeHash(board);
        long key = h;
        key ^= ((long) move.getFromRow() & 0xFL) << 0;
        key ^= ((long) move.getFromCol() & 0xFL) << 4;
        key ^= ((long) move.getToRow() & 0xFL) << 8;
        key ^= ((long) move.getToCol() & 0xFL) << 12;
        key ^= ((long) maxDepth & 0xFFL) << 16;
        key ^= mover == PieceColor.RED ? 0x12L << 24 : 0x34L << 24;
        return key;
    }

    private boolean canUseNullMove(Board board, PieceColor side) {
        int pawnCount = 0;
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece == null || piece.getColor() != side) {
                    continue;
                }
                PieceType type = piece.getType();
                if (type == PieceType.CHE || type == PieceType.CHE_RED
                    || type == PieceType.MA || type == PieceType.MA_RED
                    || type == PieceType.PAO || type == PieceType.PAO_RED) {
                    return true;
                }
                if (type == PieceType.ZU || type == PieceType.ZU_RED) {
                    pawnCount++;
                }
            }
        }
        // 仅剩将士象时禁用 null-move，避免残局误剪；有多个兵仍可作为机动子力。
        return pawnCount >= 2;
    }

    private int futilityMargin(int depth) {
        if (depth <= 1) {
            return FUTILITY_MARGIN_DEPTH_1;
        }
        if (depth == 2) {
            return FUTILITY_MARGIN_DEPTH_2;
        }
        return FUTILITY_MARGIN_DEPTH_2 + 120;
    }

    private int checkExtension(int depth, boolean givesCheck, int moveIndex) {
        if (!givesCheck || depth <= 1 || searchFastMode) {
            return 0;
        }
        if (difficulty == Difficulty.HARD) {
            return (depth <= 4 && moveIndex <= 3) ? 1 : 0;
        }
        if (difficulty == Difficulty.MEDIUM) {
            return (depth <= 3 && moveIndex == 1) ? 1 : 0;
        }
        return 0;
    }

    private boolean isKillerMove(Move move, int ply) {
        if (move == null || ply < 0 || ply >= MAX_PLY) {
            return false;
        }
        return isSameMove(move, killerMoves[ply][0]) || isSameMove(move, killerMoves[ply][1]);
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

    private static final class AspirationSearchOutcome {
        private final SearchResult result;
        private final int attempts;

        private AspirationSearchOutcome(SearchResult result, int attempts) {
            this.result = result;
            this.attempts = attempts;
        }
    }
}
