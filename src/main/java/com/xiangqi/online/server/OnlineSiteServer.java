package com.xiangqi.online.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiangqi.online.auth.AuthService;
import com.xiangqi.online.auth.AuthUser;
import com.xiangqi.online.auth.PasswordHasher;
import com.xiangqi.online.auth.UserSession;
import com.xiangqi.online.game.GameType;
import com.xiangqi.online.practice.CreatePracticeGameRequest;
import com.xiangqi.online.practice.PracticeGameHub;
import com.xiangqi.online.room.CreateRoomRequest;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.util.Headers;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.StatusCodes;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.CloseMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compact online HTTP surface used by some tests and legacy mounts.
 * Production traffic should use {@code com.xiangqi.web.PublicSiteServer},
 * which exposes the full /online/api set (search, dashboard, preferences, quick-match, practice undo).
 */
public final class OnlineSiteServer {
    private static final String AUTH_COOKIE = "XQ_AUTH";

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final OnlineStore store;
    private final AuthService authService;
    private final OnlineRoomHub roomHub;
    private final PracticeGameHub practiceHub;
    private final WsHub wsHub;
    private final RateLimiter authLimiter = new RateLimiter(8, 60_000);
    private final RateLimiter createRoomLimiter = new RateLimiter(12, 60_000);
    private Undertow server;

    public OnlineSiteServer() throws Exception {
        this.store = OnlineStore.createDefault();
        this.store.initSchema();
        this.authService = new AuthService(store.users(), store.sessions(), PasswordHasher.bcrypt(), Clock.systemUTC());
        this.roomHub = new OnlineRoomHub(store);
        this.practiceHub = new PracticeGameHub(store);
        this.wsHub = new WsHub();
    }

