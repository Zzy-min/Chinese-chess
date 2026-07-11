package com.xiangqi.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xiangqi.ai.MinimaxAI;
import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.NotationParser;
import com.xiangqi.model.PieceColor;
import com.xiangqi.online.practice.XiangqiFenParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Batch-solve Xiangqi puzzle FENs with the builtin engine and write reference lines back to seed JSON.
 *
 * Usage:
 *   java -cp target/classes;target/dependency/* com.xiangqi.tools.PuzzleSolutionBatchMain \
 *     [--input path] [--output path] [--limit N] [--offset N] [--plies 6] \
 *     [--difficulty HARD|MEDIUM|EASY] [--threads 4] [--force]
 */
public final class PuzzleSolutionBatchMain {
    private PuzzleSolutionBatchMain() {
    }

    public static void main(String[] args) throws Exception {
        Path input = Paths.get("src/main/resources/online/learn-content.seed.json");
        Path output = input;
        int limit = 0;
        int offset = 0;
        int plies = 6;
        MinimaxAI.Difficulty difficulty = MinimaxAI.Difficulty.HARD;
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        boolean force = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--input".equals(a) && i + 1 < args.length) {
                input = Paths.get(args[++i]);
            } else if ("--output".equals(a) && i + 1 < args.length) {
                output = Paths.get(args[++i]);
            } else if ("--limit".equals(a) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if ("--offset".equals(a) && i + 1 < args.length) {
                offset = Math.max(0, Integer.parseInt(args[++i]));
            } else if ("--plies".equals(a) && i + 1 < args.length) {
                plies = Math.max(1, Integer.parseInt(args[++i]));
            } else if ("--difficulty".equals(a) && i + 1 < args.length) {
                difficulty = MinimaxAI.Difficulty.valueOf(args[++i].trim().toUpperCase());
            } else if ("--threads".equals(a) && i + 1 < args.length) {
                threads = Math.max(1, Integer.parseInt(args[++i]));
            } else if ("--force".equals(a)) {
                force = true;
            }
        }

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Map<String, Object> seed = mapper.readValue(Files.readAllBytes(input), new TypeReference<Map<String, Object>>() {
        });
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> puzzles = (List<Map<String, Object>>) seed.get("puzzles");
        if (puzzles == null) {
            throw new IllegalStateException("seed has no puzzles");
        }

        List<Integer> targets = new ArrayList<Integer>();
        for (int i = 0; i < puzzles.size(); i++) {
            Map<String, Object> p = puzzles.get(i);
            if (!"XIANGQI".equalsIgnoreCase(asString(p.get("gameType")))) {
                continue;
            }
            String fen = asString(p.get("fen")).trim();
            if (fen.isEmpty()) {
                continue;
            }
            if (!force && p.get("solutionLine") instanceof List && !((List<?>) p.get("solutionLine")).isEmpty()) {
                continue;
            }
            targets.add(i);
        }
        if (offset > 0) {
            if (offset >= targets.size()) {
                targets = Collections.emptyList();
            } else {
                targets = new ArrayList<Integer>(targets.subList(offset, targets.size()));
            }
        }
        if (limit > 0 && targets.size() > limit) {
            targets = new ArrayList<Integer>(targets.subList(0, limit));
        }

