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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebXiangqiServer {
    private static final long MIN_MOVE_INTERVAL_MS = 500L;
    private static final WebXiangqiServer INSTANCE = new WebXiangqiServer();

    private HttpServer server;
    private URI uri;
    private final Session session = new Session();

    private WebXiangqiServer() {
    }

    public static WebXiangqiServer getInstance() {
        return INSTANCE;
    }

    public synchronized URI start() throws IOException {
        return start(0);
    }

    public synchronized URI start(int preferredPort) throws IOException {
        if (server != null) {
            return uri;
        }

        int bindPort = preferredPort > 0 ? preferredPort : 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", bindPort), 0);
        server.createContext("/", this::handleIndex);
        server.createContext("/api/state", this::handleState);
        server.createContext("/api/new", this::handleNewGame);
        server.createContext("/api/endgame", this::handleEndgame);
        server.createContext("/api/click", this::handleClick);
        server.createContext("/api/undo", this::handleUndo);
        server.createContext("/api/surrender", this::handleSurrender);
        server.createContext("/api/review/start", this::handleReviewStart);
        server.createContext("/api/review/exit", this::handleReviewExit);
        server.createContext("/api/review/prev", this::handleReviewPrev);
        server.createContext("/api/review/next", this::handleReviewNext);
        server.setExecutor(null);
        server.start();

        uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        return uri;
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        sendText(exchange, 200, html(), "text/html; charset=UTF-8");
    }

    private void handleState(HttpExchange exchange) throws IOException {
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
        synchronized (session) {
            session.loadEndgame(name, "pvc".equalsIgnoreCase(mode), parseDifficulty(difficulty), humanFirst);
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }
    private void handleClick(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        int row = parseInt(query.get("row"), -1);
        int col = parseInt(query.get("col"), -1);
        synchronized (session) {
            session.click(row, col);
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }

    private void handleUndo(HttpExchange exchange) throws IOException {
        synchronized (session) {
            session.undo();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }


    private void handleSurrender(HttpExchange exchange) throws IOException {
        synchronized (session) {
            session.surrender();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }    private void handleReviewStart(HttpExchange exchange) throws IOException {
        synchronized (session) {
            session.startReview();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }

    private void handleReviewExit(HttpExchange exchange) throws IOException {
        synchronized (session) {
            session.exitReview();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }

    private void handleReviewPrev(HttpExchange exchange) throws IOException {
        synchronized (session) {
            session.reviewPrev();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
    }

    private void handleReviewNext(HttpExchange exchange) throws IOException {
        synchronized (session) {
            session.reviewNext();
            sendText(exchange, 200, session.toJson(), "application/json; charset=UTF-8");
        }
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
        private final MinimaxAI ai = new MinimaxAI();
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

        void reset(boolean pvcMode, MinimaxAI.Difficulty difficulty, boolean humanFirst) {
            this.board = new Board();
            this.pvcMode = pvcMode;
            this.difficulty = difficulty;
            this.ai.setDifficulty(difficulty);
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
            this.ai.setDifficulty(difficulty);
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
                selectedRow = -1;
                selectedCol = -1;
                updateTacticFlash();

                if (pvcMode && !board.isGameOver() && board.getCurrentTurn() != pvcHumanColor) {
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
        }



        void surrender() {
            if (!started || isGameOver() || reviewMode) {
                return;
            }
            surrenderedColor = board.getCurrentTurn();
            aiPending = false;
            aiDueAt = 0L;
            selectedRow = -1;
            selectedCol = -1;
        }

        private boolean isGameOver() {
            return timeoutLoser != null || surrenderedColor != null || board.isGameOver();
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

            if (!pvcMode && !reviewMode && !isGameOver()) {
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
            PieceColor aiColor = pvcHumanColor.opposite();
            Move aiMove = ai.findBestMove(board, aiColor);
            if (aiMove != null && board.isValidMove(aiMove)) {
                board.movePiece(aiMove);
                markMove();
                updateTacticFlash();
            }
            aiPending = false;
        }

        private boolean canMoveNow() {
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
            sb.append("\"result\":\"").append(escape(getGameResult())).append("\",");
            sb.append("\"selectedRow\":").append(selectedRow).append(',');
            sb.append("\"selectedCol\":").append(selectedCol).append(',');
            sb.append("\"canReview\":").append(board.canUndo()).append(',');
            sb.append("\"reviewMode\":").append(reviewMode).append(',');
            sb.append("\"reviewMoveIndex\":").append(reviewMoveIndex).append(',');
            sb.append("\"reviewMaxMove\":").append(board.getMoveCount()).append(',');
            sb.append("\"stepRemainSec\":").append(getCurrentStepRemainingSec()).append(',');
            sb.append("\"redTotalSec\":").append(started ? (redTotalRemainingMs + 999L) / 1000L : -1).append(',');
            sb.append("\"blackTotalSec\":").append(started ? (blackTotalRemainingMs + 999L) / 1000L : -1).append(',');
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
            "  <title>轻·象棋 - 浏览器模式</title>",
            "  <style>",
            "    :root{--line:#cfa85b;--bg1:#e4d4bc;--bg2:#f2e4cc;--ink:#58371f;--red:#c6403c;--blue:#24356c;}",
            "    body{margin:0;min-height:100vh;display:grid;place-items:center;background:radial-gradient(1200px 420px at 50% -10%,rgba(250,220,160,.55),transparent 60%),linear-gradient(180deg,#9fc6de 0%,#dcb78c 40%,#b78456 100%);font-family:Microsoft YaHei UI,sans-serif;color:var(--ink);overflow:hidden;}",
            "    .wrap{width:min(1320px,98vw);height:calc(100vh - 24px);background:linear-gradient(180deg,rgba(255,251,240,.95),rgba(245,229,203,.95));border:1px solid #d9bd93;border-radius:14px;box-shadow:0 18px 38px rgba(69,47,22,.22);padding:12px;box-sizing:border-box;}",
            "    .topMeta{display:flex;justify-content:space-between;align-items:center;margin:0 2px 8px;color:#6a4629;font-weight:700;}",
            "    .layout{display:grid;grid-template-columns:1fr 320px;gap:12px;align-items:stretch;height:calc(100% - 30px);}",
            "    .boardCard{background:#fffefb;border:1px solid #e5d1b2;border-radius:10px;padding:8px;display:flex;align-items:center;justify-content:center;overflow:hidden;}",
            "    .side{background:#fffdf8;border:1px solid #e1c9a5;border-radius:10px;padding:12px;overflow:auto;}",
            "    canvas{display:block;width:100%;height:auto;border:2px solid #a67b4a;border-radius:8px;background:var(--blue);image-rendering:auto;}",
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
            "  </style>",
            "</head>",
            "<body>",
            "  <div class=\"wrap\">",
            "    <div class=\"topMeta\"><div>轻·象棋</div><div id=\"totalTop\">总时 红:--:-- 黑:--:--</div><div id=\"stepTop\">当前步时倒计时: --s</div></div>",
            "    <div class=\"layout\">",
            "      <div class=\"boardCard\"><canvas id=\"board\" width=\"800\" height=\"900\"></canvas></div>",
            "      <div class=\"side\">",
            "        <div class=\"title\">控制面板</div>",
            "        <div class=\"row\"><select id=\"mode\"><option value=\"pvp\">双人对战</option><option value=\"pvc\">人机对战</option></select><select id=\"difficulty\"><option value=\"EASY\">简单</option><option value=\"MEDIUM\" selected>中等</option><option value=\"HARD\">困难</option></select></div>",
            "        <div class=\"row\"><select id=\"firstHand\" disabled><option value=\"true\" selected>我先手（执红）</option><option value=\"false\">我后手（执黑）</option></select></div>",
            "        <div class=\"row\"><button id=\"newGame\">新开一局</button><button id=\"undo\">悔棋</button></div>",
            "        <div class=\"row\"><button id=\"surrender\">认输</button></div>",
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
            "        <div id=\"reviewTag\" class=\"tag\">回顾: 关闭</div>",
            "        <div id=\"info\" class=\"log\">等待数据...</div>",
            "      </div>",
            "    </div>",
            "  </div>",
            "<script>",
            "const BASE_W=800,BASE_H=900,CELL=78,MARGIN=64,R=31;",
            "const canvas=document.getElementById('board'); const ctx=canvas.getContext('2d',{alpha:true});",
            "let state=null,reqSeq=0,pending=false,needRender=false,scale=1,dpr=Math.max(1,window.devicePixelRatio||1),anim=null,animKey='';",
            "const cache=document.createElement('canvas'); cache.width=BASE_W; cache.height=BASE_H; const cctx=cache.getContext('2d');",
            "function setupCanvas(){const parentW=Math.max(320,canvas.parentElement.clientWidth-8);const viewH=Math.max(480,window.innerHeight-56);const fitWByH=Math.floor(viewH*BASE_W/BASE_H);const cssW=Math.max(320,Math.min(parentW,fitWByH));const cssH=Math.round(cssW*BASE_H/BASE_W);scale=cssW/BASE_W;canvas.style.width=cssW+'px';canvas.style.height=cssH+'px';canvas.width=Math.round(cssW*dpr);canvas.height=Math.round(cssH*dpr);ctx.setTransform(dpr*scale,0,0,dpr*scale,0,0);scheduleRender();}",
            "window.addEventListener('resize', setupCanvas);",
            "const isFlipped=()=>state&&state.mode==='PVC'&&state.pvcHumanColor==='BLACK';const vr=r=>isFlipped()?9-r:r;const vc=c=>isFlipped()?8-c:c;const br=r=>isFlipped()?9-r:r;const bc=c=>isFlipped()?8-c:c;const pos=(r,c)=>[MARGIN+vc(c)*CELL,MARGIN+vr(r)*CELL];function pickGrid(x,y){const vcol=Math.round((x-MARGIN)/CELL),vrow=Math.round((y-MARGIN)/CELL);if(vrow<0||vrow>=10||vcol<0||vcol>=9)return null;const gx=MARGIN+vcol*CELL,gy=MARGIN+vrow*CELL;const d=Math.hypot(x-gx,y-gy);if(d>CELL*0.62)return null;return {row:br(vrow),col:bc(vcol)};}",
            "function drawStatic(){cctx.clearRect(0,0,BASE_W,BASE_H);const g=cctx.createLinearGradient(0,0,0,BASE_H);g.addColorStop(0,'#24356c');g.addColorStop(1,'#1f2c5e');cctx.fillStyle=g;cctx.fillRect(MARGIN-26,MARGIN-26,8*CELL+52,9*CELL+52);cctx.strokeStyle='#b98a52';cctx.lineWidth=4;cctx.strokeRect(MARGIN-30,MARGIN-30,8*CELL+60,9*CELL+60);cctx.strokeStyle='#d5b26a';cctx.lineWidth=2;for(let r=0;r<10;r++){const y=MARGIN+r*CELL;cctx.beginPath();cctx.moveTo(MARGIN,y);cctx.lineTo(MARGIN+8*CELL,y);cctx.stroke();}for(let c=0;c<9;c++){const x=MARGIN+c*CELL;cctx.beginPath();if(c===0||c===8){cctx.moveTo(x,MARGIN);cctx.lineTo(x,MARGIN+9*CELL);}else{cctx.moveTo(x,MARGIN);cctx.lineTo(x,MARGIN+4*CELL);cctx.moveTo(x,MARGIN+5*CELL);cctx.lineTo(x,MARGIN+9*CELL);}cctx.stroke();}cctx.beginPath();cctx.moveTo(MARGIN+3*CELL,MARGIN);cctx.lineTo(MARGIN+5*CELL,MARGIN+2*CELL);cctx.moveTo(MARGIN+5*CELL,MARGIN);cctx.lineTo(MARGIN+3*CELL,MARGIN+2*CELL);cctx.moveTo(MARGIN+3*CELL,MARGIN+7*CELL);cctx.lineTo(MARGIN+5*CELL,MARGIN+9*CELL);cctx.moveTo(MARGIN+5*CELL,MARGIN+7*CELL);cctx.lineTo(MARGIN+3*CELL,MARGIN+9*CELL);cctx.stroke();cctx.fillStyle='rgba(44,63,130,.95)';cctx.fillRect(MARGIN+1,MARGIN+4*CELL+1,8*CELL-2,CELL-2);cctx.strokeStyle='rgba(213,178,106,.4)';cctx.beginPath();cctx.moveTo(MARGIN,MARGIN+4*CELL);cctx.lineTo(MARGIN+8*CELL,MARGIN+4*CELL);cctx.moveTo(MARGIN,MARGIN+5*CELL);cctx.lineTo(MARGIN+8*CELL,MARGIN+5*CELL);cctx.stroke();cctx.font='bold 48px KaiTi';cctx.fillStyle='#d8b574';cctx.fillText('楚 河',MARGIN+CELL-8,MARGIN+4*CELL+52);cctx.fillText('汉 界',MARGIN+5*CELL+8,MARGIN+4*CELL+52);}",
            "function scheduleRender(){if(needRender)return;needRender=true;requestAnimationFrame(()=>{needRender=false;draw();});}",
            "function draw(){ctx.clearRect(0,0,BASE_W,BASE_H);ctx.drawImage(cache,0,0);if(!state)return;drawMarkers();drawPieces();drawMoveAnim();drawSelection();drawTacticFlash();}",
            "function drawPieces(){for(let r=0;r<10;r++){for(let c=0;c<9;c++){const p=state.board[r][c];if(!p)continue;const [x,y]=pos(r,c);ctx.fillStyle='rgba(13,8,0,.22)';ctx.beginPath();ctx.arc(x+2,y+2,R,0,Math.PI*2);ctx.fill();const g=ctx.createRadialGradient(x-8,y-8,4,x,y,R);if(p.color==='RED'){g.addColorStop(0,'#fff5e9');g.addColorStop(1,'#f2d9bf');}else{g.addColorStop(0,'#fff');g.addColorStop(1,'#e8e5df');}ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,R,0,Math.PI*2);ctx.fill();ctx.lineWidth=3;ctx.strokeStyle=(p.color==='RED')?'#c6403c':'#2a2a2a';ctx.beginPath();ctx.arc(x,y,R,0,Math.PI*2);ctx.stroke();ctx.font='bold 35px KaiTi';ctx.fillStyle=(p.color==='RED')?'#c6403c':'#202020';const w=ctx.measureText(p.name).width;ctx.fillText(p.name,x-w/2,y+13);}}}",
            "function drawMarkers(){if(!state.recentMoves)return;for(const m of state.recentMoves){const [fx,fy]=pos(m.fromRow,m.fromCol),[tx,ty]=pos(m.toRow,m.toCol);const color=m.color==='RED'?'rgba(198,64,60,.96)':'rgba(35,35,35,.96)';ctx.strokeStyle=color;ctx.lineWidth=2.5;ctx.beginPath();ctx.arc(fx,fy,R-10,0,Math.PI*2);ctx.stroke();const s=(m.order===1)?R+9:R+5;ctx.lineWidth=3;ctx.strokeRect(tx-s,ty-s,s*2,s*2);ctx.fillStyle='rgba(255,248,230,.95)';ctx.font='bold 16px Consolas';ctx.fillText(String(m.order),tx-4,ty-s-6);}}",
            "function drawSelection(){if(state.reviewMode)return;if(state.selectedRow>=0&&state.selectedCol>=0){const [x,y]=pos(state.selectedRow,state.selectedCol);ctx.fillStyle='rgba(0,160,70,.20)';ctx.fillRect(x-CELL/2+3,y-CELL/2+3,CELL-6,CELL-6);}}",
            "function drawTacticFlash(){if(!state.tacticText)return;ctx.fillStyle='rgba(7,10,26,.82)';ctx.fillRect(BASE_W/2-120,BASE_H/2-44,240,62);ctx.strokeStyle='#d8b86f';ctx.lineWidth=2;ctx.strokeRect(BASE_W/2-120,BASE_H/2-44,240,62);ctx.font='bold 36px Microsoft YaHei UI';ctx.fillStyle='#ffd86e';const w=ctx.measureText(state.tacticText).width;ctx.fillText(state.tacticText,(BASE_W-w)/2,BASE_H/2);}function fmtSec(v){if(v==null||v<0)return '--:--';const m=Math.floor(v/60),s=v%60;return String(m).padStart(2,'0')+':'+String(s).padStart(2,'0');}function primeAnim(){if(!state||!state.recentMoves||!state.recentMoves.length)return;const m=state.recentMoves[0];const k=[m.fromRow,m.fromCol,m.toRow,m.toCol,m.color].join('-');if(k===animKey)return;animKey=k;const p=state.board[m.toRow][m.toCol];if(!p)return;const [fx,fy]=pos(m.fromRow,m.fromCol),[tx,ty]=pos(m.toRow,m.toCol);anim={fx,fy,tx,ty,name:p.name,color:p.color,start:performance.now(),dur:220};}function drawMoveAnim(){if(!anim)return;const t=(performance.now()-anim.start)/anim.dur;if(t>=1){anim=null;return;}const k=Math.max(0,Math.min(1,t));const ease=1-Math.pow(1-k,3);const x=anim.fx+(anim.tx-anim.fx)*ease,y=anim.fy+(anim.ty-anim.fy)*ease;ctx.fillStyle='rgba(13,8,0,.22)';ctx.beginPath();ctx.arc(x+2,y+2,R,0,Math.PI*2);ctx.fill();const g=ctx.createRadialGradient(x-8,y-8,4,x,y,R);if(anim.color==='RED'){g.addColorStop(0,'#fff5e9');g.addColorStop(1,'#f2d9bf');}else{g.addColorStop(0,'#fff');g.addColorStop(1,'#e8e5df');}ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,R,0,Math.PI*2);ctx.fill();ctx.lineWidth=3;ctx.strokeStyle=(anim.color==='RED')?'#c6403c':'#2a2a2a';ctx.beginPath();ctx.arc(x,y,R,0,Math.PI*2);ctx.stroke();ctx.font='bold 35px KaiTi';ctx.fillStyle=(anim.color==='RED')?'#c6403c':'#202020';const w=ctx.measureText(anim.name).width;ctx.fillText(anim.name,x-w/2,y+13);scheduleRender();}",
            "async function api(path){const q=path.includes('?')?'&':'?';const url=path+q+'_t='+Date.now();const res=await fetch(url,{cache:'no-store'});return await res.json();}",
            "async function refresh(){if(pending)return;pending=true;const seq=++reqSeq;try{const data=await api('/api/state');if(seq!==reqSeq)return;state=data;const modeSel=document.getElementById('mode');const firstSel=document.getElementById('firstHand');firstSel.disabled=modeSel.value!=='pvc';document.getElementById('statusTag').textContent='状态: '+(!state.started?'待开始':(state.gameOver?(state.result||'结束'):(state.reviewMode?'回顾模式':'进行中')));const sr=state.stepRemainSec;document.getElementById('stepTop').textContent='当前步时倒计时: '+((sr!=null&&sr>=0)?(sr+'s'):'--s');document.getElementById('totalTop').textContent='总时 红:'+fmtSec(state.redTotalSec)+' 黑:'+fmtSec(state.blackTotalSec);const humanTxt=state.pvcHumanColor==='BLACK'?' / 玩家执黑':' / 玩家执红';document.getElementById('modeTag').textContent='模式: '+(state.mode==='PVC'?'人机':'双人')+' / '+state.difficultyText+(state.mode==='PVC'?humanTxt:'');document.getElementById('endgameTag').textContent='残局: '+(state.endgame||'标准开局');document.getElementById('reviewTag').textContent=state.reviewMode?('回顾: 第 '+state.reviewMoveIndex+' / '+state.reviewMaxMove+' 步'):'回顾: 关闭';document.getElementById('info').textContent=!state.started?'请点击“新开一局”开始':(state.gameOver?(state.result||'对局结束'):('当前回合: '+(state.currentTurn==='RED'?'红方':'黑方')));document.getElementById('undo').disabled=!state.started||state.reviewMode||state.gameOver;document.getElementById('surrender').disabled=!state.started||state.reviewMode||state.gameOver;document.getElementById('reviewStart').disabled=!state.started||!state.canReview||state.reviewMode;document.getElementById('reviewPrev').disabled=!state.reviewMode||state.reviewMoveIndex<=0;document.getElementById('reviewNext').disabled=!state.reviewMode||state.reviewMoveIndex>=state.reviewMaxMove;document.getElementById('reviewExit').disabled=!state.reviewMode;primeAnim();scheduleRender();}finally{pending=false;}}",
            "async function act(path){if(pending)return;await api(path);await refresh();}",
            "canvas.addEventListener('click',async e=>{if(state&&(!state.started||state.reviewMode||state.gameOver))return;const rect=canvas.getBoundingClientRect();const sx=BASE_W/rect.width,sy=BASE_H/rect.height;const x=(e.clientX-rect.left)*sx,y=(e.clientY-rect.top)*sy;const g=pickGrid(x,y);if(!g)return;await act('/api/click?row='+g.row+'&col='+g.col);});",
            "document.getElementById('newGame').addEventListener('click',async()=>{const mode=document.getElementById('mode').value,d=document.getElementById('difficulty').value,h=document.getElementById('firstHand').value;await act('/api/new?mode='+mode+'&difficulty='+d+'&humanFirst='+h);});document.querySelectorAll('.egBtn').forEach(btn=>btn.addEventListener('click',async()=>{const mode=document.getElementById('mode').value,d=document.getElementById('difficulty').value,h=document.getElementById('firstHand').value,name=encodeURIComponent(btn.dataset.name);await act('/api/endgame?name='+name+'&mode='+mode+'&difficulty='+d+'&humanFirst='+h);}));",
            "document.getElementById('undo').addEventListener('click',async()=>act('/api/undo'));document.getElementById('surrender').addEventListener('click',async()=>{if(confirm('确定要认输吗？')){await act('/api/surrender');}});",
            "document.getElementById('reviewStart').addEventListener('click',async()=>act('/api/review/start'));",
            "document.getElementById('reviewPrev').addEventListener('click',async()=>act('/api/review/prev'));",
            "document.getElementById('reviewNext').addEventListener('click',async()=>act('/api/review/next'));",
            "document.getElementById('reviewExit').addEventListener('click',async()=>act('/api/review/exit'));",
            "document.getElementById('mode').addEventListener('change',()=>{document.getElementById('firstHand').disabled=document.getElementById('mode').value!=='pvc';});",
            "drawStatic();setupCanvas();refresh();setInterval(refresh,120);",
            "</script>",
            "</body>",
            "</html>");
    }
}





















