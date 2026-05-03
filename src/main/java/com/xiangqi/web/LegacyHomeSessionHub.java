package com.xiangqi.web;

import com.xiangqi.ai.ConfigurableXiangqiEngine;
import com.xiangqi.ai.MinimaxAI;
import com.xiangqi.model.PieceType;
import com.xiangqi.model.gomoku.ConfigurableGomokuEngine;
import com.xiangqi.online.auth.AuthUser;
import com.xiangqi.online.game.GameType;
import com.xiangqi.online.practice.CreatePracticeGameRequest;
import com.xiangqi.online.practice.PracticeGameHub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class LegacyHomeSessionHub {
    private static final String GAME_XIANGQI = "XIANGQI";
    private static final String GAME_GOMOKU = "GOMOKU";
    private static final String MODE_PVC = "PVC";

    private final ConcurrentHashMap<String, LegacySession> sessions = new ConcurrentHashMap<String, LegacySession>();
    private final PracticeGameHub practiceHub;
    private final boolean pikafishConfigured;
    private final boolean rapfiConfigured;
    private final boolean alphaConfigured;

    public LegacyHomeSessionHub(PracticeGameHub practiceHub) {
        this.practiceHub = practiceHub;
        this.pikafishConfigured = detectPikafishConfigured();
        boolean[] gomokuCapabilities = detectGomokuCapabilities();
        this.rapfiConfigured = gomokuCapabilities[0];
        this.alphaConfigured = gomokuCapabilities[1];
    }

    public Map<String, Object> state(String sessionId, AuthUser user) {
        LegacySession session = session(sessionId);
        synchronized (session) {
            return buildState(session, user);
        }
    }

    public Map<String, Object> newGame(String sessionId, AuthUser user, GameType gameType, String difficulty, boolean humanFirst, String preferredEngine) {
        if (user == null) {
            throw new IllegalArgumentException("login required");
        }
        LegacySession session = session(sessionId);
        synchronized (session) {
            Map<String, Object> game = practiceHub.createGame(user, new CreatePracticeGameRequest(
                gameType,
                difficulty,
                humanFirst,
                preferredEngine,
                ""
            ));
            session.gameId = asString(game.get("gameId"));
            session.gameType = normalizeGameType(gameType == null ? "" : gameType.name());
            session.difficulty = difficultyOf(difficulty).name();
            session.preferredEngine = preferredEngine == null ? defaultEngine(session.gameType) : preferredEngine.trim();
            session.humanColor = asString(game.get("viewerSide"));
            session.reviewMode = false;
            session.reviewMoveIndex = 0;
            session.selectedRow = -1;
            session.selectedCol = -1;
            session.endgameLabel = "标准开局";
            return buildState(session, user, game);
        }
    }

    public Map<String, Object> click(String sessionId, AuthUser user, int row, int col) {
        if (user == null) {
            throw new IllegalArgumentException("login required");
        }
        LegacySession session = session(sessionId);
        synchronized (session) {
            if (session.gameId.isEmpty()) {
                return buildState(session, user);
            }
            Map<String, Object> live = currentGame(session, user);
            if (!isStarted(live) || session.reviewMode || asBoolean(live.get("gameOver"))) {
                return buildState(session, user);
            }
            if (GAME_GOMOKU.equals(asString(live.get("gameType")))) {
                if (cellAt(live, row, col) != null) {
                    return buildState(session, user);
                }
                Map<String, Object> updated = practiceHub.applyMove(session.gameId, user, gomokuPayload(row, col));
                session.selectedRow = -1;
                session.selectedCol = -1;
                return buildState(session, user, updated);
            }
            return clickXiangqi(session, user, live, row, col);
        }
    }

    public Map<String, Object> surrender(String sessionId, AuthUser user) {
        if (user == null) {
            throw new IllegalArgumentException("login required");
        }
        LegacySession session = session(sessionId);
        synchronized (session) {
            if (!session.gameId.isEmpty()) {
                Map<String, Object> updated = practiceHub.resign(session.gameId, user);
                session.selectedRow = -1;
                session.selectedCol = -1;
                return buildState(session, user, updated);
            }
            session.selectedRow = -1;
            session.selectedCol = -1;
            return buildState(session, user);
        }
    }

    public Map<String, Object> reviewStart(String sessionId, AuthUser user) {
        LegacySession session = session(sessionId);
        synchronized (session) {
            int maxMove = moveCount(currentAnalysis(session));
            if (maxMove <= 0) {
                return buildState(session, user);
            }
            session.reviewMode = true;
            session.reviewMoveIndex = 0;
            session.selectedRow = -1;
            session.selectedCol = -1;
            return buildState(session, user);
        }
    }

    public Map<String, Object> reviewPrev(String sessionId, AuthUser user) {
        LegacySession session = session(sessionId);
        synchronized (session) {
            session.reviewMoveIndex = Math.max(0, session.reviewMoveIndex - 1);
            return buildState(session, user);
        }
    }

    public Map<String, Object> reviewNext(String sessionId, AuthUser user) {
        LegacySession session = session(sessionId);
        synchronized (session) {
            int maxMove = moveCount(currentAnalysis(session));
            session.reviewMoveIndex = Math.min(maxMove, session.reviewMoveIndex + 1);
            return buildState(session, user);
        }
    }

    public Map<String, Object> reviewExit(String sessionId, AuthUser user) {
        LegacySession session = session(sessionId);
        synchronized (session) {
            session.reviewMode = false;
            session.reviewMoveIndex = 0;
            session.selectedRow = -1;
            session.selectedCol = -1;
            return buildState(session, user);
        }
    }

    private Map<String, Object> clickXiangqi(LegacySession session, AuthUser user, Map<String, Object> live, int row, int col) {
        Map<String, Object> cell = cellAt(live, row, col);
        String humanColor = session.humanColor;
        String currentTurn = asString(live.get("currentTurn"));
        if (session.selectedRow < 0 || session.selectedCol < 0) {
            if (cell != null && humanColor.equals(asString(cell.get("color"))) && currentTurn.equals(humanColor)) {
                session.selectedRow = row;
                session.selectedCol = col;
            }
            return buildState(session, user);
        }
        if (cell != null && humanColor.equals(asString(cell.get("color")))) {
            session.selectedRow = row;
            session.selectedCol = col;
            return buildState(session, user);
        }
        Map<String, Object> updated = practiceHub.applyMove(session.gameId, user, xiangqiPayload(session.selectedRow, session.selectedCol, row, col));
        session.selectedRow = -1;
        session.selectedCol = -1;
        return buildState(session, user, updated);
    }

    private Map<String, Object> buildState(LegacySession session, AuthUser user) {
        return buildState(session, user, currentGame(session, user));
    }

    private Map<String, Object> buildState(LegacySession session, AuthUser user, Map<String, Object> game) {
        Map<String, Object> analysis = session.gameId.isEmpty() ? Collections.<String, Object>emptyMap() : game;
        int reviewMaxMove = moveCount(analysis);
        int reviewIndex = session.reviewMode ? Math.max(0, Math.min(session.reviewMoveIndex, reviewMaxMove)) : 0;
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("seq", session.seq.incrementAndGet());
        state.put("gameType", session.gameType);
        state.put("boardSize", GAME_GOMOKU.equals(session.gameType) ? 15 : 9);
        state.put("boardRows", GAME_GOMOKU.equals(session.gameType) ? 15 : 10);
        state.put("boardCols", GAME_GOMOKU.equals(session.gameType) ? 15 : 9);
        state.put("ruleset", GAME_GOMOKU.equals(session.gameType) ? "renju_forbidden_black" : "xiangqi_standard");
        state.put("started", isStarted(game));
        state.put("mode", MODE_PVC);
        state.put("difficulty", session.difficulty);
        state.put("difficultyText", difficultyOf(session.difficulty).getDisplayName());
        fillEngineState(state, session, game);
        state.put("pvcHumanColor", session.humanColor);
        state.put("endgame", session.endgameLabel);
        state.put("currentTurn", session.reviewMode ? reviewTurn(session.gameType, reviewIndex, reviewMaxMove, game) : asString(game.get("currentTurn")));
        state.put("gameOver", asBoolean(game.get("gameOver")));
        state.put("aiPending", asBoolean(game.get("aiPending")));
        state.put("canDraw", false);
        state.put("result", asString(game.get("resultText")));
        state.put("drawReason", "");
        state.put("selectedRow", session.reviewMode ? -1 : session.selectedRow);
        state.put("selectedCol", session.reviewMode ? -1 : session.selectedCol);
        state.put("canReview", reviewMaxMove > 0);
        state.put("reviewMode", session.reviewMode);
        state.put("reviewMoveIndex", reviewIndex);
        state.put("reviewMaxMove", reviewMaxMove);
        state.put("stepRemainSec", -1);
        state.put("redTotalSec", -1);
        state.put("blackTotalSec", -1);
        state.put("tacticText", "");
        state.put("tacticSeq", 0);
        state.put("recentMoves", recentMoves(analysis, session.reviewMode ? reviewIndex : reviewMaxMove));
        if (GAME_GOMOKU.equals(session.gameType)) {
            state.put("gomoku", gomokuMeta(game));
        }
        state.put("board", session.gameId.isEmpty() ? initialBoard(session.gameType) : viewBoard(session, game, analysis, reviewIndex));
        return state;
    }

    private void fillEngineState(Map<String, Object> state, LegacySession session, Map<String, Object> game) {
        if (GAME_GOMOKU.equals(session.gameType)) {
            state.put("gomokuAiEngine", currentEngineId(game, session.gameType, session));
            state.put("gomokuAiEngineText", currentEngineText(game, session.gameType, session));
            state.put("gomokuAiSelected", session.preferredEngine);
            state.put("gomokuAiRapfiConfigured", rapfiConfigured);
            state.put("gomokuAiAlphaConfigured", alphaConfigured);
            return;
        }
        state.put("xiangqiAiEngine", currentEngineId(game, session.gameType, session));
        state.put("xiangqiAiEngineText", currentEngineText(game, session.gameType, session));
        state.put("xiangqiAiSelected", session.preferredEngine);
        state.put("xiangqiAiPikafishConfigured", pikafishConfigured);
    }

    private Map<String, Object> gomokuMeta(Map<String, Object> game) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("forbiddenEnabled", true);
        item.put("aiEngine", currentEngineId(game, GAME_GOMOKU, null));
        item.put("aiEngineText", currentEngineText(game, GAME_GOMOKU, null));
        item.put("forbiddenReason", "");
        item.put("forbiddenPoints", Collections.emptyList());
        item.put("winnerLine", null);
        return item;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recentMoves(Map<String, Object> analysis, int moveIndex) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> moves = (List<Map<String, Object>>) analysis.get("moves");
        if (moves == null || moveIndex <= 0) {
            return items;
        }
        for (int index = Math.min(moveIndex, moves.size()) - 1, order = 1; index >= 0 && order <= 2; index--, order++) {
            Map<String, Object> source = moves.get(index);
            Map<String, Object> payload = asMap(source.get("payload"));
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("order", order);
            item.put("color", asString(source.get("side")));
            if (payload.containsKey("fromRow")) {
                item.put("fromRow", payload.get("fromRow"));
                item.put("fromCol", payload.get("fromCol"));
                item.put("toRow", payload.get("toRow"));
                item.put("toCol", payload.get("toCol"));
            } else {
                item.put("fromRow", payload.get("row"));
                item.put("fromCol", payload.get("col"));
                item.put("toRow", payload.get("row"));
                item.put("toCol", payload.get("col"));
            }
            items.add(item);
        }
        return items;
    }

    private List<List<Object>> viewBoard(LegacySession session, Map<String, Object> game, Map<String, Object> analysis, int reviewIndex) {
        Object rawBoard = session.reviewMode ? boardFromHistory(analysis, reviewIndex) : game.get("board");
        List<List<String>> board = normalizeBoard(rawBoard);
        return GAME_GOMOKU.equals(session.gameType) ? legacyGomokuBoard(board) : legacyXiangqiBoard(board);
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> normalizeBoard(Object rawBoard) {
        if (rawBoard instanceof List) {
            return (List<List<String>>) rawBoard;
        }
        if (rawBoard instanceof String[][]) {
            String[][] grid = (String[][]) rawBoard;
            List<List<String>> board = new ArrayList<List<String>>();
            for (String[] row : grid) {
                List<String> cells = new ArrayList<String>();
                Collections.addAll(cells, row);
                board.add(cells);
            }
            return board;
        }
        return new ArrayList<List<String>>();
    }

    @SuppressWarnings("unchecked")
    private Object boardFromHistory(Map<String, Object> analysis, int reviewIndex) {
        List<List<List<String>>> boards = (List<List<List<String>>>) analysis.get("historyBoards");
        if (boards == null || boards.isEmpty()) {
            return analysis.get("board");
        }
        return boards.get(Math.max(0, Math.min(reviewIndex, boards.size() - 1)));
    }

    private List<List<Object>> initialBoard(String gameType) {
        return GAME_GOMOKU.equals(gameType) ? legacyGomokuBoard(emptyGomokuBoard()) : legacyXiangqiBoard(initialXiangqiBoard());
    }

    private List<List<String>> initialXiangqiBoard() {
        List<List<String>> rows = new ArrayList<List<String>>();
        rows.add(row("车", "马", "象", "士", "将", "士", "象", "马", "车"));
        rows.add(row("", "", "", "", "", "", "", "", ""));
        rows.add(row("", "炮", "", "", "", "", "", "炮", ""));
        rows.add(row("兵", "", "兵", "", "兵", "", "兵", "", "兵"));
        rows.add(row("", "", "", "", "", "", "", "", ""));
        rows.add(row("", "", "", "", "", "", "", "", ""));
        rows.add(row("卒", "", "卒", "", "卒", "", "卒", "", "卒"));
        rows.add(row("", "砲", "", "", "", "", "", "砲", ""));
        rows.add(row("", "", "", "", "", "", "", "", ""));
        rows.add(row("車", "馬", "相", "仕", "帅", "仕", "相", "馬", "車"));
        return rows;
    }

    private List<List<String>> emptyGomokuBoard() {
        List<List<String>> rows = new ArrayList<List<String>>();
        for (int row = 0; row < 15; row++) {
            List<String> cells = new ArrayList<String>();
            for (int col = 0; col < 15; col++) {
                cells.add("");
            }
            rows.add(cells);
        }
        return rows;
    }

    private List<String> row(String... values) {
        List<String> cells = new ArrayList<String>();
        Collections.addAll(cells, values);
        return cells;
    }

    private List<List<Object>> legacyXiangqiBoard(List<List<String>> board) {
        List<List<Object>> rows = new ArrayList<List<Object>>();
        for (List<String> row : board) {
            List<Object> cells = new ArrayList<Object>();
            for (String cell : row) {
                if (cell == null || cell.isEmpty()) {
                    cells.add(null);
                } else {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("name", cell);
                    item.put("color", isRedXiangqiPiece(cell) ? "RED" : "BLACK");
                    cells.add(item);
                }
            }
            rows.add(cells);
        }
        return rows;
    }

    private List<List<Object>> legacyGomokuBoard(List<List<String>> board) {
        List<List<Object>> rows = new ArrayList<List<Object>>();
        for (List<String> row : board) {
            List<Object> cells = new ArrayList<Object>();
            for (String cell : row) {
                if (cell == null || cell.isEmpty()) {
                    cells.add(null);
                } else {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("name", "BLACK".equals(cell) ? "黑" : "白");
                    item.put("color", cell);
                    cells.add(item);
                }
            }
            rows.add(cells);
        }
        return rows;
    }

    private boolean isRedXiangqiPiece(String name) {
        return PieceType.SHUAI.getDisplayName().equals(name)
            || PieceType.SHI_RED.getDisplayName().equals(name)
            || PieceType.XIANG_RED.getDisplayName().equals(name)
            || PieceType.MA_RED.getDisplayName().equals(name)
            || PieceType.CHE_RED.getDisplayName().equals(name)
            || PieceType.PAO_RED.getDisplayName().equals(name)
            || PieceType.ZU_RED.getDisplayName().equals(name);
    }

    private Map<String, Object> cellAt(Map<String, Object> state, int row, int col) {
        Object rawBoard = state.get("board");
        if (rawBoard instanceof String[][]) {
            String gameType = asString(state.get("gameType"));
            List<List<Object>> board = GAME_GOMOKU.equals(gameType)
                ? legacyGomokuBoard(normalizeBoard(rawBoard))
                : legacyXiangqiBoard(normalizeBoard(rawBoard));
            return cellFromBoard(board, row, col);
        }
        if (rawBoard instanceof List) {
            @SuppressWarnings("unchecked")
            List<List<Object>> board = (List<List<Object>>) rawBoard;
            return cellFromBoard(board, row, col);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cellFromBoard(List<List<Object>> board, int row, int col) {
        if (board == null || row < 0 || row >= board.size()) {
            return null;
        }
        List<Object> rowCells = board.get(row);
        if (rowCells == null || col < 0 || col >= rowCells.size()) {
            return null;
        }
        Object cell = rowCells.get(col);
        return cell instanceof Map ? (Map<String, Object>) cell : null;
    }

    private int moveCount(Map<String, Object> analysis) {
        Object value = analysis.get("moveCount");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String reviewTurn(String gameType, int reviewIndex, int reviewMaxMove, Map<String, Object> game) {
        if (reviewIndex >= reviewMaxMove) {
            return asString(game.get("currentTurn"));
        }
        if (GAME_GOMOKU.equals(gameType)) {
            return reviewIndex % 2 == 0 ? "BLACK" : "WHITE";
        }
        return reviewIndex % 2 == 0 ? "RED" : "BLACK";
    }

    private Map<String, Object> currentGame(LegacySession session, AuthUser user) {
        if (session.gameId.isEmpty()) {
            return initialGameState(session);
        }
        Map<String, Object> snapshot = practiceHub.gameSnapshotById(session.gameId, user);
        if (session.humanColor.isEmpty()) {
            session.humanColor = asString(snapshot.get("viewerSide"));
        }
        return snapshot;
    }

    private Map<String, Object> currentAnalysis(LegacySession session) {
        return session.gameId.isEmpty() ? Collections.<String, Object>emptyMap() : practiceHub.analysis(session.gameId);
    }

    private Map<String, Object> initialGameState(LegacySession session) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("gameType", session.gameType);
        item.put("status", "IDLE");
        item.put("currentTurn", GAME_GOMOKU.equals(session.gameType) ? "BLACK" : "RED");
        item.put("moveCount", 0);
        item.put("resultText", "");
        item.put("board", GAME_GOMOKU.equals(session.gameType) ? emptyGomokuBoard() : initialXiangqiBoard());
        item.put("gameOver", false);
        return item;
    }

    private boolean isStarted(Map<String, Object> game) {
        return !"".equals(asString(game.get("gameId"))) || "PLAYING".equals(asString(game.get("status"))) || "FINISHED".equals(asString(game.get("status")));
    }

    private String currentEngineId(Map<String, Object> game, String gameType, LegacySession session) {
        Map<String, Object> ai = asMap(game.get("ai"));
        String engineId = asString(ai.get("engineId"));
        if (!engineId.isEmpty()) {
            return engineId;
        }
        return defaultEngineId(gameType, session == null ? "" : session.preferredEngine);
    }

    private String currentEngineText(Map<String, Object> game, String gameType, LegacySession session) {
        Map<String, Object> ai = asMap(game.get("ai"));
        String engineText = asString(ai.get("engineText"));
        if (!engineText.isEmpty()) {
            return engineText;
        }
        return defaultEngineText(gameType, session == null ? "" : session.preferredEngine);
    }

    private String defaultEngineId(String gameType, String preferredEngine) {
        String selected = preferredEngine == null || preferredEngine.trim().isEmpty() ? defaultEngine(gameType) : preferredEngine.trim();
        return "BUILTIN".equalsIgnoreCase(selected) || "AUTO".equalsIgnoreCase(selected) ? "builtin" : selected.toLowerCase();
    }

    private String defaultEngineText(String gameType, String preferredEngine) {
        String selected = preferredEngine == null || preferredEngine.trim().isEmpty() ? defaultEngine(gameType) : preferredEngine.trim();
        if ("PIKAFISH".equalsIgnoreCase(selected)) {
            return "Pikafish";
        }
        if ("RAPFI".equalsIgnoreCase(selected)) {
            return "Rapfi";
        }
        if ("ALPHAGOMOKU".equalsIgnoreCase(selected)) {
            return "AlphaGomoku";
        }
        if ("AUTO".equalsIgnoreCase(selected)) {
            return "自动选择";
        }
        return "内置AI";
    }

    private String defaultEngine(String gameType) {
        return GAME_GOMOKU.equals(gameType) ? "BUILTIN" : "BUILTIN";
    }

    private String normalizeGameType(String raw) {
        return GAME_GOMOKU.equalsIgnoreCase(raw) ? GAME_GOMOKU : GAME_XIANGQI;
    }

    private MinimaxAI.Difficulty difficultyOf(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase();
        if ("EASY".equals(value)) {
            return MinimaxAI.Difficulty.EASY;
        }
        if ("HARD".equals(value)) {
            return MinimaxAI.Difficulty.HARD;
        }
        return MinimaxAI.Difficulty.MEDIUM;
    }

    private Map<String, Object> xiangqiPayload(int fromRow, int fromCol, int toRow, int toCol) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("fromRow", fromRow);
        payload.put("fromCol", fromCol);
        payload.put("toRow", toRow);
        payload.put("toCol", toCol);
        return payload;
    }

    private Map<String, Object> gomokuPayload(int row, int col) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("row", row);
        payload.put("col", col);
        return payload;
    }

    private LegacySession session(String sessionId) {
        String key = sessionId == null || sessionId.trim().isEmpty() ? "legacy-default" : sessionId.trim();
        return sessions.computeIfAbsent(key, ignored -> new LegacySession());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.<String, Object>emptyMap();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean asBoolean(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private boolean detectPikafishConfigured() {
        ConfigurableXiangqiEngine engine = new ConfigurableXiangqiEngine();
        try {
            return engine.isPikafishConfigured();
        } finally {
            engine.close();
        }
    }

    private boolean[] detectGomokuCapabilities() {
        ConfigurableGomokuEngine engine = new ConfigurableGomokuEngine();
        try {
            return new boolean[] {engine.isRapfiConfigured(), engine.isAlphaGomokuConfigured()};
        } finally {
            engine.close();
        }
    }

    private static final class LegacySession {
        private final AtomicLong seq = new AtomicLong();
        private String gameId = "";
        private String gameType = GAME_XIANGQI;
        private String difficulty = MinimaxAI.Difficulty.MEDIUM.name();
        private String preferredEngine = "BUILTIN";
        private String humanColor = "RED";
        private boolean reviewMode = false;
        private int reviewMoveIndex = 0;
        private int selectedRow = -1;
        private int selectedCol = -1;
        private String endgameLabel = "标准开局";
    }
}
