package com.xiangqi.ai;

import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.Piece;
import com.xiangqi.model.PieceColor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * External UCI engine adapter for Xiangqi (Pikafish-first).
 * Keeps a persistent engine process and falls back to built-in logic on failures.
 */
public final class PikafishUciEngine implements XiangqiEngine {
    private static final long UCI_INIT_TIMEOUT_MS = 12000L;
    private static final long READY_TIMEOUT_MS = 10000L;
    private static final long BESTMOVE_TIMEOUT_MS = 16000L;

    private final List<String> command;
    private final String commandText;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private boolean protocolReady;

    public PikafishUciEngine(List<String> command) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("pikafish command is empty");
        }
        this.command = new ArrayList<>(command);
        this.commandText = String.join(" ", this.command);
    }

    @Override
    public synchronized Move findBestMove(Board board, PieceColor aiColor, MinimaxAI.Difficulty difficulty) {
        try {
            ensureProcess();
            applyDifficulty(difficulty);
            sendLine("ucinewgame");
            waitReady();
            sendLine("position fen " + normalizeFenForUci(FenCodec.toFen(board)));
            sendLine("go movetime " + mapMoveTimeMs(difficulty) + " depth " + mapDepth(difficulty));
            String best = waitBestMove(BESTMOVE_TIMEOUT_MS);
            if (best == null || best.isEmpty() || "none".equalsIgnoreCase(best) || "(none)".equalsIgnoreCase(best) || "0000".equals(best)) {
                return null;
            }
            return decodeMove(best, board, aiColor);
        } catch (Exception ignored) {
            closeProcess();
            return null;
        }
    }

    @Override
    public String getEngineId() {
        return "pikafish";
    }

    @Override
    public String getEngineText() {
        return "Pikafish(" + commandText + ")";
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

        sendLine("uci");
        waitForToken("uciok", UCI_INIT_TIMEOUT_MS);
        // Most UCI variant engines accept this option; unsupported option is harmless.
        sendLine("setoption name UCI_Variant value xiangqi");
        waitReady();
        protocolReady = true;
    }

    private void applyDifficulty(MinimaxAI.Difficulty difficulty) throws IOException {
        sendLine("setoption name Threads value 1");
        sendLine("setoption name Hash value 64");
        int level = mapSkillLevel(difficulty);
        sendLine("setoption name Skill Level value " + level);
        waitReady();
    }

    private int mapMoveTimeMs(MinimaxAI.Difficulty difficulty) {
        if (difficulty == MinimaxAI.Difficulty.EASY) {
            return 380;
        }
        if (difficulty == MinimaxAI.Difficulty.MEDIUM) {
            return 980;
        }
        return 2400;
    }

    private int mapDepth(MinimaxAI.Difficulty difficulty) {
        if (difficulty == MinimaxAI.Difficulty.EASY) {
            return 5;
        }
        if (difficulty == MinimaxAI.Difficulty.MEDIUM) {
            return 9;
        }
        return 14;
    }

    private int mapSkillLevel(MinimaxAI.Difficulty difficulty) {
        if (difficulty == MinimaxAI.Difficulty.EASY) {
            return 6;
        }
        if (difficulty == MinimaxAI.Difficulty.MEDIUM) {
            return 14;
        }
        return 20;
    }

    private String normalizeFenForUci(String fen) {
        if (fen == null) {
            return "";
        }
        String trimmed = fen.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length >= 6) {
            return trimmed;
        }
        if (parts.length == 2) {
            return parts[0] + " " + parts[1] + " - - 0 1";
        }
        return trimmed;
    }

    private void waitReady() throws IOException {
        sendLine("isready");
        waitForToken("readyok", READY_TIMEOUT_MS);
    }

    private void waitForToken(String token, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + Math.max(200L, timeoutMs);
        String needle = token.toLowerCase(Locale.ROOT);
        while (System.currentTimeMillis() < deadline) {
            String line = tryReadLine(deadline - System.currentTimeMillis());
            if (line == null) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains(needle)) {
                return;
            }
        }
        throw new IOException("uci timeout waiting token: " + token);
    }

    private String waitBestMove(long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + Math.max(200L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            String line = tryReadLine(deadline - System.currentTimeMillis());
            if (line == null) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT).trim();
            if (!lower.startsWith("bestmove")) {
                continue;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                return parts[1].trim();
            }
            return null;
        }
        return null;
    }

    private String tryReadLine(long remainingMs) throws IOException {
        if (remainingMs <= 0) {
            return null;
        }
        long end = System.currentTimeMillis() + remainingMs;
        while (System.currentTimeMillis() < end) {
            if (process == null || !process.isAlive()) {
                throw new IOException("uci engine exited");
            }
            if (reader.ready()) {
                return reader.readLine();
            }
            sleepQuietly(6L);
        }
        return null;
    }

    private void sendLine(String text) throws IOException {
        writer.write(text);
        writer.newLine();
        writer.flush();
    }

    private Move decodeMove(String token, Board board, PieceColor aiColor) {
        String t = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        if (t.length() < 4) {
            return null;
        }

        Move parsed = parseAlphaNumericMove(t, board, aiColor);
        if (parsed != null) {
            return parsed;
        }
        parsed = parseDigitMove(t, board, aiColor);
        return parsed;
    }

    private Move parseAlphaNumericMove(String t, Board board, PieceColor aiColor) {
        char fFile = t.charAt(0);
        char fRank = t.charAt(1);
        char tFile = t.charAt(2);
        char tRank = t.charAt(3);
        if (!isFileChar(fFile) || !isFileChar(tFile) || !Character.isDigit(fRank) || !Character.isDigit(tRank)) {
            return null;
        }
        int fromCol = fFile - 'a';
        int toCol = tFile - 'a';
        int fromRank = fRank - '0';
        int toRank = tRank - '0';
        return tryCandidateMoves(board, aiColor, fromCol, fromRank, toCol, toRank);
    }

    private Move parseDigitMove(String t, Board board, PieceColor aiColor) {
        if (t.length() < 4) {
            return null;
        }
        char c0 = t.charAt(0);
        char c1 = t.charAt(1);
        char c2 = t.charAt(2);
        char c3 = t.charAt(3);
        if (!Character.isDigit(c0) || !Character.isDigit(c1) || !Character.isDigit(c2) || !Character.isDigit(c3)) {
            return null;
        }
        int fromCol = c0 - '0';
        int fromRank = c1 - '0';
        int toCol = c2 - '0';
        int toRank = c3 - '0';
        return tryCandidateMoves(board, aiColor, fromCol, fromRank, toCol, toRank);
    }

    private Move tryCandidateMoves(Board board, PieceColor aiColor,
                                   int fromColRaw, int fromRankRaw, int toColRaw, int toRankRaw) {
        int[] colsFrom = new int[] {fromColRaw, 8 - fromColRaw};
        int[] colsTo = new int[] {toColRaw, 8 - toColRaw};
        int[] rowsFrom = new int[] {fromRankRaw, 9 - fromRankRaw};
        int[] rowsTo = new int[] {toRankRaw, 9 - toRankRaw};

        for (int cf : colsFrom) {
            for (int ct : colsTo) {
                for (int rf : rowsFrom) {
                    for (int rt : rowsTo) {
                        if (!isInside(rf, cf) || !isInside(rt, ct)) {
                            continue;
                        }
                        Move m = new Move(rf, cf, rt, ct);
                        if (!board.isValidMove(m)) {
                            continue;
                        }
                        Piece p = board.getPiece(rf, cf);
                        if (p == null || p.getColor() != aiColor) {
                            continue;
                        }
                        return m;
                    }
                }
            }
        }
        return null;
    }

    private boolean isInside(int row, int col) {
        return row >= 0 && row < Board.ROWS && col >= 0 && col < Board.COLS;
    }

    private boolean isFileChar(char ch) {
        return ch >= 'a' && ch <= 'i';
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
                writer.write("quit");
                writer.newLine();
                writer.flush();
            } catch (Exception ignored) {
                // ignore
            }
        }
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
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

