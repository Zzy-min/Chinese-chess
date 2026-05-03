package com.xiangqi.web;

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
import com.xiangqi.online.server.OnlineRoomHub;
import com.xiangqi.online.server.OnlineStore;
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

import com.xiangqi.online.server.RateLimiter;

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

public final class PublicSiteServer {
    private static final String AUTH_COOKIE = "XQ_AUTH";

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final OnlineStore store;
    private final AuthService authService;
    private final OnlineRoomHub roomHub;
    private final PracticeGameHub practiceHub;
    private final LegacyHomeSessionHub legacyHomeHub;
    private final WsHub wsHub = new WsHub();
    private final RateLimiter authLimiter = new RateLimiter(5, 60_000);
    private final RateLimiter createRoomLimiter = new RateLimiter(3, 60_000);
    private Undertow server;

    public PublicSiteServer() throws Exception {
        this(OnlineStore.createDefault());
    }

    public PublicSiteServer(OnlineStore store) {
        this.store = initializeStore(store);
        this.authService = new AuthService(store.users(), store.sessions(), PasswordHasher.bcrypt(), Clock.systemUTC());
        this.roomHub = new OnlineRoomHub(store);
        this.practiceHub = new PracticeGameHub(store);
        this.legacyHomeHub = new LegacyHomeSessionHub(practiceHub);
    }

    private static OnlineStore initializeStore(OnlineStore store) {
        try {
            store.initSchema();
            return store;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize public site schema", ex);
        }
    }

