package com.xiangqi.online.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OnlineRoomHub {
    private static final long REMATCH_OFFER_SECONDS = 60L;
    private final Clock clock;
    private final ConcurrentHashMap<String, ActiveRoom> roomsById = new ConcurrentHashMap<String, ActiveRoom>();
    private final ConcurrentHashMap<String, ActiveRoom> roomsByCode = new ConcurrentHashMap<String, ActiveRoom>();
    private final ConcurrentHashMap<String, ActiveGame> gamesById = new ConcurrentHashMap<String, ActiveGame>();
    private final OnlineStore store;
    private final RoomPersistence persistence;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public OnlineRoomHub(OnlineStore store) {
        this(store, Clock.systemUTC(), NoopRoomPersistence.instance());
    }

    public OnlineRoomHub(OnlineStore store, Clock clock) {
        this(store, clock, NoopRoomPersistence.instance());
    }

    public OnlineRoomHub(OnlineStore store, Clock clock, RoomPersistence persistence) {
        this.store = store;
        this.clock = clock;
        this.persistence = persistence == null ? NoopRoomPersistence.instance() : persistence;
        restoreRoomState();
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
        room.hostFirstSeat = true;
        room.swapColorsNext = true;
        room.status = RoomStatus.WAITING.name();
        room.updatedAt = now();
        roomsById.put(room.roomId, room);
        roomsByCode.put(room.roomCode, room);
        persistAll();
        return roomSnapshot(room);
    }

    public Map<String, Object> quickMatch(AuthUser user, CreateRoomRequest request) {
        if (request.gameType() == GameType.GO) {
            throw new IllegalArgumentException("GO online is not yet available");
        }
        for (ActiveRoom room : roomsById.values()) {
            if (!canQuickMatch(room, user, request.gameType())) {
                continue;
            }
            synchronized (room) {
                if (!canQuickMatch(room, user, request.gameType())) {
                    continue;
                }
                room.guest = user;
                room.guestReady = true;
                room.status = RoomStatus.FULL.name();
                if (room.hostReady) {
                    startNextGame(room);
                }
                room.updatedAt = now();
                persistAll();
                return quickMatchResult(true, room, user);
            }
        }

        Map<String, Object> created = createRoom(user, new CreateRoomRequest(
            request.gameType(),
            request.initialTimeSeconds(),
            true
        ));
        ActiveRoom room = room(asString(created.get("roomId")));
        synchronized (room) {
            room.hostReady = true;
            room.updatedAt = now();
            persistAll();
            return quickMatchResult(false, room, user);
        }
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
            ensureOpen(room);
            if (room.host.id().equals(user.id())) {
                return roomSnapshot(room);
            }
            if (room.guest != null && !room.guest.id().equals(user.id())) {
                throw new IllegalArgumentException("room is full");
            }
            if (room.guest != null) {
                return roomSnapshot(room);
            }
            room.guest = user;
            room.status = RoomStatus.FULL.name();
            room.updatedAt = now();
            persistAll();
            return roomSnapshot(room);
        }
    }

    public Map<String, Object> setReady(String roomId, String userId, boolean ready) {
        ActiveRoom room = room(roomId);
        synchronized (room) {
            ensureOpen(room);
            if (RoomStatus.PLAYING.name().equals(room.status)) {
                throw new IllegalStateException("game is already playing");
            }
            if (room.host.id().equals(userId)) {
                room.hostReady = ready;
            } else if (room.guest != null && room.guest.id().equals(userId)) {
                room.guestReady = ready;
            } else {
                throw new IllegalArgumentException("user is not in room");
            }
            if (room.hostReady && room.guestReady && room.guest != null) {
                startNextGame(room);
            }
            room.updatedAt = now();
            persistAll();
            return roomSnapshot(room);
        }
    }

    public Map<String, Object> rematch(String roomId, AuthUser actor, String action) {
        ActiveRoom room = room(roomId);
        synchronized (room) {
            ensureOpen(room);
            ensureRoomMember(room, actor.id());
            if (!isBetweenGames(room)) {
                throw new IllegalStateException("rematch is only available between games");
            }
            String normalized = action == null ? "" : action.trim().toLowerCase();
            boolean expired = clearExpiredRematch(room);
            if ("offer".equals(normalized)) {
                if (room.rematchOfferedByUserId != null) {
                    throw new IllegalArgumentException("rematch offer already pending");
                }
                room.rematchOfferedByUserId = actor.id();
                room.rematchOfferedByUsername = actor.username();
                room.rematchExpiresAt = now().plusSeconds(REMATCH_OFFER_SECONDS);
            } else if ("accept".equals(normalized)) {
                if (expired) {
                    throw new IllegalArgumentException("rematch offer expired");
                }
                requireRematchOffer(room);
                if (room.rematchOfferedByUserId.equals(actor.id())) {
                    throw new IllegalArgumentException("cannot accept your own rematch offer");
                }
                room.hostReady = true;
                room.guestReady = true;
                startNextGame(room);
            } else if ("decline".equals(normalized)) {
                requireRematchOffer(room);
                if (room.rematchOfferedByUserId.equals(actor.id())) {
                    throw new IllegalArgumentException("offerer must cancel the rematch offer");
                }
                clearRematch(room);
            } else if ("cancel".equals(normalized)) {
                requireRematchOffer(room);
                if (!room.rematchOfferedByUserId.equals(actor.id())) {
                    throw new IllegalArgumentException("only the offerer can cancel the rematch offer");
                }
                clearRematch(room);
            } else {
                throw new IllegalArgumentException("unsupported rematch action");
            }
            room.updatedAt = now();
            persistAll();
            return roomSnapshot(room);
        }
    }

    public Map<String, Object> closeRoom(String roomId, AuthUser actor) {
        ActiveRoom room = room(roomId);
        synchronized (room) {
            if (!room.host.id().equals(actor.id())) {
                throw new SecurityException("only room host can close room");
            }
            return closeRoomInternal(room);
        }
    }

    public Map<String, Object> leaveRoom(String roomId, AuthUser actor) {
        ActiveRoom room = room(roomId);
        synchronized (room) {
            ensureRoomMember(room, actor.id());
            return closeRoomInternal(room);
        }
    }

    private Map<String, Object> closeRoomInternal(ActiveRoom room) {
        ActiveGame game = room.gameId == null ? null : gamesById.get(room.gameId);
        if (game != null && "PLAYING".equals(game.status)) {
            throw new IllegalStateException("active game must finish before closing room");
        }
        room.closed = true;
        roomsById.remove(room.roomId, room);
        roomsByCode.remove(room.roomCode, room);
        if (game != null) {
            gamesById.remove(room.gameId, game);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("closed", true);
        result.put("roomId", room.roomId);
        persistAll();
        return result;
    }

    public Map<String, Object> roomSnapshotById(String roomId) {
        ActiveRoom room = room(roomId);
        synchronized (room) {
            clearExpiredRematch(room);
            return roomSnapshot(room);
        }
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
            ensureParticipant(game, actor.id());
            if ("FINISHED".equals(game.status)) {
                throw new IllegalArgumentException("game already finished");
            }
            MatchEvent preview = game.engine.previewMove(actor.id(), payload);
            if (!preview.accepted()) {
                // Illegal moves must not refresh lastTickAt — that would let a player
                // stall the clock by spamming invalid requests (and outsiders could
                // affect another game's timer if membership were not checked above).
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
                finishRoomEpisode(game);
            }
            persistAll();
            return snapshot;
        }
    }

    public List<Map<String, Object>> publicRoomSummaries() {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (ActiveRoom room : roomsById.values()) {
            if (room.publicRoom && !room.closed) {
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
            if (!room.closed && containsUser(room, userId)) {
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
            persistAll();
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
            persistAll();
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
            persistAll();
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

    private void startNextGame(ActiveRoom room) {
        if (room.guest == null) {
            throw new IllegalStateException("room needs two players");
        }
        if (room.roundIndex > 0 && room.swapColorsNext) {
            room.hostFirstSeat = !room.hostFirstSeat;
        }
        String previousGameId = room.gameId;
        room.status = RoomStatus.PLAYING.name();
        room.gameId = UUID.randomUUID().toString();
        room.roundIndex++;
        room.hostReady = false;
        room.guestReady = false;
        clearRematch(room);
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
        store.createGameRecord(game.gameId, room.roomId, room.publicRoom, gameSnapshot(game, room.host));
        if (previousGameId != null && !previousGameId.equals(game.gameId)) {
            ActiveGame previous = gamesById.get(previousGameId);
            if (previous != null && "FINISHED".equals(previous.status)) {
                gamesById.remove(previousGameId, previous);
            }
        }
    }

    private OnlineMatchEngine createEngine(ActiveRoom room) {
        if (room.gameType == GameType.GOMOKU) {
            return new GomokuMatch(
                new MatchPlayer(firstPlayer(room).id(), firstPlayer(room).username(), PlayerSide.BLACK),
                new MatchPlayer(secondPlayer(room).id(), secondPlayer(room).username(), PlayerSide.WHITE)
            );
        }
        return new XiangqiMatch(
            new MatchPlayer(firstPlayer(room).id(), firstPlayer(room).username(), PlayerSide.RED),
            new MatchPlayer(secondPlayer(room).id(), secondPlayer(room).username(), PlayerSide.BLACK),
            null,
            room.initialTimeSeconds,
            clock
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
        snapshot.put("roundIndex", room.roundIndex);
        snapshot.put("seriesScore", seriesScoreMap(room));
        snapshot.put("lastGameId", room.lastGameId == null ? "" : room.lastGameId);
        snapshot.put("swapColorsNext", room.swapColorsNext);
        snapshot.put("rematch", rematchMap(room));
        snapshot.put("canStartNext", room.guest != null && isBetweenGames(room));
        snapshot.put("isPublic", room.publicRoom);
        snapshot.put("updatedAt", room.updatedAt.toString());
        snapshot.put("host", userMap(room.host));
        snapshot.put("guest", room.guest == null ? null : userMap(room.guest));
        snapshot.put("firstSeat", userMap(firstPlayer(room)));
        snapshot.put("secondSeat", userMap(secondPlayer(room)));
        snapshot.put("seatAssignment", seatAssignmentMap(room));
        return snapshot;
    }

    private Map<String, Object> roomSummary(ActiveRoom room) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("roomId", room.roomId);
        item.put("roomCode", room.roomCode);
        item.put("gameId", room.gameId == null ? "" : room.gameId);
        item.put("gameType", room.gameType.name());
        item.put("status", room.status);
        item.put("initialTimeSeconds", room.initialTimeSeconds);
        item.put("hostUsername", room.host.username());
        item.put("guestUsername", room.guest == null ? "" : room.guest.username());
        item.put("roundIndex", room.roundIndex);
        item.put("seriesScore", seriesScoreMap(room));
        item.put("updatedAt", room.updatedAt.toString());
        return item;
    }

    private boolean canQuickMatch(ActiveRoom room, AuthUser user, GameType gameType) {
        return room.publicRoom
            && !room.closed
            && room.gameType == gameType
            && room.guest == null
            && room.gameId == null
            && RoomStatus.WAITING.name().equals(room.status)
            && !room.host.id().equals(user.id());
    }

    private Map<String, Object> quickMatchResult(boolean matched, ActiveRoom room, AuthUser viewer) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("matched", matched);
        body.put("room", roomSnapshot(room));
        if (room.gameId != null && !room.gameId.isEmpty()) {
            ActiveGame game = gamesById.get(room.gameId);
            if (game != null) {
                body.put("game", gameSnapshot(game, viewer));
            }
        }
        return body;
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
        snapshot.put("stateId", game.engine.stateId());
        snapshot.put("inCheckSide", game.engine.inCheckSide());
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
        return room.hostFirstSeat ? room.host : room.guest;
    }

    private AuthUser secondPlayer(ActiveRoom room) {
        return room.hostFirstSeat ? room.guest : room.host;
    }

    private ActiveRoom room(String roomId) {
        ActiveRoom room = roomsById.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("room not found");
        }
        return room;
    }

    private void ensureOpen(ActiveRoom room) {
        if (room.closed) {
            throw new IllegalArgumentException("room not found");
        }
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

    private void ensureRoomMember(ActiveRoom room, String userId) {
        if (!containsUser(room, userId)) {
            throw new IllegalArgumentException("user is not in room");
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
        finishRoomEpisode(game);
    }

    private void finishRoomEpisode(ActiveGame game) {
        if (game.roomFinalized) {
            return;
        }
        ActiveRoom room = room(game.roomId);
        synchronized (room) {
            if (game.roomFinalized) {
                return;
            }
            game.roomFinalized = true;
            room.status = RoomStatus.BETWEEN_GAMES.name();
            room.lastGameId = game.gameId;
            room.hostReady = false;
            room.guestReady = false;
            clearRematch(room);
            updateSeriesScore(room, game);
            room.updatedAt = now();
            store.updateGameRecord(gameSnapshot(game, null));
        }
    }

    private void updateSeriesScore(ActiveRoom room, ActiveGame game) {
        if (game.winnerSide == null || game.winnerSide.isEmpty()) {
            return;
        }
        AuthUser winner = game.firstSide().equals(game.winnerSide) ? game.first : game.second;
        if (winner != null && winner.id().equals(room.host.id())) {
            room.hostScore++;
        } else if (winner != null && room.guest != null && winner.id().equals(room.guest.id())) {
            room.guestScore++;
        }
    }

    private boolean isBetweenGames(ActiveRoom room) {
        return RoomStatus.BETWEEN_GAMES.name().equals(room.status)
            || RoomStatus.FINISHED.name().equals(room.status);
    }

    private void requireRematchOffer(ActiveRoom room) {
        if (room.rematchOfferedByUserId == null) {
            throw new IllegalArgumentException("no rematch offer pending");
        }
    }

    private boolean clearExpiredRematch(ActiveRoom room) {
        if (room.rematchExpiresAt == null || now().isBefore(room.rematchExpiresAt)) {
            return false;
        }
        clearRematch(room);
        room.updatedAt = now();
        return true;
    }

    private void clearRematch(ActiveRoom room) {
        room.rematchOfferedByUserId = null;
        room.rematchOfferedByUsername = null;
        room.rematchExpiresAt = null;
    }

    private Map<String, Object> rematchMap(ActiveRoom room) {
        if (room.rematchOfferedByUserId == null) {
            return null;
        }
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("state", "OFFERED");
        item.put("offeredBy", room.rematchOfferedByUserId);
        item.put("offeredByUsername", room.rematchOfferedByUsername);
        item.put("expiresAt", room.rematchExpiresAt == null ? "" : room.rematchExpiresAt.toString());
        return item;
    }

    private Map<String, Object> seriesScoreMap(ActiveRoom room) {
        Map<String, Object> score = new LinkedHashMap<String, Object>();
        score.put("host", room.hostScore);
        score.put("guest", room.guestScore);
        return score;
    }

    private Map<String, Object> seatAssignmentMap(ActiveRoom room) {
        Map<String, Object> seats = new LinkedHashMap<String, Object>();
        AuthUser first = firstPlayer(room);
        AuthUser second = secondPlayer(room);
        seats.put("firstUserId", first == null ? "" : first.id());
        seats.put("secondUserId", second == null ? "" : second.id());
        seats.put("firstSide", room.gameType == GameType.GOMOKU ? "BLACK" : "RED");
        seats.put("secondSide", room.gameType == GameType.GOMOKU ? "WHITE" : "BLACK");
        return seats;
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

    /* ------------ 持久化辅助 ------------ */

    private int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private boolean asBoolean(Object value) {
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<String, Object>();
    }

    private GameType parseGameType(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("missing gameType");
        }
        return GameType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private AuthUser toUser(Object value) {
        Map<String, Object> data = asMap(value);
        String id = asString(data.get("id"));
        return id.isEmpty() ? null : new AuthUser(id, asString(data.get("username")));
    }

    private Instant toInstant(String raw) {
        return (raw == null || raw.trim().isEmpty()) ? null : Instant.parse(raw);
    }

    private String persistenceName() {
        return persistence.getClass().getSimpleName();
    }

    private OnlineMatchEngine createMatchFor(ActiveGame game) {
        if (game.gameType == GameType.GOMOKU) {
            return new GomokuMatch(
                new MatchPlayer(game.first.id(), game.first.username(), PlayerSide.BLACK),
                new MatchPlayer(game.second.id(), game.second.username(), PlayerSide.WHITE)
            );
        }
        return new XiangqiMatch(
            new MatchPlayer(game.first.id(), game.first.username(), PlayerSide.RED),
            new MatchPlayer(game.second.id(), game.second.username(), PlayerSide.BLACK),
            null,
            game.initialTimeSeconds,
            clock
        );
    }

    /* ---------------- 运行态持久化：导出 -> 落盘 -> 重启恢复 ---------------- */

    /**
     * 导出当前所有未关闭房间（含各自最新对局元数据），交给 RoomPersistence 落盘。
     * 引擎盘面不着重存冗余：对局着法已落在 DB games/game_moves，重建时据此回放。
     */
    private void persistAll() {
        try {
            List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
            for (ActiveRoom room : roomsById.values()) {
                Map<String, Object> record = new LinkedHashMap<String, Object>();
                record.put("roomId", room.roomId);
                record.put("roomCode", room.roomCode);
                record.put("gameType", room.gameType == null ? "" : room.gameType.name());
                record.put("initialTimeSeconds", room.initialTimeSeconds);
                record.put("publicRoom", room.publicRoom);
                record.put("host", exportPlayer(room.host));
                record.put("guest", exportPlayer(room.guest));
                record.put("hostReady", room.hostReady);
                record.put("guestReady", room.guestReady);
                record.put("roundIndex", room.roundIndex);
                record.put("hostScore", room.hostScore);
                record.put("guestScore", room.guestScore);
                record.put("hostFirstSeat", room.hostFirstSeat);
                record.put("swapColorsNext", room.swapColorsNext);
                record.put("lastGameId", room.lastGameId == null ? "" : room.lastGameId);
                record.put("rematchOfferedByUserId", room.rematchOfferedByUserId == null ? "" : room.rematchOfferedByUserId);
                record.put("rematchOfferedByUsername", room.rematchOfferedByUsername == null ? "" : room.rematchOfferedByUsername);
                record.put("rematchExpiresAt", room.rematchExpiresAt == null ? "" : room.rematchExpiresAt.toString());
                record.put("status", room.status);
                record.put("gameId", room.gameId == null ? "" : room.gameId);
                record.put("updatedAt", room.updatedAt == null ? "" : room.updatedAt.toString());
                record.put("closed", room.closed);
                if (room.gameId != null && !room.gameId.isEmpty()) {
                    ActiveGame game = gamesById.get(room.gameId);
                    if (game != null) {
                        record.put("game", exportGame(game));
                    }
                }
                records.add(record);
            }
            persistence.save(records);
            System.out.println("[persistence] saved " + records.size() + " room(s) to " + persistenceName());
        } catch (Exception ex) {
            System.out.println("[persistence] save failed: " + ex);
        }
    }

    private static Map<String, Object> exportPlayer(AuthUser user) {
        Map<String, Object> player = new LinkedHashMap<String, Object>();
        if (user != null) {
            player.put("id", user.id());
            player.put("username", user.username());
        }
        return player;
    }

    private Map<String, Object> exportGame(ActiveGame game) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("gameId", game.gameId);
        item.put("roomId", game.roomId);
        item.put("gameType", game.gameType == null ? "" : game.gameType.name());
        item.put("first", exportPlayer(game.first));
        item.put("second", exportPlayer(game.second));
        item.put("status", game.status);
        item.put("currentTurn", game.currentTurn);
        item.put("winnerSide", game.winnerSide == null ? "" : game.winnerSide);
        item.put("resultText", game.resultText == null ? "" : game.resultText);
        item.put("initialTimeSeconds", game.initialTimeSeconds);
        item.put("firstRemainingSeconds", game.firstRemainingSeconds);
        item.put("secondRemainingSeconds", game.secondRemainingSeconds);
        item.put("clockState", game.clockState == null ? "" : game.clockState);
        item.put("lastTickAt", game.lastTickAt == null ? "" : game.lastTickAt.toString());
        item.put("terminationReason", game.terminationReason == null ? "" : game.terminationReason);
        item.put("drawOfferUserId", game.drawOfferUserId == null ? "" : game.drawOfferUserId);
        item.put("drawOfferUsername", game.drawOfferUsername == null ? "" : game.drawOfferUsername);
        item.put("drawOfferSide", game.drawOfferSide == null ? "" : game.drawOfferSide);
        item.put("updatedAt", game.updatedAt == null ? "" : game.updatedAt.toString());
        item.put("roomFinalized", game.roomFinalized);
        return item;
    }

    private void restoreRoomState() {
        List<Map<String, Object>> records = persistence.load();
        if (records == null || records.isEmpty()) {
            return;
        }
        for (Map<String, Object> record : records) {
            try {
                restoreRoom(record);
            } catch (Exception ex) {
                System.out.println("[persistence] skip invalid room record: " + ex);
            }
        }
        System.out.println("[persistence] restored " + records.size() + " room record(s)");
    }

    private void restoreRoom(Map<String, Object> record) {
        ActiveRoom room = new ActiveRoom();
        room.roomId = asString(record.get("roomId"));
        room.roomCode = asString(record.get("roomCode"));
        room.gameType = parseGameType(asString(record.get("gameType")));
        room.initialTimeSeconds = asInt(record.get("initialTimeSeconds"));
        room.publicRoom = asBoolean(record.get("publicRoom"));
        room.host = toUser(record.get("host"));
        room.guest = toUser(record.get("guest"));
        room.hostReady = asBoolean(record.get("hostReady"));
        room.guestReady = asBoolean(record.get("guestReady"));
        room.roundIndex = asInt(record.get("roundIndex"));
        room.hostScore = asInt(record.get("hostScore"));
        room.guestScore = asInt(record.get("guestScore"));
        room.hostFirstSeat = asBoolean(record.get("hostFirstSeat"));
        room.swapColorsNext = asBoolean(record.get("swapColorsNext"));
        room.lastGameId = asString(record.get("lastGameId"));
        room.rematchOfferedByUserId = asString(record.get("rematchOfferedByUserId"));
        room.rematchOfferedByUsername = asString(record.get("rematchOfferedByUsername"));
        room.rematchExpiresAt = toInstant(asString(record.get("rematchExpiresAt")));
        room.status = asString(record.get("status"));
        room.gameId = asString(record.get("gameId"));
        room.updatedAt = toInstant(asString(record.get("updatedAt")));
        room.closed = asBoolean(record.get("closed"));
        if (room.roomId == null || room.roomId.isEmpty()) {
            return;
        }
        roomsById.put(room.roomId, room);
        if (room.roomCode != null && !room.roomCode.isEmpty()) {
            roomsByCode.put(room.roomCode, room);
        }
        if (room.gameId != null && !room.gameId.isEmpty()) {
            Map<String, Object> gameData = asMap(record.get("game"));
            if (!gameData.isEmpty()) {
                ActiveGame restored = restoreGame(room, gameData);
                gamesById.put(restored.gameId, restored);
            }
        }
    }

    private ActiveGame restoreGame(ActiveRoom room, Map<String, Object> data) {
        ActiveGame game = new ActiveGame();
        game.gameId = asString(data.get("gameId"));
        game.roomId = asString(data.get("roomId"));
        game.gameType = room.gameType;
        game.first = toUser(data.get("first"));
        game.second = toUser(data.get("second"));
        game.status = asString(data.get("status"));
        game.currentTurn = asString(data.get("currentTurn"));
        game.winnerSide = asString(data.get("winnerSide"));
        game.resultText = asString(data.get("resultText"));
        game.initialTimeSeconds = asInt(data.get("initialTimeSeconds"));
        game.firstRemainingSeconds = asInt(data.get("firstRemainingSeconds"));
        game.secondRemainingSeconds = asInt(data.get("secondRemainingSeconds"));
        game.clockState = asString(data.get("clockState"));
        game.lastTickAt = toInstant(asString(data.get("lastTickAt")));
        game.terminationReason = asString(data.get("terminationReason"));
        game.drawOfferUserId = asString(data.get("drawOfferUserId"));
        game.drawOfferUsername = asString(data.get("drawOfferUsername"));
        game.drawOfferSide = asString(data.get("drawOfferSide"));
        game.updatedAt = toInstant(asString(data.get("updatedAt")));
        game.roomFinalized = asBoolean(data.get("roomFinalized"));
        game.engine = restoreEngine(game);
        return game;
    }

    /**
     * 依据 DB 中已持久化的着法记录，重建一个与重启前一致的 OnlineMatchEngine。
     * (createGameRecord/appendMove 已把每步 payload 写入 game_moves；这里按序回放。)
     */
    private OnlineMatchEngine restoreEngine(ActiveGame game) {
        OnlineMatchEngine engine = createMatchFor(game);
        Map<String, Object> analysis = store.loadGameAnalysis(game.gameId);
        Object rawMoves = analysis.get("moves");
        if (!(rawMoves instanceof List)) {
            return engine;
        }
        List<?> moves = (List<?>) rawMoves;
        for (Object raw : moves) {
            try {
                Map<String, Object> move = asMap(raw);
                String actorId = asString(move.get("actorUserId"));
                Object payload = move.get("payload");
                engine.applyMove(actorId.isEmpty() ? game.first.id() : actorId, asMap(payload));
            } catch (Exception ignored) {
                // 单跳异常不阻止整体重建；保留已回放的合法着法。
            }
        }
        return engine;
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
        private int roundIndex;
        private int hostScore;
        private int guestScore;
        private boolean hostFirstSeat;
        private boolean swapColorsNext;
        private String lastGameId;
        private String rematchOfferedByUserId;
        private String rematchOfferedByUsername;
        private Instant rematchExpiresAt;
        private String status;
        private String gameId;
        private Instant updatedAt;
        private boolean closed;
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
        private boolean roomFinalized;

        private String firstSide() {
            return gameType == GameType.GOMOKU ? "BLACK" : "RED";
        }

        private String secondSide() {
            return gameType == GameType.GOMOKU ? "WHITE" : "BLACK";
        }
    }
}
