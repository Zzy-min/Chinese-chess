package com.xiangqi.online.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiangqi.online.auth.AuthService;
import com.xiangqi.online.auth.AuthUser;
import com.xiangqi.online.auth.PasswordHasher;
import com.xiangqi.online.auth.UserSession;
import com.xiangqi.online.game.GameType;
import com.xiangqi.online.learn.EndgameCatalog;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class OnlineSiteServer {
    private static final String AUTH_COOKIE = "XQ_AUTH";

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final OnlineStore store;
    private final AuthService authService;
    private final OnlineRoomHub roomHub;
    private final PracticeGameHub practiceHub;
    private final EndgameCatalog endgameCatalog = new EndgameCatalog();
    private final WsHub wsHub;
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
            .get("/assets/site/app.js", this::handleJs)
            .get("/api/site/bootstrap", this::handleBootstrap)
            .get("/api/auth/me", this::handleMe)
            .get("/api/lobby/overview", this::handleLobby)
            .get("/api/rooms/{roomId}", this::handleRoomById)
            .get("/api/games/{gameId}", this::handleGameById)
            .get("/api/games/{gameId}/analysis", this::handleGameAnalysis)
            .get("/api/learn/endgames", this::handleListEndgames)
            .get("/api/learn/practice-games/{gameId}", this::handlePracticeGameById)
            .get("/api/profile/summary", this::handleProfileSummary)
            .post("/api/auth/register", this::handleRegister)
            .post("/api/auth/login", this::handleLogin)
            .post("/api/auth/logout", this::handleLogout)
            .post("/api/rooms", this::handleCreateRoom)
            .post("/api/rooms/join-by-code", this::handleJoinByCode)
            .post("/api/rooms/{roomId}/join", this::handleJoinRoom)
            .post("/api/rooms/{roomId}/ready", this::handleReady)
            .post("/api/games/{gameId}/move", this::handleMove)
            .post("/api/games/{gameId}/resign", this::handleResign)
            .post("/api/games/{gameId}/draw-offer", this::handleDrawOffer)
            .post("/api/games/{gameId}/draw-response", this::handleDrawResponse)
            .post("/api/learn/practice-games", this::handleCreatePracticeGame)
            .post("/api/learn/practice-games/{gameId}/move", this::handlePracticeMove)
            .post("/api/learn/practice-games/{gameId}/resign", this::handlePracticeResign)
            .post("/api/learn/practice-games/{gameId}/hint", this::handlePracticeHint);
        HttpHandler handler = Handlers.path(new BlockingHandler(routes))
            .addExactPath("/ws", Handlers.websocket(new WebSocketConnectionCallback() {
                @Override
                public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
                    String token = extractWsCookie(exchange, AUTH_COOKIE);
                    Optional<AuthUser> user = (token != null && !token.isEmpty())
                        ? store.findUserByToken(token) : Optional.empty();
                    if (user.isEmpty()) {
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

    private void handleJs(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/online/app.js", "application/javascript; charset=UTF-8");
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

    private void handleProfileSummary(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("user", userMap(user.get()));
        body.put("summary", store.profileSummary(user.get().id()));
        body.put("puzzleStats", store.getUserPuzzleStats(user.get().id()));
        body.put("recentGames", store.recentGamesForUser(user.get().id(), 10));
        body.put("activity", roomHub.activityForUser(user.get().id()));
        sendJson(exchange, body);
    }

    private void handleRegister(HttpServerExchange exchange) {
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
        try {
            Map<String, Object> payload = readJson(exchange);
            UserSession session = authService.login(asString(payload.get("username")), asString(payload.get("password")));
            setAuthCookie(exchange, session);
            sendJson(exchange, sessionBody(session));
        } catch (Exception ex) {
            int status = (ex.getMessage() != null && ex.getMessage().contains("invalid credentials"))
                ? StatusCodes.UNAUTHORIZED : StatusCodes.BAD_REQUEST;
            sendError(exchange, status, ex.getMessage());
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
                asString(payload.get("fen")),
                asString(payload.get("endgameId")),
                asString(payload.get("endgameName"))
            ));
            sendJson(exchange, game);
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleListEndgames(HttpServerExchange exchange) {
        String difficulty = exchange.getQueryParameters().get("difficulty") != null
            ? exchange.getQueryParameters().get("difficulty").getFirst() : null;
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        java.util.List<java.util.Map<String, String>> endgames = endgameCatalog.byDifficulty(difficulty);
        // Attach solved status for logged-in users
        Optional<AuthUser> user = currentUser(exchange);
        if (user.isPresent()) {
            java.util.List<String> solvedIds = store.getSolvedEndgameIds(user.get().id());
            java.util.Set<String> solvedSet = new java.util.HashSet<String>(solvedIds);
            java.util.List<java.util.Map<String, String>> enriched = new java.util.ArrayList<java.util.Map<String, String>>();
            for (java.util.Map<String, String> eg : endgames) {
                java.util.Map<String, String> copy = new java.util.LinkedHashMap<String, String>(eg);
                copy.put("solved", solvedSet.contains(eg.get("id")) ? "true" : "false");
                enriched.add(copy);
            }
            body.put("endgames", enriched);
        } else {
            body.put("endgames", endgames);
        }
        body.put("total", endgameCatalog.all().size());
        sendJson(exchange, body);
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

    private void handlePracticeHint(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        try {
            sendJson(exchange, practiceHub.getHint(pathParam(exchange, "gameId"), user.get()));
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

    private void setAuthCookie(HttpServerExchange exchange, UserSession session) {
        CookieImpl cookie = new CookieImpl(AUTH_COOKIE, session.token());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSameSiteMode("Lax");
        cookie.setMaxAge(14 * 24 * 3600);
        exchange.setResponseCookie(cookie);
    }

    private String extractWsCookie(WebSocketHttpExchange exchange, String name) {
        String header = exchange.getRequestHeader("Cookie");
        if (header == null || header.isEmpty()) return null;
        for (String part : header.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring(name.length() + 1);
            }
        }
        return null;
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
            // TODO: BUG-004 — IDOR: any authenticated user can subscribe to any room.
            // Room model does not yet support visibility/access-control, so we only
            // verify the room exists before allowing subscription. Once the room
            // model supports visibility (e.g. public/private/invite-only), enforce
            // authorization here.
            try {
                roomHub.roomSnapshotById(roomId);
            } catch (Exception ex) {
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
    }
}
