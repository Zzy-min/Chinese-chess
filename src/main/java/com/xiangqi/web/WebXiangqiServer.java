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
    private static final long MIN_MOVE_INTERVAL_MS = 500L;
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
        Session session = getSession(exchange);
        synchronized (session) {
            session.tick();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }

    private void handleNewGame(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String mode = query.getOrDefault("mode", "pvp");
        String difficulty = query.getOrDefault("difficulty", "MEDIUM");
        boolean humanFirst = !"false".equalsIgnoreCase(query.getOrDefault("humanFirst", "true"));
        Session session = getSession(exchange);
        synchronized (session) {
            session.reset("pvc".equalsIgnoreCase(mode), parseDifficulty(difficulty), humanFirst);
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }

    private void handleEndgame(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String name = query.getOrDefault("name", "七星聚会");
        String mode = query.getOrDefault("mode", "pvp");
        String difficulty = query.getOrDefault("difficulty", "MEDIUM");
        boolean humanFirst = !"false".equalsIgnoreCase(query.getOrDefault("humanFirst", "true"));
        Session session = getSession(exchange);
        synchronized (session) {
            session.loadEndgame(name, "pvc".equalsIgnoreCase(mode), parseDifficulty(difficulty), humanFirst);
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }
    private void handleClick(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        int row = parseInt(query.get("row"), -1);
        int col = parseInt(query.get("col"), -1);
        Session session = getSession(exchange);
        synchronized (session) {
            session.click(row, col);
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }

    private void handleUndo(HttpExchange exchange) throws IOException {
        Session session = getSession(exchange);
        synchronized (session) {
            session.undo();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }


    private void handleSurrender(HttpExchange exchange) throws IOException {
        Session session = getSession(exchange);
        synchronized (session) {
            session.surrender();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }

    private void handleDraw(HttpExchange exchange) throws IOException {
        Session session = getSession(exchange);
        synchronized (session) {
            session.draw();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }

    private void handleReviewStart(HttpExchange exchange) throws IOException {
        Session session = getSession(exchange);
        synchronized (session) {
            session.startReview();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }

    private void handleReviewExit(HttpExchange exchange) throws IOException {
        Session session = getSession(exchange);
        synchronized (session) {
            session.exitReview();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }

    private void handleReviewPrev(HttpExchange exchange) throws IOException {
        Session session = getSession(exchange);
        synchronized (session) {
            session.reviewPrev();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }

    private void handleReviewNext(HttpExchange exchange) throws IOException {
        Session session = getSession(exchange);
        synchronized (session) {
            session.reviewNext();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
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
                aiDueAt = System.currentTimeMillis() + MIN_MOVE_INTERVAL_MS;
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
                aiDueAt = System.currentTimeMillis() + MIN_MOVE_INTERVAL_MS;
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
                    aiDueAt = System.currentTimeMillis() + MIN_MOVE_INTERVAL_MS;
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
            return System.currentTimeMillis() - lastMoveAt >= MIN_MOVE_INTERVAL_MS;
        }

        private void markMove() {
            lastMoveAt = System.currentTimeMillis();
        }

        private void updateTacticFlash() {
            String t = TacticDetector.detect(board);
            if (t != null && !t.isEmpty()) {
                tacticText = t;
                tacticUntil = System.currentTimeMillis() + 500;
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
            String tip = System.currentTimeMillis() <= tacticUntil ? tacticText : "";
            sb.append("\"tacticText\":\"").append(escape(tip)).append("\",");
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
            "    :root{--line:#cfa85b;--bg1:#e4d4bc;--bg2:#f2e4cc;--ink:#58371f;--red:#c6403c;--blue:#24356c;}",
            "    body{margin:0;min-height:100vh;display:grid;place-items:center;background-image:linear-gradient(180deg,rgba(13,18,36,.28),rgba(36,21,12,.42)),url('https://commons.wikimedia.org/wiki/Special:FilePath/Beijing%20forbidden%20city%20roof-20071018-RM-144550.jpg');background-size:cover;background-position:center top;background-repeat:no-repeat;font-family:Microsoft YaHei UI,sans-serif;color:var(--ink);overflow:auto;-webkit-text-size-adjust:100%;}",
            "    .wrap{width:min(1180px,94vw);height:calc(100vh - 38px);background:linear-gradient(180deg,rgba(255,248,236,.94),rgba(245,226,196,.93));border:1px solid #d9bd93;border-radius:14px;box-shadow:0 18px 38px rgba(16,11,7,.35);padding:10px;box-sizing:border-box;position:relative;}",
            "    .topMeta{display:flex;justify-content:space-between;align-items:center;margin:0 2px 8px;color:#6a4629;font-weight:700;}",
            "    .layout{display:grid;grid-template-columns:1fr 300px;gap:10px;align-items:stretch;height:calc(100% - 30px);}",
            "    .boardCard{background:linear-gradient(180deg,#efe5d2,#ead8bb);border:1px solid #d6b98f;border-radius:10px;padding:6px;display:flex;align-items:center;justify-content:center;overflow:hidden;position:relative;}",
            "    .boardCard:before{content:'';position:absolute;inset:0;background:repeating-linear-gradient(90deg,rgba(123,77,39,.05)0,rgba(123,77,39,.05)2px,transparent 2px,transparent 14px);pointer-events:none;}",
            "    .side{background:#fffdf8;border:1px solid #e1c9a5;border-radius:10px;padding:12px;overflow:auto;}",
            "    canvas{display:block;width:100%;height:auto;border:2px solid #a67b4a;border-radius:8px;background:var(--blue);image-rendering:auto;touch-action:none;}",
            "    .title{font-size:15px;font-weight:700;margin-bottom:10px;}",
            "    .row{display:flex;gap:8px;margin-bottom:10px;}",
            "    .row>select,.row>button{flex:1;}",
            "    select,button{border:1px solid #cda67b;border-radius:8px;background:#fff7ea;color:#5b371f;padding:8px 10px;cursor:pointer;}",
            "    button:hover{background:#ffefd8;} button:disabled{opacity:.45;cursor:not-allowed;}",
            "    .tag{display:inline-block;background:#f6ebdb;border:1px solid #d6b890;border-radius:14px;padding:3px 10px;margin:4px 0;font-size:12px;}",
            "    .log{font-size:13px;line-height:1.7;color:#6b4a2f;margin-top:4px;}",
            "    .egGrid{display:grid;grid-template-columns:1fr 1fr;gap:6px;margin:8px 0 12px;}",
            "    .egBtn{padding:7px 8px;font-size:12px;}",
            "    @media (max-width:1020px){body{overflow:auto}.wrap{height:auto;min-height:100vh;border-radius:0;width:100vw}.layout{grid-template-columns:1fr;height:auto}}",
            "    @media (max-width:768px){.wrap{padding:8px}.topMeta{flex-direction:column;align-items:flex-start;gap:4px;font-size:12px}.layout{gap:8px}.side{padding:10px}.row{margin-bottom:8px}select,button{min-height:40px;font-size:14px}}",
            "  </style>",
            "</head>",
            "<body>",
            "  <div class=\"wrap\">",
            "    <div class=\"topMeta\"><div>轻·象棋 · 宫阙版</div><div id=\"totalTop\">总时 红:--:-- 黑:--:--</div><div id=\"stepTop\">当前步时倒计时: --s</div></div>",
            "    <div class=\"layout\">",
            "      <div class=\"boardCard\"><canvas id=\"board\" width=\"800\" height=\"900\"></canvas></div>",
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
            "<script>",
            "const BASE_W=800,BASE_H=900,CELL=68,MARGIN=98,R=29;",
            "const canvas=document.getElementById('board'); const ctx=canvas.getContext('2d',{alpha:true});",
            "let state=null,reqSeq=0,pending=false,needRender=false,scale=1,dpr=Math.min(1.2,Math.max(1,window.devicePixelRatio||1)),anim=null,animKey='',pollTimer=0,lastStateStamp='',actionQueue=Promise.resolve();",
            "const SID_KEY='xq_sid';function makeSid(){if(window.crypto&&window.crypto.randomUUID)return window.crypto.randomUUID().replace(/-/g,'');return String(Date.now())+Math.random().toString(16).slice(2);}const sid=(()=>{let v=sessionStorage.getItem(SID_KEY);if(!v){v=makeSid();sessionStorage.setItem(SID_KEY,v);}return v;})();function withSid(path){const sep=path.includes('?')?'&':'?';return path+sep+'sid='+encodeURIComponent(sid);}",
            "const moveAudio=new Audio('/assets/audio/move.wav');const mateAudio=new Audio('/assets/audio/mate.wav');moveAudio.preload='auto';mateAudio.preload='auto';moveAudio.volume=0.92;mateAudio.volume=0.98;let audioUnlocked=false,lastMoveSoundKey='',lastMateSoundKey='';let soundEnabled=(localStorage.getItem('xq_sound_enabled')??'1')!=='0';",
            "const ui={statusTag:document.getElementById('statusTag'),stepTop:document.getElementById('stepTop'),totalTop:document.getElementById('totalTop'),modeTag:document.getElementById('modeTag'),endgameTag:document.getElementById('endgameTag'),drawReasonTag:document.getElementById('drawReasonTag'),reviewTag:document.getElementById('reviewTag'),info:document.getElementById('info'),undo:document.getElementById('undo'),surrender:document.getElementById('surrender'),drawBtn:document.getElementById('drawBtn'),reviewStart:document.getElementById('reviewStart'),reviewPrev:document.getElementById('reviewPrev'),reviewNext:document.getElementById('reviewNext'),reviewExit:document.getElementById('reviewExit'),mode:document.getElementById('mode'),firstHand:document.getElementById('firstHand')};function setTxt(el,v){if(el&&el.textContent!==v)el.textContent=v;}function setDis(el,v){if(el&&el.disabled!==v)el.disabled=v;}",
            "const cache=document.createElement('canvas'); cache.width=BASE_W; cache.height=BASE_H; const cctx=cache.getContext('2d');",
            "const BOARD_TEXTURE_URL='https://commons.wikimedia.org/wiki/Special:FilePath/Xiangqi%20board.svg';const boardTex=new Image();let boardTexReady=false;boardTex.crossOrigin='anonymous';boardTex.onload=()=>{boardTexReady=true;drawStatic();scheduleRender();};boardTex.src=BOARD_TEXTURE_URL;",
            "function setupCanvas(){const parentW=Math.max(320,canvas.parentElement.clientWidth-8);const viewH=Math.max(460,window.innerHeight-64);const fitWByH=Math.floor(viewH*BASE_W/BASE_H);const targetW=Math.max(320,Math.min(parentW,fitWByH));const cssW=Math.round(targetW*0.92);const cssH=Math.round(cssW*BASE_H/BASE_W);scale=cssW/BASE_W;canvas.style.width=cssW+'px';canvas.style.height=cssH+'px';canvas.width=Math.round(cssW*dpr);canvas.height=Math.round(cssH*dpr);ctx.setTransform(dpr*scale,0,0,dpr*scale,0,0);scheduleRender();}",
            "window.addEventListener('resize', setupCanvas);",
            "function syncSoundToggle(){const btn=document.getElementById('soundToggle');if(btn)btn.textContent='音效:'+(soundEnabled?'开':'关');}function toggleSound(){soundEnabled=!soundEnabled;localStorage.setItem('xq_sound_enabled',soundEnabled?'1':'0');syncSoundToggle();}function unlockAudio(){if(audioUnlocked)return;audioUnlocked=true;[moveAudio,mateAudio].forEach(a=>{const p=a.play();if(p&&p.catch){p.then(()=>{a.pause();a.currentTime=0;}).catch(()=>{});}else{a.pause();a.currentTime=0;}});}function playSound(a){if(!soundEnabled||!audioUnlocked)return;try{a.pause();a.currentTime=0;const p=a.play();if(p&&p.catch)p.catch(()=>{});}catch(_e){}}",
            "const isFlipped=()=>{if(!state||state.reviewMode)return false;if(state.mode==='PVP')return state.currentTurn==='BLACK';return state.mode==='PVC'&&state.pvcHumanColor==='BLACK';};const vr=r=>isFlipped()?9-r:r;const vc=c=>isFlipped()?8-c:c;const br=r=>isFlipped()?9-r:r;const bc=c=>isFlipped()?8-c:c;const pos=(r,c)=>[MARGIN+vc(c)*CELL,MARGIN+vr(r)*CELL];function pickGrid(x,y){const vcol=Math.round((x-MARGIN)/CELL),vrow=Math.round((y-MARGIN)/CELL);const minX=MARGIN-R,maxX=MARGIN+8*CELL+R,minY=MARGIN-R,maxY=MARGIN+9*CELL+R;if(x<minX||x>maxX||y<minY||y>maxY)return null;const cc=Math.max(0,Math.min(8,vcol)),rr=Math.max(0,Math.min(9,vrow));return {row:br(rr),col:bc(cc)};}",
            "function drawStatic(){cctx.clearRect(0,0,BASE_W,BASE_H);const bx=MARGIN-34,by=MARGIN-34,bw=8*CELL+68,bh=9*CELL+68;if(boardTexReady){cctx.drawImage(boardTex,bx,by,bw,bh);}else{const g=cctx.createLinearGradient(0,0,0,BASE_H);g.addColorStop(0,'#2c3f88');g.addColorStop(1,'#1f2f66');cctx.fillStyle=g;cctx.fillRect(bx,by,bw,bh);}cctx.fillStyle='rgba(20,33,88,.34)';cctx.fillRect(MARGIN-2,MARGIN-2,8*CELL+4,9*CELL+4);cctx.strokeStyle='#7f4f25';cctx.lineWidth=9;cctx.strokeRect(bx-7,by-7,bw+14,bh+14);cctx.strokeStyle='#dfbc77';cctx.lineWidth=3;cctx.strokeRect(bx,by,bw,bh);const corner=(x,y,sx,sy)=>{cctx.strokeStyle='rgba(225,196,130,.96)';cctx.lineWidth=2;cctx.beginPath();cctx.moveTo(x,y);cctx.lineTo(x+sx*18,y);cctx.lineTo(x+sx*18,y+sy*18);cctx.moveTo(x+sx*6,y);cctx.lineTo(x+sx*24,y);cctx.moveTo(x,y+sy*6);cctx.lineTo(x,y+sy*24);cctx.stroke();};corner(bx+10,by+10,1,1);corner(bx+bw-10,by+10,-1,1);corner(bx+10,by+bh-10,1,-1);corner(bx+bw-10,by+bh-10,-1,-1);const topY=by-20;const botY=by+bh+20;cctx.strokeStyle='rgba(108,64,28,.9)';cctx.lineWidth=3;cctx.beginPath();cctx.moveTo(bx+30,topY);cctx.bezierCurveTo(bx+bw*0.32,topY-16,bx+bw*0.68,topY-16,bx+bw-30,topY);cctx.moveTo(bx+30,botY);cctx.bezierCurveTo(bx+bw*0.32,botY+16,bx+bw*0.68,botY+16,bx+bw-30,botY);cctx.stroke();cctx.strokeStyle='rgba(213,178,106,.86)';cctx.lineWidth=2;for(let r=0;r<10;r++){const y=MARGIN+r*CELL;cctx.beginPath();cctx.moveTo(MARGIN,y);cctx.lineTo(MARGIN+8*CELL,y);cctx.stroke();}for(let c=0;c<9;c++){const x=MARGIN+c*CELL;cctx.beginPath();if(c===0||c===8){cctx.moveTo(x,MARGIN);cctx.lineTo(x,MARGIN+9*CELL);}else{cctx.moveTo(x,MARGIN);cctx.lineTo(x,MARGIN+4*CELL);cctx.moveTo(x,MARGIN+5*CELL);cctx.lineTo(x,MARGIN+9*CELL);}cctx.stroke();}cctx.beginPath();cctx.moveTo(MARGIN+3*CELL,MARGIN);cctx.lineTo(MARGIN+5*CELL,MARGIN+2*CELL);cctx.moveTo(MARGIN+5*CELL,MARGIN);cctx.lineTo(MARGIN+3*CELL,MARGIN+2*CELL);cctx.moveTo(MARGIN+3*CELL,MARGIN+7*CELL);cctx.lineTo(MARGIN+5*CELL,MARGIN+9*CELL);cctx.moveTo(MARGIN+5*CELL,MARGIN+7*CELL);cctx.lineTo(MARGIN+3*CELL,MARGIN+9*CELL);cctx.stroke();const ry=MARGIN+4*CELL+3;cctx.shadowColor='rgba(0,0,0,.26)';cctx.shadowBlur=10;cctx.fillStyle='rgba(35,54,124,.92)';cctx.beginPath();cctx.roundRect(MARGIN+12,ry,8*CELL-24,CELL-8,22);cctx.fill();cctx.shadowBlur=0;cctx.strokeStyle='rgba(213,178,106,.44)';cctx.beginPath();cctx.moveTo(MARGIN, MARGIN+4*CELL);cctx.lineTo(MARGIN+8*CELL,MARGIN+4*CELL);cctx.moveTo(MARGIN,MARGIN+5*CELL);cctx.lineTo(MARGIN+8*CELL,MARGIN+5*CELL);cctx.stroke();cctx.font='bold 45px KaiTi';cctx.fillStyle='#d8b574';cctx.fillText('楚 河',MARGIN+CELL-10,MARGIN+4*CELL+44);cctx.fillText('汉 界',MARGIN+5*CELL+8,MARGIN+4*CELL+44);}",
            "function scheduleRender(){if(needRender)return;needRender=true;requestAnimationFrame(()=>{needRender=false;draw();});}",
            "function draw(){ctx.clearRect(0,0,BASE_W,BASE_H);ctx.drawImage(cache,0,0);if(!state)return;drawMarkers();drawPieces();drawMoveAnim();drawSelection();drawTacticFlash();}",
            "function drawPieceDisc(x,y,name,color){ctx.fillStyle='rgba(13,8,0,.24)';ctx.beginPath();ctx.ellipse(x+2,y+3,R*0.98,R*0.82,0,0,Math.PI*2);ctx.fill();const g=ctx.createRadialGradient(x-9,y-10,4,x,y,R);if(color==='RED'){g.addColorStop(0,'#fff7ec');g.addColorStop(0.62,'#efd8bd');g.addColorStop(1,'#d1ad86');}else{g.addColorStop(0,'#ffffff');g.addColorStop(0.62,'#ebe7df');g.addColorStop(1,'#c7c2b8');}ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,R,0,Math.PI*2);ctx.fill();ctx.strokeStyle='rgba(88,58,29,.94)';ctx.lineWidth=2.4;ctx.beginPath();ctx.arc(x,y,R,0,Math.PI*2);ctx.stroke();ctx.strokeStyle=(color==='RED')?'#d24c45':'#252525';ctx.lineWidth=2.8;ctx.beginPath();ctx.arc(x,y,R-3,0,Math.PI*2);ctx.stroke();ctx.strokeStyle='rgba(229,207,160,.9)';ctx.lineWidth=1.2;ctx.beginPath();ctx.arc(x,y,R-6,0,Math.PI*2);ctx.stroke();ctx.fillStyle='rgba(255,255,255,.22)';ctx.beginPath();ctx.arc(x-8,y-10,7,0,Math.PI*2);ctx.fill();ctx.font='bold 32px KaiTi';ctx.lineWidth=0.9;ctx.strokeStyle='rgba(255,244,220,.22)';const w=ctx.measureText(name).width;ctx.strokeText(name,x-w/2,y+11);ctx.fillStyle=(color==='RED')?'#c43d36':'#1b1b1b';ctx.fillText(name,x-w/2,y+11);}function drawPieces(){for(let r=0;r<10;r++){for(let c=0;c<9;c++){const p=state.board[r][c];if(!p)continue;const [x,y]=pos(r,c);drawPieceDisc(x,y,p.name,p.color);}}}",
            "function drawMarkers(){if(!state.recentMoves)return;for(const m of state.recentMoves){const [fx,fy]=pos(m.fromRow,m.fromCol),[tx,ty]=pos(m.toRow,m.toCol);const color=m.color==='RED'?'rgba(198,64,60,.96)':'rgba(35,35,35,.96)';ctx.strokeStyle=color;ctx.lineWidth=2.5;ctx.beginPath();ctx.arc(fx,fy,R-10,0,Math.PI*2);ctx.stroke();const s=(m.order===1)?R+9:R+5;ctx.lineWidth=3;ctx.strokeRect(tx-s,ty-s,s*2,s*2);ctx.fillStyle='rgba(255,248,230,.95)';ctx.font='bold 16px Consolas';ctx.fillText(String(m.order),tx-4,ty-s-6);}}",
            "function drawSelection(){if(state.reviewMode)return;if(state.selectedRow>=0&&state.selectedCol>=0){const [x,y]=pos(state.selectedRow,state.selectedCol);ctx.fillStyle='rgba(0,160,70,.20)';ctx.fillRect(x-CELL/2+3,y-CELL/2+3,CELL-6,CELL-6);}}",
            "function drawTacticFlash(){if(!state.tacticText)return;ctx.fillStyle='rgba(7,10,26,.82)';ctx.fillRect(BASE_W/2-120,BASE_H/2-44,240,62);ctx.strokeStyle='#d8b86f';ctx.lineWidth=2;ctx.strokeRect(BASE_W/2-120,BASE_H/2-44,240,62);ctx.font='bold 36px Microsoft YaHei UI';ctx.fillStyle='#ffd86e';const w=ctx.measureText(state.tacticText).width;ctx.fillText(state.tacticText,(BASE_W-w)/2,BASE_H/2);}function fmtSec(v){if(v==null||v<0)return '--:--';const m=Math.floor(v/60),s=v%60;return String(m).padStart(2,'0')+':'+String(s).padStart(2,'0');}function primeAnim(){if(!state||!state.recentMoves||!state.recentMoves.length)return;const m=state.recentMoves[0];const k=[m.fromRow,m.fromCol,m.toRow,m.toCol,m.color].join('-');if(k===animKey)return;animKey=k;const p=state.board[m.toRow][m.toCol];if(!p)return;const [fx,fy]=pos(m.fromRow,m.fromCol),[tx,ty]=pos(m.toRow,m.toCol);anim={fx,fy,tx,ty,name:p.name,color:p.color,start:performance.now(),dur:120};}function drawMoveAnim(){if(!anim)return;const t=(performance.now()-anim.start)/anim.dur;if(t>=1){anim=null;return;}const k=Math.max(0,Math.min(1,t));const ease=1-Math.pow(1-k,3);const x=anim.fx+(anim.tx-anim.fx)*ease,y=anim.fy+(anim.ty-anim.fy)*ease;drawPieceDisc(x,y,anim.name,anim.color);scheduleRender();}function handleSounds(){if(!state||state.reviewMode||state.gameOver||!state.recentMoves||!state.recentMoves.length)return;const m=state.recentMoves[0];const key=[m.fromRow,m.fromCol,m.toRow,m.toCol,m.color].join('-');if(key!==lastMoveSoundKey){lastMoveSoundKey=key;if(state.tacticText==='绝杀'){lastMateSoundKey=key;playSound(mateAudio);}else{playSound(moveAudio);}return;}if(state.tacticText==='绝杀'&&key!==lastMateSoundKey){lastMateSoundKey=key;playSound(mateAudio);}}function stateStamp(s){if(!s)return'';const m=(s.recentMoves&&s.recentMoves.length)?s.recentMoves[0]:null;return [s.started,s.mode,s.currentTurn,s.gameOver,s.result,s.selectedRow,s.selectedCol,s.reviewMode,s.reviewMoveIndex,s.reviewMaxMove,s.tacticText,m?m.fromRow:'',m?m.fromCol:'',m?m.toRow:'',m?m.toCol:''].join('|');}",
            "async function api(path){const base=withSid(path);const q=base.includes('?')?'&':'?';const url=base+q+'_t='+Date.now();const res=await fetch(url,{cache:'no-store'});return await res.json();}",
            "function applyState(data){const prev=lastStateStamp;state=data;ui.firstHand.disabled=ui.mode.value!=='pvc';setTxt(ui.statusTag,'状态: '+(!state.started?'待开始':(state.gameOver?(state.result||'结束'):(state.reviewMode?'回顾模式':'进行中'))));const sr=state.stepRemainSec;setTxt(ui.stepTop,'当前步时倒计时: '+((sr!=null&&sr>=0)?(sr+'s'):'--s'));setTxt(ui.totalTop,'总时 红:'+fmtSec(state.redTotalSec)+' 黑:'+fmtSec(state.blackTotalSec));const humanTxt=state.pvcHumanColor==='BLACK'?' / 玩家执黑':' / 玩家执红';setTxt(ui.modeTag,'模式: '+(state.mode==='PVC'?'人机':'双人')+' / '+state.difficultyText+(state.mode==='PVC'?humanTxt:''));setTxt(ui.endgameTag,'残局: '+(state.endgame||'标准开局'));setTxt(ui.drawReasonTag,'和棋原因: '+(state.drawReason&&state.drawReason.length?state.drawReason:'-'));setTxt(ui.reviewTag,state.reviewMode?('回顾: 第 '+state.reviewMoveIndex+' / '+state.reviewMaxMove+' 步'):'回顾: 关闭');setTxt(ui.info,!state.started?'请点击“新开一局”开始':(state.gameOver?(state.result||'对局结束'):('当前回合: '+(state.currentTurn==='RED'?'红方':'黑方'))));setDis(ui.undo,!state.started||state.reviewMode||state.gameOver);setDis(ui.surrender,!state.started||state.reviewMode||state.gameOver);setDis(ui.drawBtn,!state.canDraw);setDis(ui.reviewStart,!state.started||!state.canReview||state.reviewMode);setDis(ui.reviewPrev,!state.reviewMode||state.reviewMoveIndex<=0);setDis(ui.reviewNext,!state.reviewMode||state.reviewMoveIndex>=state.reviewMaxMove);setDis(ui.reviewExit,!state.reviewMode);handleSounds();const stamp=stateStamp(state);const changed=stamp!==prev;lastStateStamp=stamp;if(changed){primeAnim();scheduleRender();}}",
            "async function refresh(){if(pending)return;pending=true;const seq=++reqSeq;try{const data=await api('/api/state');if(seq!==reqSeq)return;applyState(data);}finally{pending=false;}}",
            "async function act(path){const data=await api(path);applyState(data);}function enqueueAct(path){actionQueue=actionQueue.then(()=>act(path)).catch(()=>{});return actionQueue;}",
            "document.addEventListener('pointerdown',unlockAudio,{once:true});canvas.addEventListener('pointerdown',e=>{if(state&&(!state.started||state.reviewMode||state.gameOver))return;e.preventDefault();const rect=canvas.getBoundingClientRect();const sx=BASE_W/rect.width,sy=BASE_H/rect.height;const x=(e.clientX-rect.left)*sx,y=(e.clientY-rect.top)*sy;const g=pickGrid(x,y);if(!g)return;const p=state&&state.board&&state.board[g.row]?state.board[g.row][g.col]:null;if(state&&state.selectedRow<0&&p&&p.color===state.currentTurn&&(state.mode!=='PVC'||p.color===state.pvcHumanColor)){state.selectedRow=g.row;state.selectedCol=g.col;scheduleRender();}enqueueAct('/api/click?row='+g.row+'&col='+g.col);},{passive:false});",
            "document.getElementById('newGame').addEventListener('click',()=>{const mode=ui.mode.value,d=document.getElementById('difficulty').value,h=ui.firstHand.value;enqueueAct('/api/new?mode='+mode+'&difficulty='+d+'&humanFirst='+h);});document.querySelectorAll('.egBtn').forEach(btn=>btn.addEventListener('click',()=>{const mode=ui.mode.value,d=document.getElementById('difficulty').value,h=ui.firstHand.value,name=encodeURIComponent(btn.dataset.name);enqueueAct('/api/endgame?name='+name+'&mode='+mode+'&difficulty='+d+'&humanFirst='+h);}));",
            "document.getElementById('undo').addEventListener('click',()=>enqueueAct('/api/undo'));document.getElementById('surrender').addEventListener('click',()=>{if(confirm('确定要认输吗？')){enqueueAct('/api/surrender');}});document.getElementById('drawBtn').addEventListener('click',()=>{if(confirm('确认本局和棋？')){enqueueAct('/api/draw');}});document.getElementById('soundToggle').addEventListener('click',toggleSound);",
            "document.getElementById('reviewStart').addEventListener('click',()=>enqueueAct('/api/review/start'));",
            "document.getElementById('reviewPrev').addEventListener('click',()=>enqueueAct('/api/review/prev'));",
            "document.getElementById('reviewNext').addEventListener('click',()=>enqueueAct('/api/review/next'));",
            "document.getElementById('reviewExit').addEventListener('click',()=>enqueueAct('/api/review/exit'));",
            "ui.mode.addEventListener('change',()=>{ui.firstHand.disabled=ui.mode.value!=='pvc';});",
            "function pollDelay(){return document.hidden?1200:260;}function schedulePoll(){clearTimeout(pollTimer);pollTimer=setTimeout(async()=>{await refresh();schedulePoll();},pollDelay());}document.addEventListener('visibilitychange',schedulePoll);drawStatic();setupCanvas();syncSoundToggle();refresh().finally(schedulePoll);",
            "</script>",
            "</body>",
            "</html>");
    }
}






















