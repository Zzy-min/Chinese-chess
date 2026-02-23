package com.xiangqi.model.gomoku;

import com.xiangqi.ai.MinimaxAI;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Piskvork-compatible external Gomoku engine adapter.
 * Typical engines: Rapfi / AlphaGomoku.
 */
public final class PiskvorkGomokuEngine implements GomokuEngine {
    private static final Pattern XY = Pattern.compile("\\s*(\\d+)\\s*,\\s*(\\d+)\\s*.*");
    private static final long IO_TIMEOUT_MS = 9000L;
    private static final long MOVE_TIMEOUT_MS = 12000L;

    private final String engineId;
    private final String displayName;
    private final List<String> command;
    private final String commandText;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private boolean protocolReady = false;

    public PiskvorkGomokuEngine(String engineId, String displayName, List<String> command) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("piskvork command is empty");
        }
        this.engineId = (engineId == null || engineId.trim().isEmpty()) ? "piskvork" : engineId.trim().toLowerCase();
        this.displayName = (displayName == null || displayName.trim().isEmpty()) ? "Piskvork" : displayName.trim();
        this.command = new ArrayList<>(command);
        this.commandText = String.join(" ", this.command);
    }

    @Override
    public synchronized int[] findBestMove(GomokuBoard board, GomokuStone aiStone, MinimaxAI.Difficulty difficulty) {
        try {
            ensureProcess();
            applyDifficultyInfo(difficulty);
            restartBoard();
            sendBoard(board);
            int[] move = readMove(MOVE_TIMEOUT_MS);
            if (move == null) {
                return null;
            }
            int row = move[1];
            int col = move[0];
            if (!board.isInside(row, col)) {
                return null;
            }
            return new int[] {row, col};
        } catch (Exception e) {
            closeProcess();
            return null;
        }
    }

    @Override
    public String getEngineId() {
        return engineId;
    }

    @Override
    public String getEngineText() {
        return displayName + "(" + commandText + ")";
    }

    @Override
    public synchronized void close() {
        closeProcess();
    }

    private void ensureProcess() throws IOException {
        if (process != null && process.isAlive() && protocolReady) {
            return;
        }
        closeProcess();
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        process = pb.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        protocolReady = false;

        sendLine("START " + GomokuBoard.SIZE);
        waitForOk(IO_TIMEOUT_MS);
        protocolReady = true;
    }

    private void restartBoard() throws IOException {
        sendLine("RESTART");
        waitForOk(IO_TIMEOUT_MS);
    }

    private void applyDifficultyInfo(MinimaxAI.Difficulty difficulty) throws IOException {
        int timeoutTurnMs;
        int maxDepth;
        if (difficulty == MinimaxAI.Difficulty.EASY) {
            timeoutTurnMs = 350;
            maxDepth = 6;
        } else if (difficulty == MinimaxAI.Difficulty.MEDIUM) {
            timeoutTurnMs = 900;
            maxDepth = 9;
        } else {
            timeoutTurnMs = 1800;
            maxDepth = 13;
        }
        // Piskvork INFO keys; unsupported keys are ignored by most engines.
        sendLine("INFO timeout_turn " + timeoutTurnMs);
        sendLine("INFO max_depth " + maxDepth);
        sendLine("INFO time_left 300000");
        sendLine("INFO game_type 1");
        sendLine("INFO rule 0");
    }

    private void sendBoard(GomokuBoard board) throws IOException {
        sendLine("BOARD");
        List<GomokuMove> moves = board.getMoveHistory();
        for (GomokuMove move : moves) {
            int x = move.getCol();
            int y = move.getRow();
            int side = move.getStone() == GomokuStone.BLACK ? 1 : 2;
            sendLine(x + "," + y + "," + side);
        }
        sendLine("DONE");
    }

    private int[] readMove(long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + Math.max(100L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            String line = tryReadLine(deadline - System.currentTimeMillis());
            if (line == null) {
                continue;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher matcher = XY.matcher(line);
            if (matcher.matches()) {
                int x = parseIntSafe(matcher.group(1), -1);
                int y = parseIntSafe(matcher.group(2), -1);
                if (x >= 0 && y >= 0) {
                    return new int[] {x, y};
                }
            }
        }
        return null;
    }

    private void waitForOk(long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + Math.max(100L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            String line = tryReadLine(deadline - System.currentTimeMillis());
            if (line == null) {
                continue;
            }
            String upper = line.trim().toUpperCase();
            if ("OK".equals(upper) || upper.startsWith("OK ")) {
                return;
            }
            if (upper.startsWith("ERROR")) {
                throw new IOException("engine error: " + line);
            }
        }
        throw new IOException("engine timeout waiting OK");
    }

    private String tryReadLine(long remainingMs) throws IOException {
        if (remainingMs <= 0) {
            return null;
        }
        long end = System.currentTimeMillis() + remainingMs;
        while (System.currentTimeMillis() < end) {
            if (process == null || !process.isAlive()) {
                throw new IOException("engine process exited");
            }
            if (reader.ready()) {
                return reader.readLine();
            }
            sleepQuietly(5L);
        }
        return null;
    }

    private void sendLine(String line) throws IOException {
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    private int parseIntSafe(String raw, int defaultValue) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeProcess() {
        if (writer != null) {
            try {
                writer.write("END");
                writer.newLine();
                writer.flush();
            } catch (Exception ignored) {
                // ignore
            }
        }
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(200, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        process = null;
        writer = null;
        reader = null;
        protocolReady = false;
    }
}
