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
import com.xiangqi.online.server.RateLimiter;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.StatusCodes;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final RateLimiter authLimiter = new RateLimiter(8, 60_000);
    private final RateLimiter createRoomLimiter = new RateLimiter(12, 60_000);
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
            .get("/", this::handleRootRedirect)
            .add(Methods.HEAD, "/", this::handleRootRedirect)
            .get("/home-ai", this::handleLegacyIndex)
            .get("/assets/ui/app.css", this::handleLegacyCss)
            .get("/assets/ui/app.js", this::handleLegacyJs)
            .get("/assets/audio/move.wav", this::handleMoveAudio)
            .get("/assets/audio/mate.wav", this::handleMateAudio)
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
            .add(Methods.HEAD, "/online", this::handleOnlineIndex)
            .get("/online/index.html", this::handleOnlineIndex)
            .get("/online/assets/site/app.css", this::handleOnlineCss)
            .get("/online/assets/site/mobile.css", this::handleOnlineMobileCss)
            .get("/online/assets/site/app.js", this::handleOnlineJs)
            .get("/online/assets/site/board.js", this::handleOnlineBoardJs)
            .get("/online/api/site/bootstrap", this::handleBootstrap)
            .get("/online/api/auth/me", this::handleMe)
            .get("/online/api/lobby/overview", this::handleLobby)
            .get("/online/api/lobby/search", this::handleLobbySearch)
            .get("/online/api/rooms/{roomId}", this::handleRoomById)
            .get("/online/api/games/{gameId}", this::handleGameById)
            .get("/online/api/games/{gameId}/analysis", this::handleGameAnalysis)
            .get("/online/api/learn/practice-games/{gameId}", this::handlePracticeGameById)
            .get("/online/api/learn/content", this::handleLearnContent)
            .get("/online/api/learn/catalog", this::handleLearnCatalog)
            .get("/online/api/learn/items/{id}", this::handleLearnItem)
            .get("/online/api/learn/progress", this::handleLearnProgress)
            .get("/online/api/watch/overview", this::handleWatchOverview)
            .get("/online/api/community/leaderboard", this::handleCommunityLeaderboard)
            .get("/online/api/profile/summary", this::handleProfileSummary)
            .get("/online/api/profile/dashboard", this::handleProfileDashboard)
            .get("/online/api/profile/preferences", this::handleProfilePreferences)
            .post("/online/api/auth/register", this::handleRegister)
            .post("/online/api/auth/login", this::handleLogin)
            .post("/online/api/auth/logout", this::handleLogout)
            .post("/online/api/profile/preferences", this::handleSaveProfilePreferences)
            .post("/online/api/rooms", this::handleCreateRoom)
            .post("/online/api/rooms/quick-match", this::handleQuickMatch)
            .post("/online/api/rooms/join-by-code", this::handleJoinByCode)
            .post("/online/api/rooms/{roomId}/join", this::handleJoinRoom)
            .post("/online/api/rooms/{roomId}/ready", this::handleReady)
            .delete("/online/api/rooms/{roomId}", this::handleCloseRoom)
            .post("/online/api/games/{gameId}/move", this::handleMove)
            .post("/online/api/games/{gameId}/resign", this::handleResign)
            .post("/online/api/games/{gameId}/draw-offer", this::handleDrawOffer)
            .post("/online/api/games/{gameId}/draw-response", this::handleDrawResponse)
            .post("/online/api/learn/practice-games", this::handleCreatePracticeGame)
            .post("/online/api/learn/practice-games/{gameId}/move", this::handlePracticeMove)
            .post("/online/api/learn/practice-games/{gameId}/resign", this::handlePracticeResign)
            .post("/online/api/learn/practice-games/{gameId}/undo", this::handlePracticeUndo)
            .post("/online/api/learn/puzzles/{id}/complete", this::handleCompletePuzzle)
            .post("/online/api/learn/tutorials/{id}/complete", this::handleCompleteTutorial);
        HttpHandler handler = Handlers.path(new BlockingHandler(routes))
            .addExactPath("/online/ws", Handlers.websocket(new WebSocketConnectionCallback() {
                @Override
                public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
                    String token = extractWsCookie(exchange, AUTH_COOKIE);
                    Optional<AuthUser> user = token.isEmpty() ? Optional.<AuthUser>empty() : store.findUserByToken(token);
                    if (user.isPresent()) {
                        channel.setAttribute("userId", user.get().id());
                        channel.setAttribute("username", user.get().username());
                    }
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

    private void handleRootRedirect(HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.FOUND);
        exchange.getResponseHeaders().put(Headers.LOCATION, "/online#/home");
        exchange.endExchange();
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

    private void handleOnlineMobileCss(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/online/mobile.css", "text/css; charset=UTF-8");
    }

    private void handleOnlineJs(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/online/app.js", "application/javascript; charset=UTF-8");
    }

    private void handleOnlineBoardJs(HttpServerExchange exchange) throws IOException {
        sendResource(exchange, "/online/board.js", "application/javascript; charset=UTF-8");
    }

    private void handleBootstrap(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        List<Map<String, Object>> publicRooms = roomHub.publicRoomSummaries();
        Map<String, Object> gameStats = withActivePublicRooms(store.gameTypeStats(), publicRooms);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("siteName", "轻棋局 Online");
        body.put("games", supportedGames());
        body.put("activeRooms", roomHub.activeRoomCount());
        body.put("totalUsers", store.countUsers());
        body.put("totalGames", store.countGames());
        body.put("recentGames", store.recentGames(8));
        body.put("gameStats", gameStats);
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

    private void handleLobbySearch(HttpServerExchange exchange) {
        String query = asString(queryParam(exchange, "q", "")).trim();
        int limit = Math.max(1, asInt(queryParam(exchange, "limit", "8"), 8));
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("query", query);
        body.put("rooms", searchPublicRooms(query, limit));
        body.put("players", store.searchUsers(query, limit));
        body.put("generatedAt", Instant.now().toString());
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
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("publicRooms", watchPublicRooms());
        body.put("archivedGames", store.watchableGames(limit));
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
            String gameType = asString(room.get("gameType"));
            String hostSide = defaultHostSide(gameType);
            String guestSide = defaultGuestSide(gameType);
            String guestUsername = asString(room.get("guestUsername"));
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("roomId", asString(room.get("roomId")));
            item.put("roomCode", asString(room.get("roomCode")));
            item.put("gameId", asString(room.get("gameId")));
            item.put("gameType", gameType);
            item.put("status", asString(room.get("status")));
            item.put("updatedAt", asString(room.get("updatedAt")));
            Map<String, Object> players = new LinkedHashMap<String, Object>();
            Map<String, Object> first = new LinkedHashMap<String, Object>();
            first.put("username", asString(room.get("hostUsername")));
            first.put("side", hostSide);
            Map<String, Object> second = new LinkedHashMap<String, Object>();
            second.put("username", guestUsername);
            second.put("side", guestUsername.isEmpty() ? "" : guestSide);
            players.put("first", first);
            players.put("second", second);
            item.put("players", players);
            rooms.add(item);
        }
        return rooms;
    }

    private List<Map<String, Object>> searchPublicRooms(String query, int limit) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> matches = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> room : roomHub.publicRoomSummaries()) {
            if (matches.size() >= limit) {
                break;
            }
            if (containsIgnoreCase(room.get("roomCode"), normalized)
                || containsIgnoreCase(room.get("hostUsername"), normalized)
                || containsIgnoreCase(room.get("guestUsername"), normalized)
                || containsIgnoreCase(room.get("gameType"), normalized)) {
                matches.add(room);
            }
        }
        return matches;
    }

    private Map<String, Object> withActivePublicRooms(Map<String, Object> gameStats, List<Map<String, Object>> publicRooms) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.putAll(gameStats);
        Map<String, Integer> activeCounts = new LinkedHashMap<String, Integer>();
        activeCounts.put("XIANGQI", 0);
        activeCounts.put("GOMOKU", 0);
        for (Map<String, Object> room : publicRooms) {
            String gameType = asString(room.get("gameType")).trim().toUpperCase(Locale.ROOT);
            if (!activeCounts.containsKey(gameType)) {
                activeCounts.put(gameType, 0);
            }
            activeCounts.put(gameType, activeCounts.get(gameType) + 1);
        }
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> stat = (Map<String, Object>) entry.getValue();
            stat.put("activePublicRooms", activeCounts.getOrDefault(entry.getKey(), 0));
        }
        return body;
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

    private void handleProfileDashboard(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        sendJson(exchange, buildProfileDashboard(user.get()));
    }

    private void handleProfilePreferences(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        sendJson(exchange, store.profilePreferences(user.get().id()));
    }

    private void handleSaveProfilePreferences(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        try {
            Map<String, Object> payload = readJson(exchange);
            sendJson(exchange, store.saveProfilePreferences(user.get().id(), sanitizePreferencesPatch(payload)));
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private Map<String, Object> sanitizePreferencesPatch(Map<String, Object> payload) {
        Map<String, Object> safe = new LinkedHashMap<String, Object>();
        if (payload == null) {
            return safe;
        }
        if (payload.containsKey("soundEnabled")) {
            Object raw = payload.get("soundEnabled");
            if (!(raw instanceof Boolean)) {
                throw new IllegalArgumentException("soundEnabled must be boolean");
            }
            safe.put("soundEnabled", raw);
        }
        if (payload.containsKey("boardFlipped")) {
            Object raw = payload.get("boardFlipped");
            if (!(raw instanceof Boolean)) {
                throw new IllegalArgumentException("boardFlipped must be boolean");
            }
            safe.put("boardFlipped", raw);
        }
        if (payload.containsKey("boardTheme")) {
            String theme = asString(payload.get("boardTheme")).trim().toLowerCase();
            if (!"wood".equals(theme) && !"ink".equals(theme)) {
                throw new IllegalArgumentException("boardTheme must be wood or ink");
            }
            safe.put("boardTheme", theme);
        }
        return safe;
    }

    private Map<String, Object> buildProfileDashboard(AuthUser user) {
        Map<String, Object> summary = store.profileSummary(user.id());
        List<Map<String, Object>> recentGames = store.recentGamesForUser(user.id(), 12);
        Map<String, Object> activity = roomHub.activityForUser(user.id());
        Map<String, Object> preferences = store.profilePreferences(user.id());
        Map<String, Object> learnProgress = store.learnProgress(user.id());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("user", userMap(user));
        body.put("summary", summary);
        body.put("recentGames", recentGames);
        body.put("activity", activity);
        body.put("preferences", preferences);
        body.put("learnProgress", learnProgress);
        body.put("achievements", buildProfileAchievements(summary, learnProgress));
        body.put("notifications", buildProfileNotifications(summary, recentGames, activity, preferences, learnProgress));
        return body;
    }

    private List<Map<String, Object>> buildProfileAchievements(Map<String, Object> summary, Map<String, Object> learnProgress) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        int totalGames = asInt(summary.get("totalGames"), 0);
        int wins = asInt(summary.get("wins"), 0);
        int learnDone = learnProgressCount(learnProgress);
        items.add(profileAchievement("first-game", "初次对局", "完成 1 局正式或练习对局。", totalGames >= 1, Math.min(totalGames, 1), 1));
        items.add(profileAchievement("first-win", "拿下首胜", "至少取得 1 局胜利。", wins >= 1, Math.min(wins, 1), 1));
        items.add(profileAchievement("study-starter", "学习起步", "完成任意教程或残局题。", learnDone >= 1, Math.min(learnDone, 1), 1));
        items.add(profileAchievement("steady-practice", "持续练习", "累计完成 5 项学习或对局。", totalGames + learnDone >= 5, Math.min(totalGames + learnDone, 5), 5));
        return items;
    }

    private Map<String, Object> profileAchievement(String id, String title, String description, boolean earned, int current, int target) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("id", id);
        body.put("title", title);
        body.put("description", description);
        body.put("earned", earned);
        body.put("current", current);
        body.put("target", target);
        return body;
    }

    private List<Map<String, Object>> buildProfileNotifications(
        Map<String, Object> summary,
        List<Map<String, Object>> recentGames,
        Map<String, Object> activity,
        Map<String, Object> preferences,
        Map<String, Object> learnProgress
    ) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        Object room = activity.get("room");
        if (room instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> roomMap = (Map<String, Object>) room;
            String roomId = asString(roomMap.get("roomId"));
            items.add(profileNotification(
                "active-room",
                "房间仍在进行中",
                "你当前仍在房间中，可继续准备、等待或回到对局。",
                "activity",
                roomId.isEmpty() ? "play" : "room/" + roomId,
                asString(roomMap.get("updatedAt"))
            ));
        }
        Object game = activity.get("game");
        if (game instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> gameMap = (Map<String, Object>) game;
            String gameId = asString(gameMap.get("gameId"));
            items.add(profileNotification(
                "active-game",
                "当前有进行中的棋局",
                "你的对局仍可继续，直接回到棋桌即可恢复操作。",
                "game",
                gameId.isEmpty() ? "play" : "game/" + gameId,
                asString(gameMap.get("updatedAt"))
            ));
        }
        if (!recentGames.isEmpty()) {
            Map<String, Object> latest = recentGames.get(0);
            String gameId = asString(latest.get("gameId"));
            items.add(profileNotification(
                "latest-archive",
                "最近对局已归档",
                "最新一局已经可进入分析页复盘。",
                "analysis",
                gameId.isEmpty() ? "me/records" : "analysis/" + gameId,
                firstNonEmpty(asString(latest.get("finishedAt")), asString(summary.get("lastGameAt")))
            ));
        }
        if (!asString(preferences.get("updatedAt")).isEmpty()) {
            items.add(profileNotification(
                "settings-synced",
                "棋桌设置已同步",
                "音效、主题与翻转偏好已保存在当前账号下。",
                "settings",
                "me/settings",
                asString(preferences.get("updatedAt"))
            ));
        }
        if (learnProgressCount(learnProgress) > 0) {
            items.add(profileNotification(
                "study-progress",
                "学习档案有新进展",
                "你的教程或残局进度已记录，可继续从上次位置开始。",
                "study",
                "me/study",
                asString(learnProgress.get("updatedAt"))
            ));
        }
        return items;
    }

    private Map<String, Object> profileNotification(String id, String title, String bodyText, String kind, String path, String createdAt) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("id", id);
        body.put("title", title);
        body.put("body", bodyText);
        body.put("kind", kind);
        body.put("path", path);
        body.put("createdAt", createdAt == null ? "" : createdAt);
        return body;
    }

    private int learnProgressCount(Map<String, Object> progress) {
        return mapListSize(progress.get("tutorialsCompleted")) + mapListSize(progress.get("puzzlesCompleted"));
    }

    private int mapListSize(Object value) {
        if (value instanceof List) {
            return ((List<?>) value).size();
        }
        return 0;
    }

    private String firstNonEmpty(String first, String second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        return second == null ? "" : second;
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
            GameType gameType = parseGameType(payload.get("gameType"));
            Map<String, Object> room = roomHub.createRoom(user.get(), new CreateRoomRequest(
                gameType,
                asInt(payload.get("initialTimeSeconds"), 600),
                asBoolean(payload.get("isPublic"))
            ));
            wsHub.broadcastRoom(asString(room.get("roomId")), roomEvent(asString(room.get("roomId"))));
            wsHub.broadcastLobby();
            sendJson(exchange, room);
        } catch (Exception ex) {
            sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
        }
    }

    private void handleQuickMatch(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        String ip = clientIp(exchange);
        if (!createRoomLimiter.allow(ip + ":quick-match")) {
            sendError(exchange, StatusCodes.TOO_MANY_REQUESTS, "too many requests");
            return;
        }
        try {
            Map<String, Object> payload = readJson(exchange);
            GameType gameType = parseGameType(payload.get("gameType"));
            Map<String, Object> result = roomHub.quickMatch(user.get(), new CreateRoomRequest(
                gameType,
                asInt(payload.get("initialTimeSeconds"), 300),
                true
            ));
            @SuppressWarnings("unchecked")
            Map<String, Object> room = (Map<String, Object>) result.get("room");
            String roomId = asString(room.get("roomId"));
            wsHub.broadcastRoom(roomId, roomEvent(roomId));
            wsHub.broadcastLobby();
            sendJson(exchange, result);
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
            wsHub.broadcastLobby();
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
            wsHub.broadcastLobby();
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
            wsHub.broadcastLobby();
            sendJson(exchange, room);
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
            wsHub.broadcastLobby();
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

    private void handlePracticeUndo(HttpServerExchange exchange) {
        Optional<AuthUser> user = currentUser(exchange);
        if (!user.isPresent()) {
            sendError(exchange, StatusCodes.UNAUTHORIZED, "login required");
            return;
        }
        try {
            sendJson(exchange, practiceHub.undo(pathParam(exchange, "gameId"), user.get()));
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

    private Map<String, Object> lobbyEvent() {
        Map<String, Object> lobby = new LinkedHashMap<String, Object>();
        lobby.put("games", supportedGames());
        lobby.put("rooms", roomHub.publicRoomSummaries());
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("type", "lobby");
        event.put("lobby", lobby);
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
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(body.length));
            exchange.setStatusCode(StatusCodes.OK);
            exchange.getResponseSender().send(ByteBuffer.wrap(body));
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

    private boolean containsIgnoreCase(Object value, String normalizedNeedle) {
        if (normalizedNeedle == null || normalizedNeedle.isEmpty()) {
            return false;
        }
        return asString(value).toLowerCase(Locale.ROOT).contains(normalizedNeedle);
    }

    private GameType parseGameType(Object raw) {
        String normalized = asString(raw).trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("gameType is required");
        }
        try {
            return GameType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unsupported gameType: " + normalized);
        }
    }

    private String defaultHostSide(String gameType) {
        if ("XIANGQI".equals(gameType)) {
            return "RED";
        }
        if ("GOMOKU".equals(gameType)) {
            return "BLACK";
        }
        return "";
    }

    private String defaultGuestSide(String gameType) {
        if ("XIANGQI".equals(gameType)) {
            return "BLACK";
        }
        if ("GOMOKU".equals(gameType)) {
            return "WHITE";
        }
        return "";
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
        private final Set<WebSocketChannel> lobbyChannels = ConcurrentHashMap.newKeySet();

        private void onConnect(final WebSocketChannel channel) {
            channel.getReceiveSetter().set(new AbstractReceiveListener() {
                @Override
                protected void onFullTextMessage(WebSocketChannel webSocketChannel, BufferedTextMessage message) throws IOException {
                    Map<String, Object> payload = mapper.readValue(message.getData(), new TypeReference<Map<String, Object>>() { });
                    if ("subscribe".equals(asString(payload.get("type")))) {
                        subscribe(webSocketChannel, asString(payload.get("roomId")));
                    } else if ("subscribe_lobby".equals(asString(payload.get("type")))) {
                        subscribeLobby(webSocketChannel);
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

        private void subscribeLobby(WebSocketChannel channel) {
            unsubscribe(channel);
            lobbyChannels.add(channel);
            try {
                WebSockets.sendText(mapper.writeValueAsString(lobbyEvent()), channel, null);
            } catch (Exception ignored) {
                unsubscribe(channel);
            }
        }

        private void unsubscribe(WebSocketChannel channel) {
            lobbyChannels.remove(channel);
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

        private void broadcastLobby() {
            if (lobbyChannels.isEmpty()) {
                return;
            }
            try {
                String text = mapper.writeValueAsString(lobbyEvent());
                for (WebSocketChannel channel : lobbyChannels) {
                    WebSockets.sendText(text, channel, null);
                }
            } catch (Exception ignored) {
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