        final List<Integer> jobIds = new ArrayList<Integer>(targets);
        final int totalJobs = jobIds.size();
        System.out.println("Solving " + totalJobs + " puzzles"
            + " difficulty=" + difficulty
            + " plies=" + plies
            + " threads=" + threads
            + " force=" + force);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        ConcurrentHashMap<Integer, Map<String, Object>> updates = new ConcurrentHashMap<Integer, Map<String, Object>>();
        AtomicInteger done = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        final long started = System.currentTimeMillis();
        final MinimaxAI.Difficulty diff = difficulty;
        final int maxPlies = plies;
        final List<Map<String, Object>> puzzleList = puzzles;

        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (final Integer idx : jobIds) {
            futures.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    Map<String, Object> puzzle = puzzleList.get(idx);
                    String fen = asString(puzzle.get("fen"));
                    try {
                        Map<String, Object> result = solve(fen, maxPlies, diff);
                        updates.put(idx, result);
                        int n = done.incrementAndGet();
                        if (n % 25 == 0 || n == totalJobs) {
                            long elapsed = System.currentTimeMillis() - started;
                            System.out.println("progress " + n + "/" + totalJobs
                                + " failed=" + failed.get()
                                + " elapsedMs=" + elapsed);
                        }
                    } catch (Exception ex) {
                        failed.incrementAndGet();
                        Map<String, Object> err = new LinkedHashMap<String, Object>();
                        err.put("solutionError", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                        updates.put(idx, err);
                    }
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.HOURS);

        int updated = 0;
        for (Map.Entry<Integer, Map<String, Object>> e : updates.entrySet()) {
            Map<String, Object> puzzle = puzzles.get(e.getKey());
            Map<String, Object> patch = e.getValue();
            if (patch.containsKey("solutionError")) {
                puzzle.put("solutionError", patch.get("solutionError"));
                continue;
            }
            puzzle.putAll(patch);
            // Keep human-readable solution text aligned with engine line.
            @SuppressWarnings("unchecked")
            List<String> line = (List<String>) patch.get("solutionLine");
            if (line != null && !line.isEmpty()) {
                List<String> solution = new ArrayList<String>();
                solution.add("引擎参考着法（" + asString(patch.get("solver")) + "，" + maxPlies + " 半步）：");
                for (int i = 0; i < line.size(); i++) {
                    solution.add((i + 1) + ". " + line.get(i));
                }
                String ended = asString(patch.get("endedBy"));
                if (!ended.isEmpty()) {
                    solution.add("终止原因：" + ended);
                }
                solution.add("提示：引擎着法供参考，实战中可有多种正解。");
                puzzle.put("solution", solution);
            }
            updated++;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = seed.get("meta") instanceof Map
            ? (Map<String, Object>) seed.get("meta")
            : new LinkedHashMap<String, Object>();
        meta.put("solutionsUpdatedAt", Instant.now().toString());
        meta.put("solutionsEngine", "builtin-" + difficulty.name());
        meta.put("solutionsPlies", plies);
        meta.put("solutionsUpdatedCount", updated);
        meta.put("solutionsFailedCount", failed.get());
        seed.put("meta", meta);
        seed.put("puzzles", puzzles);

        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(seed);
        // Prefer UTF-8 text with trailing newline for readability.
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.endsWith("\n")) {
            text = text + "\n";
        }
        Files.write(output, text.getBytes(StandardCharsets.UTF_8));
        System.out.println("Wrote " + output.toAbsolutePath()
            + " updated=" + updated
            + " failed=" + failed.get()
            + " elapsedMs=" + (System.currentTimeMillis() - started));
    }

    private static Map<String, Object> solve(String fen, int maxPlies, MinimaxAI.Difficulty difficulty) {
        long t0 = System.currentTimeMillis();
        Board board = XiangqiFenParser.parse(fen);
        MinimaxAI ai = new MinimaxAI();
        ai.setDifficulty(difficulty);

        List<String> line = new ArrayList<String>();
        List<String> uci = new ArrayList<String>();
        String endedBy = "depth";

        for (int ply = 0; ply < maxPlies; ply++) {
            PieceColor side = board.getCurrentTurn();
            List<Move> legal = board.getAllValidMoves(side);
            if (legal == null || legal.isEmpty()) {
                endedBy = board.isInCheck(side) ? "checkmate" : "stalemate";
                break;
            }
            Move best = ai.findBestMove(board, side);
            if (best == null) {
                endedBy = "nomove";
                break;
            }
            String notation;
            try {
                notation = NotationParser.format(best, board);
            } catch (Exception ex) {
                notation = coord(best);
            }
            if (notation == null || notation.trim().isEmpty()) {
                notation = coord(best);
            }
            line.add(notation.trim());
            uci.add(coord(best));
            board.movePiece(best);
            if (board.isCheckmate(board.getCurrentTurn())) {
                endedBy = "checkmate";
                break;
            }
        }

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("solutionLine", line);
        body.put("solutionUci", uci);
        body.put("solver", "builtin-" + difficulty.name().toLowerCase());
        body.put("solutionPlies", line.size());
        body.put("endedBy", endedBy);
        body.put("solvedAt", Instant.now().toString());
        body.put("solveElapsedMs", System.currentTimeMillis() - t0);
        body.remove("solutionError");
        return body;
    }

    private static String coord(Move move) {
        return move.getFromRow() + "," + move.getFromCol() + "-" + move.getToRow() + "," + move.getToCol();
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
