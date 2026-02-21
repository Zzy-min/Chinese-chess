package com.xiangqi.tools;

import com.xiangqi.ai.MinimaxAI;
import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.PieceColor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * AI baseline benchmark runner:
 * - Average thinking time per difficulty
 * - Win-rate sampling across EASY/MEDIUM/HARD pairings
 */
public class AIBenchmarkMain {
    private static final MinimaxAI.Difficulty[] DIFFS = new MinimaxAI.Difficulty[]{
        MinimaxAI.Difficulty.EASY,
        MinimaxAI.Difficulty.MEDIUM,
        MinimaxAI.Difficulty.HARD
    };

    private static final class MoveStats {
        long moves;
        long totalNanos;
        long maxNanos;

        void add(long nanos) {
            moves++;
            totalNanos += nanos;
            if (nanos > maxNanos) {
                maxNanos = nanos;
            }
        }

        double avgMs() {
            if (moves == 0) {
                return 0.0;
            }
            return (totalNanos / 1_000_000.0) / moves;
        }

        double maxMs() {
            return maxNanos / 1_000_000.0;
        }
    }

    private static final class ScoreStats {
        int wins;
        int losses;
        int draws;

        void win() {
            wins++;
        }

        void loss() {
            losses++;
        }

        void draw() {
            draws++;
        }

        int games() {
            return wins + losses + draws;
        }

        double winRate() {
            int total = games();
            return total == 0 ? 0.0 : (wins * 100.0 / total);
        }
    }

    private static final class PairStats {
        int redWins;
        int blackWins;
        int draws;
        long totalPlies;

        void add(PieceColor winner, int plies) {
            totalPlies += plies;
            if (winner == PieceColor.RED) {
                redWins++;
            } else if (winner == PieceColor.BLACK) {
                blackWins++;
            } else {
                draws++;
            }
        }

        int games() {
            return redWins + blackWins + draws;
        }

        double avgPlies() {
            int g = games();
            return g == 0 ? 0.0 : totalPlies * 1.0 / g;
        }
    }

    private static final class GameResult {
        final PieceColor winner;
        final int plies;
        final String reason;

        GameResult(PieceColor winner, int plies, String reason) {
            this.winner = winner;
            this.plies = plies;
            this.reason = reason;
        }
    }

    public static void main(String[] args) throws Exception {
        int gamesPerPair = intArg(args, "--gamesPerPair", 2);
        int maxPlies = intArg(args, "--maxPlies", 70);
        int openingJitter = intArg(args, "--openingJitter", 2);
        long seed = longArg(args, "--seed", 20260220L);

        Random rng = new Random(seed);
        EnumMap<MinimaxAI.Difficulty, MinimaxAI> aiPool = new EnumMap<MinimaxAI.Difficulty, MinimaxAI>(MinimaxAI.Difficulty.class);
        EnumMap<MinimaxAI.Difficulty, MoveStats> thinkStats = new EnumMap<MinimaxAI.Difficulty, MoveStats>(MinimaxAI.Difficulty.class);
        EnumMap<MinimaxAI.Difficulty, ScoreStats> scoreStats = new EnumMap<MinimaxAI.Difficulty, ScoreStats>(MinimaxAI.Difficulty.class);
        EnumMap<MinimaxAI.Difficulty, EnumMap<MinimaxAI.Difficulty, PairStats>> pairStats =
            new EnumMap<MinimaxAI.Difficulty, EnumMap<MinimaxAI.Difficulty, PairStats>>(MinimaxAI.Difficulty.class);

        for (MinimaxAI.Difficulty d : DIFFS) {
            MinimaxAI ai = new MinimaxAI();
            ai.setDifficulty(d);
            aiPool.put(d, ai);
            thinkStats.put(d, new MoveStats());
            scoreStats.put(d, new ScoreStats());
        }
        for (MinimaxAI.Difficulty red : DIFFS) {
            EnumMap<MinimaxAI.Difficulty, PairStats> row = new EnumMap<MinimaxAI.Difficulty, PairStats>(MinimaxAI.Difficulty.class);
            for (MinimaxAI.Difficulty black : DIFFS) {
                row.put(black, new PairStats());
            }
            pairStats.put(red, row);
        }

        int totalGames = DIFFS.length * DIFFS.length * gamesPerPair;
        int gameNo = 0;
        long benchStart = System.currentTimeMillis();

        for (MinimaxAI.Difficulty redDiff : DIFFS) {
            for (MinimaxAI.Difficulty blackDiff : DIFFS) {
                for (int i = 0; i < gamesPerPair; i++) {
                    gameNo++;
                    GameResult r = runOneGame(
                        aiPool,
                        thinkStats,
                        redDiff,
                        blackDiff,
                        maxPlies,
                        openingJitter,
                        rng
                    );
                    pairStats.get(redDiff).get(blackDiff).add(r.winner, r.plies);
                    updateScore(scoreStats.get(redDiff), scoreStats.get(blackDiff), r.winner);

                    System.out.println(
                        String.format(
                            Locale.ROOT,
                            "[%d/%d] RED=%s vs BLACK=%s -> winner=%s, plies=%d, reason=%s",
                            gameNo, totalGames, redDiff.name(), blackDiff.name(),
                            r.winner == null ? "DRAW" : r.winner.name(), r.plies, r.reason
                        )
                    );
                }
            }
        }

        long elapsedMs = System.currentTimeMillis() - benchStart;
        String report = renderReport(
            gamesPerPair,
            maxPlies,
            openingJitter,
            seed,
            elapsedMs,
            thinkStats,
            scoreStats,
            pairStats
        );

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path outDir = Paths.get("docs", "benchmarks");
        Files.createDirectories(outDir);
        Path out = outDir.resolve("ai-benchmark-" + ts + ".md");
        writeUtf8(out, report);

        System.out.println();
        System.out.println("Benchmark completed.");
        System.out.println("Report: " + out.toString());
    }

