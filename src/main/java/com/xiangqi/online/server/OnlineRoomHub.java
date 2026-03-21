package com.xiangqi.online.server;

import com.xiangqi.online.auth.AuthUser;
import com.xiangqi.online.game.GameType;
import com.xiangqi.online.game.GomokuMatch;
import com.xiangqi.online.game.MatchEvent;
import com.xiangqi.online.game.MatchPlayer;
import com.xiangqi.online.game.OnlineMatchEngine;
import com.xiangqi.online.game.PlayerSide;
import com.xiangqi.online.game.XiangqiMatch;
import com.xiangqi.online.room.CreateRoomRequest;
import com.xiangqi.online.room.RoomStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OnlineRoomHub {
    private final Clock clock;
    private final ConcurrentHashMap<String, ActiveRoom> roomsById = new ConcurrentHashMap<String, ActiveRoom>();
    private final ConcurrentHashMap<String, ActiveRoom> roomsByCode = new ConcurrentHashMap<String, ActiveRoom>();
    private final ConcurrentHashMap<String, ActiveGame> gamesById = new ConcurrentHashMap<String, ActiveGame>();
    private final OnlineStore store;

    public OnlineRoomHub(OnlineStore store) {
        this(store, Clock.systemUTC());
    }

    public OnlineRoomHub(OnlineStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Map<String, Object> createRoom(AuthUser host, CreateRoomRequest request) {
        if (request.gameType() == GameType.GO) {
            throw new IllegalArgumentException("GO online is not yet available");
        }
        ActiveRoom room = new ActiveRoom();
        room.roomId = UUID.randomUUID().toString();
        room.roomCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        room.gameType = request.gameType();
        room.initialTimeSeconds = request.initialTimeSeconds();
        room.publicRoom = request.isPublic();
        room.host = host;
        room.status = RoomStatus.WAITING.name();
        room.updatedAt = now();
        roomsById.put(room.roomId, room);
        roomsByCode.put(room.roomCode, room);
        return roomSnapshot(room);
    }

    public Map<String, Object> joinByCode(String roomCode, AuthUser user) {
        ActiveRoom room = roomsByCode.get(roomCode == null ? "" : roomCode.trim().toUpperCase());
        if (room == null) {
            throw new IllegalArgumentException("room not found");
        }
        return joinRoom(room.roomId, user);
    }

    public Map<String, Object> joinRoom(String roomId, AuthUser user) {
        ActiveRoom room = room(roomId);
        synchronized (room) {
            if (room.host.id().equals(user.id())) {
                return roomSnapshot(room);
            }
            if (room.guest != null && !room.guest.id().equals(user.id())) {
                throw new IllegalArgumentException("room is full");
            }
            room.guest = user;
            room.status = RoomStatus.FULL.name();
            room.updatedAt = now();
            return roomSnapshot(room);
        }
    }

    public Map<String, Object> setReady(String roomId, String userId, boolean ready) {
        ActiveRoom room = room(roomId);
        synchronized (room) {
            if (room.host.id().equals(userId)) {
                room.hostReady = ready;
            } else if (room.guest != null && room.guest.id().equals(userId)) {
                room.guestReady = ready;
            } else {
                throw new IllegalArgumentException("user is not in room");
            }
            if (room.hostReady && room.guestReady && room.guest != null && room.gameId == null) {
                startGame(room);
            }
            room.updatedAt = now();
            return roomSnapshot(room);
        }
    }

    public Map<String, Object> roomSnapshotById(String roomId) {
        return roomSnapshot(room(roomId));
    }

    public Map<String, Object> gameSnapshotById(String gameId, AuthUser viewer) {
        ActiveGame game = gamesById.get(gameId);
        if (game == null) {
            return store.loadGameAnalysis(gameId);
        }
        return gameSnapshot(game, viewer);
    }

    public Map<String, Object> analysis(String gameId) {
        ActiveGame game = gamesById.get(gameId);
        if (game != null) {
            return gameSnapshot(game, null);
        }
        return store.loadGameAnalysis(gameId);
    }

    public Map<String, Object> applyMove(String gameId, AuthUser actor, Map<String, Object> payload) {
        ActiveGame game = game(gameId);
        synchronized (game) {
            if ("FINISHED".equals(game.status)) {
                throw new IllegalArgumentException("game already finished");
            }
            MatchEvent preview = game.engine.previewMove(actor.id(), payload);
            if (!preview.accepted()) {
                game.lastTickAt = now();
                game.updatedAt = now();
                throw new IllegalArgumentException(preview.message());
            }
            if (applyElapsed(game, game.currentTurn)) {
                return gameSnapshot(game, actor);
            }
            MatchEvent event = game.engine.applyMove(actor.id(), payload);
            if (!event.accepted()) {
                throw new IllegalArgumentException(event.message());
            }
            List<Map<String, Object>> moves = game.engine.moves();
            Map<String, Object> lastMove = moves.isEmpty() ? Collections.<String, Object>emptyMap() : moves.get(moves.size() - 1);
            game.status = game.engine.finished() ? "FINISHED" : "PLAYING";
            game.currentTurn = game.engine.currentTurnKey();
            game.winnerSide = game.engine.winnerSide();
            game.resultText = game.engine.resultText();
            game.lastTickAt = now();
            game.clockState = "FINISHED".equals(game.status) ? "FINISHED" : "RUNNING";
            game.updatedAt = now();
            Map<String, Object> snapshot = gameSnapshot(game, actor);
            store.appendMove(game.gameId, moves.size(), actor.id(), asString(lastMove.get("side")), asString(lastMove.get("notation")), payload, snapshot);
            if (game.engine.finished()) {
                ActiveRoom room = room(game.roomId);
                room.status = RoomStatus.FINISHED.name();
                room.updatedAt = now();
            }
            return snapshot;
        }
    }

    public List<Map<String, Object>> publicRoomSummaries() {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (ActiveRoom room : roomsById.values()) {
            if (room.publicRoom) {
                items.add(roomSummary(room));
            }
        }
        items.sort((a, b) -> asString(b.get("updatedAt")).compareTo(asString(a.get("updatedAt"))));
        return items;
    }

    public Map<String, Object> activityForUser(String userId) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        ActiveRoom matchedRoom = null;
        for (ActiveRoom room : roomsById.values()) {
            if (containsUser(room, userId)) {
                matchedRoom = room;
                break;
            }
        }
        if (matchedRoom != null) {
            body.put("room", roomSnapshot(matchedRoom));
            if (matchedRoom.gameId != null) {
                ActiveGame game = gamesById.get(matchedRoom.gameId);
                if (game != null) {
                    body.put("game", gameSnapshot(game, null));
                }
            }
        }
        return body;
    }

    public Map<String, Object> resign(String gameId, AuthUser actor) {
        ActiveGame game = game(gameId);
        synchronized (game) {
            ensureParticipant(game, actor.id());
            if (game.engine.finished() || "FINISHED".equals(game.status)) {
                throw new IllegalArgumentException("game already finished");
            }
            String resultText = actor.username() + " resigned";
            finalizeGame(game, winnerForResignation(game, actor.id()), resultText, "RESIGN");
            return gameSnapshot(game, actor);
        }
    }

    public Map<String, Object> offerDraw(String gameId, AuthUser actor) {
        ActiveGame game = game(gameId);
        synchronized (game) {
            ensureParticipant(game, actor.id());
            if ("FINISHED".equals(game.status)) {
                throw new IllegalArgumentException("game already finished");
            }
            if (game.drawOfferUserId != null && !game.drawOfferUserId.isEmpty()) {
                throw new IllegalArgumentException("draw offer already pending");
            }
            game.drawOfferUserId = actor.id();
            game.drawOfferUsername = actor.username();
            game.drawOfferSide = playerSideForUser(game, actor.id());
            game.updatedAt = now();
            return gameSnapshot(game, actor);
        }
    }

    public Map<String, Object> respondDraw(String gameId, AuthUser actor, boolean accept) {
        ActiveGame game = game(gameId);
        synchronized (game) {
            ensureParticipant(game, actor.id());
            if (game.drawOfferUserId == null || game.drawOfferUserId.isEmpty()) {
                throw new IllegalArgumentException("no draw offer pending");
            }
            if (game.drawOfferUserId.equals(actor.id())) {
                throw new IllegalArgumentException("cannot respond to your own draw offer");
            }
            if (accept) {
                finalizeGame(game, "", "draw agreed", "DRAW_AGREED");
            } else {
                clearDrawOffer(game);
                game.updatedAt = now();
            }
            return gameSnapshot(game, actor);
        }
    }

    public int activeRoomCount() {
        return roomsById.size();
    }

    public Optional<String> roomIdForGame(String gameId) {
        ActiveGame game = gamesById.get(gameId);
        return game == null ? Optional.<String>empty() : Optional.of(game.roomId);
    }

    private void startGame(ActiveRoom room) {
        room.status = RoomStatus.PLAYING.name();
        room.gameId = UUID.randomUUID().toString();
        ActiveGame game = new ActiveGame();
        game.gameId = room.gameId;
        game.roomId = room.roomId;
        game.gameType = room.gameType;
        game.first = firstPlayer(room);
        game.second = secondPlayer(room);
        game.engine = createEngine(room);
        game.status = "PLAYING";
        game.currentTurn = game.engine.currentTurnKey();
        game.initialTimeSeconds = room.initialTimeSeconds;
        game.firstRemainingSeconds = room.initialTimeSeconds;
        game.secondRemainingSeconds = room.initialTimeSeconds;
        game.clockState = "RUNNING";
        game.lastTickAt = now();
        game.updatedAt = now();
        gamesById.put(game.gameId, game);
        store.createGameRecord(game.gameId, room.roomId, gameSnapshot(game, room.host));
    }

    private OnlineMatchEngine createEngine(ActiveRoom room) {
        if (room.gameType == GameType.GOMOKU) {
            return new GomokuMatch(
                new MatchPlayer(room.host.id(), room.host.username(), PlayerSide.BLACK),
                new MatchPlayer(room.guest.id(), room.guest.username(), PlayerSide.WHITE)
            );
        }
        return new XiangqiMatch(
            new MatchPlayer(room.host.id(), room.host.username(), PlayerSide.RED),
            new MatchPlayer(room.guest.id(), room.guest.username(), PlayerSide.BLACK)
        );
    }

    private Map<String, Object> roomSnapshot(ActiveRoom room) {
        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("roomId", room.roomId);
        snapshot.put("roomCode", room.roomCode);
        snapshot.put("gameType", room.gameType.name());
        snapshot.put("status", room.status);
        snapshot.put("gameId", room.gameId == null ? "" : room.gameId);
        snapshot.put("initialTimeSeconds", room.initialTimeSeconds);
        snapshot.put("hostReady", room.hostReady);
        snapshot.put("guestReady", room.guestReady);
        snapshot.put("isPublic", room.publicRoom);
        snapshot.put("updatedAt", room.updatedAt.toString());
        snapshot.put("host", userMap(room.host));
        snapshot.put("guest", room.guest == null ? null : userMap(room.guest));
        snapshot.put("firstSeat", userMap(firstPlayer(room)));
        snapshot.put("secondSeat", userMap(secondPlayer(room)));
        return snapshot;
    }

    private Map<String, Object> roomSummary(ActiveRoom room) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("roomId", room.roomId);
        item.put("roomCode", room.roomCode);
        item.put("gameType", room.gameType.name());
        item.put("status", room.status);
        item.put("initialTimeSeconds", room.initialTimeSeconds);
        item.put("hostUsername", room.host.username());
        item.put("guestUsername", room.guest == null ? "" : room.guest.username());
        item.put("updatedAt", room.updatedAt.toString());
        return item;
    }

    private Map<String, Object> gameSnapshot(ActiveGame game, AuthUser viewer) {
        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("gameId", game.gameId);
        snapshot.put("roomId", game.roomId);
        snapshot.put("gameType", game.gameType.name());
        snapshot.put("status", game.status);
        snapshot.put("currentTurn", game.currentTurn);
        snapshot.put("winnerSide", game.winnerSide == null ? "" : game.winnerSide);
        snapshot.put("resultText", game.resultText == null ? "" : game.resultText);
        snapshot.put("initialTimeSeconds", game.initialTimeSeconds);
        snapshot.put("firstRemainingSeconds", game.firstRemainingSeconds);
        snapshot.put("secondRemainingSeconds", game.secondRemainingSeconds);
        snapshot.put("clockState", game.clockState == null ? "" : game.clockState);
        snapshot.put("lastTickAt", game.lastTickAt == null ? "" : game.lastTickAt.toString());
        snapshot.put("terminationReason", game.terminationReason == null ? "" : game.terminationReason);
        snapshot.put("drawOffer", drawOfferMap(game));
        snapshot.put("board", game.engine.board());
        snapshot.put("moveCount", game.engine.moves().size());
        snapshot.put("moves", game.engine.moves());
        attachReplayBoards(snapshot);
        snapshot.put("updatedAt", game.updatedAt.toString());
        Map<String, Object> players = new LinkedHashMap<String, Object>();
        players.put("first", playerMap(game.first, game.firstSide()));
        players.put("second", playerMap(game.second, game.secondSide()));
        snapshot.put("players", players);
        if (viewer != null) {
            snapshot.put("viewerSide", viewerSide(game, viewer));
        }
        return snapshot;
    }

    private Map<String, Object> drawOfferMap(ActiveGame game) {
        if (game.drawOfferUserId == null || game.drawOfferUserId.isEmpty()) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("userId", game.drawOfferUserId);
        item.put("username", game.drawOfferUsername);
        item.put("side", game.drawOfferSide);
        return item;
    }

    @SuppressWarnings("unchecked")
    private void attachReplayBoards(Map<String, Object> snapshot) {
        List<Map<String, Object>> moves = (List<Map<String, Object>>) snapshot.get("moves");
        List<List<List<String>>> boards = buildReplayBoards(asString(snapshot.get("gameType")), moves);
        if (boards.isEmpty()) {
            return;
        }
        snapshot.put("initialBoard", boards.get(0));
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

    private List<List<List<String>>> replayBoards(OnlineMatchEngine replay, List<Map<String, Object>> moves, String firstSide, String firstActor, String secondActor) {
        List<List<List<String>>> boards = new ArrayList<List<List<String>>>();
        boards.add(toBoardList(replay.board()));
        for (Map<String, Object> move : moves) {
            String actor = firstSide.equals(asString(move.get("side"))) ? firstActor : secondActor;
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) move;
            Object rawPayload = payload.get("payload");
            if (rawPayload instanceof Map) {
                replay.applyMove(actor, (Map<String, Object>) rawPayload);
            } else {
                replay.applyMove(actor, payload);
            }
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

    private String viewerSide(ActiveGame game, AuthUser viewer) {
        if (game.first.id().equals(viewer.id())) {
            return game.firstSide();
        }
        if (game.second.id().equals(viewer.id())) {
            return game.secondSide();
        }
        return "";
    }

    private Map<String, Object> userMap(AuthUser user) {
        if (user == null) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", user.id());
        item.put("username", user.username());
        return item;
    }

    private Map<String, Object> playerMap(AuthUser user, String side) {
        Map<String, Object> item = userMap(user);
        if (item != null) {
            item.put("side", side);
        }
        return item;
    }

    private AuthUser firstPlayer(ActiveRoom room) {
        return room.host;
    }

    private AuthUser secondPlayer(ActiveRoom room) {
        return room.guest;
    }

    private ActiveRoom room(String roomId) {
        ActiveRoom room = roomsById.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("room not found");
        }
        return room;
    }

    private ActiveGame game(String gameId) {
        ActiveGame game = gamesById.get(gameId);
        if (game == null) {
            throw new IllegalArgumentException("game not found");
        }
        return game;
    }

    private boolean containsUser(ActiveRoom room, String userId) {
        return room.host != null && room.host.id().equals(userId)
            || room.guest != null && room.guest.id().equals(userId);
    }

    private void ensureParticipant(ActiveGame game, String userId) {
        if (!game.first.id().equals(userId) && !game.second.id().equals(userId)) {
            throw new IllegalArgumentException("user is not in game");
        }
    }

    private String playerSideForUser(ActiveGame game, String userId) {
        if (game.first.id().equals(userId)) {
            return game.firstSide();
        }
        if (game.second.id().equals(userId)) {
            return game.secondSide();
        }
        return "";
    }

    private String winnerForResignation(ActiveGame game, String actorUserId) {
        return game.first.id().equals(actorUserId) ? game.secondSide() : game.firstSide();
    }

    private void clearDrawOffer(ActiveGame game) {
        game.drawOfferUserId = null;
        game.drawOfferUsername = null;
        game.drawOfferSide = null;
    }

    private void finalizeGame(ActiveGame game, String winnerSide, String resultText, String terminationReason) {
        game.status = "FINISHED";
        game.currentTurn = "";
        game.winnerSide = winnerSide;
        game.resultText = resultText;
        game.terminationReason = terminationReason;
        game.clockState = "FINISHED";
        clearDrawOffer(game);
        game.updatedAt = now();
        ActiveRoom room = room(game.roomId);
        room.status = RoomStatus.FINISHED.name();
        room.updatedAt = now();
        store.updateGameRecord(gameSnapshot(game, null));
    }

    private boolean applyElapsed(ActiveGame game, String side) {
        if (game.lastTickAt == null || side == null || side.isEmpty()) {
            return false;
        }
        long elapsedSeconds = Math.max(0L, now().getEpochSecond() - game.lastTickAt.getEpochSecond());
        if (elapsedSeconds <= 0L) {
            return false;
        }
        if (game.firstSide().equals(side)) {
            game.firstRemainingSeconds = Math.max(0, game.firstRemainingSeconds - (int) elapsedSeconds);
            if (game.firstRemainingSeconds <= 0) {
                finalizeGame(game, game.secondSide(), side.toLowerCase() + " timeout", "TIMEOUT");
                return true;
            }
        } else if (game.secondSide().equals(side)) {
            game.secondRemainingSeconds = Math.max(0, game.secondRemainingSeconds - (int) elapsedSeconds);
            if (game.secondRemainingSeconds <= 0) {
                finalizeGame(game, game.firstSide(), side.toLowerCase() + " timeout", "TIMEOUT");
                return true;
            }
        }
        return false;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static final class ActiveRoom {
        private String roomId;
        private String roomCode;
        private GameType gameType;
        private int initialTimeSeconds;
        private boolean publicRoom;
        private AuthUser host;
        private AuthUser guest;
        private boolean hostReady;
        private boolean guestReady;
        private String status;
        private String gameId;
        private Instant updatedAt;
    }

    private static final class ActiveGame {
        private String gameId;
        private String roomId;
        private GameType gameType;
        private AuthUser first;
        private AuthUser second;
        private OnlineMatchEngine engine;
        private String status;
        private String currentTurn;
        private String winnerSide;
        private String resultText;
        private int initialTimeSeconds;
        private int firstRemainingSeconds;
        private int secondRemainingSeconds;
        private String clockState;
        private Instant lastTickAt;
        private String terminationReason;
        private String drawOfferUserId;
        private String drawOfferUsername;
        private String drawOfferSide;
        private Instant updatedAt;

        private String firstSide() {
            return gameType == GameType.GOMOKU ? "BLACK" : "RED";
        }

        private String secondSide() {
            return gameType == GameType.GOMOKU ? "WHITE" : "BLACK";
        }
    }
}
