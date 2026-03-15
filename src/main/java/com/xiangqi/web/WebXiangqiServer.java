package com.xiangqi.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xiangqi.ai.BuiltinXiangqiEngine;
import com.xiangqi.ai.ConfigurableXiangqiEngine;
import com.xiangqi.controller.EndgameLoader;
import com.xiangqi.ai.MinimaxAI;
import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.Piece;
import com.xiangqi.model.PieceColor;
import com.xiangqi.model.TacticDetector;
import com.xiangqi.model.go.ConfigurableGoEngine;
import com.xiangqi.model.go.GoBoard;
import com.xiangqi.model.go.GoEngineMove;
import com.xiangqi.model.go.GoMoveResult;
import com.xiangqi.model.go.GoScenario;
import com.xiangqi.model.go.GoScenarioLoader;
import com.xiangqi.model.go.GoScoreSummary;
import com.xiangqi.model.go.GoStone;
import com.xiangqi.model.gomoku.BuiltinGomokuEngine;
import com.xiangqi.model.gomoku.ConfigurableGomokuEngine;
import com.xiangqi.model.gomoku.GomokuBoard;
import com.xiangqi.model.gomoku.GomokuMove;
import com.xiangqi.model.gomoku.GomokuPlaceResult;
import com.xiangqi.model.gomoku.GomokuStone;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WebXiangqiServer {
    private static final long MIN_MOVE_INTERVAL_MS = 120L;
    private static final String GAME_XIANGQI = "XIANGQI";
    private static final String GAME_GOMOKU = "GOMOKU";
    private static final String GAME_GO = "GO";
    private static final WebXiangqiServer INSTANCE = new WebXiangqiServer();
    private static final String SID_COOKIE = "XQSID";
    private static final int HTTP_THREADS = Math.max(8, Runtime.getRuntime().availableProcessors() * 4);
    private static final int AI_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final int AI_QUEUE_CAPACITY = Math.max(32, AI_THREADS * 8);
    private static final long SESSION_TTL_MS = TimeUnit.HOURS.toMillis(6);
    private static final long SESSION_CLEAN_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);
    private static final int SESSION_MAX_ENTRIES = 5000;
    private static volatile ExecutorService HTTP_EXECUTOR = createExecutor(HTTP_THREADS, "xq-http-", -1);
    private static volatile ExecutorService AI_EXECUTOR = createExecutor(AI_THREADS, "xq-ai-", AI_QUEUE_CAPACITY);
    private static volatile boolean SHUTDOWN_HOOK_INSTALLED = false;

    private HttpServer server;
    private URI uri;
    private final Map<String, SessionSlot> sessions = new ConcurrentHashMap<>();
    private volatile long lastSessionCleanupAt = 0L;

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
        HTTP_EXECUTOR = ensureExecutor(HTTP_EXECUTOR, HTTP_THREADS, "xq-http-", -1);
        AI_EXECUTOR = ensureExecutor(AI_EXECUTOR, AI_THREADS, "xq-ai-", AI_QUEUE_CAPACITY);
        installShutdownHookOnce();

        int bindPort = preferredPort > 0 ? preferredPort : 0;
        String host = (bindHost == null || bindHost.trim().isEmpty()) ? "127.0.0.1" : bindHost.trim();
        server = HttpServer.create(new InetSocketAddress(host, bindPort), 0);
        server.createContext("/", this::handleIndex);
        server.createContext("/api/state", this::handleState);
        server.createContext("/api/new", this::handleNewGame);
        server.createContext("/api/endgame", this::handleEndgame);
        server.createContext("/api/scenario", this::handleScenario);
        server.createContext("/api/click", this::handleClick);
        server.createContext("/api/go/pass", this::handleGoPass);
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
        server.createContext("/assets/ui", this::handleUiAsset);
        server.setExecutor(HTTP_EXECUTOR);
        server.start();

        String uriHost = "0.0.0.0".equals(host) ? "127.0.0.1" : host;
        uri = URI.create("http://" + uriHost + ":" + server.getAddress().getPort() + "/");
        return uri;
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            uri = null;
        }
        for (SessionSlot slot : sessions.values()) {
            if (slot != null && slot.session != null) {
                slot.session.close();
            }
        }
        sessions.clear();
        shutdownExecutor(HTTP_EXECUTOR);
        HTTP_EXECUTOR = null;
        shutdownExecutor(AI_EXECUTOR);
        AI_EXECUTOR = null;
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
        String gameType = parseGameType(query.getOrDefault("gameType", GAME_XIANGQI));
        String xiangqiEngine = query.getOrDefault("xiangqiEngine", "");
        String gomokuEngine = query.getOrDefault("gomokuEngine", "");
        boolean humanFirst = !"false".equalsIgnoreCase(query.getOrDefault("humanFirst", "true"));
        withSession(exchange, "new", session -> {
            session.setXiangqiEnginePreference(xiangqiEngine);
            session.setGomokuEnginePreference(gomokuEngine);
            session.resetByGame(gameType, "pvc".equalsIgnoreCase(mode), parseDifficulty(difficulty), humanFirst);
            return session.toJson();
        });
    }

    private void handleEndgame(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String name = query.getOrDefault("name", "七星聚会");
        String mode = query.getOrDefault("mode", "pvp");
        String difficulty = query.getOrDefault("difficulty", "MEDIUM");
        String gameType = parseGameType(query.getOrDefault("gameType", GAME_XIANGQI));
        String xiangqiEngine = query.getOrDefault("xiangqiEngine", "");
        String gomokuEngine = query.getOrDefault("gomokuEngine", "");
        boolean humanFirst = !"false".equalsIgnoreCase(query.getOrDefault("humanFirst", "true"));
        withSession(exchange, "endgame", session -> {
            session.setXiangqiEnginePreference(xiangqiEngine);
            session.setGomokuEnginePreference(gomokuEngine);
            if (GAME_GOMOKU.equals(gameType)) {
                session.resetByGame(GAME_GOMOKU, "pvc".equalsIgnoreCase(mode), parseDifficulty(difficulty), humanFirst);
            } else if (GAME_GO.equals(gameType)) {
                session.loadScenario(name, "pvc".equalsIgnoreCase(mode), parseDifficulty(difficulty), humanFirst);
            } else {
                session.loadEndgame(name, "pvc".equalsIgnoreCase(mode), parseDifficulty(difficulty), humanFirst);
            }
            return session.toJson();
        });
    }

    private void handleScenario(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String name = query.getOrDefault("name", "七星聚会");
        String mode = query.getOrDefault("mode", "pvp");
        String difficulty = query.getOrDefault("difficulty", "MEDIUM");
        String gameType = parseGameType(query.getOrDefault("gameType", GAME_XIANGQI));
        String xiangqiEngine = query.getOrDefault("xiangqiEngine", "");
        String gomokuEngine = query.getOrDefault("gomokuEngine", "");
        boolean humanFirst = !"false".equalsIgnoreCase(query.getOrDefault("humanFirst", "true"));
        withSession(exchange, "scenario", session -> {
            session.setXiangqiEnginePreference(xiangqiEngine);
            session.setGomokuEnginePreference(gomokuEngine);
            if (GAME_GO.equals(gameType)) {
                session.loadScenario(name, "pvc".equalsIgnoreCase(mode), parseDifficulty(difficulty), humanFirst);
            } else if (GAME_GOMOKU.equals(gameType)) {
                session.resetByGame(GAME_GOMOKU, "pvc".equalsIgnoreCase(mode), parseDifficulty(difficulty), humanFirst);
            } else {
                session.loadEndgame(name, "pvc".equalsIgnoreCase(mode), parseDifficulty(difficulty), humanFirst);
            }
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

    private void handleGoPass(HttpExchange exchange) throws IOException {
        withSession(exchange, "go_pass", session -> {
            session.goPass();
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

    private void handleUiAsset(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path == null || !path.startsWith("/assets/ui")) {
            sendText(exchange, 404, "Not Found", "text/plain");
            return;
        }

        String fileName = path.substring("/assets/ui".length());
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }
        if (fileName.isEmpty() || fileName.contains("..")) {
            sendText(exchange, 404, "Not Found", "text/plain");
            return;
        }

        try (InputStream is = WebXiangqiServer.class.getResourceAsStream("/web/" + fileName)) {
            if (is == null) {
                sendText(exchange, 404, "Not Found", "text/plain");
                return;
            }
            sendBytes(exchange, 200, readAllBytes(is), contentType(fileName));
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

    private String contentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (lower.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    private String readTextResource(String resourcePath) throws IOException {
        try (InputStream is = WebXiangqiServer.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(readAllBytes(is), StandardCharsets.UTF_8);
        }
    }

    private MinimaxAI.Difficulty parseDifficulty(String raw) {
        try {
            return MinimaxAI.Difficulty.valueOf(raw.toUpperCase());
        } catch (Exception e) {
            return MinimaxAI.Difficulty.MEDIUM;
        }
    }

    private String parseGameType(String raw) {
        if (raw == null) {
            return GAME_XIANGQI;
        }
        if (GAME_GOMOKU.equalsIgnoreCase(raw)) {
            return GAME_GOMOKU;
        }
        if (GAME_GO.equalsIgnoreCase(raw)) {
            return GAME_GO;
        }
        return GAME_XIANGQI;
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

    private static ExecutorService createExecutor(int threads, String prefix, int queueCapacity) {
        if (queueCapacity <= 0) {
            return Executors.newFixedThreadPool(threads, namedFactory(prefix));
        }
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            threads,
            threads,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            namedFactory(prefix),
            new ThreadPoolExecutor.AbortPolicy()
        );
        executor.prestartAllCoreThreads();
        return executor;
    }

    private static ExecutorService ensureExecutor(ExecutorService executor, int threads, String prefix, int queueCapacity) {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            return createExecutor(threads, prefix, queueCapacity);
        }
        return executor;
    }

    private static void shutdownExecutor(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void installShutdownHookOnce() {
        if (SHUTDOWN_HOOK_INSTALLED) {
            return;
        }
        synchronized (WebXiangqiServer.class) {
            if (SHUTDOWN_HOOK_INSTALLED) {
                return;
            }
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    INSTANCE.stop();
                }
            }, "xq-server-shutdown"));
            SHUTDOWN_HOOK_INSTALLED = true;
        }
    }

    private void cleanupSessionsIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastSessionCleanupAt < SESSION_CLEAN_INTERVAL_MS) {
            return;
        }
        synchronized (this) {
            if (now - lastSessionCleanupAt < SESSION_CLEAN_INTERVAL_MS) {
                return;
            }
            lastSessionCleanupAt = now;
            for (Map.Entry<String, SessionSlot> entry : sessions.entrySet()) {
                SessionSlot slot = entry.getValue();
                if (slot == null || now - slot.lastSeen > SESSION_TTL_MS) {
                    if (slot != null && slot.session != null) {
                        slot.session.close();
                    }
                    sessions.remove(entry.getKey(), slot);
                }
            }
            if (sessions.size() > SESSION_MAX_ENTRIES) {
                List<Map.Entry<String, SessionSlot>> entries = new ArrayList<Map.Entry<String, SessionSlot>>(sessions.entrySet());
                Collections.sort(entries, new java.util.Comparator<Map.Entry<String, SessionSlot>>() {
                    @Override
                    public int compare(Map.Entry<String, SessionSlot> a, Map.Entry<String, SessionSlot> b) {
                        long av = a.getValue() == null ? 0L : a.getValue().lastSeen;
                        long bv = b.getValue() == null ? 0L : b.getValue().lastSeen;
                        return Long.compare(av, bv);
                    }
                });
                int toRemove = sessions.size() - SESSION_MAX_ENTRIES;
                for (int i = 0; i < toRemove && i < entries.size(); i++) {
                    SessionSlot slot = entries.get(i).getValue();
                    if (slot != null && slot.session != null) {
                        slot.session.close();
                    }
                    sessions.remove(entries.get(i).getKey(), entries.get(i).getValue());
                }
            }
        }
    }

    private static final class SessionSlot {
        private final Session session;
        private volatile long lastSeen;

        private SessionSlot(Session session, long lastSeen) {
            this.session = session;
            this.lastSeen = lastSeen;
        }
    }

    private Session getSession(HttpExchange exchange) {
        cleanupSessionsIfNeeded();
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String sid = query.get("sid");
        if (!isValidSid(sid)) {
            sid = readSidFromCookie(exchange);
        }
        if (!isValidSid(sid)) {
            sid = UUID.randomUUID().toString().replace("-", "");
            exchange.getResponseHeaders().add("Set-Cookie", SID_COOKIE + "=" + sid + "; Path=/; HttpOnly; SameSite=Lax");
        }
        final long now = System.currentTimeMillis();
        SessionSlot slot = sessions.compute(sid, (key, existing) -> {
            if (existing == null) {
                return new SessionSlot(new Session(), now);
            }
            existing.lastSeen = now;
            return existing;
        });
        return slot.session;
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
            if (header.length() > 4096) {
                continue;
            }
            String[] cookies = header.split(";");
            for (String cookie : cookies) {
                String item = cookie.trim();
                if (item.startsWith(SID_COOKIE + "=")) {
                    String candidate = item.substring((SID_COOKIE + "=").length()).trim();
                    if (candidate.startsWith("\"") && candidate.endsWith("\"") && candidate.length() >= 2) {
                        candidate = candidate.substring(1, candidate.length() - 1).trim();
                    }
                    if (isValidSid(candidate)) {
                        return candidate;
                    }
                    return null;
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
        private String gameType = GAME_XIANGQI;
        private Board board = new Board();
        private final ConfigurableXiangqiEngine xiangqiAI = new ConfigurableXiangqiEngine();
        private GomokuBoard gomokuBoard = new GomokuBoard();
        private final ConfigurableGomokuEngine gomokuAI = new ConfigurableGomokuEngine();
        private GoBoard goBoard = new GoBoard(19, 7.5d);
        private final ConfigurableGoEngine goAI = new ConfigurableGoEngine();
        private boolean pvcMode;
        private MinimaxAI.Difficulty difficulty = MinimaxAI.Difficulty.MEDIUM;
        private int selectedRow = -1;
        private int selectedCol = -1;
        private boolean reviewMode;
        private int reviewMoveIndex;
        private String tacticText = "";
        private String currentEndgame = "标准开局";
        private String currentScenario = "标准开局";
        private long tacticUntil = 0L;
        private String gomokuForbiddenReason = "";
        private GomokuStone gomokuSurrenderedStone = GomokuStone.EMPTY;
        private GoStone goSurrenderedStone = GoStone.EMPTY;
        private long lastMoveAt = 0L;
        private boolean aiPending = false;
        private long aiDueAt = 0L;
        private long aiEpoch = 0L;
        private CompletableFuture<Move> aiFuture = null;
        private CompletableFuture<int[]> gomokuAiFuture = null;
        private CompletableFuture<GoEngineMove> goAiFuture = null;
        private long aiFutureEpoch = -1L;
        private PieceColor aiFutureColor = null;
        private long gomokuAiFutureEpoch = -1L;
        private long goAiFutureEpoch = -1L;
        private PieceColor surrenderedColor = null;
        private GomokuStone gomokuHumanStone = GomokuStone.BLACK;
        private GoStone goHumanStone = GoStone.BLACK;
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

        void resetByGame(String gameType, boolean pvcMode, MinimaxAI.Difficulty difficulty, boolean humanFirst) {
            if (GAME_GOMOKU.equalsIgnoreCase(gameType)) {
                this.gameType = GAME_GOMOKU;
                resetGomoku(pvcMode, difficulty, humanFirst);
            } else if (GAME_GO.equalsIgnoreCase(gameType)) {
                this.gameType = GAME_GO;
                resetGo(pvcMode, difficulty, humanFirst);
            } else {
                this.gameType = GAME_XIANGQI;
                reset(pvcMode, difficulty, humanFirst);
            }
        }

        void reset(boolean pvcMode, MinimaxAI.Difficulty difficulty, boolean humanFirst) {
            cancelPendingAiTasks();
            this.gameType = GAME_XIANGQI;
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
            this.currentScenario = "标准开局";
            this.lastMoveAt = 0L;
            this.aiPending = false;
            this.aiDueAt = 0L;
            this.aiEpoch++;
            this.aiFutureEpoch = -1L;
            this.aiFutureColor = null;
            this.gomokuAiFutureEpoch = -1L;
            this.goAiFuture = null;
            this.goAiFutureEpoch = -1L;
            this.surrenderedColor = null;
            this.gomokuSurrenderedStone = GomokuStone.EMPTY;
            this.goSurrenderedStone = GoStone.EMPTY;
            this.gomokuForbiddenReason = "";
            this.goBoard = new GoBoard(19, 7.5d);
            this.goHumanStone = GoStone.BLACK;
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

        private void resetGomoku(boolean pvcMode, MinimaxAI.Difficulty difficulty, boolean humanFirst) {
            cancelPendingAiTasks();
            this.gomokuBoard = new GomokuBoard();
            this.pvcMode = pvcMode;
            this.difficulty = difficulty;
            this.gomokuHumanStone = humanFirst ? GomokuStone.BLACK : GomokuStone.WHITE;
            this.selectedRow = -1;
            this.selectedCol = -1;
            this.reviewMode = false;
            this.reviewMoveIndex = 0;
            this.tacticText = "";
            this.tacticUntil = 0L;
            this.currentEndgame = "标准开局";
            this.currentScenario = "标准开局";
            this.gomokuForbiddenReason = "";
            this.lastMoveAt = 0L;
            this.aiPending = false;
            this.aiDueAt = 0L;
            this.aiEpoch++;
            this.aiFutureEpoch = -1L;
            this.aiFutureColor = null;
            this.gomokuAiFutureEpoch = -1L;
            this.goAiFuture = null;
            this.goAiFutureEpoch = -1L;
            this.surrenderedColor = null;
            this.gomokuSurrenderedStone = GomokuStone.EMPTY;
            this.goSurrenderedStone = GoStone.EMPTY;
            this.goBoard = new GoBoard(19, 7.5d);
            this.goHumanStone = GoStone.BLACK;
            this.trackedTurn = null;
            this.turnStartedAt = System.currentTimeMillis();
            this.redCompletedMoves = 0;
            this.blackCompletedMoves = 0;
            this.started = true;
            this.timeoutLoser = null;
            this.timeoutType = null;
            this.redTotalRemainingMs = -1L;
            this.blackTotalRemainingMs = -1L;
            this.lastTickAt = System.currentTimeMillis();
            this.pvpClockEnabled = false;
            this.agreedDraw = false;
            this.autoDraw = false;
            this.drawReason = "";
            this.noCaptureHalfMoves = 0;
            this.positionCount.clear();
            this.tacticSeq++;

            if (pvcMode && gomokuBoard.getCurrentTurn() != gomokuHumanStone && !gomokuBoard.isGameOver()) {
                aiPending = true;
                aiDueAt = System.currentTimeMillis() + getAiMoveIntervalMs();
            }
        }

        private void resetGo(boolean pvcMode, MinimaxAI.Difficulty difficulty, boolean humanFirst) {
            this.goBoard = new GoBoard(19, 7.5d);
            this.pvcMode = pvcMode && goAI.isAvailable();
            this.difficulty = difficulty;
            this.goHumanStone = humanFirst ? GoStone.BLACK : GoStone.WHITE;
            this.selectedRow = -1;
            this.selectedCol = -1;
            this.reviewMode = false;
            this.reviewMoveIndex = 0;
            this.currentEndgame = "标准开局";
            this.currentScenario = "标准开局";
            this.gomokuForbiddenReason = "";
            this.goSurrenderedStone = GoStone.EMPTY;
            this.lastMoveAt = 0L;
            this.aiPending = false;
            this.aiDueAt = 0L;
            this.aiEpoch++;
            this.aiFuture = null;
            this.aiFutureEpoch = -1L;
            this.aiFutureColor = null;
            this.gomokuAiFuture = null;
            this.gomokuAiFutureEpoch = -1L;
            this.goAiFuture = null;
            this.goAiFutureEpoch = -1L;
            this.surrenderedColor = null;
            this.gomokuSurrenderedStone = GomokuStone.EMPTY;
            this.trackedTurn = null;
            this.turnStartedAt = System.currentTimeMillis();
            this.redCompletedMoves = 0;
            this.blackCompletedMoves = 0;
            this.started = true;
            this.timeoutLoser = null;
            this.timeoutType = null;
            this.redTotalRemainingMs = -1L;
            this.blackTotalRemainingMs = -1L;
            this.lastTickAt = System.currentTimeMillis();
            this.pvpClockEnabled = false;
            this.agreedDraw = false;
            this.autoDraw = false;
            this.drawReason = "";
            this.noCaptureHalfMoves = 0;
            this.positionCount.clear();
            if (pvcMode && !this.pvcMode) {
                this.tacticText = "围棋引擎未就绪，已切换为双人";
                this.tacticUntil = System.currentTimeMillis() + 900L;
                this.tacticSeq++;
            } else {
                this.tacticText = "";
                this.tacticUntil = 0L;
            }

            if (this.pvcMode && goBoard.getCurrentTurn() != goHumanStone) {
                aiPending = true;
                aiDueAt = System.currentTimeMillis() + getAiMoveIntervalMs();
            }
        }

        void setGomokuEnginePreference(String preference) {
            if (preference == null || preference.trim().isEmpty()) {
                return;
            }
            gomokuAI.setPreferredEngine(preference);
        }

        void setXiangqiEnginePreference(String preference) {
            if (preference == null || preference.trim().isEmpty()) {
                return;
            }
            xiangqiAI.setPreferredEngine(preference);
        }

        void loadEndgame(String endgameName, boolean pvcMode, MinimaxAI.Difficulty difficulty, boolean humanFirst) {
            cancelPendingAiTasks();
            this.gameType = GAME_XIANGQI;
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
            this.currentScenario = "标准开局";
            this.lastMoveAt = 0L;
            this.aiPending = false;
            this.aiDueAt = 0L;
            this.aiEpoch++;
            this.aiFutureEpoch = -1L;
            this.aiFutureColor = null;
            this.gomokuAiFutureEpoch = -1L;
            this.goAiFuture = null;
            this.goAiFutureEpoch = -1L;
            this.surrenderedColor = null;
            this.gomokuSurrenderedStone = GomokuStone.EMPTY;
            this.goSurrenderedStone = GoStone.EMPTY;
            this.goBoard = new GoBoard(19, 7.5d);
            this.goHumanStone = GoStone.BLACK;
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

        void loadScenario(String scenarioName, boolean pvcMode, MinimaxAI.Difficulty difficulty, boolean humanFirst) {
            this.gameType = GAME_GO;
            this.goBoard = new GoBoard(19, 7.5d);
            GoScenario scenario = GoScenarioLoader.findByName(scenarioName);
            if (scenario != null) {
                this.goBoard.loadPosition(scenario.getRows(), scenario.getTurn());
                this.currentScenario = scenario.getName();
            } else {
                this.currentScenario = "标准开局";
            }
            this.pvcMode = pvcMode && goAI.isAvailable();
            this.difficulty = difficulty;
            this.goHumanStone = humanFirst ? GoStone.BLACK : GoStone.WHITE;
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
            this.gomokuAiFuture = null;
            this.gomokuAiFutureEpoch = -1L;
            this.goAiFuture = null;
            this.goAiFutureEpoch = -1L;
            this.surrenderedColor = null;
            this.gomokuSurrenderedStone = GomokuStone.EMPTY;
            this.goSurrenderedStone = GoStone.EMPTY;
            this.gomokuForbiddenReason = "";
            this.trackedTurn = null;
            this.turnStartedAt = System.currentTimeMillis();
            this.redCompletedMoves = 0;
            this.blackCompletedMoves = 0;
            this.started = true;
            this.timeoutLoser = null;
            this.timeoutType = null;
            this.redTotalRemainingMs = -1L;
            this.blackTotalRemainingMs = -1L;
            this.lastTickAt = System.currentTimeMillis();
            this.pvpClockEnabled = false;
            this.agreedDraw = false;
            this.autoDraw = false;
            this.drawReason = "";
            this.noCaptureHalfMoves = 0;
            this.positionCount.clear();

            if (pvcMode && !this.pvcMode) {
                this.tacticText = "围棋引擎未就绪，已切换为双人";
                this.tacticUntil = System.currentTimeMillis() + 900L;
                this.tacticSeq++;
            }

            if (this.pvcMode && goBoard.getCurrentTurn() != goHumanStone) {
                aiPending = true;
                aiDueAt = System.currentTimeMillis() + getAiMoveIntervalMs();
            }
        }

        private boolean isGomoku() {
            return GAME_GOMOKU.equals(gameType);
        }

        private boolean isGo() {
            return GAME_GO.equals(gameType);
        }

        void startReview() {
            if (!started) {
                return;
            }
            boolean can = isGomoku() ? gomokuBoard.canUndo() : (isGo() ? goBoard.canUndo() : board.canUndo());
            if (can) {
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
            int maxMove = isGomoku() ? gomokuBoard.getMoveCount() : (isGo() ? goBoard.getMoveCount() : board.getMoveCount());
            if (reviewMode && reviewMoveIndex < maxMove) {
                reviewMoveIndex++;
            }
        }

        void click(int row, int col) {
            if (!started || reviewMode) {
                return;
            }
            tick();
            if (isGomoku()) {
                clickGomoku(row, col);
                return;
            }
            if (isGo()) {
                clickGo(row, col);
                return;
            }
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

        private void clickGomoku(int row, int col) {
            if (row < 0 || row >= GomokuBoard.SIZE || col < 0 || col >= GomokuBoard.SIZE || isGameOver()) {
                return;
            }
            if (pvcMode && gomokuBoard.getCurrentTurn() != gomokuHumanStone) {
                return;
            }
            GomokuPlaceResult result = gomokuBoard.place(row, col, true);
            if (!result.isSuccess()) {
                if (result.isForbidden()) {
                    gomokuForbiddenReason = result.getReason();
                    tacticText = result.getReason();
                    tacticUntil = System.currentTimeMillis() + 500;
                    tacticSeq++;
                }
                return;
            }
            gomokuForbiddenReason = "";
            markMove();
            aiEpoch++;
            selectedRow = -1;
            selectedCol = -1;
            tacticText = gomokuBoard.getWinner() != GomokuStone.EMPTY ? "绝杀" : "";
            if (!tacticText.isEmpty()) {
                tacticUntil = System.currentTimeMillis() + 500;
                tacticSeq++;
            }

            if (pvcMode && !isGameOver() && gomokuBoard.getCurrentTurn() != gomokuHumanStone) {
                aiPending = true;
                aiDueAt = System.currentTimeMillis() + getAiMoveIntervalMs();
            }
        }

        private void clickGo(int row, int col) {
            if (row < 0 || row >= goBoard.getSize() || col < 0 || col >= goBoard.getSize() || isGameOver()) {
                return;
            }
            if (pvcMode && goBoard.getCurrentTurn() != goHumanStone) {
                return;
            }
            GoMoveResult result = goBoard.place(row, col);
            if (!result.isSuccess()) {
                tacticText = result.getReason();
                tacticUntil = System.currentTimeMillis() + 700L;
                tacticSeq++;
                return;
            }
            markMove();
            aiEpoch++;
            tacticText = result.getCapturedStones() > 0 ? "提子" : "";
            if (!tacticText.isEmpty()) {
                tacticUntil = System.currentTimeMillis() + 500L;
                tacticSeq++;
            }
            if (pvcMode && !isGameOver() && goBoard.getCurrentTurn() != goHumanStone) {
                aiPending = true;
                aiDueAt = System.currentTimeMillis() + getAiMoveIntervalMs();
            }
        }

        void goPass() {
            if (!started || reviewMode || !isGo() || isGameOver()) {
                return;
            }
            if (pvcMode && goBoard.getCurrentTurn() != goHumanStone) {
                return;
            }
            GoMoveResult result = goBoard.pass();
            if (!result.isSuccess()) {
                return;
            }
            markMove();
            aiEpoch++;
            tacticText = goBoard.getConsecutivePasses() >= 2 ? "双方停一手，已计分" : "停一手";
            tacticUntil = System.currentTimeMillis() + 700L;
            tacticSeq++;
            if (pvcMode && goBoard.getCurrentTurn() != goHumanStone) {
                aiPending = true;
                aiDueAt = System.currentTimeMillis() + getAiMoveIntervalMs();
            }
        }

        void undo() {
            if (!started || reviewMode || isGameOver()) {
                return;
            }
            if (isGomoku()) {
                if (!gomokuBoard.canUndo()) {
                    return;
                }
                if (pvcMode) {
                    gomokuBoard.undoMove();
                    if (gomokuBoard.canUndo()) {
                        gomokuBoard.undoMove();
                    }
                } else {
                    gomokuBoard.undoMove();
                }
                selectedRow = -1;
                selectedCol = -1;
                aiEpoch++;
                cancelPendingAiTasks();
                aiFutureEpoch = -1L;
                aiFutureColor = null;
                gomokuAiFutureEpoch = -1L;
                agreedDraw = false;
                autoDraw = false;
                drawReason = "";
                tacticText = "";
                gomokuForbiddenReason = "";
                return;
            }
            if (isGo()) {
                if (!goBoard.canUndo()) {
                    return;
                }
                if (pvcMode) {
                    goBoard.undoMove();
                    if (goBoard.canUndo()) {
                        goBoard.undoMove();
                    }
                } else {
                    goBoard.undoMove();
                }
                selectedRow = -1;
                selectedCol = -1;
                aiEpoch++;
                aiFuture = null;
                aiFutureEpoch = -1L;
                aiFutureColor = null;
                gomokuAiFuture = null;
                gomokuAiFutureEpoch = -1L;
                goAiFuture = null;
                goAiFutureEpoch = -1L;
                agreedDraw = false;
                autoDraw = false;
                drawReason = "";
                tacticText = "";
                return;
            }
            if (!board.canUndo()) {
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
            cancelPendingAiTasks();
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
            if (isGomoku()) {
                gomokuSurrenderedStone = gomokuBoard.getCurrentTurn();
                aiPending = false;
                aiDueAt = 0L;
                aiEpoch++;
                cancelPendingAiTasks();
                gomokuAiFutureEpoch = -1L;
                selectedRow = -1;
                selectedCol = -1;
                return;
            }
            if (isGo()) {
                goSurrenderedStone = goBoard.getCurrentTurn();
                aiPending = false;
                aiDueAt = 0L;
                aiEpoch++;
                goAiFuture = null;
                goAiFutureEpoch = -1L;
                selectedRow = -1;
                selectedCol = -1;
                return;
            }
            surrenderedColor = board.getCurrentTurn();
            aiPending = false;
            aiDueAt = 0L;
            aiEpoch++;
            cancelPendingAiTasks();
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
            cancelPendingAiTasks();
            aiFutureEpoch = -1L;
            aiFutureColor = null;
            selectedRow = -1;
            selectedCol = -1;
        }

        private boolean isGameOver() {
            if (isGomoku()) {
                return timeoutLoser != null || gomokuSurrenderedStone != GomokuStone.EMPTY || agreedDraw || autoDraw || gomokuBoard.isGameOver();
            }
            if (isGo()) {
                return goSurrenderedStone != GoStone.EMPTY || agreedDraw;
            }
            return timeoutLoser != null || surrenderedColor != null || agreedDraw || autoDraw || board.isGameOver();
        }

        private String getGameResult() {
            if (!started) {
                return "点击“新开一局”开始对局";
            }
            if (isGomoku()) {
                if (gomokuSurrenderedStone == GomokuStone.BLACK) {
                    return "黑方认输！白方获胜";
                }
                if (gomokuSurrenderedStone == GomokuStone.WHITE) {
                    return "白方认输！黑方获胜";
                }
                if (agreedDraw || autoDraw) {
                    return (drawReason == null || drawReason.isEmpty()) ? "和棋" : drawReason;
                }
                String result = gomokuBoard.getGameResult();
                return result == null ? "" : result;
            }
            if (isGo()) {
                if (goSurrenderedStone == GoStone.BLACK) {
                    return "黑方认输！白方获胜";
                }
                if (goSurrenderedStone == GoStone.WHITE) {
                    return "白方认输！黑方获胜";
                }
                if (agreedDraw) {
                    return (drawReason == null || drawReason.isEmpty()) ? "和棋" : drawReason;
                }
                GoScoreSummary summary = goBoard.getScoreSummary();
                if (summary != null) {
                    return summary.getResultText() + "（双停后计分，可继续落子）";
                }
                return "";
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
            if (isGomoku()) {
                return;
            }
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
            if (isGomoku() || isGo()) {
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
            if (isGomoku()) {
                tickGomoku();
                return;
            }
            if (isGo()) {
                tickGo();
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
                if (aiFutureEpoch == aiEpoch && aiFutureColor == board.getCurrentTurn()) {
                    if (aiMove == null || !board.isValidMove(aiMove)) {
                        aiMove = findBuiltinXiangqiMove();
                    }
                    if (aiMove == null || !board.isValidMove(aiMove)) {
                        aiMove = findFirstLegalXiangqiMove();
                    }
                    if (aiMove != null && board.isValidMove(aiMove)) {
                        board.movePiece(aiMove);
                        markMove();
                        updateAutoDrawStateAfterMove();
                        updateTacticFlash();
                        aiEpoch++;
                    }
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
            try {
                aiFuture = CompletableFuture.supplyAsync(() ->
                    xiangqiAI.findBestMove(snapshot, aiColor, currentDifficulty), AI_EXECUTOR);
            } catch (RejectedExecutionException ignored) {
                aiFuture = null;
                aiFutureEpoch = -1L;
                aiFutureColor = null;
                aiDueAt = System.currentTimeMillis() + 200L;
            }
        }

        private Move findBuiltinXiangqiMove() {
            try {
                Board snapshot = new Board(board);
                PieceColor side = board.getCurrentTurn();
                Move m = new BuiltinXiangqiEngine().findBestMove(snapshot, side, difficulty);
                if (m != null && board.isValidMove(m)) {
                    return m;
                }
            } catch (Exception ignored) {
                // ignore
            }
            return null;
        }

        private Move findFirstLegalXiangqiMove() {
            List<Move> all = board.getAllValidMoves(board.getCurrentTurn());
            if (all == null || all.isEmpty()) {
                return null;
            }
            return all.get(0);
        }

        private void tickGomoku() {
            if (!aiPending || reviewMode || isGameOver() || !pvcMode) {
                return;
            }
            if (System.currentTimeMillis() < aiDueAt) {
                return;
            }
            if (gomokuBoard.getCurrentTurn() == gomokuHumanStone) {
                aiPending = false;
                return;
            }
            if (gomokuAiFuture != null) {
                if (!gomokuAiFuture.isDone()) {
                    return;
                }
                int[] aiMove;
                try {
                    aiMove = gomokuAiFuture.getNow(null);
                } catch (Exception ignored) {
                    aiMove = null;
                }
                if (aiMove == null || !isLegalGomokuMove(aiMove)) {
                    aiMove = findBuiltinGomokuMove();
                }
                if (aiMove == null || !isLegalGomokuMove(aiMove)) {
                    aiMove = findFirstLegalGomokuMove();
                }
                if (gomokuAiFutureEpoch == aiEpoch && aiMove != null) {
                    GomokuPlaceResult placed = gomokuBoard.place(aiMove[0], aiMove[1], true);
                    if (!placed.isSuccess()) {
                        int[] fallback = findBuiltinGomokuMove();
                        if (fallback == null || !isLegalGomokuMove(fallback)) {
                            fallback = findFirstLegalGomokuMove();
                        }
                        if (fallback != null) {
                            placed = gomokuBoard.place(fallback[0], fallback[1], true);
                        }
                    }
                    if (placed.isSuccess()) {
                        markMove();
                        tacticText = gomokuBoard.getWinner() != GomokuStone.EMPTY ? "绝杀" : "";
                        if (!tacticText.isEmpty()) {
                            tacticUntil = System.currentTimeMillis() + 500;
                            tacticSeq++;
                        }
                        aiEpoch++;
                    }
                }
                gomokuAiFuture = null;
                gomokuAiFutureEpoch = -1L;
                aiPending = false;
                return;
            }
            final GomokuBoard snapshot = new GomokuBoard(gomokuBoard);
            final MinimaxAI.Difficulty currentDifficulty = this.difficulty;
            final GomokuStone aiStone = gomokuBoard.getCurrentTurn();
            final long launchEpoch = aiEpoch;
            gomokuAiFutureEpoch = launchEpoch;
            try {
                gomokuAiFuture = CompletableFuture.supplyAsync(() ->
                    gomokuAI.findBestMove(snapshot, aiStone, currentDifficulty), AI_EXECUTOR);
            } catch (RejectedExecutionException ignored) {
                gomokuAiFuture = null;
                gomokuAiFutureEpoch = -1L;
                aiDueAt = System.currentTimeMillis() + 200L;
            }
        }

        private void tickGo() {
            if (!aiPending || reviewMode || isGameOver() || !pvcMode) {
                return;
            }
            if (System.currentTimeMillis() < aiDueAt) {
                return;
            }
            if (goBoard.getCurrentTurn() == goHumanStone) {
                aiPending = false;
                return;
            }
            if (goAiFuture != null) {
                if (!goAiFuture.isDone()) {
                    return;
                }
                GoEngineMove aiMove;
                try {
                    aiMove = goAiFuture.getNow(null);
                } catch (Exception ignored) {
                    aiMove = null;
                }
                if (aiMove == null || !isLegalGoMove(aiMove)) {
                    aiMove = findFirstLegalGoMove();
                }
                if (goAiFutureEpoch == aiEpoch && aiMove != null) {
                    GoMoveResult placed = aiMove.isPass()
                        ? goBoard.pass()
                        : goBoard.place(aiMove.getRow(), aiMove.getCol());
                    if (placed != null && placed.isSuccess()) {
                        markMove();
                        if (aiMove.isPass()) {
                            tacticText = goBoard.getConsecutivePasses() >= 2 ? "双方停一手，已计分" : "停一手";
                            tacticUntil = System.currentTimeMillis() + 700L;
                            tacticSeq++;
                        }
                        aiEpoch++;
                    }
                }
                goAiFuture = null;
                goAiFutureEpoch = -1L;
                aiPending = false;
                return;
            }
            final GoBoard snapshot = snapshotGoBoard();
            final MinimaxAI.Difficulty currentDifficulty = this.difficulty;
            final GoStone aiStone = goBoard.getCurrentTurn();
            final long launchEpoch = aiEpoch;
            goAiFutureEpoch = launchEpoch;
            goAiFuture = CompletableFuture.supplyAsync(() ->
                goAI.genMove(snapshot, aiStone, currentDifficulty), AI_EXECUTOR);
        }

        private int[] findBuiltinGomokuMove() {
            try {
                GomokuBoard snapshot = new GomokuBoard(gomokuBoard);
                GomokuStone side = gomokuBoard.getCurrentTurn();
                int[] m = new BuiltinGomokuEngine().findBestMove(snapshot, side, difficulty);
                if (isLegalGomokuMove(m)) {
                    return m;
                }
            } catch (Exception ignored) {
                // ignore
            }
            return null;
        }

        private boolean isLegalGomokuMove(int[] move) {
            if (move == null || move.length < 2) {
                return false;
            }
            GomokuBoard test = new GomokuBoard(gomokuBoard);
            test.setCurrentTurnForSearch(gomokuBoard.getCurrentTurn());
            GomokuPlaceResult pr = test.place(move[0], move[1], true);
            return pr.isSuccess();
        }

        private int[] findFirstLegalGomokuMove() {
            for (int r = 0; r < GomokuBoard.SIZE; r++) {
                for (int c = 0; c < GomokuBoard.SIZE; c++) {
                    GomokuBoard test = new GomokuBoard(gomokuBoard);
                    test.setCurrentTurnForSearch(gomokuBoard.getCurrentTurn());
                    GomokuPlaceResult pr = test.place(r, c, true);
                    if (pr.isSuccess()) {
                        return new int[] {r, c};
                    }
                }
            }
            return null;
        }

        private boolean isLegalGoMove(GoEngineMove move) {
            if (move == null) {
                return false;
            }
            if (move.isPass()) {
                return true;
            }
            GoBoard test = snapshotGoBoard();
            GoMoveResult result = test.place(move.getRow(), move.getCol());
            return result.isSuccess();
        }

        private GoEngineMove findFirstLegalGoMove() {
            for (int row = 0; row < goBoard.getSize(); row++) {
                for (int col = 0; col < goBoard.getSize(); col++) {
                    GoBoard test = snapshotGoBoard();
                    GoMoveResult result = test.place(row, col);
                    if (result.isSuccess()) {
                        return new GoEngineMove(row, col, false);
                    }
                }
            }
            return GoEngineMove.pass();
        }

        private GoBoard snapshotGoBoard() {
            return new GoBoard(goBoard);
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
            if (isGo()) {
                if (difficulty == MinimaxAI.Difficulty.EASY) {
                    return 20L;
                }
                if (difficulty == MinimaxAI.Difficulty.MEDIUM) {
                    return 35L;
                }
                return 45L;
            }
            if (isGomoku()) {
                if (difficulty == MinimaxAI.Difficulty.EASY) {
                    return 8L;
                }
                if (difficulty == MinimaxAI.Difficulty.MEDIUM) {
                    return 14L;
                }
                return 20L;
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
            if (isGomoku()) {
                return toJsonGomoku();
            }
            if (isGo()) {
                return toJsonGo();
            }
            Board boardToDraw = reviewMode ? board.getBoardAtMove(reviewMoveIndex) : board;
            if (boardToDraw == null) {
                boardToDraw = board;
            }

            StringBuilder sb = new StringBuilder(4096);
            sb.append('{');
            sb.append("\"seq\":").append(++responseSeq).append(',');
            sb.append("\"gameType\":\"").append(GAME_XIANGQI).append("\",");
            sb.append("\"boardSize\":9,");
            sb.append("\"boardRows\":10,");
            sb.append("\"boardCols\":9,");
            sb.append("\"ruleset\":\"xiangqi_standard\",");
            sb.append("\"started\":").append(started).append(',');
            sb.append("\"mode\":\"").append(pvcMode ? "PVC" : "PVP").append("\",");
            sb.append("\"difficulty\":\"").append(difficulty.name()).append("\",");
            sb.append("\"difficultyText\":\"").append(difficulty.getDisplayName()).append("\",");
            sb.append("\"goEngineAvailable\":").append(goAI.isAvailable()).append(',');
            sb.append("\"xiangqiAiEngine\":\"").append(escape(xiangqiAI.getEngineId())).append("\",");
            sb.append("\"xiangqiAiEngineText\":\"").append(escape(xiangqiAI.getEngineText())).append("\",");
            sb.append("\"xiangqiAiSelected\":\"").append(escape(xiangqiAI.getPreferredEngine())).append("\",");
            sb.append("\"xiangqiAiPikafishConfigured\":").append(xiangqiAI.isPikafishConfigured()).append(',');
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

        private String toJsonGo() {
            GoStone[][] boardToDraw = reviewMode ? goBoard.getBoardAtMove(reviewMoveIndex) : goBoard.getBoardAtMove(goBoard.getMoveCount());
            if (boardToDraw == null) {
                boardToDraw = goBoard.getBoardAtMove(goBoard.getMoveCount());
            }

            StringBuilder sb = new StringBuilder(8192);
            sb.append('{');
            sb.append("\"seq\":").append(++responseSeq).append(',');
            sb.append("\"gameType\":\"").append(GAME_GO).append("\",");
            sb.append("\"boardSize\":").append(goBoard.getSize()).append(',');
            sb.append("\"boardRows\":").append(goBoard.getSize()).append(',');
            sb.append("\"boardCols\":").append(goBoard.getSize()).append(',');
            sb.append("\"ruleset\":\"go_cn_area_komi_7_5\",");
            sb.append("\"started\":").append(started).append(',');
            sb.append("\"mode\":\"").append(pvcMode ? "PVC" : "PVP").append("\",");
            sb.append("\"difficulty\":\"").append(difficulty.name()).append("\",");
            sb.append("\"difficultyText\":\"").append(difficulty.getDisplayName()).append("\",");
            sb.append("\"goEngineAvailable\":").append(goAI.isAvailable()).append(',');
            sb.append("\"goAiEngine\":\"").append(escape(goAI.getEngineName())).append("\",");
            sb.append("\"goAiEngineText\":\"").append(escape(goAI.getEngineName())).append("\",");
            sb.append("\"pvcHumanColor\":\"").append(goHumanStone.name()).append("\",");
            sb.append("\"endgame\":\"").append(escape(currentScenario)).append("\",");
            sb.append("\"currentTurn\":\"").append(goBoard.getCurrentTurn().name()).append("\",");
            sb.append("\"gameOver\":").append(isGameOver()).append(',');
            sb.append("\"canDraw\":").append(started && !reviewMode && !isGameOver() && !pvcMode).append(',');
            sb.append("\"result\":\"").append(escape(getGameResult())).append("\",");
            sb.append("\"drawReason\":\"").append(escape(agreedDraw ? drawReason : "")).append("\",");
            sb.append("\"selectedRow\":-1,");
            sb.append("\"selectedCol\":-1,");
            sb.append("\"canReview\":").append(goBoard.canUndo()).append(',');
            sb.append("\"reviewMode\":").append(reviewMode).append(',');
            sb.append("\"reviewMoveIndex\":").append(reviewMoveIndex).append(',');
            sb.append("\"reviewMaxMove\":").append(goBoard.getMoveCount()).append(',');
            sb.append("\"stepRemainSec\":-1,");
            sb.append("\"redTotalSec\":-1,");
            sb.append("\"blackTotalSec\":-1,");
            sb.append("\"tacticText\":\"").append(escape(tacticText)).append("\",");
            sb.append("\"tacticSeq\":").append(tacticSeq).append(',');
            appendRecentMovesGo(sb, reviewMode ? reviewMoveIndex : goBoard.getMoveCount());
            sb.append(',');
            sb.append("\"go\":{");
            sb.append("\"komi\":").append(goBoard.getKomi()).append(',');
            sb.append("\"engineAvailable\":").append(goAI.isAvailable()).append(',');
            sb.append("\"scenarioName\":\"").append(escape(currentScenario)).append("\",");
            sb.append("\"consecutivePasses\":").append(goBoard.getConsecutivePasses()).append(',');
            sb.append("\"captures\":{\"black\":").append(goBoard.getBlackCaptures()).append(",\"white\":").append(goBoard.getWhiteCaptures()).append("},");
            sb.append("\"score\":");
            GoScoreSummary summary = goBoard.getScoreSummary();
            if (summary == null) {
                sb.append("null");
            } else {
                sb.append('{');
                sb.append("\"blackArea\":").append(summary.getBlackArea()).append(',');
                sb.append("\"whiteArea\":").append(summary.getWhiteArea()).append(',');
                sb.append("\"komi\":").append(summary.getKomi()).append(',');
                sb.append("\"finalScore\":").append(summary.getFinalScore()).append(',');
                sb.append("\"winner\":\"").append(escape(summary.getWinner())).append("\",");
                sb.append("\"resultText\":\"").append(escape(summary.getResultText())).append("\"");
                sb.append('}');
            }
            sb.append("},");
            sb.append("\"board\":[");
            for (int row = 0; row < goBoard.getSize(); row++) {
                if (row > 0) {
                    sb.append(',');
                }
                sb.append('[');
                for (int col = 0; col < goBoard.getSize(); col++) {
                    if (col > 0) {
                        sb.append(',');
                    }
                    GoStone stone = boardToDraw[row][col];
                    if (stone == GoStone.EMPTY) {
                        sb.append("null");
                    } else {
                        sb.append("{\"name\":\"")
                            .append(stone.getDisplayText())
                            .append("\",\"color\":\"")
                            .append(stone.name())
                            .append("\"}");
                    }
                }
                sb.append(']');
            }
            sb.append(']');
            sb.append('}');
            return sb.toString();
        }

        private String toJsonGomoku() {
            GomokuStone[][] boardToDraw = reviewMode ? gomokuBoard.getBoardAtMove(reviewMoveIndex) : gomokuBoard.getBoardAtMove(gomokuBoard.getMoveCount());
            if (boardToDraw == null) {
                boardToDraw = gomokuBoard.getBoardAtMove(gomokuBoard.getMoveCount());
            }

            StringBuilder sb = new StringBuilder(6144);
            sb.append('{');
            sb.append("\"seq\":").append(++responseSeq).append(',');
            sb.append("\"gameType\":\"").append(GAME_GOMOKU).append("\",");
            sb.append("\"boardSize\":").append(GomokuBoard.SIZE).append(',');
            sb.append("\"boardRows\":").append(GomokuBoard.SIZE).append(',');
            sb.append("\"boardCols\":").append(GomokuBoard.SIZE).append(',');
            sb.append("\"ruleset\":\"renju_forbidden_black\",");
            sb.append("\"started\":").append(started).append(',');
            sb.append("\"mode\":\"").append(pvcMode ? "PVC" : "PVP").append("\",");
            sb.append("\"difficulty\":\"").append(difficulty.name()).append("\",");
            sb.append("\"difficultyText\":\"").append(difficulty.getDisplayName()).append("\",");
            sb.append("\"goEngineAvailable\":").append(goAI.isAvailable()).append(',');
            sb.append("\"gomokuAiEngine\":\"").append(escape(gomokuAI.getEngineId())).append("\",");
            sb.append("\"gomokuAiEngineText\":\"").append(escape(gomokuAI.getEngineText())).append("\",");
            sb.append("\"gomokuAiSelected\":\"").append(escape(gomokuAI.getPreferredEngine())).append("\",");
            sb.append("\"gomokuAiRapfiConfigured\":").append(gomokuAI.isRapfiConfigured()).append(',');
            sb.append("\"gomokuAiAlphaConfigured\":").append(gomokuAI.isAlphaGomokuConfigured()).append(',');
            sb.append("\"pvcHumanColor\":\"").append(gomokuHumanStone.name()).append("\",");
            sb.append("\"endgame\":\"标准开局\",");
            sb.append("\"currentTurn\":\"").append(gomokuBoard.getCurrentTurn().name()).append("\",");
            sb.append("\"gameOver\":").append(isGameOver()).append(',');
            sb.append("\"canDraw\":").append(started && !reviewMode && !isGameOver() && !pvcMode).append(',');
            sb.append("\"result\":\"").append(escape(getGameResult())).append("\",");
            sb.append("\"drawReason\":\"").append(escape((agreedDraw || autoDraw) ? drawReason : "")).append("\",");
            sb.append("\"selectedRow\":").append(-1).append(',');
            sb.append("\"selectedCol\":").append(-1).append(',');
            sb.append("\"canReview\":").append(gomokuBoard.canUndo()).append(',');
            sb.append("\"reviewMode\":").append(reviewMode).append(',');
            sb.append("\"reviewMoveIndex\":").append(reviewMoveIndex).append(',');
            sb.append("\"reviewMaxMove\":").append(gomokuBoard.getMoveCount()).append(',');
            sb.append("\"stepRemainSec\":-1,");
            sb.append("\"redTotalSec\":-1,");
            sb.append("\"blackTotalSec\":-1,");
            sb.append("\"tacticText\":\"").append(escape(tacticText)).append("\",");
            sb.append("\"tacticSeq\":").append(tacticSeq).append(',');
            appendRecentMovesGomoku(sb, reviewMode ? reviewMoveIndex : gomokuBoard.getMoveCount());
            sb.append(',');
            sb.append("\"gomoku\":{");
            sb.append("\"forbiddenEnabled\":true,");
            sb.append("\"aiEngine\":\"").append(escape(gomokuAI.getEngineId())).append("\",");
            sb.append("\"aiEngineText\":\"").append(escape(gomokuAI.getEngineText())).append("\",");
            sb.append("\"forbiddenReason\":\"").append(escape(gomokuForbiddenReason)).append("\",");
            sb.append("\"forbiddenPoints\":[");
            List<int[]> forbidden = gomokuBoard.getForbiddenPointsForBlack(80);
            for (int i = 0; i < forbidden.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                int[] p = forbidden.get(i);
                sb.append("{\"row\":").append(p[0]).append(",\"col\":").append(p[1]).append('}');
            }
            sb.append("],");
            int[] winLine = gomokuBoard.getWinnerLine();
            if (winLine == null) {
                sb.append("\"winnerLine\":null");
            } else {
                sb.append("\"winnerLine\":{\"fromRow\":").append(winLine[0])
                    .append(",\"fromCol\":").append(winLine[1])
                    .append(",\"toRow\":").append(winLine[2])
                    .append(",\"toCol\":").append(winLine[3]).append('}');
            }
            sb.append("},");
            sb.append("\"board\":[");
            for (int row = 0; row < GomokuBoard.SIZE; row++) {
                if (row > 0) {
                    sb.append(',');
                }
                sb.append('[');
                for (int col = 0; col < GomokuBoard.SIZE; col++) {
                    if (col > 0) {
                        sb.append(',');
                    }
                    GomokuStone stone = boardToDraw[row][col];
                    if (stone == GomokuStone.EMPTY) {
                        sb.append("null");
                    } else {
                        sb.append("{\"name\":\"")
                            .append(stone == GomokuStone.BLACK ? "黑" : "白")
                            .append("\",\"color\":\"")
                            .append(stone.name())
                            .append("\"}");
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

        private void appendRecentMovesGomoku(StringBuilder sb, int moveIndex) {
            List<GomokuMove> history = gomokuBoard.getMoveHistory();
            sb.append("\"recentMoves\":[");
            int total = Math.min(moveIndex, history.size());
            int show = Math.min(2, total);
            for (int i = 0; i < show; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                GomokuMove move = history.get(total - 1 - i);
                sb.append('{');
                sb.append("\"order\":").append(i + 1).append(',');
                sb.append("\"color\":\"").append(move.getStone().name()).append("\",");
                sb.append("\"fromRow\":").append(move.getRow()).append(',');
                sb.append("\"fromCol\":").append(move.getCol()).append(',');
                sb.append("\"toRow\":").append(move.getRow()).append(',');
                sb.append("\"toCol\":").append(move.getCol());
                sb.append('}');
            }
            sb.append(']');
        }

        private void appendRecentMovesGo(StringBuilder sb, int moveIndex) {
            List<GoBoard.GoHistoryEntry> history = goBoard.getMoveHistory();
            sb.append("\"recentMoves\":[");
            int total = Math.min(moveIndex, history.size());
            int show = Math.min(2, total);
            for (int i = 0; i < show; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                GoBoard.GoHistoryEntry move = history.get(total - 1 - i);
                sb.append('{');
                sb.append("\"order\":").append(i + 1).append(',');
                sb.append("\"color\":\"").append(move.getStone().name()).append("\",");
                sb.append("\"fromRow\":").append(move.isPass() ? -1 : move.getRow()).append(',');
                sb.append("\"fromCol\":").append(move.isPass() ? -1 : move.getCol()).append(',');
                sb.append("\"toRow\":").append(move.isPass() ? -1 : move.getRow()).append(',');
                sb.append("\"toCol\":").append(move.isPass() ? -1 : move.getCol()).append(',');
                sb.append("\"pass\":").append(move.isPass());
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

        private void cancelPendingAiTasks() {
            if (aiFuture != null) {
                aiFuture.cancel(true);
                aiFuture = null;
            }
            if (gomokuAiFuture != null) {
                gomokuAiFuture.cancel(true);
                gomokuAiFuture = null;
            }
        }

        void close() {
            cancelPendingAiTasks();
            xiangqiAI.close();
            gomokuAI.close();
            goAI.close();
        }
    }

    private String html() throws IOException {
        return readTextResource("/web/index.html");
    }

}






