    private static GameResult runOneGame(
        EnumMap<MinimaxAI.Difficulty, MinimaxAI> aiPool,
        EnumMap<MinimaxAI.Difficulty, MoveStats> thinkStats,
        MinimaxAI.Difficulty redDiff,
        MinimaxAI.Difficulty blackDiff,
        int maxPlies,
        int openingJitter,
        Random rng
    ) {
        Board board = new Board();
        int jitterPlies = openingJitter <= 0 ? 0 : rng.nextInt(openingJitter + 1);
        applyOpeningJitter(board, jitterPlies, rng);

        while (!board.isGameOver() && board.getMoveCount() < maxPlies) {
            PieceColor turn = board.getCurrentTurn();
            MinimaxAI.Difficulty sideDiff = turn == PieceColor.RED ? redDiff : blackDiff;
            MinimaxAI ai = aiPool.get(sideDiff);
            if (ai == null) {
                return new GameResult(turn.opposite(), board.getMoveCount(), "ai-missing");
            }

            Move move;
            long t0 = System.nanoTime();
            try {
                move = ai.findBestMove(board, turn);
            } catch (Throwable t) {
                return new GameResult(turn.opposite(), board.getMoveCount(), "search-error:" + t.getClass().getSimpleName());
            } finally {
                long cost = System.nanoTime() - t0;
                thinkStats.get(sideDiff).add(cost);
            }

            if (move == null) {
                return new GameResult(turn.opposite(), board.getMoveCount(), "no-legal-move");
            }
            board.movePiece(move);
        }

        if (board.isGameOver()) {
            PieceColor winner = board.getWinner();
            return new GameResult(winner, board.getMoveCount(), "normal-end");
        }
        return new GameResult(null, board.getMoveCount(), "ply-cap");
    }

    private static void applyOpeningJitter(Board board, int jitterPlies, Random rng) {
        for (int i = 0; i < jitterPlies; i++) {
            if (board.isGameOver()) {
                return;
            }
            PieceColor side = board.getCurrentTurn();
            List<Move> moves = board.getAllValidMoves(side);
            if (moves == null || moves.isEmpty()) {
                return;
            }
            Move move = moves.get(rng.nextInt(moves.size()));
            board.movePiece(move);
        }
    }

