package com.xiangqi.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xiangqi.controller.EndgameLoader;
import com.xiangqi.ai.MinimaxAI;
import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.Piece;
import com.xiangqi.model.PieceColor;
import com.xiangqi.model.TacticDetector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class WebXiangqiServer {
    private static final long MIN_MOVE_INTERVAL_MS = 120L;
    private static final WebXiangqiServer INSTANCE = new WebXiangqiServer();
    private static final String SID_COOKIE = "XQSID";
    private static final int HTTP_THREADS = Math.max(8, Runtime.getRuntime().availableProcessors() * 4);
    private static final int AI_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final ExecutorService HTTP_EXECUTOR = Executors.newFixedThreadPool(HTTP_THREADS, namedFactory("xq-http-"));
    private static final ExecutorService AI_EXECUTOR = Executors.newFixedThreadPool(AI_THREADS, namedFactory("xq-ai-"));

    private HttpServer server;
    private URI uri;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private WebXiangqiServer() {
    }

    public static WebXiangqiServer getInstance() {
        return INSTANCE;
    }

    public synchronized URI start() throws IOException {
        return start(0);
    }

    public synchronized URI start(int preferredPort) throws IOException {
        return start("127.0.0.1", preferredPort);
    }

    public synchronized URI start(String bindHost, int preferredPort) throws IOException {
        if (server != null) {
            return uri;
        }

        int bindPort = preferredPort > 0 ? preferredPort : 0;
        String host = (bindHost == null || bindHost.trim().isEmpty()) ? "127.0.0.1" : bindHost.trim();
        server = HttpServer.create(new InetSocketAddress(host, bindPort), 0);
        server.createContext("/", this::handleIndex);
        server.createContext("/api/state", this::handleState);
        server.createContext("/api/new", this::handleNewGame);
        server.createContext("/api/endgame", this::handleEndgame);
        server.createContext("/api/click", this::handleClick);
        server.createContext("/api/undo", this::handleUndo);
        server.createContext("/api/surrender", this::handleSurrender);
        server.createContext("/api/draw", this::handleDraw);
        server.createContext("/api/review/start", this::handleReviewStart);
        server.createContext("/api/review/exit", this::handleReviewExit);
        server.createContext("/api/review/prev", this::handleReviewPrev);
        server.createContext("/api/review/next", this::handleReviewNext);
        server.createContext("/api/perf", this::handlePerf);
        server.createContext("/api/perf/reset", this::handlePerfReset);
        server.createContext("/api/perf/event", this::handlePerfEvent);
        server.createContext("/assets/audio", this::handleAudioAsset);
        server.setExecutor(HTTP_EXECUTOR);
        server.start();

        String uriHost = "0.0.0.0".equals(host) ? "127.0.0.1" : host;
        uri = URI.create("http://" + uriHost + ":" + server.getAddress().getPort() + "/");
        return uri;
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        getSession(exchange);
        sendText(exchange, 200, html(), "text/html; charset=UTF-8");
    }

    private void handleState(HttpExchange exchange) throws IOException {
        withSession(exchange, "state", session -> {
            session.tick();
            return session.toJson();
        });
    }

    private void handleNewGame(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String mode = query.getOrDefault("mode", "pvp");
        String difficulty = query.getOrDefault("difficulty", "MEDIUM");
        boolean humanFirst = !"false".equalsIgnoreCase(query.getOrDefault("humanFirst", "true"));
        withSession(exchange, "new", session -> {
            session.reset("pvc".equalsIgnoreCase(mode), parseDifficulty(difficulty), humanFirst);
            return session.toJson();
        });
    }

    private void handleEndgame(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String name = query.getOrDefault("name", "七星聚会");
        String mode = query.getOrDefault("mode", "pvp");
        String difficulty = query.getOrDefault("difficulty", "MEDIUM");
        boolean humanFirst = !"false".equalsIgnoreCase(query.getOrDefault("humanFirst", "true"));
        withSession(exchange, "endgame", session -> {
            session.loadEndgame(name, "pvc".equalsIgnoreCase(mode), parseDifficulty(difficulty), humanFirst);
            return session.toJson();
        });
    }
    private void handleClick(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        int row = parseInt(query.get("row"), -1);
        int col = parseInt(query.get("col"), -1);
        withSession(exchange, "click", session -> {
            session.click(row, col);
            return session.toJson();
        });
    }

    private void handleUndo(HttpExchange exchange) throws IOException {
        withSession(exchange, "undo", session -> {
            session.undo();
            return session.toJson();
        });
    }


    private void handleSurrender(HttpExchange exchange) throws IOException {
        withSession(exchange, "surrender", session -> {
            session.surrender();
            return session.toJson();
        });
    }

    private void handleDraw(HttpExchange exchange) throws IOException {
        withSession(exchange, "draw", session -> {
            session.draw();
            return session.toJson();
        });
    }

    private void handleReviewStart(HttpExchange exchange) throws IOException {
        withSession(exchange, "review_start", session -> {
            session.startReview();
            return session.toJson();
        });
    }

    private void handleReviewExit(HttpExchange exchange) throws IOException {
        withSession(exchange, "review_exit", session -> {
            session.exitReview();
            return session.toJson();
        });
    }

    private void handleReviewPrev(HttpExchange exchange) throws IOException {
        withSession(exchange, "review_prev", session -> {
            session.reviewPrev();
            return session.toJson();
        });
    }

    private void handleReviewNext(HttpExchange exchange) throws IOException {
        withSession(exchange, "review_next", session -> {
            session.reviewNext();
            return session.toJson();
        });
    }

    private void handlePerf(HttpExchange exchange) throws IOException {
        withSession(exchange, "perf", Session::perfJson);
    }

    private void handlePerfReset(HttpExchange exchange) throws IOException {
        withSession(exchange, "perf_reset", session -> {
            session.resetPerf();
            return session.perfJson();
        });
    }

    private void handlePerfEvent(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String type = query.getOrDefault("type", "client");
        long cost = parseLong(query.get("cost"), -1L);
        withSession(exchange, "perf_event", session -> {
            session.recordPerfEvent(type, cost);
            return session.perfJson();
        });
    }

    private interface SessionAction {
        String run(Session session);
    }

    private void withSession(HttpExchange exchange, String eventType, SessionAction action) throws IOException {
        long t0 = System.currentTimeMillis();
        Session session = getSession(exchange);
        String body;
        synchronized (session) {
            body = action.run(session);
            session.recordPerfEvent(eventType, System.currentTimeMillis() - t0);
        }
        sendText(exchange, 200, body, "application/json; charset=UTF-8");
    }

    private void handleAudioAsset(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path == null) {
            sendText(exchange, 404, "Not Found", "text/plain");
            return;
        }

        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String resourcePath;
        if ("move.wav".equalsIgnoreCase(fileName)) {
            resourcePath = "/audio/move.wav";
        } else if ("mate.wav".equalsIgnoreCase(fileName)) {
            resourcePath = "/audio/mate.wav";
        } else {
            sendText(exchange, 404, "Not Found", "text/plain");
            return;
        }

        try (InputStream is = WebXiangqiServer.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                sendText(exchange, 404, "Not Found", "text/plain");
                return;
            }
            byte[] bytes = readAllBytes(is);
            sendBytes(exchange, 200, bytes, "audio/wav");
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = inputStream.read(buffer)) >= 0) {
            outputStream.write(buffer, 0, n);
        }
        return outputStream.toByteArray();
    }

    private MinimaxAI.Difficulty parseDifficulty(String raw) {
        try {
            return MinimaxAI.Difficulty.valueOf(raw.toUpperCase());
        } catch (Exception e) {
            return MinimaxAI.Difficulty.MEDIUM;
        }
    }

    private int parseInt(String raw, int defaultValue) {
        try {
        return Integer.parseInt(raw);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long parseLong(String raw, long defaultValue) {
        try {
            return Long.parseLong(raw);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static ThreadFactory namedFactory(final String prefix) {
        return new ThreadFactory() {
            private int idx = 0;
            @Override
            public synchronized Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + (++idx));
                t.setDaemon(true);
                return t;
            }
        };
    }

    private Session getSession(HttpExchange exchange) {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String sid = query.get("sid");
        if (!isValidSid(sid)) {
            sid = readSidFromCookie(exchange);
        }
        if (!isValidSid(sid)) {
            sid = UUID.randomUUID().toString().replace("-", "");
            exchange.getResponseHeaders().add("Set-Cookie", SID_COOKIE + "=" + sid + "; Path=/; HttpOnly; SameSite=Lax");
        }
        return sessions.computeIfAbsent(sid, key -> new Session());
    }

    private String readSidFromCookie(HttpExchange exchange) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) {
            return null;
        }
        for (String header : cookieHeaders) {
            if (header == null || header.trim().isEmpty()) {
                continue;
            }
            String[] cookies = header.split(";");
            for (String cookie : cookies) {
                String item = cookie.trim();
                if (item.startsWith(SID_COOKIE + "=")) {
                    return item.substring((SID_COOKIE + "=").length()).trim();
                }
            }
        }
        return null;
    }

    private boolean isValidSid(String sid) {
        if (sid == null) {
            return false;
        }
        String s = sid.trim();
        return s.matches("[A-Za-z0-9_-]{16,64}");
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return map;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            try {
                String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name());
                String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()) : "";
                map.put(key, value);
            } catch (Exception ignored) {
                map.put(kv[0], kv.length > 1 ? kv[1] : "");
            }
        }
        return map;
    }

    private void sendText(HttpExchange exchange, int code, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        sendBytes(exchange, code, bytes, contentType);
    }

    private void sendBytes(HttpExchange exchange, int code, byte[] bytes, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        exchange.getResponseHeaders().set("Expires", "0");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static final class Session {
        private static final int PERF_RING_CAP = 240;
        private static final int PERF_EVENT_CAP = 120;
        private Board board = new Board();
        private boolean pvcMode;
        private MinimaxAI.Difficulty difficulty = MinimaxAI.Difficulty.MEDIUM;
        private int selectedRow = -1;
        private int selectedCol = -1;
        private boolean reviewMode;
        private int reviewMoveIndex;
        private String tacticText = "";
        private String currentEndgame = "标准开局";
        private long tacticUntil = 0L;
        private long lastMoveAt = 0L;
        private boolean aiPending = false;
        private long aiDueAt = 0L;
        private long aiEpoch = 0L;
        private CompletableFuture<Move> aiFuture = null;
        private long aiFutureEpoch = -1L;
        private PieceColor aiFutureColor = null;
        private PieceColor surrenderedColor = null;
        private PieceColor trackedTurn = null;
        private long turnStartedAt = System.currentTimeMillis();
        private int redCompletedMoves = 0;
        private int blackCompletedMoves = 0;
        private boolean started = false;
        private PieceColor timeoutLoser = null;
        private String timeoutType = null;
        private PieceColor pvcHumanColor = PieceColor.RED;
        private long redTotalRemainingMs = 10 * 60 * 1000L;
        private long blackTotalRemainingMs = 10 * 60 * 1000L;
        private long lastTickAt = System.currentTimeMillis();
        private boolean pvpClockEnabled = false;
        private boolean agreedDraw = false;
        private boolean autoDraw = false;
        private String drawReason = "";
        private int noCaptureHalfMoves = 0;
        private final Map<String, Integer> positionCount = new HashMap<>();
        private long tacticSeq = 0L;
        private long responseSeq = 0L;
        private long perfCount = 0L;
        private long perfTotalMs = 0L;
        private long perfMaxMs = 0L;
        private final ArrayDeque<Long> perfCosts = new ArrayDeque<>();
        private final ArrayDeque<PerfEvent> perfEvents = new ArrayDeque<>();

        private static final class PerfEvent {
            private final long at;
            private final String type;
            private final long costMs;

            private PerfEvent(long at, String type, long costMs) {
                this.at = at;
                this.type = type;
                this.costMs = costMs;
            }
        }

        void reset(boolean pvcMode, MinimaxAI.Difficulty difficulty, boolean humanFirst) {
            this.board = new Board();
            this.pvcMode = pvcMode;
            this.difficulty = difficulty;
            this.pvcHumanColor = humanFirst ? PieceColor.RED : PieceColor.BLACK;
            this.selectedRow = -1;
            this.selectedCol = -1;
            this.reviewMode = false;
            this.reviewMoveIndex = 0;
            this.tacticText = "";
            this.tacticUntil = 0L;
            this.currentEndgame = "标准开局";
            this.lastMoveAt = 0L;
            this.aiPending = false;
            this.aiDueAt = 0L;
            this.aiEpoch++;
            this.aiFuture = null;
            this.aiFutureEpoch = -1L;
            this.aiFutureColor = null;
            this.surrenderedColor = null;
            this.trackedTurn = board.getCurrentTurn();
            this.turnStartedAt = System.currentTimeMillis();
            this.redCompletedMoves = 0;
            this.blackCompletedMoves = 0;
            this.started = true;
            this.timeoutLoser = null;
            this.timeoutType = null;
            this.redTotalRemainingMs = 10 * 60 * 1000L;
            this.blackTotalRemainingMs = 10 * 60 * 1000L;
            this.lastTickAt = System.currentTimeMillis();
            this.pvpClockEnabled = false;
            this.agreedDraw = false;
            this.autoDraw = false;
            this.drawReason = "";
            this.noCaptureHalfMoves = 0;
            this.positionCount.clear();
            initDrawTracking();

            if (pvcMode && board.getCurrentTurn() != pvcHumanColor && !board.isGameOver()) {
                aiPending = true;
                aiDueAt = System.currentTimeMillis() + getAiMoveIntervalMs();
            }
        }

        void loadEndgame(String endgameName, boolean pvcMode, MinimaxAI.Difficulty difficulty, boolean humanFirst) {
            this.board = new Board();
            EndgameLoader.loadEndgame(this.board, endgameName);
            this.pvcMode = pvcMode;
            this.difficulty = difficulty;
            this.pvcHumanColor = humanFirst ? PieceColor.RED : PieceColor.BLACK;
            this.selectedRow = -1;
            this.selectedCol = -1;
            this.reviewMode = false;
            this.reviewMoveIndex = 0;
            this.tacticText = "";
            this.tacticUntil = 0L;
            this.currentEndgame = endgameName;
            this.lastMoveAt = 0L;
            this.aiPending = false;
            this.aiDueAt = 0L;
            this.aiEpoch++;
            this.aiFuture = null;
            this.aiFutureEpoch = -1L;
            this.aiFutureColor = null;
            this.surrenderedColor = null;
            this.trackedTurn = board.getCurrentTurn();
            this.turnStartedAt = System.currentTimeMillis();
            this.redCompletedMoves = 0;
            this.blackCompletedMoves = 0;
            this.started = true;
            this.timeoutLoser = null;
            this.timeoutType = null;
            this.redTotalRemainingMs = 10 * 60 * 1000L;
            this.blackTotalRemainingMs = 10 * 60 * 1000L;
            this.lastTickAt = System.currentTimeMillis();
            this.pvpClockEnabled = false;
            this.agreedDraw = false;
            this.autoDraw = false;
            this.drawReason = "";
            this.noCaptureHalfMoves = 0;
            this.positionCount.clear();
            initDrawTracking();

            if (pvcMode && !board.isGameOver() && board.getCurrentTurn() != pvcHumanColor) {
                aiPending = true;
                aiDueAt = System.currentTimeMillis() + getAiMoveIntervalMs();
            }
        }
        void startReview() {
            if (!started) {
                return;
            }
            if (board.canUndo()) {
                reviewMode = true;
                reviewMoveIndex = 0;
                selectedRow = -1;
                selectedCol = -1;
            }
        }

        void exitReview() {
            reviewMode = false;
            reviewMoveIndex = 0;
            selectedRow = -1;
            selectedCol = -1;
        }

        void reviewPrev() {
            if (reviewMode && reviewMoveIndex > 0) {
                reviewMoveIndex--;
            }
        }

        void reviewNext() {
            if (reviewMode && reviewMoveIndex < board.getMoveCount()) {
                reviewMoveIndex++;
            }
        }

        void click(int row, int col) {
            if (!started || reviewMode) {
                return;
            }
            tick();
            if (row < 0 || row >= Board.ROWS || col < 0 || col >= Board.COLS || isGameOver()) {
                selectedRow = -1;
                selectedCol = -1;
                return;
            }
            if (pvcMode && board.getCurrentTurn() != pvcHumanColor) {
                return;
            }

            Piece clickedPiece = board.getPiece(row, col);
            if (selectedRow == -1) {
                if (clickedPiece != null && clickedPiece.getColor() == board.getCurrentTurn()) {
                    if (!pvcMode || clickedPiece.getColor() == pvcHumanColor) {
                        selectedRow = row;
                        selectedCol = col;
                    }
                }
                return;
            }

            Move move = new Move(selectedRow, selectedCol, row, col);
            if (board.isValidMove(move)) {
                if (!canMoveNow()) {
                    return;
                }
                board.movePiece(move);
                markMove();
                updateAutoDrawStateAfterMove();
                aiEpoch++;
                selectedRow = -1;
                selectedCol = -1;
                updateTacticFlash();

                if (pvcMode && !isGameOver() && !board.isGameOver() && board.getCurrentTurn() != pvcHumanColor) {
                    aiPending = true;
                    aiDueAt = System.currentTimeMillis() + getAiMoveIntervalMs();
                }
            } else if (clickedPiece != null && clickedPiece.getColor() == board.getCurrentTurn()) {
                selectedRow = row;
                selectedCol = col;
            } else {
                selectedRow = -1;
                selectedCol = -1;
            }
        }

        void undo() {
            if (!started || reviewMode || !board.canUndo() || isGameOver()) {
                return;
            }
            if (pvcMode) {
                board.undoMove();
                if (board.canUndo()) {
                    board.undoMove();
                }
            } else {
                board.undoMove();
            }
            selectedRow = -1;
            selectedCol = -1;
            aiEpoch++;
            aiFuture = null;
            aiFutureEpoch = -1L;
            aiFutureColor = null;
            agreedDraw = false;
            autoDraw = false;
            drawReason = "";
            initDrawTracking();
        }



        void surrender() {
            if (!started || isGameOver() || reviewMode) {
                return;
            }
            surrenderedColor = board.getCurrentTurn();
            aiPending = false;
            aiDueAt = 0L;
            aiEpoch++;
            aiFuture = null;
            aiFutureEpoch = -1L;
            aiFutureColor = null;
            selectedRow = -1;
            selectedCol = -1;
        }

        void draw() {
            if (!started || isGameOver() || reviewMode || pvcMode) {
                return;
            }
            agreedDraw = true;
            drawReason = "双方议和，和棋";
            aiPending = false;
            aiDueAt = 0L;
            aiEpoch++;
            aiFuture = null;
            aiFutureEpoch = -1L;
            aiFutureColor = null;
            selectedRow = -1;
            selectedCol = -1;
        }

        private boolean isGameOver() {
            return timeoutLoser != null || surrenderedColor != null || agreedDraw || autoDraw || board.isGameOver();
        }

        private String getGameResult() {
            if (!started) {
                return "点击“新开一局”开始对局";
            }
            if (timeoutLoser == PieceColor.RED) {
                return "TOTAL".equals(timeoutType) ? "红方总时超时！黑方获胜" : "红方步时超限！黑方获胜";
            }
            if (timeoutLoser == PieceColor.BLACK) {
                return "TOTAL".equals(timeoutType) ? "黑方总时超时！红方获胜" : "黑方步时超限！红方获胜";
            }
            if (surrenderedColor == PieceColor.RED) {
                return "红方认输！黑方获胜";
            }
            if (surrenderedColor == PieceColor.BLACK) {
                return "黑方认输！红方获胜";
            }
            if (agreedDraw || autoDraw) {
                return (drawReason == null || drawReason.isEmpty()) ? "和棋" : drawReason;
            }
            return board.getGameResult();
        }
        private void updateTurnTracking() {
            PieceColor current = board.getCurrentTurn();
            if (trackedTurn == null) {
                trackedTurn = current;
                turnStartedAt = System.currentTimeMillis();
                return;
            }
            if (current != trackedTurn) {
                if (trackedTurn == PieceColor.RED) {
                    redCompletedMoves++;
                } else {
                    blackCompletedMoves++;
                }
                trackedTurn = current;
                turnStartedAt = System.currentTimeMillis();
            }
        }

        private int getCurrentStepLimitSec(PieceColor color) {
            int completed = color == PieceColor.RED ? redCompletedMoves : blackCompletedMoves;
            return completed < 3 ? 30 : 60;
        }

        private int getCurrentStepRemainingSec() {
            if (!started || reviewMode || isGameOver()) {
                return -1;
            }
            if (!pvcMode && !pvpClockEnabled) {
                return -1;
            }
            updateTurnTracking();
            int limit = getCurrentStepLimitSec(board.getCurrentTurn());
            long elapsedMs = Math.max(0L, System.currentTimeMillis() - turnStartedAt);
            long remainMs = Math.max(0L, limit * 1000L - elapsedMs);
            return (int) ((remainMs + 999L) / 1000L);
        }

        void tick() {
            if (!started) {
                return;
            }
            long now = System.currentTimeMillis();
            long delta = Math.max(0L, now - lastTickAt);
            lastTickAt = now;

            updateTurnTracking();

            if (!pvcMode && pvpClockEnabled && !reviewMode && !isGameOver()) {
                if (board.getCurrentTurn() == PieceColor.RED) {
                    redTotalRemainingMs = Math.max(0L, redTotalRemainingMs - delta);
                    if (redTotalRemainingMs <= 0L) {
                        timeoutLoser = PieceColor.RED;
                        timeoutType = "TOTAL";
                    }
                } else {
                    blackTotalRemainingMs = Math.max(0L, blackTotalRemainingMs - delta);
                    if (blackTotalRemainingMs <= 0L) {
                        timeoutLoser = PieceColor.BLACK;
                        timeoutType = "TOTAL";
                    }
                }

                if (!isGameOver() && getCurrentStepRemainingSec() <= 0) {
                    timeoutLoser = board.getCurrentTurn();
                    timeoutType = "STEP";
                }

                if (isGameOver()) {
                    selectedRow = -1;
                    selectedCol = -1;
                    aiPending = false;
                    aiDueAt = 0L;
                    return;
                }
            }

            if (!aiPending || reviewMode || isGameOver() || !pvcMode) {
                return;
            }
            if (System.currentTimeMillis() < aiDueAt) {
                return;
            }
            if (board.getCurrentTurn() == pvcHumanColor) {
                aiPending = false;
                return;
            }

            if (aiFuture != null) {
                if (!aiFuture.isDone()) {
                    return;
                }
                Move aiMove = null;
                try {
                    aiMove = aiFuture.getNow(null);
                } catch (Exception ignored) {
                    aiMove = null;
                }
                if (aiFutureEpoch == aiEpoch && aiFutureColor == board.getCurrentTurn() && aiMove != null && board.isValidMove(aiMove)) {
                    board.movePiece(aiMove);
                    markMove();
                    updateAutoDrawStateAfterMove();
                    updateTacticFlash();
                    aiEpoch++;
                }
                aiFuture = null;
                aiFutureEpoch = -1L;
                aiFutureColor = null;
                aiPending = false;
                return;
            }

            final PieceColor aiColor = pvcHumanColor.opposite();
            final Board snapshot = new Board(board);
            final MinimaxAI.Difficulty currentDifficulty = this.difficulty;
            final long launchEpoch = aiEpoch;
            aiFutureEpoch = launchEpoch;
            aiFutureColor = aiColor;
            aiFuture = CompletableFuture.supplyAsync(() -> {
                MinimaxAI worker = new MinimaxAI();
                worker.setDifficulty(currentDifficulty);
                return worker.findBestMove(snapshot, aiColor);
            }, AI_EXECUTOR);
        }

        private boolean canMoveNow() {
            // 双人同屏不做人为最短步间隔，提升手感
            if (!pvcMode) {
                return true;
            }
            return System.currentTimeMillis() - lastMoveAt >= getAiMoveIntervalMs();
        }

        private long getAiMoveIntervalMs() {
            if (!pvcMode) {
                return 0L;
            }
            if (difficulty == MinimaxAI.Difficulty.EASY) {
                return 45L;
            }
            if (difficulty == MinimaxAI.Difficulty.MEDIUM) {
                return 75L;
            }
            return MIN_MOVE_INTERVAL_MS;
        }

        private void markMove() {
            lastMoveAt = System.currentTimeMillis();
        }

        private void updateTacticFlash() {
            String t = TacticDetector.detect(board);
            if (t != null && !t.isEmpty()) {
                tacticText = t;
                tacticUntil = System.currentTimeMillis() + 500;
                tacticSeq++;
            }
        }

        private void initDrawTracking() {
            noCaptureHalfMoves = 0;
            positionCount.clear();
            positionCount.put(buildPositionKey(board), 1);
        }

        private void updateAutoDrawStateAfterMove() {
            Move lastMove = board.getLastMove();
            if (lastMove == null) {
                return;
            }
            if (lastMove.getCapturedPiece() == null) {
                noCaptureHalfMoves++;
            } else {
                noCaptureHalfMoves = 0;
            }

            String key = buildPositionKey(board);
            int seen = positionCount.getOrDefault(key, 0) + 1;
            positionCount.put(key, seen);

            if (!pvcMode || isGameOver()) {
                return;
            }
            if (noCaptureHalfMoves >= 120) {
                autoDraw = true;
                drawReason = "自动判和：连续60回合无吃子";
                aiPending = false;
                aiDueAt = 0L;
                selectedRow = -1;
                selectedCol = -1;
                return;
            }
            if (seen >= 3) {
                autoDraw = true;
                drawReason = "自动判和：三次重复局面";
                aiPending = false;
                aiDueAt = 0L;
                selectedRow = -1;
                selectedCol = -1;
            }
        }

        private String buildPositionKey(Board board) {
            StringBuilder sb = new StringBuilder(256);
            sb.append(board.getCurrentTurn().name()).append('|');
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    Piece p = board.getPiece(r, c);
                    if (p == null) {
                        sb.append('.');
                    } else {
                        sb.append(p.getColor() == PieceColor.RED ? 'R' : 'B');
                        sb.append(p.getType().ordinal());
                    }
                }
                sb.append('/');
            }
            return sb.toString();
        }

        String toJson() {
            Board boardToDraw = reviewMode ? board.getBoardAtMove(reviewMoveIndex) : board;
            if (boardToDraw == null) {
                boardToDraw = board;
            }

            StringBuilder sb = new StringBuilder(4096);
            sb.append('{');
            sb.append("\"seq\":").append(++responseSeq).append(',');
            sb.append("\"started\":").append(started).append(',');
            sb.append("\"mode\":\"").append(pvcMode ? "PVC" : "PVP").append("\",");
            sb.append("\"difficulty\":\"").append(difficulty.name()).append("\",");
            sb.append("\"difficultyText\":\"").append(difficulty.getDisplayName()).append("\",");
            sb.append("\"pvcHumanColor\":\"").append(pvcHumanColor).append("\",");
            sb.append("\"endgame\":\"").append(escape(currentEndgame)).append("\",");
            sb.append("\"currentTurn\":\"").append(boardToDraw.getCurrentTurn()).append("\",");
            sb.append("\"gameOver\":").append(isGameOver()).append(',');
            sb.append("\"canDraw\":").append(started && !reviewMode && !isGameOver() && !pvcMode).append(',');
            sb.append("\"result\":\"").append(escape(getGameResult())).append("\",");
            sb.append("\"drawReason\":\"").append(escape((agreedDraw || autoDraw) ? drawReason : "")).append("\",");
            sb.append("\"selectedRow\":").append(selectedRow).append(',');
            sb.append("\"selectedCol\":").append(selectedCol).append(',');
            sb.append("\"canReview\":").append(board.canUndo()).append(',');
            sb.append("\"reviewMode\":").append(reviewMode).append(',');
            sb.append("\"reviewMoveIndex\":").append(reviewMoveIndex).append(',');
            sb.append("\"reviewMaxMove\":").append(board.getMoveCount()).append(',');
            sb.append("\"stepRemainSec\":").append(getCurrentStepRemainingSec()).append(',');
            long redTotalSec = (started && (pvcMode || pvpClockEnabled)) ? (redTotalRemainingMs + 999L) / 1000L : -1;
            long blackTotalSec = (started && (pvcMode || pvpClockEnabled)) ? (blackTotalRemainingMs + 999L) / 1000L : -1;
            sb.append("\"redTotalSec\":").append(redTotalSec).append(',');
            sb.append("\"blackTotalSec\":").append(blackTotalSec).append(',');
            sb.append("\"tacticText\":\"").append(escape(tacticText)).append("\",");
            sb.append("\"tacticSeq\":").append(tacticSeq).append(',');
            appendRecentMoves(sb, boardToDraw);
            sb.append(',');
            sb.append("\"board\":[");

            for (int row = 0; row < Board.ROWS; row++) {
                if (row > 0) {
                    sb.append(',');
                }
                sb.append('[');
                for (int col = 0; col < Board.COLS; col++) {
                    if (col > 0) {
                        sb.append(',');
                    }
                    Piece piece = boardToDraw.getPiece(row, col);
                    if (piece == null) {
                        sb.append("null");
                    } else {
                        sb.append('{');
                        sb.append("\"name\":\"").append(escape(piece.getType().getDisplayName())).append("\",");
                        sb.append("\"color\":\"").append(piece.getColor()).append("\"");
                        sb.append('}');
                    }
                }
                sb.append(']');
            }
            sb.append(']');
            sb.append('}');
            return sb.toString();
        }

        void recordPerfEvent(String type, long costMs) {
            long now = System.currentTimeMillis();
            long cost = Math.max(0L, costMs);
            String eventType = (type == null || type.isEmpty()) ? "unknown" : type;
            perfCount++;
            perfTotalMs += cost;
            perfMaxMs = Math.max(perfMaxMs, cost);
            perfCosts.addLast(cost);
            if (perfCosts.size() > PERF_RING_CAP) {
                perfCosts.removeFirst();
            }
            perfEvents.addLast(new PerfEvent(now, eventType, cost));
            if (perfEvents.size() > PERF_EVENT_CAP) {
                perfEvents.removeFirst();
            }
        }

        void resetPerf() {
            perfCount = 0L;
            perfTotalMs = 0L;
            perfMaxMs = 0L;
            perfCosts.clear();
            perfEvents.clear();
        }

        String perfJson() {
            List<Long> costs = new ArrayList<>(perfCosts);
            costs.sort(Long::compareTo);
            long p50 = percentile(costs, 0.50);
            long p95 = percentile(costs, 0.95);
            long p99 = percentile(costs, 0.99);
            long avg = perfCount == 0 ? 0 : perfTotalMs / perfCount;
            StringBuilder sb = new StringBuilder(2048);
            sb.append('{');
            sb.append("\"count\":").append(perfCount).append(',');
            sb.append("\"avgMs\":").append(avg).append(',');
            sb.append("\"maxMs\":").append(perfMaxMs).append(',');
            sb.append("\"p50Ms\":").append(p50).append(',');
            sb.append("\"p95Ms\":").append(p95).append(',');
            sb.append("\"p99Ms\":").append(p99).append(',');
            sb.append("\"recent\":[");
            int idx = 0;
            for (PerfEvent event : perfEvents) {
                if (idx++ > 0) {
                    sb.append(',');
                }
                sb.append('{');
                sb.append("\"at\":").append(event.at).append(',');
                sb.append("\"type\":\"").append(escape(event.type)).append("\",");
                sb.append("\"costMs\":").append(event.costMs);
                sb.append('}');
            }
            sb.append(']');
            sb.append('}');
            return sb.toString();
        }

        private long percentile(List<Long> sorted, double ratio) {
            if (sorted.isEmpty()) {
                return 0L;
            }
            int idx = (int) Math.ceil(Math.max(0.0, Math.min(1.0, ratio)) * sorted.size()) - 1;
            idx = Math.max(0, Math.min(sorted.size() - 1, idx));
            return sorted.get(idx);
        }

        private void appendRecentMoves(StringBuilder sb, Board boardToDraw) {
            List<Move> history = boardToDraw.getMoveHistory();
            sb.append("\"recentMoves\":[");
            int total = history.size();
            int show = Math.min(2, total);
            for (int i = 0; i < show; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                Move move = history.get(total - 1 - i);
                PieceColor mover = (i == 0) ? boardToDraw.getCurrentTurn().opposite() : boardToDraw.getCurrentTurn();
                sb.append('{');
                sb.append("\"order\":").append(i + 1).append(',');
                sb.append("\"color\":\"").append(mover).append("\",");
                sb.append("\"fromRow\":").append(move.getFromRow()).append(',');
                sb.append("\"fromCol\":").append(move.getFromCol()).append(',');
                sb.append("\"toRow\":").append(move.getToRow()).append(',');
                sb.append("\"toCol\":").append(move.getToCol());
                sb.append('}');
            }
            sb.append(']');
        }

        private String escape(String input) {
            if (input == null) {
                return "";
            }
            return input.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

        private String html() {
        return String.join("\n",
            "<!doctype html>",
            "<html lang=\"zh-CN\">",
            "<head>",
            "  <meta charset=\"utf-8\" />",
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />",
            "  <title>轻·象棋 - 浏览器模式 v20260219-宫阙</title>",
            "  <style>",
            "    :root{--line:#d6b271;--bg1:#ecd9bf;--bg2:#f5e6cc;--ink:#5a3a22;--red:#c6403c;--blue:#24356c;--gold:#e3c47a;--bronze:#9a6a36;--deep:#3b2414;--glow:rgba(255,229,180,.42);}",
            "    html,body{height:100%;}",
            "    body{margin:0;height:100dvh;display:grid;place-items:center;position:relative;background-image:linear-gradient(180deg,rgba(10,12,22,.35),rgba(36,21,12,.55)),url('https://commons.wikimedia.org/wiki/Special:FilePath/Beijing%20forbidden%20city%20roof-20071018-RM-144550.jpg');background-size:cover;background-position:center 6%;background-repeat:no-repeat;font-family:STKaiti,\"Kaiti SC\",KaiTi,\"Microsoft YaHei UI\",sans-serif;color:var(--ink);overflow:hidden;-webkit-text-size-adjust:100%;}",
            "    body::before{content:'';position:fixed;inset:0;background:radial-gradient(ellipse at 50% -10%,rgba(255,236,205,.55),rgba(10,9,8,.7));pointer-events:none;}",
            "    body>*{position:relative;z-index:1;}",
            "    .wrap{width:min(1360px,calc(100vw - 16px));height:min(960px,calc(100dvh - 16px));background:linear-gradient(180deg,rgba(255,248,236,.92),rgba(245,226,196,.86));border:1px solid #e2c8a0;border-radius:16px;box-shadow:0 26px 55px rgba(12,8,4,.45),inset 0 0 0 1px rgba(255,248,229,.65);padding:12px;box-sizing:border-box;position:relative;isolation:isolate;backdrop-filter:blur(2px);}",
            "    .wrap:before{content:'';position:absolute;left:18px;right:18px;top:-30px;height:60px;border-radius:16px 16px 10px 10px;background:linear-gradient(180deg,#d09a4d,#b27034);background-image:repeating-linear-gradient(90deg,rgba(255,239,205,.35)0,rgba(255,239,205,.35)10px,rgba(168,108,54,.45)10px,rgba(168,108,54,.45)20px);box-shadow:0 10px 20px rgba(24,14,6,.4),inset 0 1px 0 rgba(255,234,190,.6),inset 0 -6px 10px rgba(73,38,12,.45);z-index:-1;}",
            "    .wrap:after{content:'';position:absolute;left:26px;right:26px;bottom:-24px;height:38px;border-radius:12px;background:linear-gradient(180deg,#a86b33,#5e3418);box-shadow:0 8px 16px rgba(17,9,4,.45),inset 0 1px 0 rgba(255,222,184,.35);z-index:-1;}",
            "    .topMeta{display:flex;justify-content:space-between;align-items:center;margin:0 4px 10px;padding:6px 12px;background:linear-gradient(180deg,#c99043,#9b612c);color:#fdf0d8;border:1px solid rgba(255,230,190,.45);border-radius:10px;box-shadow:inset 0 1px 0 rgba(255,236,206,.7),inset 0 -6px 10px rgba(85,43,12,.45),0 6px 14px rgba(18,9,4,.35);text-shadow:0 1px 0 rgba(30,16,6,.55);font-weight:700;}",
            "    .topMeta>div{white-space:nowrap;}",
            "    .layout{display:grid;grid-template-columns:minmax(420px,1fr) 228px;gap:10px;align-items:stretch;height:calc(100% - 38px);min-height:0;}",
            "    .boardCard{background:radial-gradient(140% 120% at 50% -10%,#f7ead1 0,#e1c39a 55%,#b5793e 100%);border:1px solid #d2b186;border-radius:16px;padding:16px 16px 18px;display:flex;align-items:center;justify-content:center;overflow:visible;position:relative;box-shadow:inset 0 2px 0 rgba(255,244,220,.8),inset 0 -10px 16px rgba(75,44,18,.28),0 18px 34px rgba(18,10,4,.45);}",
            "    .boardCard:before{content:'';position:absolute;inset:6px;border-radius:12px;border:1px solid rgba(255,232,196,.55);box-shadow:inset 0 0 18px rgba(57,32,12,.15);pointer-events:none;}",
            "    .boardCard:after{content:'';position:absolute;inset:12px;border-radius:10px;border:2px solid rgba(122,73,34,.5);box-shadow:inset 0 0 16px rgba(0,0,0,.18);pointer-events:none;}",
            "    .palaceFrame{position:absolute;left:-38px;right:-38px;top:-88px;bottom:-74px;pointer-events:none;z-index:4;}",
            "    .eave{position:absolute;left:0;right:0;height:120px;filter:drop-shadow(0 10px 12px rgba(20,10,4,.35));}",
            "    .eaveTop{top:0;}",
            "    .eaveBottom{bottom:0;transform:scaleY(-1);}",
            "    .jasmine{position:absolute;left:18px;top:4px;width:110px;height:110px;opacity:.95;filter:drop-shadow(0 2px 4px rgba(22,14,7,.36));}",
            "    .filigree{position:absolute;inset:86px 28px 86px 28px;opacity:.85;}",
            "    .filigree path,.filigree rect{vector-effect:non-scaling-stroke;}",
            "    .side{background:linear-gradient(180deg,rgba(255,252,245,.95),rgba(247,235,212,.96));border:1px solid #e0c49a;border-radius:12px;padding:10px;overflow:auto;box-shadow:inset 0 1px 0 rgba(255,255,255,.8),inset 0 -8px 14px rgba(135,92,50,.18);}",
            "    .boardStage{position:relative;z-index:1;display:flex;align-items:center;justify-content:center;}",
            "    canvas{position:relative;z-index:1;display:block;width:100%;height:100%;border:3px solid #9a6b34;border-radius:10px;background:linear-gradient(180deg,#22326e,#1a244f);box-shadow:0 10px 18px rgba(11,7,3,.45),inset 0 0 22px rgba(17,10,4,.35);image-rendering:auto;touch-action:none;}",
            "    .courtBanner{position:absolute;left:50%;top:52%;transform:translate(-50%,-50%);min-width:138px;padding:10px 22px;border-radius:12px;border:1px solid rgba(255,228,178,.68);background:linear-gradient(180deg,rgba(95,57,24,.9),rgba(46,25,12,.92));color:#ffe6b6;font-size:30px;font-weight:700;letter-spacing:6px;text-align:center;text-shadow:0 2px 4px rgba(0,0,0,.5);box-shadow:0 12px 22px rgba(12,6,2,.45),inset 0 1px 0 rgba(255,237,207,.55);opacity:0;pointer-events:none;z-index:9;}",
            "    .courtBanner[data-tone='crimson']{background:linear-gradient(180deg,rgba(130,32,24,.92),rgba(62,13,10,.92));border-color:rgba(255,186,168,.7);color:#ffd7ce;}",
            "    .courtBanner[data-tone='blackgold']{background:linear-gradient(180deg,rgba(56,45,26,.95),rgba(15,12,9,.96));border-color:rgba(234,201,132,.72);color:#ffe09a;}",
            "    .title{font-size:14px;font-weight:700;margin-bottom:6px;letter-spacing:.45px;color:#5a351a;}",
            "    .row{display:flex;gap:8px;margin-bottom:7px;}",
            "    .row>select,.row>button{flex:1;}",
            "    select,button{border:1px solid #caa16c;border-radius:10px;background:linear-gradient(180deg,#fff5e4,#f2dcb7);color:#5b371f;padding:7px 9px;cursor:pointer;box-shadow:inset 0 1px 0 rgba(255,255,255,.8),inset 0 -4px 6px rgba(109,73,35,.18);}",
            "    button:hover{background:linear-gradient(180deg,#fff1d6,#f0d0a2);} button:disabled{opacity:.45;cursor:not-allowed;box-shadow:none;}",
            "    .tag{display:inline-block;background:linear-gradient(180deg,#f8eedc,#ead1a7);border:1px solid #d7b78b;border-radius:14px;padding:3px 10px;margin:4px 0;font-size:12px;box-shadow:inset 0 1px 0 rgba(255,255,255,.7);}",
            "    .log{font-size:12px;line-height:1.55;color:#6b4a2f;margin-top:3px;}",
            "    .egGrid{display:grid;grid-template-columns:1fr 1fr;gap:5px;margin:7px 0 10px;}",
            "    .egBtn{padding:6px 7px;font-size:11px;}",
            "    @media (max-width:1020px){.wrap{width:100vw;height:100dvh;border-radius:0;padding:8px}.layout{grid-template-columns:minmax(0,1fr) 208px;gap:8px;height:calc(100% - 34px)}.topMeta{font-size:12px;margin-bottom:8px;padding:5px 8px}}",
            "    @media (max-width:768px){.layout{grid-template-columns:minmax(0,1fr) 178px;gap:6px}.side{padding:8px}.title{font-size:13px;margin-bottom:5px}.row{gap:6px;margin-bottom:6px}select,button{min-height:34px;font-size:12px;padding:6px 6px}.tag{font-size:11px;padding:3px 8px}.courtBanner{font-size:22px;letter-spacing:4px;padding:8px 14px;top:50%}.jasmine{left:10px;top:2px;width:84px;height:84px}}",
            "  </style>",
            "</head>",
            "<body>",
            "  <div class=\"wrap\">",
            "    <div class=\"topMeta\"><div>轻·象棋 · 宫阙版</div><div id=\"totalTop\">总时 红:--:-- 黑:--:--</div><div id=\"stepTop\">当前步时倒计时: --s</div></div>",
            "    <div id=\"courtBanner\" class=\"courtBanner\" data-tone=\"gold\"></div>",
            "    <div class=\"layout\">",
            "      <div class=\"boardCard\">",
            "        <div class=\"palaceFrame\">",
            "          <svg class=\"eave eaveTop\" viewBox=\"0 0 1000 160\" preserveAspectRatio=\"none\" aria-hidden=\"true\">",
            "            <defs>",
            "              <linearGradient id=\"eaveG\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">",
            "                <stop offset=\"0\" stop-color=\"#e0b267\"/>",
            "                <stop offset=\"0.55\" stop-color=\"#b26f33\"/>",
            "                <stop offset=\"1\" stop-color=\"#6d3818\"/>",
            "              </linearGradient>",
            "              <pattern id=\"tileP\" width=\"80\" height=\"18\" patternUnits=\"userSpaceOnUse\">",
            "                <path d=\"M0 18 L20 0 L40 18 L60 0 L80 18\" fill=\"none\" stroke=\"rgba(255,230,190,.7)\" stroke-width=\"3\"/>",
            "              </pattern>",
            "            </defs>",
            "            <path d=\"M0 40 L1000 40 L1000 88 C900 130 700 126 500 110 C300 126 100 130 0 88 Z\" fill=\"url(#eaveG)\"/>",
            "            <rect x=\"0\" y=\"42\" width=\"1000\" height=\"20\" fill=\"url(#tileP)\" opacity=\".85\"/>",
            "            <path d=\"M20 64 L980 64\" stroke=\"rgba(255,236,200,.7)\" stroke-width=\"3\"/>",
            "            <path d=\"M0 88 C120 130 280 128 500 108 C720 128 880 130 1000 88\" stroke=\"rgba(255,220,170,.6)\" stroke-width=\"2\" fill=\"none\"/>",
            "            <circle cx=\"500\" cy=\"76\" r=\"10\" fill=\"rgba(255,220,170,.8)\"/>",
            "            <circle cx=\"500\" cy=\"76\" r=\"5\" fill=\"#8a4c1f\"/>",
            "          </svg>",
            "          <svg class=\"jasmine\" viewBox=\"0 0 120 120\" preserveAspectRatio=\"xMidYMid meet\" aria-hidden=\"true\">",
            "            <defs><radialGradient id=\"jCore\" cx=\"50%\" cy=\"50%\" r=\"50%\"><stop offset=\"0\" stop-color=\"#fff7d9\"/><stop offset=\"1\" stop-color=\"#d8b574\"/></radialGradient></defs>",
            "            <g fill=\"#fffdf8\" stroke=\"rgba(215,206,187,.8)\" stroke-width=\"1.4\">",
            "              <path d=\"M60 16 C71 26,73 42,60 50 C47 42,49 26,60 16\"/>",
            "              <path d=\"M104 60 C94 71,78 73,70 60 C78 47,94 49,104 60\"/>",
            "              <path d=\"M60 104 C49 94,47 78,60 70 C73 78,71 94,60 104\"/>",
            "              <path d=\"M16 60 C26 49,42 47,50 60 C42 73,26 71,16 60\"/>",
            "              <path d=\"M89 31 C94 42,89 56,76 58 C68 47,73 34,89 31\"/>",
            "              <path d=\"M89 89 C78 94,64 89,62 76 C73 68,86 73,89 89\"/>",
            "              <path d=\"M31 89 C26 78,31 64,44 62 C52 73,47 86,31 89\"/>",
            "              <path d=\"M31 31 C42 26,56 31,58 44 C47 52,34 47,31 31\"/>",
            "            </g>",
            "            <circle cx=\"60\" cy=\"60\" r=\"8\" fill=\"url(#jCore)\"/>",
            "            <circle cx=\"60\" cy=\"60\" r=\"3\" fill=\"#c6a157\"/>",
            "          </svg>",
            "          <svg class=\"eave eaveBottom\" viewBox=\"0 0 1000 160\" preserveAspectRatio=\"none\" aria-hidden=\"true\">",
            "            <defs>",
            "              <linearGradient id=\"eaveG2\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">",
            "                <stop offset=\"0\" stop-color=\"#e0b267\"/>",
            "                <stop offset=\"0.55\" stop-color=\"#b26f33\"/>",
            "                <stop offset=\"1\" stop-color=\"#6d3818\"/>",
            "              </linearGradient>",
            "              <pattern id=\"tileP2\" width=\"80\" height=\"18\" patternUnits=\"userSpaceOnUse\">",
            "                <path d=\"M0 18 L20 0 L40 18 L60 0 L80 18\" fill=\"none\" stroke=\"rgba(255,230,190,.7)\" stroke-width=\"3\"/>",
            "              </pattern>",
            "            </defs>",
            "            <path d=\"M0 40 L1000 40 L1000 88 C900 130 700 126 500 110 C300 126 100 130 0 88 Z\" fill=\"url(#eaveG2)\"/>",
            "            <rect x=\"0\" y=\"42\" width=\"1000\" height=\"20\" fill=\"url(#tileP2)\" opacity=\".85\"/>",
            "            <path d=\"M20 64 L980 64\" stroke=\"rgba(255,236,200,.7)\" stroke-width=\"3\"/>",
            "            <path d=\"M0 88 C120 130 280 128 500 108 C720 128 880 130 1000 88\" stroke=\"rgba(255,220,170,.6)\" stroke-width=\"2\" fill=\"none\"/>",
            "            <circle cx=\"500\" cy=\"76\" r=\"10\" fill=\"rgba(255,220,170,.8)\"/>",
            "            <circle cx=\"500\" cy=\"76\" r=\"5\" fill=\"#8a4c1f\"/>",
            "          </svg>",
            "          <svg class=\"filigree\" viewBox=\"0 0 1000 1000\" preserveAspectRatio=\"none\" aria-hidden=\"true\">",
            "            <defs>",
            "              <linearGradient id=\"goldLine\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">",
            "                <stop offset=\"0\" stop-color=\"rgba(255,232,190,.85)\"/>",
            "                <stop offset=\"1\" stop-color=\"rgba(163,103,48,.85)\"/>",
            "              </linearGradient>",
            "            </defs>",
            "            <rect x=\"34\" y=\"34\" width=\"932\" height=\"932\" fill=\"none\" stroke=\"url(#goldLine)\" stroke-width=\"2\"/>",
            "          </svg>",
            "        </div>",
            "        <div id=\"boardStage\" class=\"boardStage\"><canvas id=\"board\" width=\"800\" height=\"900\"></canvas></div>",
            "      </div>",
            "      <div class=\"side\">",
            "        <div class=\"title\">控制面板</div>",
            "        <div class=\"row\"><select id=\"mode\"><option value=\"pvp\">双人对战</option><option value=\"pvc\">人机对战</option></select><select id=\"difficulty\"><option value=\"EASY\">简单</option><option value=\"MEDIUM\" selected>中等</option><option value=\"HARD\">困难</option></select></div>",
            "        <div class=\"row\"><select id=\"firstHand\" disabled><option value=\"true\" selected>我先手（执红）</option><option value=\"false\">我后手（执黑）</option></select></div>",
            "        <div class=\"row\"><button id=\"newGame\">新开一局</button><button id=\"undo\">悔棋</button></div>",
            "        <div class=\"row\"><button id=\"surrender\">认输</button><button id=\"drawBtn\">和棋</button></div>",
            "        <div class=\"row\"><button id=\"soundToggle\">音效:开</button></div>",
            "        <div class=\"title\" style=\"margin-top:8px\">残局练习</div>",
            "        <div class=\"egGrid\">",
            "          <button class=\"egBtn\" data-name=\"七星聚会\">七星聚会</button><button class=\"egBtn\" data-name=\"蚯蚓降龙\">蚯蚓降龙</button>",
            "          <button class=\"egBtn\" data-name=\"千里独行\">千里独行</button><button class=\"egBtn\" data-name=\"野马操田\">野马操田</button>",
            "          <button class=\"egBtn\" data-name=\"梅花谱\">梅花谱</button><button class=\"egBtn\" data-name=\"百局象棋谱\">百局象棋谱</button>",
            "          <button class=\"egBtn\" data-name=\"适情雅趣\">适情雅趣</button><button class=\"egBtn\" data-name=\"烂柯神机\">烂柯神机</button>",
            "          <button class=\"egBtn\" data-name=\"梦入神机\">梦入神机</button><button class=\"egBtn\" data-name=\"韬略元机\">韬略元机</button>",
            "        </div>",
            "        <div class=\"title\" style=\"margin-top:10px\">棋盘回顾</div>",
            "        <div class=\"row\"><button id=\"reviewStart\">开始回顾</button><button id=\"reviewExit\">退出回顾</button></div>",
            "        <div class=\"row\"><button id=\"reviewPrev\">上一步</button><button id=\"reviewNext\">下一步</button></div>",
            "        <div id=\"statusTag\" class=\"tag\">状态: 加载中</div><br/>",
            "        <div id=\"modeTag\" class=\"tag\">模式: -</div>",
            "        <div id=\"endgameTag\" class=\"tag\">残局: 标准开局</div>",
            "        <div id=\"drawReasonTag\" class=\"tag\">和棋原因: -</div>",
            "        <div id=\"reviewTag\" class=\"tag\">回顾: 关闭</div>",
            "        <div id=\"info\" class=\"log\">等待数据...</div>",
            "      </div>",
            "    </div>",
            "  </div>",
            "<script src=\"https://cdn.jsdelivr.net/npm/gsap@3.12.7/dist/gsap.min.js\"></script>",
            "<script src=\"https://cdn.jsdelivr.net/npm/pixi.js@7.4.2/dist/pixi.min.js\"></script>",
            "<script>",
            "const BASE_W=800,BASE_H=900,CELL=68,MARGIN=98,R=29;",
            "const canvas=document.getElementById('board'); const boardStage=document.getElementById('boardStage'); const boardCard=canvas.closest('.boardCard'); const ctx=canvas.getContext('2d',{alpha:true});",
            "let state=null,reqSeq=0,pending=false,needRender=false,scale=1,dpr=Math.min(2,Math.max(1,window.devicePixelRatio||1)),anim=null,animKey='',pollTimer=0,lastStateStamp='',actionQueue=Promise.resolve(),lastAppliedSeq=0,lastPerfPostAt=0,lastTacticSeq=0,tacticOverlayText='',tacticOverlayUntil=0,courtBannerTimer=0,lastOpeningSeq=0,lastCheckSeq=0,lastMateSeq=0;",
            "const SID_KEY='xq_sid';function makeSid(){if(window.crypto&&window.crypto.randomUUID)return window.crypto.randomUUID().replace(/-/g,'');return String(Date.now())+Math.random().toString(16).slice(2);}const sid=(()=>{let v=sessionStorage.getItem(SID_KEY);if(!v){v=makeSid();sessionStorage.setItem(SID_KEY,v);}return v;})();function withSid(path){const sep=path.includes('?')?'&':'?';return path+sep+'sid='+encodeURIComponent(sid);}",
            "const moveAudio=new Audio('/assets/audio/move.wav');const mateAudio=new Audio('/assets/audio/mate.wav');moveAudio.preload='auto';mateAudio.preload='auto';moveAudio.volume=0.92;mateAudio.volume=0.98;let audioUnlocked=false,lastMoveSoundKey='',lastMateSoundKey='';let soundEnabled=(localStorage.getItem('xq_sound_enabled')??'1')!=='0';",
            "const ui={statusTag:document.getElementById('statusTag'),stepTop:document.getElementById('stepTop'),totalTop:document.getElementById('totalTop'),modeTag:document.getElementById('modeTag'),endgameTag:document.getElementById('endgameTag'),drawReasonTag:document.getElementById('drawReasonTag'),reviewTag:document.getElementById('reviewTag'),info:document.getElementById('info'),undo:document.getElementById('undo'),surrender:document.getElementById('surrender'),drawBtn:document.getElementById('drawBtn'),reviewStart:document.getElementById('reviewStart'),reviewPrev:document.getElementById('reviewPrev'),reviewNext:document.getElementById('reviewNext'),reviewExit:document.getElementById('reviewExit'),mode:document.getElementById('mode'),firstHand:document.getElementById('firstHand')};function setTxt(el,v){if(el&&el.textContent!==v)el.textContent=v;}function setDis(el,v){if(el&&el.disabled!==v)el.disabled=v;}",
            "const cache=document.createElement('canvas'); cache.width=BASE_W; cache.height=BASE_H; const cctx=cache.getContext('2d');const pieceSpriteCache=Object.create(null);let pixiReady=false,pixiApp=null,pixiLayers=null,pixiBoardTexture=null;",
            "const BOARD_TEXTURE_URL='https://commons.wikimedia.org/wiki/Special:FilePath/Xiangqi%20board.svg';const boardTex=new Image();let boardTexReady=false;boardTex.crossOrigin='anonymous';boardTex.onload=()=>{boardTexReady=true;drawStatic();scheduleRender();};boardTex.src=BOARD_TEXTURE_URL;",
            "function initPixiRenderer(){if(!window.PIXI)return;try{pixiApp=new PIXI.Application({view:canvas,width:BASE_W,height:BASE_H,backgroundAlpha:0,antialias:true,resolution:dpr,autoDensity:false,autoStart:false,sharedTicker:false});pixiApp.stop();pixiLayers={root:new PIXI.Container(),staticLayer:new PIXI.Container(),markerLayer:new PIXI.Container(),pieceLayer:new PIXI.Container(),fxLayer:new PIXI.Container(),uiLayer:new PIXI.Container()};pixiLayers.root.sortableChildren=true;pixiLayers.root.addChild(pixiLayers.staticLayer,pixiLayers.markerLayer,pixiLayers.pieceLayer,pixiLayers.fxLayer,pixiLayers.uiLayer);pixiApp.stage.addChild(pixiLayers.root);pixiReady=true;}catch(_e){pixiReady=false;}}",
            "function runCourtAnimations(){if(!window.gsap)return;gsap.from('.wrap',{duration:0.9,y:26,opacity:0,ease:'power3.out'});gsap.from('.topMeta',{duration:0.75,scaleX:.94,opacity:0,transformOrigin:'50% 0%',ease:'back.out(1.2)',delay:.08});gsap.from('.boardCard',{duration:0.95,y:18,opacity:0,ease:'power2.out',delay:.14});gsap.from('.side',{duration:0.85,x:24,opacity:0,ease:'power2.out',delay:.2});gsap.from('.side .row,.side .title,.side .tag',{duration:.7,y:12,opacity:0,stagger:.035,ease:'power2.out',delay:.26});document.querySelectorAll('button,select').forEach(el=>{el.addEventListener('pointerenter',()=>gsap.to(el,{duration:.22,y:-1.2,scale:1.015,boxShadow:'0 6px 14px rgba(76,42,18,.28)',ease:'power2.out'}));el.addEventListener('pointerleave',()=>gsap.to(el,{duration:.24,y:0,scale:1,boxShadow:'inset 0 1px 0 rgba(255,255,255,.8),inset 0 -4px 6px rgba(109,73,35,.18)',ease:'power2.out'}));});}",
            "function flashBanner(text,tone){const el=document.getElementById('courtBanner');if(!el)return;el.dataset.tone=tone||'gold';el.textContent=text;if(!window.gsap){el.style.opacity='1';setTimeout(()=>{el.style.opacity='0';},700);return;}if(courtBannerTimer){clearTimeout(courtBannerTimer);courtBannerTimer=0;}gsap.killTweensOf(el);gsap.fromTo(el,{opacity:0,scale:.82,y:8},{opacity:1,scale:1,y:0,duration:.24,ease:'back.out(1.5)'});courtBannerTimer=setTimeout(()=>{gsap.to(el,{opacity:0,scale:.92,y:-6,duration:.32,ease:'power2.in'});},640);}",
            "function playOpeningCeremony(){if(!window.gsap)return;flashBanner('开局','gold');const tl=gsap.timeline();tl.fromTo('.boardCard',{filter:'brightness(.86) saturate(.9)'},{filter:'brightness(1.05) saturate(1.12)',duration:.32,ease:'power2.out'}).to('.boardCard',{filter:'brightness(1) saturate(1)',duration:.44,ease:'power2.inOut'});tl.fromTo('.topMeta',{boxShadow:'0 0 0 rgba(0,0,0,0)'},{boxShadow:'0 0 26px rgba(255,220,150,.38)',duration:.28},0).to('.topMeta',{boxShadow:'inset 0 1px 0 rgba(255,236,206,.7),inset 0 -6px 10px rgba(85,43,12,.45),0 6px 14px rgba(18,9,4,.35)',duration:.46},'>-.02');}",
            "function playCheckCeremony(){if(!window.gsap)return;const tl=gsap.timeline();tl.fromTo('.boardCard',{boxShadow:'0 18px 34px rgba(18,10,4,.45),inset 0 2px 0 rgba(255,244,220,.8),inset 0 -10px 16px rgba(75,44,18,.28)'},{boxShadow:'0 0 0 2px rgba(214,59,50,.65),0 24px 44px rgba(120,18,10,.5),inset 0 0 28px rgba(212,56,45,.35)',duration:.18,ease:'power2.out'}).to('.boardCard',{boxShadow:'0 18px 34px rgba(18,10,4,.45),inset 0 2px 0 rgba(255,244,220,.8),inset 0 -10px 16px rgba(75,44,18,.28)',duration:.32,ease:'power2.in'});}",
            "function playMateCeremony(){if(!window.gsap)return;const tl=gsap.timeline();tl.fromTo('.wrap',{filter:'brightness(.86) contrast(1.1)'},{filter:'brightness(1.08) contrast(1.18)',duration:.24,ease:'power1.out'}).to('.wrap',{filter:'brightness(1) contrast(1)',duration:.36,ease:'power2.out'});}",
            "function setupCanvas(){const maxW=Math.max(280,boardCard.clientWidth-24);const maxH=Math.max(360,boardCard.clientHeight-28);const fitWByH=Math.floor(maxH*BASE_W/BASE_H);const rawW=Math.max(260,Math.min(maxW,fitWByH));const palaceScale=window.innerWidth<=768?0.84:0.78;const cssW=Math.round(rawW*palaceScale);const cssH=Math.round(cssW*BASE_H/BASE_W);scale=cssW/BASE_W;boardStage.style.width=cssW+'px';boardStage.style.height=cssH+'px';canvas.style.width=cssW+'px';canvas.style.height=cssH+'px';if(pixiReady&&pixiApp){dpr=Math.min(2,Math.max(1,window.devicePixelRatio||1));pixiApp.renderer.resolution=dpr;pixiApp.renderer.resize(BASE_W,BASE_H);}else{canvas.width=Math.round(cssW*dpr);canvas.height=Math.round(cssH*dpr);ctx.setTransform(dpr*scale,0,0,dpr*scale,0,0);}scheduleRender();}",
            "window.addEventListener('resize', setupCanvas);",
            "function syncSoundToggle(){const btn=document.getElementById('soundToggle');if(btn)btn.textContent='音效:'+(soundEnabled?'开':'关');}function toggleSound(){soundEnabled=!soundEnabled;localStorage.setItem('xq_sound_enabled',soundEnabled?'1':'0');syncSoundToggle();}function unlockAudio(){if(audioUnlocked)return;audioUnlocked=true;[moveAudio,mateAudio].forEach(a=>{const p=a.play();if(p&&p.catch){p.then(()=>{a.pause();a.currentTime=0;}).catch(()=>{});}else{a.pause();a.currentTime=0;}});}function playSound(a){if(!soundEnabled||!audioUnlocked)return;try{a.pause();a.currentTime=0;const p=a.play();if(p&&p.catch)p.catch(()=>{});}catch(_e){}}",
            "const isFlipped=()=>{if(!state||state.reviewMode)return false;if(state.mode==='PVP')return state.currentTurn==='BLACK';return state.mode==='PVC'&&state.pvcHumanColor==='BLACK';};const vr=r=>isFlipped()?9-r:r;const vc=c=>isFlipped()?8-c:c;const br=r=>isFlipped()?9-r:r;const bc=c=>isFlipped()?8-c:c;const pos=(r,c)=>[MARGIN+vc(c)*CELL,MARGIN+vr(r)*CELL];function pickGrid(x,y){const vcol=Math.round((x-MARGIN)/CELL),vrow=Math.round((y-MARGIN)/CELL);const hitExpand=state&&state.mode==='PVC'?(state.difficulty==='EASY'?14:11):10;const minX=MARGIN-R-hitExpand,maxX=MARGIN+8*CELL+R+hitExpand,minY=MARGIN-R-hitExpand,maxY=MARGIN+9*CELL+R+hitExpand;if(x<minX||x>maxX||y<minY||y>maxY)return null;const cc=Math.max(0,Math.min(8,vcol)),rr=Math.max(0,Math.min(9,vrow));return {row:br(rr),col:bc(cc)};}",
            "function drawStatic(){if(pixiReady){drawStaticPixi();return;}cctx.clearRect(0,0,BASE_W,BASE_H);const bx=MARGIN-34,by=MARGIN-34,bw=8*CELL+68,bh=9*CELL+68;if(boardTexReady){cctx.drawImage(boardTex,bx,by,bw,bh);}else{const g=cctx.createLinearGradient(0,0,0,BASE_H);g.addColorStop(0,'#2c3f88');g.addColorStop(1,'#1f2f66');cctx.fillStyle=g;cctx.fillRect(bx,by,bw,bh);}const vignette=cctx.createRadialGradient(BASE_W/2,BASE_H/2,120,BASE_W/2,BASE_H/2,520);vignette.addColorStop(0,'rgba(255,255,255,.05)');vignette.addColorStop(1,'rgba(0,0,0,.16)');cctx.fillStyle=vignette;cctx.fillRect(bx,by,bw,bh);cctx.fillStyle='rgba(20,33,88,.34)';cctx.fillRect(MARGIN-2,MARGIN-2,8*CELL+4,9*CELL+4);cctx.save();cctx.shadowColor='rgba(0,0,0,.35)';cctx.shadowBlur=16;cctx.strokeStyle='rgba(96,57,24,.96)';cctx.lineWidth=14;cctx.strokeRect(bx-11,by-11,bw+22,bh+22);cctx.restore();cctx.strokeStyle='#7f4f25';cctx.lineWidth=8;cctx.strokeRect(bx-6,by-6,bw+12,bh+12);cctx.strokeStyle='#dfbc77';cctx.lineWidth=3;cctx.strokeRect(bx,by,bw,bh);cctx.strokeStyle='rgba(255,240,210,.75)';cctx.lineWidth=1.5;cctx.strokeRect(bx+3,by+3,bw-6,bh-6);const corner=(x,y,sx,sy)=>{cctx.strokeStyle='rgba(225,196,130,.96)';cctx.lineWidth=2;cctx.beginPath();cctx.moveTo(x,y);cctx.lineTo(x+sx*18,y);cctx.lineTo(x+sx*18,y+sy*18);cctx.moveTo(x+sx*6,y);cctx.lineTo(x+sx*24,y);cctx.moveTo(x,y+sy*6);cctx.lineTo(x,y+sy*24);cctx.stroke();};corner(bx+10,by+10,1,1);corner(bx+bw-10,by+10,-1,1);corner(bx+10,by+bh-10,1,-1);corner(bx+bw-10,by+bh-10,-1,-1);const topY=by-20;const botY=by+bh+20;cctx.strokeStyle='rgba(108,64,28,.9)';cctx.lineWidth=3;cctx.beginPath();cctx.moveTo(bx+30,topY);cctx.bezierCurveTo(bx+bw*0.32,topY-16,bx+bw*0.68,topY-16,bx+bw-30,topY);cctx.moveTo(bx+30,botY);cctx.bezierCurveTo(bx+bw*0.32,botY+16,bx+bw*0.68,botY+16,bx+bw-30,botY);cctx.stroke();cctx.strokeStyle='rgba(213,178,106,.86)';cctx.lineWidth=2;for(let r=0;r<10;r++){const y=MARGIN+r*CELL;cctx.beginPath();cctx.moveTo(MARGIN,y);cctx.lineTo(MARGIN+8*CELL,y);cctx.stroke();}for(let c=0;c<9;c++){const x=MARGIN+c*CELL;cctx.beginPath();if(c===0||c===8){cctx.moveTo(x,MARGIN);cctx.lineTo(x,MARGIN+9*CELL);}else{cctx.moveTo(x,MARGIN);cctx.lineTo(x,MARGIN+4*CELL);cctx.moveTo(x,MARGIN+5*CELL);cctx.lineTo(x,MARGIN+9*CELL);}cctx.stroke();}cctx.beginPath();cctx.moveTo(MARGIN+3*CELL,MARGIN);cctx.lineTo(MARGIN+5*CELL,MARGIN+2*CELL);cctx.moveTo(MARGIN+5*CELL,MARGIN);cctx.lineTo(MARGIN+3*CELL,MARGIN+2*CELL);cctx.moveTo(MARGIN+3*CELL,MARGIN+7*CELL);cctx.lineTo(MARGIN+5*CELL,MARGIN+9*CELL);cctx.moveTo(MARGIN+5*CELL,MARGIN+7*CELL);cctx.lineTo(MARGIN+3*CELL,MARGIN+9*CELL);cctx.stroke();const ry=MARGIN+4*CELL+3;cctx.shadowColor='rgba(0,0,0,.26)';cctx.shadowBlur=10;cctx.fillStyle='rgba(35,54,124,.92)';cctx.beginPath();cctx.roundRect(MARGIN+12,ry,8*CELL-24,CELL-8,22);cctx.fill();cctx.shadowBlur=0;cctx.strokeStyle='rgba(213,178,106,.44)';cctx.beginPath();cctx.moveTo(MARGIN, MARGIN+4*CELL);cctx.lineTo(MARGIN+8*CELL,MARGIN+4*CELL);cctx.moveTo(MARGIN,MARGIN+5*CELL);cctx.lineTo(MARGIN+8*CELL,MARGIN+5*CELL);cctx.stroke();cctx.font='bold 45px KaiTi';cctx.fillStyle='#d8b574';cctx.fillText('楚 河',MARGIN+CELL-10,MARGIN+4*CELL+44);cctx.fillText('汉 界',MARGIN+5*CELL+8,MARGIN+4*CELL+44);}",
            "function scheduleRender(){if(needRender)return;needRender=true;requestAnimationFrame(()=>{needRender=false;draw();});}",
            "function draw(){const t0=performance.now();if(pixiReady){drawPixi();const cost=performance.now()-t0;if(performance.now()-lastPerfPostAt>5000&&cost>14){lastPerfPostAt=performance.now();api('/api/perf/event?type=render&cost='+Math.round(cost)).catch(()=>{});}return;}ctx.clearRect(0,0,BASE_W,BASE_H);ctx.drawImage(cache,0,0);if(!state)return;drawMarkers();drawPieces();drawMoveAnim();drawSelection();drawTacticFlash();const cost=performance.now()-t0;if(performance.now()-lastPerfPostAt>5000&&cost>14){lastPerfPostAt=performance.now();api('/api/perf/event?type=render&cost='+Math.round(cost)).catch(()=>{});}}",
            "function drawStaticPixi(){if(!pixiReady||!pixiLayers)return;const sl=pixiLayers.staticLayer;sl.removeChildren();const bx=MARGIN-34,by=MARGIN-34,bw=8*CELL+68,bh=9*CELL+68;const bg=new PIXI.Graphics();bg.beginFill(0x243b80,0.96);bg.drawRoundedRect(bx,by,bw,bh,10);bg.endFill();sl.addChild(bg);if(boardTexReady){if(!pixiBoardTexture)pixiBoardTexture=PIXI.Texture.from(boardTex);const texSprite=new PIXI.Sprite(pixiBoardTexture);texSprite.x=bx;texSprite.y=by;texSprite.width=bw;texSprite.height=bh;texSprite.alpha=.92;sl.addChild(texSprite);}const grid=new PIXI.Graphics();grid.lineStyle(2,0xd5b26a,.9);for(let r=0;r<10;r++){const y=MARGIN+r*CELL;grid.moveTo(MARGIN,y);grid.lineTo(MARGIN+8*CELL,y);}for(let c=0;c<9;c++){const x=MARGIN+c*CELL;if(c===0||c===8){grid.moveTo(x,MARGIN);grid.lineTo(x,MARGIN+9*CELL);}else{grid.moveTo(x,MARGIN);grid.lineTo(x,MARGIN+4*CELL);grid.moveTo(x,MARGIN+5*CELL);grid.lineTo(x,MARGIN+9*CELL);}}grid.moveTo(MARGIN+3*CELL,MARGIN);grid.lineTo(MARGIN+5*CELL,MARGIN+2*CELL);grid.moveTo(MARGIN+5*CELL,MARGIN);grid.lineTo(MARGIN+3*CELL,MARGIN+2*CELL);grid.moveTo(MARGIN+3*CELL,MARGIN+7*CELL);grid.lineTo(MARGIN+5*CELL,MARGIN+9*CELL);grid.moveTo(MARGIN+5*CELL,MARGIN+7*CELL);grid.lineTo(MARGIN+3*CELL,MARGIN+9*CELL);sl.addChild(grid);const river=new PIXI.Graphics();river.beginFill(0x20357a,.92);river.drawRoundedRect(MARGIN+12,MARGIN+4*CELL+3,8*CELL-24,CELL-8,20);river.endFill();sl.addChild(river);const frame=new PIXI.Graphics();frame.lineStyle(12,0x5d3718,.55);frame.drawRect(bx-10,by-10,bw+20,bh+20);frame.lineStyle(3,0xdfbc77,.95);frame.drawRect(bx,by,bw,bh);sl.addChild(frame);const riverTextL=new PIXI.Text('楚 河',{fontFamily:'KaiTi,STKaiti,serif',fontSize:45,fontWeight:'700',fill:0xd8b574});riverTextL.x=MARGIN+CELL-10;riverTextL.y=MARGIN+4*CELL+6;const riverTextR=new PIXI.Text('汉 界',{fontFamily:'KaiTi,STKaiti,serif',fontSize:45,fontWeight:'700',fill:0xd8b574});riverTextR.x=MARGIN+5*CELL+8;riverTextR.y=MARGIN+4*CELL+6;sl.addChild(riverTextL,riverTextR);}",
            "function getPieceTexturePixi(name,color){const key='pixi|'+color+'|'+name;if(pieceSpriteCache[key])return pieceSpriteCache[key];const size=Math.ceil(R*2+18);const can=document.createElement('canvas');can.width=size;can.height=size;const g=can.getContext('2d');const cx=size/2,cy=size/2;g.fillStyle='rgba(10,6,2,.28)';g.beginPath();g.ellipse(cx+2,cy+5,R*1.02,R*.83,0,0,Math.PI*2);g.fill();const rg=g.createRadialGradient(cx-R*.36,cy-R*.48,R*.1,cx,cy,R*1.08);if(color==='RED'){rg.addColorStop(0,'#fff9ef');rg.addColorStop(.34,'#f4dfc2');rg.addColorStop(.7,'#d5b189');rg.addColorStop(1,'#996946');}else{rg.addColorStop(0,'#ffffff');rg.addColorStop(.36,'#ece8df');rg.addColorStop(.7,'#c5bdb2');rg.addColorStop(1,'#8f8477');}g.fillStyle=rg;g.beginPath();g.arc(cx,cy,R,0,Math.PI*2);g.fill();const bevel=g.createLinearGradient(cx,cy-R*.95,cx,cy+R*.95);bevel.addColorStop(0,'rgba(255,255,255,.45)');bevel.addColorStop(.4,'rgba(255,255,255,.08)');bevel.addColorStop(1,'rgba(0,0,0,.26)');g.strokeStyle=bevel;g.lineWidth=3.2;g.beginPath();g.arc(cx,cy,R-1.7,0,Math.PI*2);g.stroke();g.strokeStyle='rgba(80,45,18,.95)';g.lineWidth=2.3;g.beginPath();g.arc(cx,cy,R-3.3,0,Math.PI*2);g.stroke();g.strokeStyle=color==='RED'?'#c63f37':'#202020';g.lineWidth=2.3;g.beginPath();g.arc(cx,cy,R-5.6,0,Math.PI*2);g.stroke();g.strokeStyle='rgba(243,219,173,.92)';g.lineWidth=1.1;g.beginPath();g.arc(cx,cy,R-8.2,0,Math.PI*2);g.stroke();const spec=g.createRadialGradient(cx-R*.42,cy-R*.56,1,cx-R*.1,cy-R*.18,R*.92);spec.addColorStop(0,'rgba(255,255,255,.58)');spec.addColorStop(.4,'rgba(255,255,255,.2)');spec.addColorStop(1,'rgba(255,255,255,0)');g.fillStyle=spec;g.beginPath();g.arc(cx,cy,R-5,Math.PI*.98,Math.PI*1.92);g.lineTo(cx,cy);g.closePath();g.fill();g.font='bold 32px KaiTi';g.lineWidth=1.1;g.strokeStyle='rgba(255,246,225,.36)';const w=g.measureText(name).width;g.strokeText(name,cx-w/2,cy+11);g.fillStyle=color==='RED'?'#bc332c':'#141414';g.fillText(name,cx-w/2,cy+11);const tex=PIXI.Texture.from(can);pieceSpriteCache[key]=tex;return tex;}",
            "function getPieceBloomTexturePixi(color){const key='pixi-bloom|'+color;if(pieceSpriteCache[key])return pieceSpriteCache[key];const size=Math.ceil(R*2+26);const can=document.createElement('canvas');can.width=size;can.height=size;const g=can.getContext('2d');const cx=size/2,cy=size/2;const grd=g.createRadialGradient(cx,cy,R*.3,cx,cy,R*1.25);if(color==='RED'){grd.addColorStop(0,'rgba(255,150,130,.26)');grd.addColorStop(.6,'rgba(207,78,62,.14)');grd.addColorStop(1,'rgba(160,45,34,0)');}else{grd.addColorStop(0,'rgba(255,246,224,.23)');grd.addColorStop(.6,'rgba(206,197,178,.13)');grd.addColorStop(1,'rgba(120,112,98,0)');}g.fillStyle=grd;g.beginPath();g.arc(cx,cy,R*1.25,0,Math.PI*2);g.fill();const tex=PIXI.Texture.from(can);pieceSpriteCache[key]=tex;return tex;}",
            "function getPieceSpecTexturePixi(color){const key='pixi-spec|'+color;if(pieceSpriteCache[key])return pieceSpriteCache[key];const size=Math.ceil(R*2+18);const can=document.createElement('canvas');can.width=size;can.height=size;const g=can.getContext('2d');const cx=size/2,cy=size/2;const spec=g.createRadialGradient(cx-R*.4,cy-R*.6,1,cx-R*.08,cy-R*.15,R*.95);spec.addColorStop(0,'rgba(255,255,255,.62)');spec.addColorStop(.45,'rgba(255,255,255,.22)');spec.addColorStop(1,'rgba(255,255,255,0)');g.fillStyle=spec;g.beginPath();g.arc(cx,cy,R-4,Math.PI*.95,Math.PI*1.9);g.lineTo(cx,cy);g.closePath();g.fill();g.strokeStyle=color==='RED'?'rgba(255,219,206,.5)':'rgba(255,255,255,.45)';g.lineWidth=1;g.beginPath();g.arc(cx-1,cy-1,R-10,Math.PI*1.06,Math.PI*1.78);g.stroke();const tex=PIXI.Texture.from(can);pieceSpriteCache[key]=tex;return tex;}",
            "function makePieceLayerSprite(name,color){const root=new PIXI.Container();const sh=new PIXI.Graphics();sh.beginFill(0x080502,.28);sh.drawEllipse(2,4,R*1.02,R*.82);sh.endFill();root.addChild(sh);const bloom=new PIXI.Sprite(getPieceBloomTexturePixi(color));bloom.anchor.set(.5);bloom.alpha=.7;bloom.blendMode=PIXI.BLEND_MODES.ADD;root.addChild(bloom);const body=new PIXI.Sprite(getPieceTexturePixi(name,color));body.anchor.set(.5);root.addChild(body);const spec=new PIXI.Sprite(getPieceSpecTexturePixi(color));spec.anchor.set(.5);spec.alpha=.75;spec.blendMode=PIXI.BLEND_MODES.SCREEN;root.addChild(spec);return root;}",
            "function drawMarkersPixi(){if(!state||!state.recentMoves)return;const gl=new PIXI.Graphics();for(const m of state.recentMoves){const [fx,fy]=pos(m.fromRow,m.fromCol),[tx,ty]=pos(m.toRow,m.toCol);const color=m.color==='RED'?0xc6403c:0x262626;const alpha=m.order===1?.96:.82;gl.lineStyle(m.order===1?3.8:3,color,alpha);const dx=tx-fx,dy=ty-fy,len=Math.hypot(dx,dy);if(len>8){const ux=dx/len,uy=dy/len;const sx=fx+ux*(R-7),sy=fy+uy*(R-7),ex=tx-ux*(R-6),ey=ty-uy*(R-6);gl.moveTo(sx,sy);gl.lineTo(ex,ey);const hs=10,px=-uy,py=ux;gl.beginFill(color,alpha);gl.moveTo(ex,ey);gl.lineTo(ex-ux*hs+px*hs*0.62,ey-uy*hs+py*hs*0.62);gl.lineTo(ex-ux*hs-px*hs*0.62,ey-uy*hs-py*hs*0.62);gl.closePath();gl.endFill();}gl.lineStyle(2.2,color,alpha);gl.drawCircle(fx,fy,R-10);const s=(m.order===1)?R+9:R+6;gl.drawRect(tx-s,ty-s,s*2,s*2);}pixiLayers.markerLayer.addChild(gl);}",
            "function drawPiecesPixi(){if(!state)return;for(let r=0;r<10;r++){for(let c=0;c<9;c++){const p=state.board[r][c];if(!p)continue;const [x,y]=pos(r,c);const sp=makePieceLayerSprite(p.name,p.color);sp.x=x;sp.y=y;pixiLayers.pieceLayer.addChild(sp);}}}",
            "function drawSelectionPixi(){if(!state||state.reviewMode||state.selectedRow<0||state.selectedCol<0)return;const [x,y]=pos(state.selectedRow,state.selectedCol);const s=CELL/2-4;const g=new PIXI.Graphics();g.lineStyle(2.8,0x14a05a,.95);g.drawRect(x-s,y-s,s*2,s*2);g.lineStyle(1.4,0xa8e4c4,.95);g.drawRect(x-s+3,y-s+3,s*2-6,s*2-6);pixiLayers.uiLayer.addChild(g);}",
            "function drawTacticFlashPixi(){if(!tacticOverlayText||performance.now()>tacticOverlayUntil)return;const panel=new PIXI.Graphics();panel.beginFill(0x070a1a,.82);panel.drawRect(BASE_W/2-120,BASE_H/2-44,240,62);panel.endFill();panel.lineStyle(2,0xd8b86f,.9);panel.drawRect(BASE_W/2-120,BASE_H/2-44,240,62);const t=new PIXI.Text(tacticOverlayText,{fontFamily:'Microsoft YaHei UI',fontSize:36,fontWeight:'700',fill:0xffd86e});t.anchor.set(.5);t.x=BASE_W/2;t.y=BASE_H/2-2;if(window.gsap){gsap.fromTo(panel,{alpha:.55},{alpha:1,duration:.26,yoyo:true,repeat:1});gsap.fromTo(t.scale,{x:.9,y:.9},{x:1,y:1,duration:.24,ease:'back.out(1.4)'});}pixiLayers.uiLayer.addChild(panel,t);}",
            "function drawMoveAnimPixi(){if(!anim)return;const t=(performance.now()-anim.start)/anim.dur;if(t>=1){anim=null;return;}const k=Math.max(0,Math.min(1,t));const ease=1-Math.pow(1-k,3);const x=anim.fx+(anim.tx-anim.fx)*ease,y=anim.fy+(anim.ty-anim.fy)*ease;const sp=makePieceLayerSprite(anim.name,anim.color);sp.x=x;sp.y=y;sp.alpha=.92;sp.scale.set(1.02);pixiLayers.fxLayer.addChild(sp);scheduleRender();}",
            "function drawPixi(){if(!pixiReady||!pixiLayers)return;if(!pixiLayers.staticLayer.children.length)drawStaticPixi();[pixiLayers.markerLayer,pixiLayers.pieceLayer,pixiLayers.fxLayer,pixiLayers.uiLayer].forEach(layer=>{for(const n of layer.removeChildren()){if(n&&n.destroy)n.destroy({children:true});}});if(state){drawMarkersPixi();drawPiecesPixi();drawMoveAnimPixi();drawSelectionPixi();drawTacticFlashPixi();}pixiApp.render();}",
            "function makePieceSprite(name,color){const key=color+'|'+name;if(pieceSpriteCache[key])return pieceSpriteCache[key];const size=Math.ceil(R*2+12);const can=document.createElement('canvas');can.width=size;can.height=size;const g=can.getContext('2d');const cx=size/2,cy=size/2;g.fillStyle='rgba(13,8,0,.24)';g.beginPath();g.ellipse(cx+2,cy+3,R*0.98,R*0.82,0,0,Math.PI*2);g.fill();const rg=g.createRadialGradient(cx-9,cy-10,4,cx,cy,R);if(color==='RED'){rg.addColorStop(0,'#fff7ec');rg.addColorStop(0.62,'#efd8bd');rg.addColorStop(1,'#d1ad86');}else{rg.addColorStop(0,'#ffffff');rg.addColorStop(0.62,'#ebe7df');rg.addColorStop(1,'#c7c2b8');}g.fillStyle=rg;g.beginPath();g.arc(cx,cy,R,0,Math.PI*2);g.fill();const sh=g.createLinearGradient(cx,cy-R*0.2,cx,cy+R);sh.addColorStop(0,'rgba(0,0,0,0)');sh.addColorStop(1,'rgba(0,0,0,.22)');g.fillStyle=sh;g.beginPath();g.arc(cx,cy,R,0,Math.PI*2);g.fill();g.strokeStyle='rgba(88,58,29,.94)';g.lineWidth=2.4;g.beginPath();g.arc(cx,cy,R,0,Math.PI*2);g.stroke();g.strokeStyle=(color==='RED')?'#d24c45':'#252525';g.lineWidth=2.8;g.beginPath();g.arc(cx,cy,R-3,0,Math.PI*2);g.stroke();g.strokeStyle='rgba(229,207,160,.9)';g.lineWidth=1.2;g.beginPath();g.arc(cx,cy,R-6,0,Math.PI*2);g.stroke();g.strokeStyle='rgba(255,248,224,.72)';g.lineWidth=1;g.beginPath();g.arc(cx-1,cy-1,R-9,Math.PI*1.05,Math.PI*1.82);g.stroke();g.fillStyle='rgba(255,255,255,.22)';g.beginPath();g.arc(cx-8,cy-10,7,0,Math.PI*2);g.fill();g.font='bold 32px KaiTi';g.lineWidth=0.9;g.strokeStyle='rgba(255,244,220,.22)';const w=g.measureText(name).width;g.strokeText(name,cx-w/2,cy+11);g.fillStyle=(color==='RED')?'#c43d36':'#1b1b1b';g.fillText(name,cx-w/2,cy+11);pieceSpriteCache[key]=can;return can;}function drawPieceDisc(x,y,name,color){const s=makePieceSprite(name,color);ctx.drawImage(s,x-s.width/2,y-s.height/2);}function drawPieces(){for(let r=0;r<10;r++){for(let c=0;c<9;c++){const p=state.board[r][c];if(!p)continue;const [x,y]=pos(r,c);drawPieceDisc(x,y,p.name,p.color);}}}",
            "function drawMarkers(){if(!state.recentMoves)return;for(const m of state.recentMoves){const [fx,fy]=pos(m.fromRow,m.fromCol),[tx,ty]=pos(m.toRow,m.toCol);const color=m.color==='RED'?'rgba(198,64,60,.94)':'rgba(35,35,35,.94)';const glow=m.color==='RED'?'rgba(255,134,126,.22)':'rgba(160,160,160,.18)';ctx.fillStyle=glow;ctx.beginPath();ctx.arc(fx,fy,R-5,0,Math.PI*2);ctx.fill();const dx=tx-fx,dy=ty-fy,len=Math.hypot(dx,dy);if(len>8){const ux=dx/len,uy=dy/len;const sx=fx+ux*(R-7),sy=fy+uy*(R-7),ex=tx-ux*(R-6),ey=ty-uy*(R-6);ctx.strokeStyle=color;ctx.lineWidth=(m.order===1)?3.8:3;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(sx,sy);ctx.lineTo(ex,ey);ctx.stroke();const hs=10,px=-uy,py=ux;const ax1=ex-ux*hs+px*hs*0.62,ay1=ey-uy*hs+py*hs*0.62,ax2=ex-ux*hs-px*hs*0.62,ay2=ey-uy*hs-py*hs*0.62;ctx.beginPath();ctx.moveTo(ex,ey);ctx.lineTo(ax1,ay1);ctx.lineTo(ax2,ay2);ctx.closePath();ctx.fillStyle=color;ctx.fill();}ctx.strokeStyle=color;ctx.lineWidth=2.5;ctx.beginPath();ctx.arc(fx,fy,R-10,0,Math.PI*2);ctx.stroke();const s=(m.order===1)?R+9:R+6;ctx.lineWidth=(m.order===1)?3.6:2.8;ctx.strokeRect(tx-s,ty-s,s*2,s*2);const br=(m.order===1)?11:9,bx=tx+s-4,by=ty-s+4;ctx.fillStyle='rgba(251,243,224,.96)';ctx.beginPath();ctx.arc(bx,by,br,0,Math.PI*2);ctx.fill();ctx.strokeStyle=color;ctx.lineWidth=2;ctx.stroke();ctx.fillStyle='rgba(22,22,22,.95)';ctx.font=(m.order===1)?'bold 13px Consolas':'bold 12px Consolas';ctx.fillText(String(m.order),bx-3,by+4);}}",
            "function drawSelection(){if(state.reviewMode)return;if(state.selectedRow>=0&&state.selectedCol>=0){const [x,y]=pos(state.selectedRow,state.selectedCol);const s=CELL/2-4;ctx.strokeStyle='rgba(20,160,90,.92)';ctx.lineWidth=2.8;ctx.strokeRect(x-s,y-s,s*2,s*2);ctx.strokeStyle='rgba(168,228,196,.95)';ctx.lineWidth=1.6;ctx.strokeRect(x-s+3,y-s+3,s*2-6,s*2-6);}}",
            "function drawTacticFlash(){if(!tacticOverlayText||performance.now()>tacticOverlayUntil)return;ctx.fillStyle='rgba(7,10,26,.82)';ctx.fillRect(BASE_W/2-120,BASE_H/2-44,240,62);ctx.strokeStyle='#d8b86f';ctx.lineWidth=2;ctx.strokeRect(BASE_W/2-120,BASE_H/2-44,240,62);ctx.font='bold 36px Microsoft YaHei UI';ctx.fillStyle='#ffd86e';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(tacticOverlayText,BASE_W/2,BASE_H/2-2);ctx.textAlign='start';ctx.textBaseline='alphabetic';}function fmtSec(v){if(v==null||v<0)return '--:--';const m=Math.floor(v/60),s=v%60;return String(m).padStart(2,'0')+':'+String(s).padStart(2,'0');}function primeAnim(){if(!state||!state.recentMoves||!state.recentMoves.length)return;const m=state.recentMoves[0];const k=[m.fromRow,m.fromCol,m.toRow,m.toCol,m.color].join('-');if(k===animKey)return;animKey=k;const p=state.board[m.toRow][m.toCol];if(!p)return;const [fx,fy]=pos(m.fromRow,m.fromCol),[tx,ty]=pos(m.toRow,m.toCol);anim={fx,fy,tx,ty,name:p.name,color:p.color,start:performance.now(),dur:120};}function drawMoveAnim(){if(!anim)return;const t=(performance.now()-anim.start)/anim.dur;if(t>=1){anim=null;return;}const k=Math.max(0,Math.min(1,t));const ease=1-Math.pow(1-k,3);const x=anim.fx+(anim.tx-anim.fx)*ease,y=anim.fy+(anim.ty-anim.fy)*ease;drawPieceDisc(x,y,anim.name,anim.color);scheduleRender();}function handleSounds(){if(!state||state.reviewMode||!state.recentMoves||!state.recentMoves.length)return;const m=state.recentMoves[0];const key=[m.fromRow,m.fromCol,m.toRow,m.toCol,m.color].join('-');const isMateCue=(state.tacticText==='绝杀')||(state.gameOver&&(state.result||'').indexOf('胜')>=0);if(key!==lastMoveSoundKey){lastMoveSoundKey=key;if(isMateCue){lastMateSoundKey=key;playSound(mateAudio);}else{playSound(moveAudio);}return;}if(isMateCue&&key!==lastMateSoundKey){lastMateSoundKey=key;playSound(mateAudio);}}function stateStamp(s){if(!s)return'';const m=(s.recentMoves&&s.recentMoves.length)?s.recentMoves[0]:null;return [s.seq,s.started,s.mode,s.currentTurn,s.gameOver,s.result,s.selectedRow,s.selectedCol,s.reviewMode,s.reviewMoveIndex,s.reviewMaxMove,s.tacticSeq,m?m.fromRow:'',m?m.fromCol:'',m?m.toRow:'',m?m.toCol:''].join('|');}",
            "async function api(path){const base=withSid(path);const q=base.includes('?')?'&':'?';const url=base+q+'_t='+Date.now();const res=await fetch(url,{cache:'no-store'});return await res.json();}",
            "function applyState(data){const seq=(data&&data.seq)||0;if(seq&&seq<lastAppliedSeq)return;lastAppliedSeq=Math.max(lastAppliedSeq,seq);const prev=lastStateStamp;const wasStarted=!!(state&&state.started);const wasGameOver=!!(state&&state.gameOver);state=data;const tq=state.tacticSeq||0;if(tq>lastTacticSeq&&state.tacticText){lastTacticSeq=tq;const tt=state.tacticText||'';if(tt.indexOf('将军')>=0||tt==='绝杀'){tacticOverlayText='';tacticOverlayUntil=0;}else{tacticOverlayText=tt;tacticOverlayUntil=performance.now()+500;}}if(!state.reviewMode&&state.started&&!wasStarted&&seq!==lastOpeningSeq){lastOpeningSeq=seq;playOpeningCeremony();}if(!state.reviewMode&&tq>lastCheckSeq&&state.tacticText&&state.tacticText.indexOf('将军')>=0){lastCheckSeq=tq;playCheckCeremony();}if(!state.reviewMode&&(((tq>lastMateSeq)&&state.tacticText==='绝杀')||(!wasGameOver&&state.gameOver&&(state.result||'').indexOf('胜')>=0))){lastMateSeq=tq||seq;playMateCeremony();}ui.firstHand.disabled=ui.mode.value!=='pvc';setTxt(ui.statusTag,'状态: '+(!state.started?'待开始':(state.gameOver?(state.result||'结束'):(state.reviewMode?'回顾模式':'进行中'))));const sr=state.stepRemainSec;setTxt(ui.stepTop,'当前步时倒计时: '+((sr!=null&&sr>=0)?(sr+'s'):'--s'));setTxt(ui.totalTop,'总时 红:'+fmtSec(state.redTotalSec)+' 黑:'+fmtSec(state.blackTotalSec));const humanTxt=state.pvcHumanColor==='BLACK'?' / 玩家执黑':' / 玩家执红';setTxt(ui.modeTag,'模式: '+(state.mode==='PVC'?'人机':'双人')+' / '+state.difficultyText+(state.mode==='PVC'?humanTxt:''));setTxt(ui.endgameTag,'残局: '+(state.endgame||'标准开局'));setTxt(ui.drawReasonTag,'和棋原因: '+(state.drawReason&&state.drawReason.length?state.drawReason:'-'));setTxt(ui.reviewTag,state.reviewMode?('回顾: 第 '+state.reviewMoveIndex+' / '+state.reviewMaxMove+' 步'):'回顾: 关闭');setTxt(ui.info,!state.started?'请点击“新开一局”开始':(state.gameOver?(state.result||'对局结束'):('当前回合: '+(state.currentTurn==='RED'?'红方':'黑方'))));setDis(ui.undo,!state.started||state.reviewMode||state.gameOver);setDis(ui.surrender,!state.started||state.reviewMode||state.gameOver);setDis(ui.drawBtn,!state.canDraw);setDis(ui.reviewStart,!state.started||!state.canReview||state.reviewMode);setDis(ui.reviewPrev,!state.reviewMode||state.reviewMoveIndex<=0);setDis(ui.reviewNext,!state.reviewMode||state.reviewMoveIndex>=state.reviewMaxMove);setDis(ui.reviewExit,!state.reviewMode);handleSounds();const stamp=stateStamp(state);const changed=stamp!==prev;lastStateStamp=stamp;if(changed){primeAnim();scheduleRender();}}",
            "async function refresh(){if(pending)return;pending=true;const seq=++reqSeq;const t0=performance.now();try{const data=await api('/api/state');if(seq!==reqSeq)return;applyState(data);}finally{pending=false;const cost=performance.now()-t0;if(cost>120){api('/api/perf/event?type=state_fetch&cost='+Math.round(cost)).catch(()=>{});}}}",
            "async function act(path){const data=await api(path);applyState(data);}function enqueueAct(path){actionQueue=actionQueue.then(()=>act(path)).catch(()=>{});return actionQueue;}",
            "document.addEventListener('pointerdown',unlockAudio,{once:true});canvas.addEventListener('pointerdown',e=>{if(state&&(!state.started||state.reviewMode||state.gameOver))return;e.preventDefault();const rect=canvas.getBoundingClientRect();const sx=BASE_W/rect.width,sy=BASE_H/rect.height;const x=(e.clientX-rect.left)*sx,y=(e.clientY-rect.top)*sy;const g=pickGrid(x,y);if(!g)return;const p=state&&state.board&&state.board[g.row]?state.board[g.row][g.col]:null;if(state&&state.selectedRow<0&&p&&p.color===state.currentTurn&&(state.mode!=='PVC'||p.color===state.pvcHumanColor)){state.selectedRow=g.row;state.selectedCol=g.col;scheduleRender();}enqueueAct('/api/click?row='+g.row+'&col='+g.col);},{passive:false});",
            "document.getElementById('newGame').addEventListener('click',()=>{const mode=ui.mode.value,d=document.getElementById('difficulty').value,h=ui.firstHand.value;enqueueAct('/api/new?mode='+mode+'&difficulty='+d+'&humanFirst='+h);});document.querySelectorAll('.egBtn').forEach(btn=>btn.addEventListener('click',()=>{const mode=ui.mode.value,d=document.getElementById('difficulty').value,h=ui.firstHand.value,name=encodeURIComponent(btn.dataset.name);enqueueAct('/api/endgame?name='+name+'&mode='+mode+'&difficulty='+d+'&humanFirst='+h);}));",
            "document.getElementById('undo').addEventListener('click',()=>enqueueAct('/api/undo'));document.getElementById('surrender').addEventListener('click',()=>{if(confirm('确定要认输吗？')){enqueueAct('/api/surrender');}});document.getElementById('drawBtn').addEventListener('click',()=>{if(confirm('确认本局和棋？')){enqueueAct('/api/draw');}});document.getElementById('soundToggle').addEventListener('click',toggleSound);",
            "document.getElementById('reviewStart').addEventListener('click',()=>enqueueAct('/api/review/start'));",
            "document.getElementById('reviewPrev').addEventListener('click',()=>enqueueAct('/api/review/prev'));",
            "document.getElementById('reviewNext').addEventListener('click',()=>enqueueAct('/api/review/next'));",
            "document.getElementById('reviewExit').addEventListener('click',()=>enqueueAct('/api/review/exit'));",
            "ui.mode.addEventListener('change',()=>{ui.firstHand.disabled=ui.mode.value!=='pvc';});",
            "function pollDelay(){if(document.hidden)return 900;if(!state||!state.started)return 120;if(state.mode==='PVC'&&state.currentTurn!==state.pvcHumanColor)return 58;return 82;}function schedulePoll(){clearTimeout(pollTimer);pollTimer=setTimeout(async()=>{await refresh();schedulePoll();},pollDelay());}document.addEventListener('visibilitychange',schedulePoll);initPixiRenderer();drawStatic();setupCanvas();runCourtAnimations();syncSoundToggle();refresh().finally(schedulePoll);",
            "</script>",
            "</body>",
            "</html>");
    }
}






