    public void start(String host, int port) {
        if (server != null) {
            return;
        }
        RoutingHandler routes = Handlers.routing(false)
            .get("/", this::handleIndex)
            .get("/assets/site/app.css", this::handleCss)
            .get("/assets/site/mobile.css", this::handleMobileCss)
            .get("/assets/site/app.js", this::handleJs)
            .get("/assets/site/board.js", this::handleBoardJs)
            .get("/api/site/bootstrap", this::handleBootstrap)
            .get("/api/auth/me", this::handleMe)
            .get("/api/lobby/overview", this::handleLobby)
            .get("/api/rooms/{roomId}", this::handleRoomById)
            .get("/api/games/{gameId}", this::handleGameById)
            .get("/api/games/{gameId}/analysis", this::handleGameAnalysis)
            .get("/api/learn/practice-games/{gameId}", this::handlePracticeGameById)
            .get("/api/learn/content", this::handleLearnContent)
            .get("/api/learn/catalog", this::handleLearnCatalog)
            .get("/api/learn/items/{id}", this::handleLearnItem)
            .get("/api/learn/progress", this::handleLearnProgress)
            .get("/api/watch/overview", this::handleWatchOverview)
            .get("/api/community/leaderboard", this::handleCommunityLeaderboard)
            .get("/api/profile/summary", this::handleProfileSummary)
            .post("/api/auth/register", this::handleRegister)
            .post("/api/auth/login", this::handleLogin)
            .post("/api/auth/logout", this::handleLogout)
            .post("/api/rooms", this::handleCreateRoom)
            .post("/api/rooms/join-by-code", this::handleJoinByCode)
            .post("/api/rooms/{roomId}/join", this::handleJoinRoom)
            .post("/api/rooms/{roomId}/ready", this::handleReady)
            .post("/api/rooms/{roomId}/rematch", this::handleRematch)
            .post("/api/rooms/{roomId}/leave", this::handleLeaveRoom)
            .delete("/api/rooms/{roomId}", this::handleCloseRoom)
            .post("/api/games/{gameId}/move", this::handleMove)
            .post("/api/games/{gameId}/resign", this::handleResign)
            .post("/api/games/{gameId}/draw-offer", this::handleDrawOffer)
            .post("/api/games/{gameId}/draw-response", this::handleDrawResponse)
            .post("/api/learn/practice-games", this::handleCreatePracticeGame)
            .post("/api/learn/practice-games/{gameId}/move", this::handlePracticeMove)
            .post("/api/learn/practice-games/{gameId}/resign", this::handlePracticeResign)
            .post("/api/learn/puzzles/{id}/complete", this::handleCompletePuzzle)
            .post("/api/learn/tutorials/{id}/complete", this::handleCompleteTutorial);
        HttpHandler handler = Handlers.path(new BlockingHandler(routes))
            .addExactPath("/ws", Handlers.websocket(new WebSocketConnectionCallback() {
                @Override
                public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
                    String token = extractWsCookie(exchange, AUTH_COOKIE);
                    Optional<AuthUser> user = token.isEmpty() ? Optional.<AuthUser>empty() : store.findUserByToken(token);
                    if (!user.isPresent()) {
                        try {
                            WebSockets.sendClose(CloseMessage.NORMAL_CLOSURE, "not authenticated", channel, null);
                        } catch (Exception ignored) {
                        }
                        try {
                            channel.close();
                        } catch (IOException ignored) {
                        }
                        return;
                    }
                    channel.setAttribute("userId", user.get().id());
                    channel.setAttribute("username", user.get().username());
                    wsHub.onConnect(channel);
                }
            }))
            .addPrefixPath("/", new BlockingHandler(routes));
        server = Undertow.builder().addHttpListener(port, host).setHandler(handler).build();
        server.start();
    }

    private void handleIndex(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/online/index.html", "text/html; charset=UTF-8");
    }

    private void handleCss(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/online/app.css", "text/css; charset=UTF-8");
    }

    private void handleMobileCss(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/online/mobile.css", "text/css; charset=UTF-8");
    }

    private void handleJs(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/online/app.js", "application/javascript; charset=UTF-8");
    }

    private void handleBoardJs(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/online/board.js", "application/javascript; charset=UTF-8");
    }

    private void handleBootstrap(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("siteName", "轻棋局 Online");
        body.put("games", supportedGames());
        body.put("activeRooms", roomHub.activeRoomCount());
        body.put("totalUsers", store.countUsers());
        body.put("totalGames", store.countGames());
        body.put("recentGames", store.recentGames(8));
        body.put("me", user.map(this::userMap).orElse(null));
        body.put("activity", user.map(value -> roomHub.activityForUser(value.id())).orElse(Collections.emptyMap()));
        sendJson(exchange, body);
    }

    private void handleMe(HttpServerExchange exchange) {
        sendJson(exchange, currentUser(exchange).map(this::userMap).orElse(null));
    }

    private void handleLobby(HttpServerExchange exchange) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("games", supportedGames());
        body.put("rooms", roomHub.publicRoomSummaries());
        sendJson(exchange, body);
    }

    private void handleRoomById(HttpServerExchange exchange) {
        try {
            sendJson(exchange, roomHub.roomSnapshotById(pathParam(exchange, "roomId")));
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.NOT_FOUND, ex.getMessage());
        }
    }

    private void handleGameById(HttpServerExchange exchange) {
        try {
            sendJson(exchange, practiceOrRoomGame(pathParam(exchange, "gameId"), currentUser(exchange).orElse(null)));
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.NOT_FOUND, ex.getMessage());
        }
    }

    private void handleGameAnalysis(HttpServerExchange exchange) {
        try {
            sendJson(exchange, practiceOrRoomAnalysis(pathParam(exchange, "gameId")));
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.NOT_FOUND, ex.getMessage());
        }
    }

    private void handlePracticeGameById(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        try {
            sendJson(exchange, practiceHub.gameSnapshotById(pathParam(exchange, "gameId"), user.get()));
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.NOT_FOUND, ex.getMessage());
        }
    }

    private void handleLearnContent(HttpServerExchange exchange) {
        sendJson(exchange, store.learnContent());
    }

    private void handleLearnCatalog(HttpServerExchange exchange) {
        sendJson(exchange, store.learnCatalog(
                queryParam(exchange, "filter", "all"),
                queryParam(exchange, "q", ""),
                asInt(queryParam(exchange, "offset", "0"), 0),
                asInt(queryParam(exchange, "limit", "24"), 24)
        ));
    }

    private void handleLearnItem(HttpServerExchange exchange) {
        Optional<Map<String, Object>> item = store.learnItem(pathParam(exchange, "id"));
        if (item.isEmpty()) {
            sendError(exchange, 404, "learn item not found");
            return;
        }
        sendJson(exchange, item.get());
    }

    private void handleLearnProgress(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        sendJson(exchange, store.learnProgress(user.get().id()));
    }

    private void handleCompletePuzzle(HttpServerExchange exchange) {
        completeLearnItem(exchange, "PUZZLE");
    }

    private void handleCompleteTutorial(HttpServerExchange exchange) {
        completeLearnItem(exchange, "TUTORIAL");
    }

    private void completeLearnItem(HttpServerExchange exchange, String contentType) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        try {
            store.markLearnProgress(user.get().id(), contentType, pathParam(exchange, "id"));
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("ok", true);
            body.put("contentType", contentType);
            body.put("contentId", pathParam(exchange, "id"));
            sendJson(exchange, body);
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleWatchOverview(HttpServerExchange exchange) {
        int limit = asInt(queryParam(exchange, "limit", "20"), 20);
        List<Map<String, Object>> replayGames = store.watchableGames(limit);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("publicRooms", watchPublicRooms());
        body.put("replayGames", replayGames);
        body.put("archivedGames", replayGames);
        body.put("generatedAt", Instant.now().toString());
        sendJson(exchange, body);
    }

    private void handleCommunityLeaderboard(HttpServerExchange exchange) {
        int windowDays = asInt(queryParam(exchange, "windowDays", "30"), 30);
        int limit = asInt(queryParam(exchange, "limit", "20"), 20);
        sendJson(exchange, store.communityLeaderboard(windowDays, limit));
    }

    private List<Map<String, Object>> watchPublicRooms() {
        List<Map<String, Object>> rooms = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> room : roomHub.publicRoomSummaries()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("roomId", asString(room.get("roomId")));
            item.put("roomCode", asString(room.get("roomCode")));
            item.put("gameId", asString(room.get("gameId")));
            item.put("gameType", asString(room.get("gameType")));
            item.put("status", asString(room.get("status")));
            item.put("updatedAt", asString(room.get("updatedAt")));
            Map<String, Object> players = new LinkedHashMap<String, Object>();
            Map<String, Object> first = new LinkedHashMap<String, Object>();
            first.put("username", asString(room.get("hostUsername")));
            first.put("side", "");
            Map<String, Object> second = new LinkedHashMap<String, Object>();
            second.put("username", asString(room.get("guestUsername")));
            second.put("side", "");
            players.put("first", first);
            players.put("second", second);
            item.put("players", players);
            rooms.add(item);
        }
        return rooms;
    }

    private void handleProfileSummary(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("user", userMap(user.get()));
        body.put("summary", store.profileSummary(user.get().id()));
        body.put("recentGames", store.recentGamesForUser(user.get().id(), 10));
        body.put("activity", roomHub.activityForUser(user.get().id()));
        sendJson(exchange, body);
    }

    private void handleRegister(HttpServerExchange exchange) {
        String ip = clientIp(exchange);
        if (!authLimiter.allow(ip + ":register")) {
            sendError(exchange, StatusCodes.TOO_MANY_REQUESTS, "too many requests");
            return;
        }
        try {
            Map<String, Object> payload = readJson(exchange);
            UserSession session = authService.register(asString(payload.get("username")), asString(payload.get("password")));
            setAuthCookie(exchange, session);
            sendJson(exchange, sessionBody(session));
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleLogin(HttpServerExchange exchange) {
        String ip = clientIp(exchange);
        if (!authLimiter.allow(ip + ":login")) {
            sendError(exchange, StatusCodes.TOO_MANY_REQUESTS, "too many requests");
            return;
        }
        try {
            Map<String, Object> payload = readJson(exchange);
            UserSession session = authService.login(asString(payload.get("username")), asString(payload.get("password")));
            setAuthCookie(exchange, session);
            sendJson(exchange, sessionBody(session));
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleLogout(HttpServerExchange exchange) {
        String token = authToken(exchange);
        if (!token.isEmpty()) {
            store.sessions().deleteByToken(token);
        }
        CookieImpl cookie = new CookieImpl(AUTH_COOKIE, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        exchange.setResponseCookie(cookie);
        sendJson(exchange, Collections.singletonMap("ok", true));
    }

    private void handleCreateRoom(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        String ip = clientIp(exchange);
        if (!createRoomLimiter.allow(ip + ":create-room")) {
            sendError(exchange, StatusCodes.TOO_MANY_REQUESTS, "too many requests");
            return;
        }
        try {
            Map<String, Object> payload = readJson(exchange);
            Map<String, Object> room = roomHub.createRoom(user.get(), new CreateRoomRequest(
                GameType.valueOf(asString(payload.get("gameType"))),
                asInt(payload.get("initialTimeSeconds"), 600),
                asBoolean(payload.get("isPublic"))
            ));
            wsHub.broadcastRoom(asString(room.get("roomId")), roomEvent(asString(room.get("roomId"))));
            sendJson(exchange, room);
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleJoinByCode(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        try {
            Map<String, Object> payload = readJson(exchange);
            Map<String, Object> room = roomHub.joinByCode(asString(payload.get("roomCode")), user.get());
            wsHub.broadcastRoom(asString(room.get("roomId")), roomEvent(asString(room.get("roomId"))));
            sendJson(exchange, room);
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleJoinRoom(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        String roomId = pathParam(exchange, "roomId");
        try {
            Map<String, Object> room = roomHub.joinRoom(roomId, user.get());
            wsHub.broadcastRoom(roomId, roomEvent(roomId));
            sendJson(exchange, room);
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleReady(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        String roomId = pathParam(exchange, "roomId");
        try {
            Map<String, Object> payload = readJson(exchange);
            Map<String, Object> room = roomHub.setReady(roomId, user.get().id(), asBoolean(payload.get("ready")));
            wsHub.broadcastRoom(roomId, roomEvent(roomId));
            sendJson(exchange, room);
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleRematch(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        String roomId = pathParam(exchange, "roomId");
        try {
            Map<String, Object> payload = readJson(exchange);
            Map<String, Object> room = roomHub.rematch(roomId, user.get(), asString(payload.get("action")));
            wsHub.broadcastRoom(roomId, roomEvent(roomId));
            sendJson(exchange, room);
        } catch (IllegalStateException ex) {
            sendError(exchange, StatusCodes.CONFLICT, ex.getMessage());
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleCloseRoom(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        String roomId = pathParam(exchange, "roomId");
        try {
            Map<String, Object> result = roomHub.closeRoom(roomId, user.get());
            wsHub.broadcastRoom(roomId, roomClosedEvent(roomId));
            sendJson(exchange, result);
        } catch (SecurityException ex) {
            sendError(exchange, StatusCodes.FORBIDDEN, ex.getMessage());
        } catch (IllegalStateException ex) {
            sendError(exchange, StatusCodes.CONFLICT, ex.getMessage());
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleLeaveRoom(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        String roomId = pathParam(exchange, "roomId");
        try {
            Map<String, Object> result = roomHub.leaveRoom(roomId, user.get());
            wsHub.broadcastRoom(roomId, roomClosedEvent(roomId));
            sendJson(exchange, result);
        } catch (SecurityException ex) {
            sendError(exchange, StatusCodes.FORBIDDEN, ex.getMessage());
        } catch (IllegalStateException ex) {
            sendError(exchange, StatusCodes.CONFLICT, ex.getMessage());
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleMove(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        String gameId = pathParam(exchange, "gameId");
        try {
            Map<String, Object> payload = readJson(exchange);
            Map<String, Object> game = roomHub.applyMove(gameId, user.get(), payload);
            String roomId = asString(game.get("roomId"));
            wsHub.broadcastRoom(roomId, roomEvent(roomId));
            sendJson(exchange, game);
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleResign(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        String gameId = pathParam(exchange, "gameId");
        try {
            Map<String, Object> game = roomHub.resign(gameId, user.get());
            String roomId = asString(game.get("roomId"));
            wsHub.broadcastRoom(roomId, roomEvent(roomId));
            sendJson(exchange, game);
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleDrawOffer(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        String gameId = pathParam(exchange, "gameId");
        try {
            Map<String, Object> game = roomHub.offerDraw(gameId, user.get());
            String roomId = asString(game.get("roomId"));
            wsHub.broadcastRoom(roomId, roomEvent(roomId));
            sendJson(exchange, game);
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleDrawResponse(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        String gameId = pathParam(exchange, "gameId");
        try {
            Map<String, Object> payload = readJson(exchange);
            Map<String, Object> game = roomHub.respondDraw(gameId, user.get(), asBoolean(payload.get("accept")));
            String roomId = asString(game.get("roomId"));
            wsHub.broadcastRoom(roomId, roomEvent(roomId));
            sendJson(exchange, game);
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleCreatePracticeGame(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        try {
            Map<String, Object> payload = readJson(exchange);
            Map<String, Object> game = practiceHub.createGame(user.get(), new CreatePracticeGameRequest(
                GameType.valueOf(asString(payload.get("gameType"))),
                asString(payload.get("difficulty")),
                asBoolean(payload.get("humanFirst")),
                asString(payload.get("preferredEngine")),
                asString(payload.get("initialFen"))
            ));
            sendJson(exchange, game);
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handlePracticeMove(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        try {
            sendJson(exchange, practiceHub.applyMove(pathParam(exchange, "gameId"), user.get(), readJson(exchange)));
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handlePracticeResign(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        try {
            sendJson(exchange, practiceHub.resign(pathParam(exchange, "gameId"), user.get()));
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private Optional<AuthUser> currentUser(HttpServerExchange exchange) {
        String token = authToken(exchange);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        return store.findUserByToken(token);
    }

    private String authToken(HttpServerExchange exchange) {
        if (exchange.getRequestCookies().containsKey(AUTH_COOKIE)) {
            return exchange.getRequestCookies().get(AUTH_COOKIE).getValue();
        }
        return "";
    }

    private String extractWsCookie(WebSocketHttpExchange exchange, String name) {
        String header = exchange.getRequestHeader("Cookie");
        if (header == null || header.trim().isEmpty()) {
            return "";
        }
        String[] parts = header.split(";");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && name.equals(kv[0].trim())) {
                return kv[1].trim();
            }
        }
        return "";
    }

    private String clientIp(HttpServerExchange exchange) {
        if (exchange.getSourceAddress() == null || exchange.getSourceAddress().getAddress() == null) {
            return "unknown";
        }
        return exchange.getSourceAddress().getAddress().getHostAddress();
    }

    private void setAuthCookie(HttpServerExchange exchange, UserSession session) {
        CookieImpl cookie = new CookieImpl(AUTH_COOKIE, session.token());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(14 * 24 * 3600);
        exchange.setResponseCookie(cookie);
    }

    private Map<String, Object> sessionBody(UserSession session) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("user", userMap(session.user()));
        body.put("expiresAt", session.expiresAt().toString());
        return body;
    }

    private Map<String, Object> userMap(AuthUser user) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", user.id());
        item.put("username", user.username());
        return item;
    }

    private Map<String, Object> supportedGames() {
        Map<String, Object> games = new LinkedHashMap<String, Object>();
        games.put("XIANGQI", gameInfo("中国象棋", true));
        games.put("GOMOKU", gameInfo("五子棋", true));
        games.put("GO", gameInfo("围棋", false));
        return games;
    }

    private Map<String, Object> gameInfo(String label, boolean enabled) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("label", label);
        item.put("enabled", enabled);
        return item;
    }

    private Map<String, Object> roomEvent(String roomId) {
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        Map<String, Object> room = roomHub.roomSnapshotById(roomId);
        event.put("type", "room_state");
        event.put("room", room);
        String gameId = asString(room.get("gameId"));
        if (!gameId.isEmpty()) {
            event.put("game", roomHub.gameSnapshotById(gameId, null));
        }
        return event;
    }

    private Map<String, Object> roomClosedEvent(String roomId) {
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("type", "room_closed");
        event.put("roomId", roomId);
        event.put("message", "房间已关闭");
        return event;
    }

    private Map<String, Object> readJson(HttpServerExchange exchange) throws IOException {
        exchange.startBlocking();
        return mapper.readValue(exchange.getInputStream(), new TypeReference<Map<String, Object>>() { });
    }

    private void sendResource(HttpServerExchange exchange, String resourcePath, String contentType) throws IOException {
        try (InputStream input = OnlineSiteServer.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                sendError(exchange, StatusCodes.NOT_FOUND, "resource not found");
                return;
            }
            byte[] body = input.readAllBytes();
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
            exchange.setStatusCode(StatusCodes.OK);
            exchange.getResponseSender().send(new String(body, StandardCharsets.UTF_8));
        }
    }

    private void sendJson(HttpServerExchange exchange, Object body) {
        try {
            byte[] json = mapper.writeValueAsBytes(body);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=UTF-8");
            exchange.setStatusCode(StatusCodes.OK);
            exchange.getResponseSender().send(new String(json, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "json encode failed");
        }
    }

    private void sendError(HttpServerExchange exchange, int statusCode, String message) {
        try {
            byte[] json = mapper.writeValueAsBytes(Collections.singletonMap("error", message));
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=UTF-8");
            exchange.setStatusCode(statusCode);
            exchange.getResponseSender().send(new String(json, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            exchange.setStatusCode(statusCode);
            exchange.getResponseSender().send(message == null ? "" : message);
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String queryParam(HttpServerExchange exchange, String key, String fallback) {
        if (!exchange.getQueryParameters().containsKey(key) || exchange.getQueryParameters().get(key).isEmpty()) {
            return fallback;
        }
        return exchange.getQueryParameters().get(key).getFirst();
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return fallback;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String pathParam(HttpServerExchange exchange, String key) {
        PathTemplateMatch match = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
        if (match == null || !match.getParameters().containsKey(key)) {
            throw new IllegalArgumentException("missing path parameter: " + key);
        }
        return match.getParameters().get(key);
    }

    private Map<String, Object> practiceOrRoomGame(String gameId, AuthUser viewer) {
        if (practiceHub.hasActiveGame(gameId)) {
            return practiceHub.gameSnapshotById(gameId, viewer);
        }
        return roomHub.gameSnapshotById(gameId, viewer);
    }

    private Map<String, Object> practiceOrRoomAnalysis(String gameId) {
        if (practiceHub.hasActiveGame(gameId)) {
            return practiceHub.analysis(gameId);
        }
        return roomHub.analysis(gameId);
    }

    private final class WsHub {
        private final ConcurrentHashMap<String, Set<WebSocketChannel>> byRoom = new ConcurrentHashMap<String, Set<WebSocketChannel>>();
        private final ConcurrentHashMap<WebSocketChannel, String> channelRooms = new ConcurrentHashMap<WebSocketChannel, String>();

        private void onConnect(final WebSocketChannel channel) {
            channel.getReceiveSetter().set(new AbstractReceiveListener() {
                @Override
                protected void onFullTextMessage(WebSocketChannel webSocketChannel, BufferedTextMessage message) throws IOException {
                    Map<String, Object> payload = mapper.readValue(message.getData(), new TypeReference<Map<String, Object>>() { });
                    if ("subscribe".equals(asString(payload.get("type")))) {
                        subscribe(webSocketChannel, asString(payload.get("roomId")));
                    }
                }

                @Override
                protected void onClose(WebSocketChannel webSocketChannel, io.undertow.websockets.core.StreamSourceFrameChannel channelFrame) throws IOException {
                    unsubscribe(webSocketChannel);
                    super.onClose(webSocketChannel, channelFrame);
                }

                @Override
                protected void onError(WebSocketChannel webSocketChannel, Throwable error) {
                    unsubscribe(webSocketChannel);
                }
            });
            channel.resumeReceives();
        }

        private void subscribe(WebSocketChannel channel, String roomId) {
            unsubscribe(channel);
            if (roomId == null || roomId.trim().isEmpty()) {
                return;
            }
            String userId = asString(channel.getAttribute("userId"));
            Map<String, Object> room;
            try {
                room = roomHub.roomSnapshotById(roomId);
            } catch (Exception ex) {
                return;
            }
            if (!canSubscribeRoom(userId, room)) {
                return;
            }
            byRoom.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(channel);
            channelRooms.put(channel, roomId);
            try {
                WebSockets.sendText(mapper.writeValueAsString(roomEvent(roomId)), channel, null);
            } catch (Exception ignored) {
                unsubscribe(channel);
            }
        }

        private void unsubscribe(WebSocketChannel channel) {
            String roomId = channelRooms.remove(channel);
            if (roomId == null) {
                return;
            }
            Set<WebSocketChannel> channels = byRoom.get(roomId);
            if (channels != null) {
                channels.remove(channel);
                if (channels.isEmpty()) {
                    byRoom.remove(roomId, channels);
                }
            }
        }

        private void broadcastRoom(String roomId, Map<String, Object> payload) {
            Set<WebSocketChannel> channels = byRoom.get(roomId);
            if (channels == null || channels.isEmpty()) {
                return;
            }
            try {
                String text = mapper.writeValueAsString(payload);
                for (WebSocketChannel channel : channels) {
                    WebSockets.sendText(text, channel, null);
                }
            } catch (Exception ignored) {
            }
        }

        private boolean canSubscribeRoom(String userId, Map<String, Object> room) {
            if (userId == null || userId.trim().isEmpty() || room == null) {
                return false;
            }
            if (asBoolean(room.get("isPublic"))) {
                return true;
            }
            String hostId = nestedUserId(room.get("host"));
            String guestId = nestedUserId(room.get("guest"));
            return userId.equals(hostId) || userId.equals(guestId);
        }

        @SuppressWarnings("unchecked")
        private String nestedUserId(Object value) {
            if (!(value instanceof Map)) {
                return "";
            }
            Object id = ((Map<String, Object>) value).get("id");
            return id == null ? "" : String.valueOf(id);
        }
    }
}
