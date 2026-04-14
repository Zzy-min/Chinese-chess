package com.xiangqi.online.practice;

import com.xiangqi.ai.ConfigurableXiangqiEngine;
import com.xiangqi.ai.MinimaxAI;
import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.PieceColor;
import com.xiangqi.model.gomoku.ConfigurableGomokuEngine;
import com.xiangqi.model.gomoku.GomokuStone;
import com.xiangqi.online.auth.AuthUser;
import com.xiangqi.online.game.GameType;
import com.xiangqi.online.game.GomokuMatch;
import com.xiangqi.online.game.MatchEvent;
import com.xiangqi.online.game.MatchPlayer;
import com.xiangqi.online.game.OnlineMatchEngine;
import com.xiangqi.online.game.PlayerSide;
import com.xiangqi.online.game.XiangqiMatch;
import com.xiangqi.controller.EndgameLoader;
import com.xiangqi.online.server.OnlineStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PracticeGameHub {
    private final ConcurrentHashMap<String, ActivePracticeGame> gamesById = new ConcurrentHashMap<String, ActivePracticeGame>();
    private final OnlineStore store;

    public PracticeGameHub(OnlineStore store) {
        this.store = store;
    }

    public Map<String, Object> createGame(AuthUser user, CreatePracticeGameRequest request) {
        if (request == null || request.gameType() == null) {
            throw new IllegalArgumentException("game type is required");
        }
        if (request.gameType() == GameType.GO) {
            throw new IllegalArgumentException("GO practice is not yet available");
        }

        ActivePracticeGame game = new ActivePracticeGame();
        game.gameId = UUID.randomUUID().toString();
        game.gameType = request.gameType();
        game.human = user;
        game.aiUser = new AuthUser(aiUserId(request.gameType()), "AI");
        game.difficulty = difficultyOf(request.difficulty());
        game.enginePreference = request.preferredEngine() == null ? "" : request.preferredEngine().trim();
        game.updatedAt = Instant.now();
        game.status = "PLAYING";
        game.opponentType = "AI";

        if (request.gameType() == GameType.GOMOKU) {
            if (request.humanFirst()) {
                game.humanSide = "BLACK";
                game.aiSide = "WHITE";
                game.match = new GomokuMatch(
                    new MatchPlayer(user.id(), user.username(), PlayerSide.BLACK),
                    new MatchPlayer(game.aiUser.id(), game.aiUser.username(), PlayerSide.WHITE)
                );
            } else {
                game.humanSide = "WHITE";
                game.aiSide = "BLACK";
                game.match = new GomokuMatch(
                    new MatchPlayer(game.aiUser.id(), game.aiUser.username(), PlayerSide.BLACK),
                    new MatchPlayer(user.id(), user.username(), PlayerSide.WHITE)
                );
            }
            game.gomokuEngine = new ConfigurableGomokuEngine();
            game.gomokuEngine.setPreferredEngine(game.enginePreference);
        } else {
            Board customBoard = null;
            if (request.fen() != null && !request.fen().isBlank()) {
                customBoard = new Board();
                customBoard.initializeBoard();
                for (int r = 0; r < Board.ROWS; r++) {
                    for (int c = 0; c < Board.COLS; c++) {
                        customBoard.setPiece(r, c, null);
                    }
                }
                EndgameLoader.loadFromFen(customBoard, request.fen());
                game.endgameId = request.endgameId();
                game.endgameName = request.endgameName();
            }
            if (request.humanFirst()) {
                game.humanSide = "RED";
                game.aiSide = "BLACK";
                game.match = customBoard != null
                    ? new XiangqiMatch(
                        new MatchPlayer(user.id(), user.username(), PlayerSide.RED),
                        new MatchPlayer(game.aiUser.id(), game.aiUser.username(), PlayerSide.BLACK),
                        customBoard)
                    : new XiangqiMatch(
                        new MatchPlayer(user.id(), user.username(), PlayerSide.RED),
                        new MatchPlayer(game.aiUser.id(), game.aiUser.username(), PlayerSide.BLACK));
            } else {
                game.humanSide = "BLACK";
                game.aiSide = "RED";
                game.match = customBoard != null
                    ? new XiangqiMatch(
                        new MatchPlayer(game.aiUser.id(), game.aiUser.username(), PlayerSide.RED),
                        new MatchPlayer(user.id(), user.username(), PlayerSide.BLACK),
                        customBoard)
                    : new XiangqiMatch(
                        new MatchPlayer(game.aiUser.id(), game.aiUser.username(), PlayerSide.RED),
                        new MatchPlayer(user.id(), user.username(), PlayerSide.BLACK));
            }
            game.xiangqiEngine = new ConfigurableXiangqiEngine();
            game.xiangqiEngine.setPreferredEngine(game.enginePreference);
        }

        game.currentTurn = game.match.currentTurnKey();
        gamesById.put(game.gameId, game);
        store.createGameRecord(game.gameId, "", snapshot(game, user));

        if (game.aiSide.equals(game.currentTurn)) {
            applyAiMove(game);
        }
        return snapshot(game, user);
    }

    public boolean hasActiveGame(String gameId) {
        return gamesById.containsKey(gameId);
    }

    public Map<String, Object> gameSnapshotById(String gameId, AuthUser viewer) {
        ActivePracticeGame game = gamesById.get(gameId);
        if (game == null) {
            return store.loadGameAnalysis(gameId);
        }
        synchronized (game) {
            return snapshot(game, viewer);
        }
    }

    public Map<String, Object> analysis(String gameId) {
        ActivePracticeGame game = gamesById.get(gameId);
        if (game == null) {
            return store.loadGameAnalysis(gameId);
        }
        synchronized (game) {
            return snapshot(game, null);
        }
    }

    public Board xiangqiBoard(String gameId) {
        ActivePracticeGame game = gamesById.get(gameId);
        if (game == null || !(game.match instanceof XiangqiMatch)) {
            return null;
        }
        synchronized (game) {
            return ((XiangqiMatch) game.match).boardState();
        }
    }

    public Map<String, Object> applyMove(String gameId, AuthUser actor, Map<String, Object> payload) {
        ActivePracticeGame game = game(gameId);
        synchronized (game) {
            ensureHumanParticipant(game, actor);
            ensurePlayable(game);
            MatchEvent preview = game.match.previewMove(actor.id(), payload);
            if (!preview.accepted()) {
                throw new IllegalArgumentException(preview.message());
            }
            MatchEvent event = game.match.applyMove(actor.id(), payload);
            if (!event.accepted()) {
                throw new IllegalArgumentException(event.message());
            }
            syncStateFromMatch(game);
            appendLatestMove(game, actor.id(), payloadWithSide(payload, game.humanSide));
            if (!refreshOutcome(game)) {
                applyAiMove(game);
            }
            return snapshot(game, actor);
        }
    }

    public Map<String, Object> resign(String gameId, AuthUser actor) {
        ActivePracticeGame game = game(gameId);
        synchronized (game) {
            ensureHumanParticipant(game, actor);
            ensurePlayable(game);
            finalizeGame(game, game.aiSide, actor.username() + " resigned", "RESIGN");
            return snapshot(game, actor);
        }
    }

    public Map<String, Object> getHint(String gameId, AuthUser actor) {
        ActivePracticeGame game = game(gameId);
        synchronized (game) {
            ensureHumanParticipant(game, actor);
            ensurePlayable(game);
            if (game.gameType != GameType.XIANGQI) {
                throw new IllegalArgumentException("hints only available for xiangqi puzzles");
            }
            XiangqiMatch match = (XiangqiMatch) game.match;
            PieceColor humanColor = "RED".equals(game.humanSide) ? PieceColor.RED : PieceColor.BLACK;
            Move move = game.xiangqiEngine.findBestMove(match.boardState(), humanColor, game.difficulty);
            if (move == null) {
                throw new IllegalArgumentException("no hint available");
            }
            game.hintUsed++;
            Map<String, Object> hint = new LinkedHashMap<String, Object>();
            hint.put("fromRow", move.getFromRow());
            hint.put("fromCol", move.getFromCol());
            hint.put("toRow", move.getToRow());
            hint.put("toCol", move.getToCol());
            hint.put("hintUsed", game.hintUsed);
            return hint;
        }
    }

    private void applyAiMove(ActivePracticeGame game) {
        if ("FINISHED".equals(game.status)) {
            return;
        }
        Map<String, Object> payload = nextAiPayload(game);
        if (payload.isEmpty()) {
            finalizeGame(game, game.humanSide, "AI has no legal move", "AI_NO_MOVE");
            return;
        }
        MatchEvent event = game.match.applyMove(game.aiUser.id(), payload);
        if (!event.accepted()) {
            finalizeGame(game, game.humanSide, "AI move failed", "AI_ERROR");
            return;
        }
        syncStateFromMatch(game);
        appendLatestMove(game, game.aiUser.id(), payloadWithSide(payload, game.aiSide));
        refreshOutcome(game);
    }

    private void appendLatestMove(ActivePracticeGame game, String actorUserId, Map<String, Object> payload) {
        List<Map<String, Object>> engineMoves = game.match.moves();
        Map<String, Object> base = engineMoves.isEmpty()
            ? new LinkedHashMap<String, Object>()
            : engineMoves.get(engineMoves.size() - 1);
        Map<String, Object> move = new LinkedHashMap<String, Object>();
        move.put("index", base.get("index"));
        move.put("side", base.get("side"));
        move.put("notation", base.get("notation"));
        move.put("payload", payload);
        move.put("actorUserId", actorUserId);
        move.put("createdAt", Instant.now().toString());
        game.moves.add(move);
        game.updatedAt = Instant.now();
        store.appendMove(
            game.gameId,
            game.moves.size(),
            actorUserId,
            asString(base.get("side")),
            asString(base.get("notation")),
            payload,
            snapshot(game, null)
        );
    }

    private boolean refreshOutcome(ActivePracticeGame game) {
        syncStateFromMatch(game);
        if (!game.match.finished()) {
            return false;
        }
        finalizeGame(game, game.match.winnerSide(), game.match.resultText(), "GAME_OVER");
        return true;
    }

    private void finalizeGame(ActivePracticeGame game, String winnerSide, String resultText, String terminationReason) {
        game.status = "FINISHED";
        game.currentTurn = "";
        game.winnerSide = winnerSide == null ? "" : winnerSide;
        game.resultText = resultText == null ? "" : resultText;
        game.terminationReason = terminationReason == null ? "" : terminationReason;
        game.updatedAt = Instant.now();
        if (game.endgameId != null && !game.endgameId.isEmpty()
            && winnerSide != null && winnerSide.equals(game.humanSide)) {
            try {
                store.recordPuzzleCompletion(game.human.id(), game.endgameId, game.moves.size(), game.hintUsed);
            } catch (Exception e) {
                // Non-critical: don't let puzzle recording failure break game finalization
            }
        }
        store.updateGameRecord(snapshot(game, null));
        closeEngines(game);
    }

    private void syncStateFromMatch(ActivePracticeGame game) {
        game.currentTurn = game.match.finished() ? "" : game.match.currentTurnKey();
        game.status = game.match.finished() ? "FINISHED" : "PLAYING";
        game.winnerSide = game.match.winnerSide();
        game.resultText = game.match.resultText();
        game.updatedAt = Instant.now();
    }

    private void ensurePlayable(ActivePracticeGame game) {
        if ("FINISHED".equals(game.status)) {
            throw new IllegalArgumentException("game already finished");
        }
    }

    private void ensureHumanParticipant(ActivePracticeGame game, AuthUser actor) {
        if (actor == null || !game.human.id().equals(actor.id())) {
            throw new IllegalArgumentException("user is not in practice game");
        }
    }

    private ActivePracticeGame game(String gameId) {
        ActivePracticeGame game = gamesById.get(gameId);
        if (game == null) {
            throw new IllegalArgumentException("game not found");
        }
        return game;
    }

    private Map<String, Object> nextAiPayload(ActivePracticeGame game) {
        if (game.gameType == GameType.GOMOKU) {
            GomokuMatch match = (GomokuMatch) game.match;
            GomokuStone stone = "BLACK".equals(game.aiSide) ? GomokuStone.BLACK : GomokuStone.WHITE;
            int[] move = game.gomokuEngine.findBestMove(match.boardState(), stone, game.difficulty);
            if (move == null || move.length < 2) {
                return new LinkedHashMap<String, Object>();
            }
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("row", move[0]);
            payload.put("col", move[1]);
            return payload;
        }

        XiangqiMatch match = (XiangqiMatch) game.match;
        PieceColor color = "RED".equals(game.aiSide) ? PieceColor.RED : PieceColor.BLACK;
        Move move = game.xiangqiEngine.findBestMove(match.boardState(), color, game.difficulty);
        if (move == null) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("fromRow", move.getFromRow());
        payload.put("fromCol", move.getFromCol());
        payload.put("toRow", move.getToRow());
        payload.put("toCol", move.getToCol());
        return payload;
    }

    private Map<String, Object> snapshot(ActivePracticeGame game, AuthUser viewer) {
        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("gameId", game.gameId);
        snapshot.put("roomId", "");
        snapshot.put("gameType", game.gameType.name());
        snapshot.put("status", game.status);
        snapshot.put("currentTurn", nullToEmpty(game.currentTurn));
        snapshot.put("winnerSide", nullToEmpty(game.winnerSide));
        snapshot.put("resultText", nullToEmpty(game.resultText));
        snapshot.put("terminationReason", nullToEmpty(game.terminationReason));
        snapshot.put("initialTimeSeconds", 0);
        snapshot.put("firstRemainingSeconds", 0);
        snapshot.put("secondRemainingSeconds", 0);
        snapshot.put("clockState", "");
        snapshot.put("lastTickAt", "");
        snapshot.put("board", game.match.board());
        snapshot.put("moveCount", game.moves.size());
        snapshot.put("moves", new ArrayList<Map<String, Object>>(game.moves));
        snapshot.put("updatedAt", game.updatedAt.toString());
        snapshot.put("isTraining", true);
        snapshot.put("opponentType", game.opponentType);
        if (game.endgameName != null && !game.endgameName.isEmpty()) {
            snapshot.put("endgameName", game.endgameName);
        }
        if (game.hintUsed > 0) {
            snapshot.put("hintUsed", game.hintUsed);
        }
        snapshot.put("aiEngine", aiEngineId(game));
        snapshot.put("difficulty", game.difficulty.name());
        snapshot.put("ai", aiMap(game));
        Map<String, Object> players = new LinkedHashMap<String, Object>();
        players.put("first", playerMap(game, firstUser(game), firstSide(game)));
        players.put("second", playerMap(game, secondUser(game), secondSide(game)));
        snapshot.put("players", players);
        if (viewer != null && game.human.id().equals(viewer.id())) {
            snapshot.put("viewerSide", game.humanSide);
        }
        attachReplayBoards(snapshot);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private void attachReplayBoards(Map<String, Object> snapshot) {
        List<Map<String, Object>> moves = (List<Map<String, Object>>) snapshot.get("moves");
        List<List<List<String>>> boards = buildReplayBoards(asString(snapshot.get("gameType")), moves);
        snapshot.put("initialBoard", boards.isEmpty() ? new ArrayList<List<String>>() : boards.get(0));
        snapshot.put("historyBoards", boards);
    }

    private List<List<List<String>>> buildReplayBoards(String gameType, List<Map<String, Object>> moves) {
        if ("GOMOKU".equals(gameType)) {
            GomokuMatch replay = new GomokuMatch(
                new MatchPlayer("replay-black", "replay-black", PlayerSide.BLACK),
                new MatchPlayer("replay-white", "replay-white", PlayerSide.WHITE)
            );
            return replayBoards(replay, moves, "BLACK", "replay-black", "replay-white");
        }
        XiangqiMatch replay = new XiangqiMatch(
            new MatchPlayer("replay-red", "replay-red", PlayerSide.RED),
            new MatchPlayer("replay-black", "replay-black", PlayerSide.BLACK)
        );
        return replayBoards(replay, moves, "RED", "replay-red", "replay-black");
    }

    @SuppressWarnings("unchecked")
    private List<List<List<String>>> replayBoards(OnlineMatchEngine replay, List<Map<String, Object>> moves, String firstSide, String firstActor, String secondActor) {
        List<List<List<String>>> boards = new ArrayList<List<List<String>>>();
        boards.add(toBoardList(replay.board()));
        for (Map<String, Object> move : moves) {
            String actor = firstSide.equals(asString(move.get("side"))) ? firstActor : secondActor;
            Map<String, Object> payload = (Map<String, Object>) move.get("payload");
            replay.applyMove(actor, payload);
            boards.add(toBoardList(replay.board()));
        }
        return boards;
    }

    private List<List<String>> toBoardList(String[][] board) {
        List<List<String>> rows = new ArrayList<List<String>>();
        for (String[] row : board) {
            List<String> cells = new ArrayList<String>();
            for (String cell : row) {
                cells.add(cell == null ? "" : cell);
            }
            rows.add(cells);
        }
        return rows;
    }

    private Map<String, Object> aiMap(ActivePracticeGame game) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("engineId", aiEngineId(game));
        item.put("engineText", aiEngineText(game));
        item.put("difficulty", game.difficulty.name());
        item.put("side", game.aiSide);
        item.put("preferredEngine", game.enginePreference);
        return item;
    }

    private Map<String, Object> playerMap(ActivePracticeGame game, AuthUser user, String side) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", user.id());
        item.put("username", user.username());
        item.put("side", side);
        if (game.aiUser.id().equals(user.id())) {
            item.put("opponentType", "AI");
        } else {
            item.put("opponentType", "HUMAN");
        }
        return item;
    }

    private AuthUser firstUser(ActivePracticeGame game) {
        return firstSide(game).equals(game.humanSide) ? game.human : game.aiUser;
    }

    private AuthUser secondUser(ActivePracticeGame game) {
        return secondSide(game).equals(game.humanSide) ? game.human : game.aiUser;
    }

    private String firstSide(ActivePracticeGame game) {
        return game.gameType == GameType.GOMOKU ? "BLACK" : "RED";
    }

    private String secondSide(ActivePracticeGame game) {
        return game.gameType == GameType.GOMOKU ? "WHITE" : "BLACK";
    }

    private String aiEngineId(ActivePracticeGame game) {
        return game.xiangqiEngine != null ? game.xiangqiEngine.getEngineId() : game.gomokuEngine.getEngineId();
    }

    private String aiEngineText(ActivePracticeGame game) {
        return game.xiangqiEngine != null ? game.xiangqiEngine.getEngineText() : game.gomokuEngine.getEngineText();
    }

    private String aiUserId(GameType gameType) {
        return "ai-" + gameType.name().toLowerCase();
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

    private Map<String, Object> payloadWithSide(Map<String, Object> payload, String side) {
        Map<String, Object> enriched = new LinkedHashMap<String, Object>();
        if (payload != null) {
            enriched.putAll(payload);
        }
        enriched.put("side", side);
        return enriched;
    }

    private void closeEngines(ActivePracticeGame game) {
        if (game.xiangqiEngine != null) {
            game.xiangqiEngine.close();
        }
        if (game.gomokuEngine != null) {
            game.gomokuEngine.close();
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class ActivePracticeGame {
        private String gameId;
        private GameType gameType;
        private AuthUser human;
        private AuthUser aiUser;
        private OnlineMatchEngine match;
        private ConfigurableXiangqiEngine xiangqiEngine;
        private ConfigurableGomokuEngine gomokuEngine;
        private MinimaxAI.Difficulty difficulty;
        private String enginePreference;
        private String humanSide;
        private String aiSide;
        private String status;
        private String currentTurn;
        private String winnerSide;
        private String resultText;
        private String terminationReason;
        private String opponentType;
        private String endgameId;
        private String endgameName;
        private int hintUsed;
        private Instant updatedAt;
        private final List<Map<String, Object>> moves = new ArrayList<Map<String, Object>>();
    }
}