    private static void updateScore(ScoreStats red, ScoreStats black, PieceColor winner) {
        if (winner == PieceColor.RED) {
            red.win();
            black.loss();
        } else if (winner == PieceColor.BLACK) {
            red.loss();
            black.win();
        } else {
            red.draw();
            black.draw();
        }
    }

    private static String renderReport(
        int gamesPerPair,
        int maxPlies,
        int openingJitter,
        long seed,
        long elapsedMs,
        EnumMap<MinimaxAI.Difficulty, MoveStats> thinkStats,
        EnumMap<MinimaxAI.Difficulty, ScoreStats> scoreStats,
        EnumMap<MinimaxAI.Difficulty, EnumMap<MinimaxAI.Difficulty, PairStats>> pairStats
    ) {
        StringBuilder sb = new StringBuilder(8192);
        LocalDateTime now = LocalDateTime.now();
        sb.append("# AI 实测基准报告\n\n");
        sb.append("- 时间: ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        sb.append("- 配置: gamesPerPair=").append(gamesPerPair)
            .append(", maxPlies=").append(maxPlies)
            .append(", openingJitter=").append(openingJitter)
            .append(", seed=").append(seed).append('\n');
        sb.append("- 总耗时: ").append(String.format(Locale.ROOT, "%.2f", elapsedMs / 1000.0)).append("s\n\n");

        sb.append("## 1) 各难度平均思考时长\n\n");
        sb.append("| 难度 | 采样步数 | 平均(ms/步) | 最长(ms/步) |\n");
        sb.append("|---|---:|---:|---:|\n");
        for (MinimaxAI.Difficulty d : DIFFS) {
            MoveStats m = thinkStats.get(d);
            sb.append('|').append(d.getDisplayName())
                .append('|').append(m.moves)
                .append('|').append(String.format(Locale.ROOT, "%.2f", m.avgMs()))
                .append('|').append(String.format(Locale.ROOT, "%.2f", m.maxMs()))
                .append("|\n");
        }
        sb.append('\n');

        sb.append("## 2) 胜率抽样（按难度聚合）\n\n");
        sb.append("| 难度 | 对局数 | 胜 | 负 | 和 | 胜率 |\n");
        sb.append("|---|---:|---:|---:|---:|---:|\n");
        for (MinimaxAI.Difficulty d : DIFFS) {
            ScoreStats s = scoreStats.get(d);
            sb.append('|').append(d.getDisplayName())
                .append('|').append(s.games())
                .append('|').append(s.wins)
                .append('|').append(s.losses)
                .append('|').append(s.draws)
                .append('|').append(String.format(Locale.ROOT, "%.2f%%", s.winRate()))
                .append("|\n");
        }
        sb.append('\n');

        sb.append("## 3) 对阵明细（红方难度 vs 黑方难度）\n\n");
        sb.append("| 红方 \\\\ 黑方 | 简单 | 中等 | 困难 |\n");
        sb.append("|---|---|---|---|\n");
        for (MinimaxAI.Difficulty red : DIFFS) {
            sb.append('|').append(red.getDisplayName());
            for (MinimaxAI.Difficulty black : DIFFS) {
                PairStats p = pairStats.get(red).get(black);
                String cell = String.format(
                    Locale.ROOT,
                    "R%d/B%d/D%d, avgPly=%.1f",
                    p.redWins, p.blackWins, p.draws, p.avgPlies()
                );
                sb.append('|').append(cell);
            }
            sb.append("|\n");
        }
        sb.append('\n');

        sb.append("## 4) 说明\n\n");
        sb.append("- `maxPlies` 达到上限记为和棋（reason=`ply-cap`）。\n");
        sb.append("- `openingJitter` 用于开局轻微扰动，避免完全同型复盘。\n");
        sb.append("- 该报告用于版本间横向对比，建议固定同一参数重复多轮取均值。\n");
        return sb.toString();
    }

    private static int intArg(String[] args, String key, int defaultValue) {
        String raw = argValue(args, key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private static long longArg(String[] args, String key, long defaultValue) {
        String raw = argValue(args, key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private static String argValue(String[] args, String key) {
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equalsIgnoreCase(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static void writeUtf8(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