    public void start(String host, int port) {
        if (server != null) {
            return;
        }
        RoutingHandler routes = Handlers.routing(false)
            .get("/", this::handleOnlineIndex)
            .get("/ai", this::handleLegacyIndex)
            .get("/assets/ui/app.css", this::handleLegacyCss)
            .get("/assets/ui/app.js", this::handleLegacyJs)
            .get("/assets/audio/move.wav", this::handleMoveAudio)
            .get("/assets/audio/mate.wav", this::handleMateAudio)
            .get("/assets/audio/capture.wav", this::handleCaptureAudio)
            .get("/assets/audio/check.wav", this::handleCheckAudio)
            .get("/api/state", this::handleLegacyState)
            .get("/api/new", this::handleLegacyNewGame)
            .get("/api/endgame", this::handleLegacyEndgame)
            .get("/api/click", this::handleLegacyClick)
            .get("/api/undo", this::handleLegacyNoop)
            .get("/api/surrender", this::handleLegacySurrender)
            .get("/api/draw", this::handleLegacyNoop)
            .get("/api/review/start", this::handleLegacyReviewStart)
            .get("/api/review/prev", this::handleLegacyReviewPrev)
            .get("/api/review/next", this::handleLegacyReviewNext)
            .get("/api/review/exit", this::handleLegacyReviewExit)
            .get("/api/perf", this::handleLegacyPerf)
            .get("/api/perf/reset", this::handleLegacyPerf)
            .get("/api/perf/event", this::handleLegacyPerf)
            .get("/api/auth/me", this::handleMe)
            .post("/api/auth/register", this::handleRegister)
            .post("/api/auth/login", this::handleLogin)
            .post("/api/auth/logout", this::handleLogout)
            .get("/online", this::handleOnlineIndex)
            .get("/online/assets/site/app.css", this::handleOnlineCss)
            .get("/online/assets/site/app.js", this::handleOnlineJs)
            .get("/online/assets/site/board.js", this::handleOnlineBoardJs)
            .get("/online/api/site/bootstrap", this::handleBootstrap)
            .get("/online/api/auth/me", this::handleMe)
            .get("/online/api/lobby/overview", this::handleLobby)
            .get("/online/api/rooms/{roomId}", this::handleRoomById)
            .get("/online/api/games/{gameId}", this::handleGameById)
            .get("/online/api/games/{gameId}/analysis", this::handleGameAnalysis)
            .get("/online/api/learn/practice-games/{gameId}", this::handlePracticeGameById)
            .get("/online/api/profile/summary", this::handleProfileSummary)
            .post("/online/api/auth/register", this::handleRegister)
            .post("/online/api/auth/login", this::handleLogin)
            .post("/online/api/auth/logout", this::handleLogout)
            .post("/online/api/rooms", this::handleCreateRoom)
            .post("/online/api/rooms/join-by-code", this::handleJoinByCode)
            .post("/online/api/rooms/{roomId}/join", this::handleJoinRoom)
            .post("/online/api/rooms/{roomId}/ready", this::handleReady)
            .post("/online/api/games/{gameId}/move", this::handleMove)
            .post("/online/api/games/{gameId}/resign", this::handleResign)
            .post("/online/api/games/{gameId}/draw-offer", this::handleDrawOffer)
            .post("/online/api/games/{gameId}/draw-response", this::handleDrawResponse)
            .post("/online/api/learn/practice-games", this::handleCreatePracticeGame)
            .post("/online/api/learn/practice-games/{gameId}/move", this::handlePracticeMove)
            .post("/online/api/learn/practice-games/{gameId}/resign", this::handlePracticeResign)
            .post("/online/api/learn/practice-games/{gameId}/hint", this::handlePracticeHint);
        HttpHandler handler = Handlers.path(new BlockingHandler(routes))
            .addExactPath("/online/ws", Handlers.websocket(new WebSocketConnectionCallback() {
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

    public void stop() {
        if (server == null) {
            return;
        }
        server.stop();
        server = null;
    }

    private void handleLegacyIndex(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/web/index.html", "text/html; charset=UTF-8");
    }

    private void handleLegacyCss(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/web/app.css", "text/css; charset=UTF-8");
    }

    private void handleLegacyJs(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/web/app.js", "application/javascript; charset=UTF-8");
    }

    private void handleMoveAudio(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/audio/move.wav", "audio/wav");
    }

    private void handleMateAudio(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/audio/mate.wav", "audio/wav");
    }

    private void handleCaptureAudio(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/audio/capture.wav", "audio/wav");
    }

    private void handleCheckAudio(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/audio/check.wav", "audio/wav");
    }

    private void handleLegacyState(HttpServerExchange exchange) {
        sendJson(exchange, legacyHomeHub.state(legacySessionId(exchange), currentUser(exchange).orElse(null)));
    }

    private void handleLegacyNewGame(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        try {
            sendJson(exchange, legacyHomeHub.newGame(
                legacySessionId(exchange),
                user.get(),
                GameType.valueOf(queryParam(exchange, "gameType", "XIANGQI")),
                queryParam(exchange, "difficulty", "MEDIUM"),
                !"false".equalsIgnoreCase(queryParam(exchange, "humanFirst", "true")),
                preferredEngine(exchange)
            ));
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleLegacyEndgame(HttpServerExchange exchange) {
        handleLegacyNewGame(exchange);
    }

    private void handleLegacyClick(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        try {
            sendJson(exchange, legacyHomeHub.click(
                legacySessionId(exchange),
                user.get(),
                asInt(queryParam(exchange, "row", "-1"), -1),
                asInt(queryParam(exchange, "col", "-1"), -1)
            ));
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleLegacySurrender(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        try {
            sendJson(exchange, legacyHomeHub.surrender(legacySessionId(exchange), user.get()));
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleLegacyReviewStart(HttpServerExchange exchange) {
        sendJson(exchange, legacyHomeHub.reviewStart(legacySessionId(exchange), currentUser(exchange).orElse(null)));
    }

    private void handleLegacyReviewPrev(HttpServerExchange exchange) {
        sendJson(exchange, legacyHomeHub.reviewPrev(legacySessionId(exchange), currentUser(exchange).orElse(null)));
    }

    private void handleLegacyReviewNext(HttpServerExchange exchange) {
        sendJson(exchange, legacyHomeHub.reviewNext(legacySessionId(exchange), currentUser(exchange).orElse(null)));
    }

    private void handleLegacyReviewExit(HttpServerExchange exchange) {
        sendJson(exchange, legacyHomeHub.reviewExit(legacySessionId(exchange), currentUser(exchange).orElse(null)));
    }

    private void handleLegacyNoop(HttpServerExchange exchange) {
        sendJson(exchange, legacyHomeHub.state(legacySessionId(exchange), currentUser(exchange).orElse(null)));
    }

    private void handleLegacyPerf(HttpServerExchange exchange) {
        sendJson(exchange, Collections.singletonMap("ok", true));
    }

    private void handleOnlineIndex(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/online/index.html", "text/html; charset=UTF-8");
    }

    private void handleOnlineCss(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/online/app.css", "text/css; charset=UTF-8");
    }

    private void handleOnlineJs(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/online/app.js", "application/javascript; charset=UTF-8");
    }

    private void handleOnlineBoardJs(HttpServerExchange exchange) throws IOException {
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
        String ip = exchange.getSourceAddress().getAddress().getHostAddress();
        if (!authLimiter.allow(ip)) {
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
        String ip = exchange.getSourceAddress().getAddress().getHostAddress();
        if (!authLimiter.allow(ip)) {
            sendError(exchange, StatusCodes.TOO_MANY_REQUESTS, "too many requests");
            return;
        }
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
        try {
            Map<String, Object> game = roomHub.applyMove(pathParam(exchange, "gameId"), user.get(), readJson(exchange));
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
        try {
            Map<String, Object> game = roomHub.resign(pathParam(exchange, "gameId"), user.get());
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
        try {
            Map<String, Object> game = roomHub.offerDraw(pathParam(exchange, "gameId"), user.get());
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
        try {
            Map<String, Object> payload = readJson(exchange);
            Map<String, Object> game = roomHub.respondDraw(pathParam(exchange, "gameId"), user.get(), asBoolean(payload.get("accept")));
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
                asString(payload.get("preferredEngine"))
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
        return token.isEmpty() ? Optional.<AuthUser>empty() : store.findUserByToken(token);
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
        try (InputStream input = PublicSiteServer.class.getResourceAsStream(resourcePath)) {
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

    private String queryParam(HttpServerExchange exchange, String key, String fallback) {
        if (!exchange.getQueryParameters().containsKey(key) || exchange.getQueryParameters().get(key).isEmpty()) {
            return fallback;
        }
        return exchange.getQueryParameters().get(key).getFirst();
    }

    private String preferredEngine(HttpServerExchange exchange) {
        String gameType = queryParam(exchange, "gameType", "XIANGQI");
        return "GOMOKU".equalsIgnoreCase(gameType)
            ? queryParam(exchange, "gomokuEngine", "BUILTIN")
            : queryParam(exchange, "xiangqiEngine", "BUILTIN");
    }

    private String legacySessionId(HttpServerExchange exchange) {
        return queryParam(exchange, "sid", "legacy-default");
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
            try {
                Map<String, Object> room = roomHub.roomSnapshotById(roomId);
                // 权限检查：只有房间成员或公开房间才能订阅
                String userId = (String) channel.getAttribute("userId");
                Map<String, Object> host = (Map<String, Object>) room.get("host");
                Map<String, Object> guest = (Map<String, Object>) room.get("guest");
                boolean isPlayer = userId != null && (
                    userId.equals(host != null ? host.get("id") : null) ||
                    (guest != null && userId.equals(guest.get("id")))
                );
                boolean isPublic = !"false".equals(String.valueOf(room.get("isPublic")));
                if (!isPlayer && !isPublic) {
                    return;
                }
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
