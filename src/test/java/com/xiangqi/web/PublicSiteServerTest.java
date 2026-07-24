package com.xiangqi.web;

import com.xiangqi.online.server.OnlineStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicSiteServerTest {

    @Test
    void redirectsRootToOnlineHomeAndKeepsLegacyHomepageOnDedicatedPath() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> root = client.send(request(port, "/"), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> rootHead = client.send(headRequest(port, "/"), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> legacy = client.send(request(port, "/home-ai"), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> online = client.send(request(port, "/online"), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> mobileCss = client.send(
                request(port, "/online/assets/site/mobile.css?v=20260724m1"),
                HttpResponse.BodyHandlers.ofString()
            );
            HttpResponse<String> onlineBootstrap = client.send(request(port, "/online/api/site/bootstrap"), HttpResponse.BodyHandlers.ofString());

            assertEquals(302, root.statusCode());
            assertEquals("/online#/home", root.headers().firstValue("Location").orElse(""));
            assertEquals(302, rootHead.statusCode());
            assertEquals("/online#/home", rootHead.headers().firstValue("Location").orElse(""));

            assertEquals(200, legacy.statusCode());
            assertTrue(legacy.body().contains("/assets/ui/app.js"));

            assertEquals(200, online.statusCode());
            assertTrue(online.body().contains("/online/assets/site/app.js"));

            assertEquals(200, mobileCss.statusCode());
            assertEquals("text/css; charset=UTF-8", mobileCss.headers().firstValue("Content-Type").orElse(""));
            assertTrue(mobileCss.body().contains(".mobileHome"));

            assertEquals(200, onlineBootstrap.statusCode());
            assertTrue(onlineBootstrap.body().contains("\"siteName\""));
        } finally {
            server.stop();
        }
    }

    @Test
    void initializesSchemaForPublicSiteStoreAutomatically() throws Exception {
        OnlineStore store = newUninitializedStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> bootstrap = client.send(request(port, "/online/api/site/bootstrap"), HttpResponse.BodyHandlers.ofString());

            assertEquals(200, bootstrap.statusCode());
            assertTrue(bootstrap.body().contains("\"totalUsers\""));
        } finally {
            server.stop();
        }
    }

    @Test
    void exposesLearnWatchCommunityReadApisForGuestAndProtectsProgressWrites() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> learnContent = client.send(getRequest(port, "/online/api/learn/content", ""), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> watch = client.send(getRequest(port, "/online/api/watch/overview", ""), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> community = client.send(getRequest(port, "/online/api/community/leaderboard", ""), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> progress = client.send(getRequest(port, "/online/api/learn/progress", ""), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> tutorialComplete = client.send(postRequest(port, "/online/api/learn/tutorials/t-1/complete", "{}", ""), HttpResponse.BodyHandlers.ofString());

            assertEquals(200, learnContent.statusCode());
            assertTrue(learnContent.body().contains("\"tutorials\""));
            assertEquals(200, watch.statusCode());
            assertTrue(watch.body().contains("\"publicRooms\""));
            assertEquals(200, community.statusCode());
            assertTrue(community.body().contains("\"winBoard\""));
            assertEquals(401, progress.statusCode());
            assertEquals(401, tutorialComplete.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void writesAndReadsLearnProgressAfterLogin() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();
            String username = "u_" + Instant.now().toEpochMilli();
            String registerBody = "{\"username\":\"" + username + "\",\"password\":\"Passw0rd123!\"}";

            HttpResponse<String> register = client.send(postRequest(port, "/online/api/auth/register", registerBody, ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, register.statusCode());
            String authCookie = register.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];
            assertTrue(authCookie.startsWith("XQ_AUTH="));

            HttpResponse<String> before = client.send(getRequest(port, "/online/api/learn/progress", authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, before.statusCode());

            HttpResponse<String> completeTutorial = client.send(postRequest(port, "/online/api/learn/tutorials/tutorial-001/complete", "{}", authCookie), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> completePuzzle = client.send(postRequest(port, "/online/api/learn/puzzles/puzzle-001/complete", "{}", authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, completeTutorial.statusCode());
            assertEquals(200, completePuzzle.statusCode());

            HttpResponse<String> after = client.send(getRequest(port, "/online/api/learn/progress", authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, after.statusCode());
            assertTrue(after.body().contains("tutorial-001"));
            assertTrue(after.body().contains("puzzle-001"));
        } finally {
            server.stop();
        }
    }

    @Test
    void profilePreferencesRequireLoginAndCanBeSavedAfterRegister() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> guest = client.send(getRequest(port, "/online/api/profile/preferences", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, guest.statusCode());

            String username = "pref_" + Instant.now().toEpochMilli();
            String registerBody = "{\"username\":\"" + username + "\",\"password\":\"Passw0rd123!\"}";
            HttpResponse<String> register = client.send(postRequest(port, "/online/api/auth/register", registerBody, ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, register.statusCode());
            String authCookie = register.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];

            HttpResponse<String> defaults = client.send(getRequest(port, "/online/api/profile/preferences", authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, defaults.statusCode());
            assertTrue(defaults.body().contains("\"boardTheme\":\"wood\""));
            assertTrue(defaults.body().contains("\"soundEnabled\":true"));

            HttpResponse<String> saved = client.send(postRequest(port, "/online/api/profile/preferences",
                "{\"soundEnabled\":false,\"boardTheme\":\"ink\",\"boardFlipped\":true}", authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, saved.statusCode());
            assertTrue(saved.body().contains("\"soundEnabled\":false"));
            assertTrue(saved.body().contains("\"boardTheme\":\"ink\""));
            assertTrue(saved.body().contains("\"boardFlipped\":true"));

            HttpResponse<String> invalid = client.send(postRequest(port, "/online/api/profile/preferences",
                "{\"boardTheme\":\"bad\"}", authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalid.statusCode());
            assertTrue(invalid.body().contains("boardTheme must be wood or ink"));
        } finally {
            server.stop();
        }
    }

    @Test
    void createsPracticeGameFromInitialFenAndRejectsInvalidFenPayload() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();
            String username = "u_" + Instant.now().toEpochMilli();
            String registerBody = "{\"username\":\"" + username + "\",\"password\":\"Passw0rd123!\"}";

            HttpResponse<String> register = client.send(postRequest(port, "/online/api/auth/register", registerBody, ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, register.statusCode());
            String authCookie = register.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];

            String validFenBody = "{\"gameType\":\"XIANGQI\",\"difficulty\":\"MEDIUM\",\"humanFirst\":false,\"preferredEngine\":\"BUILTIN\",\"initialFen\":\"4k4/9/9/9/9/4R4/9/9/9/4K4 b - - 0 1\"}";
            HttpResponse<String> created = client.send(postRequest(port, "/online/api/learn/practice-games", validFenBody, authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, created.statusCode());
            assertTrue(created.body().contains("\"currentTurn\":\"BLACK\""));
            assertTrue(created.body().contains("\"initialFen\":\"4k4/9/9/9/9/4R4/9/9/9/4K4 b - - 0 1\""));

            String invalidFenBody = "{\"gameType\":\"XIANGQI\",\"difficulty\":\"MEDIUM\",\"humanFirst\":true,\"preferredEngine\":\"BUILTIN\",\"initialFen\":\"bad-fen\"}";
            HttpResponse<String> invalid = client.send(postRequest(port, "/online/api/learn/practice-games", invalidFenBody, authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalid.statusCode());
            assertTrue(invalid.body().contains("invalid initial FEN"));
        } finally {
            server.stop();
        }
    }

    @Test
    void practiceUndoRequiresLoginAndRewindsAfterAiReply() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();
            String username = "undo_" + Instant.now().toEpochMilli();
            String registerBody = "{\"username\":\"" + username + "\",\"password\":\"Passw0rd123!\"}";

            HttpResponse<String> register = client.send(postRequest(port, "/online/api/auth/register", registerBody, ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, register.statusCode());
            String authCookie = register.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];

            String createBody = "{\"gameType\":\"XIANGQI\",\"difficulty\":\"EASY\",\"humanFirst\":true,\"preferredEngine\":\"BUILTIN\"}";
            HttpResponse<String> created = client.send(postRequest(port, "/online/api/learn/practice-games", createBody, authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, created.statusCode());
            String gameId = extract(created.body(), "gameId");
            assertTrue(!gameId.isEmpty());

            HttpResponse<String> guestUndo = client.send(postRequest(port, "/online/api/learn/practice-games/" + gameId + "/undo", "{}", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, guestUndo.statusCode());

            String moveBody = "{\"fromRow\":6,\"fromCol\":0,\"toRow\":5,\"toCol\":0}";
            HttpResponse<String> move = client.send(postRequest(port, "/online/api/learn/practice-games/" + gameId + "/move", moveBody, authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, move.statusCode());
            assertTrue(move.body().contains("\"aiPending\":true"));
            assertTrue(move.body().contains("\"moveCount\":1"));

            long deadline = System.currentTimeMillis() + 5000L;
            HttpResponse<String> snapshot = client.send(getRequest(port, "/online/api/learn/practice-games/" + gameId, authCookie), HttpResponse.BodyHandlers.ofString());
            while (System.currentTimeMillis() < deadline
                && (!snapshot.body().contains("\"aiPending\":false") || !snapshot.body().contains("\"moveCount\":2"))) {
                Thread.sleep(50L);
                snapshot = client.send(getRequest(port, "/online/api/learn/practice-games/" + gameId, authCookie), HttpResponse.BodyHandlers.ofString());
            }
            assertTrue(snapshot.body().contains("\"aiPending\":false"));

            HttpResponse<String> undone = client.send(postRequest(port, "/online/api/learn/practice-games/" + gameId + "/undo", "{}", authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, undone.statusCode());
            assertTrue(undone.body().contains("\"moveCount\":0"));
            assertTrue(undone.body().contains("\"status\":\"PLAYING\""));
        } finally {
            server.stop();
        }
    }

    @Test
    void watchOverviewIncludesSideForPublicRooms() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();

            String u1 = "watch_u1_" + Instant.now().toEpochMilli();
            String u2 = "watch_u2_" + Instant.now().toEpochMilli();
            String registerU1 = "{\"username\":\"" + u1 + "\",\"password\":\"Passw0rd123!\"}";
            String registerU2 = "{\"username\":\"" + u2 + "\",\"password\":\"Passw0rd123!\"}";

            HttpResponse<String> r1 = client.send(postRequest(port, "/online/api/auth/register", registerU1, ""), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> r2 = client.send(postRequest(port, "/online/api/auth/register", registerU2, ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r1.statusCode());
            assertEquals(200, r2.statusCode());
            String c1 = r1.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];
            String c2 = r2.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];

            HttpResponse<String> created = client.send(postRequest(port, "/online/api/rooms",
                "{\"gameType\":\"XIANGQI\",\"initialTimeSeconds\":600,\"isPublic\":true}", c1), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, created.statusCode());
            String roomCode = extract(created.body(), "roomCode");
            String roomId = extract(created.body(), "roomId");
            assertTrue(!roomCode.isEmpty());
            assertTrue(!roomId.isEmpty());

            HttpResponse<String> joined = client.send(postRequest(port, "/online/api/rooms/join-by-code",
                "{\"roomCode\":\"" + roomCode + "\"}", c2), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, joined.statusCode());

            HttpResponse<String> ready1 = client.send(postRequest(port, "/online/api/rooms/" + roomId + "/ready",
                "{\"ready\":true}", c1), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> ready2 = client.send(postRequest(port, "/online/api/rooms/" + roomId + "/ready",
                "{\"ready\":true}", c2), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ready1.statusCode());
            assertEquals(200, ready2.statusCode());

            HttpResponse<String> watch = client.send(getRequest(port, "/online/api/watch/overview", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, watch.statusCode());
            assertTrue(watch.body().contains("\"roomCode\":\"" + roomCode + "\""));
            assertTrue(watch.body().contains("\"side\":\"RED\""));
            assertTrue(watch.body().contains("\"side\":\"BLACK\""));
        } finally {
            server.stop();
        }
    }

    @Test
    void communityLeaderboardResponseIncludesPerGameTypeBuckets() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();

            String u1 = "lb_u1_" + Instant.now().toEpochMilli();
            String u2 = "lb_u2_" + Instant.now().toEpochMilli();
            String u3 = "lb_u3_" + Instant.now().toEpochMilli();
            HttpResponse<String> r1 = client.send(postRequest(port, "/online/api/auth/register",
                "{\"username\":\"" + u1 + "\",\"password\":\"Passw0rd123!\"}", ""), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> r2 = client.send(postRequest(port, "/online/api/auth/register",
                "{\"username\":\"" + u2 + "\",\"password\":\"Passw0rd123!\"}", ""), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> r3 = client.send(postRequest(port, "/online/api/auth/register",
                "{\"username\":\"" + u3 + "\",\"password\":\"Passw0rd123!\"}", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r1.statusCode());
            assertEquals(200, r2.statusCode());
            assertEquals(200, r3.statusCode());
            String c1 = r1.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];
            String c2 = r2.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];
            String c3 = r3.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];

            String room1 = extract(client.send(postRequest(port, "/online/api/rooms",
                "{\"gameType\":\"XIANGQI\",\"initialTimeSeconds\":600,\"isPublic\":false}", c1), HttpResponse.BodyHandlers.ofString()).body(), "roomId");
            client.send(postRequest(port, "/online/api/rooms/" + room1 + "/join",
                "{\"roomId\":\"" + room1 + "\"}", c2), HttpResponse.BodyHandlers.ofString());
            client.send(postRequest(port, "/online/api/rooms/" + room1 + "/ready", "{\"ready\":true}", c1), HttpResponse.BodyHandlers.ofString());
            client.send(postRequest(port, "/online/api/rooms/" + room1 + "/ready", "{\"ready\":true}", c2), HttpResponse.BodyHandlers.ofString());
            String game1 = extract(client.send(getRequest(port, "/online/api/rooms/" + room1, c1), HttpResponse.BodyHandlers.ofString()).body(), "gameId");
            client.send(postRequest(port, "/online/api/games/" + game1 + "/resign", "{}", c2), HttpResponse.BodyHandlers.ofString());

            String room2 = extract(client.send(postRequest(port, "/online/api/rooms",
                "{\"gameType\":\"GOMOKU\",\"initialTimeSeconds\":600,\"isPublic\":false}", c2), HttpResponse.BodyHandlers.ofString()).body(), "roomId");
            client.send(postRequest(port, "/online/api/rooms/" + room2 + "/join",
                "{\"roomId\":\"" + room2 + "\"}", c3), HttpResponse.BodyHandlers.ofString());
            client.send(postRequest(port, "/online/api/rooms/" + room2 + "/ready", "{\"ready\":true}", c2), HttpResponse.BodyHandlers.ofString());
            client.send(postRequest(port, "/online/api/rooms/" + room2 + "/ready", "{\"ready\":true}", c3), HttpResponse.BodyHandlers.ofString());
            String game2 = extract(client.send(getRequest(port, "/online/api/rooms/" + room2, c2), HttpResponse.BodyHandlers.ofString()).body(), "gameId");
            client.send(postRequest(port, "/online/api/games/" + game2 + "/resign", "{}", c3), HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> community = client.send(getRequest(port, "/online/api/community/leaderboard", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, community.statusCode());
            assertTrue(community.body().contains("\"byGameType\""));
            assertTrue(community.body().contains("\"XIANGQI\""));
            assertTrue(community.body().contains("\"GOMOKU\""));
            assertTrue(community.body().contains("\"gameType\":\"XIANGQI\""));
            assertTrue(community.body().contains("\"gameType\":\"GOMOKU\""));
        } finally {
            server.stop();
        }
    }

    @Test
    void lobbySearchFindsMatchingPublicRoomsAndPlayers() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();

            String hostName = "SearchHost_" + Instant.now().toEpochMilli();
            String guestName = "RiverGuest_" + Instant.now().toEpochMilli();
            HttpResponse<String> host = client.send(postRequest(port, "/online/api/auth/register",
                "{\"username\":\"" + hostName + "\",\"password\":\"Passw0rd123!\"}", ""), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> guest = client.send(postRequest(port, "/online/api/auth/register",
                "{\"username\":\"" + guestName + "\",\"password\":\"Passw0rd123!\"}", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, host.statusCode());
            assertEquals(200, guest.statusCode());
            String hostCookie = host.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];
            String guestCookie = guest.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];

            HttpResponse<String> created = client.send(postRequest(port, "/online/api/rooms",
                "{\"gameType\":\"XIANGQI\",\"initialTimeSeconds\":600,\"isPublic\":true}", hostCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, created.statusCode());
            String roomCode = extract(created.body(), "roomCode");
            String roomId = extract(created.body(), "roomId");
            assertTrue(!roomCode.isEmpty());

            HttpResponse<String> joined = client.send(postRequest(port, "/online/api/rooms/" + roomId + "/join", "{}", guestCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, joined.statusCode());

            HttpResponse<String> searchByCode = client.send(getRequest(port, "/online/api/lobby/search?q=" + roomCode, ""), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> searchByUser = client.send(getRequest(port, "/online/api/lobby/search?q=riverguest", ""), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> empty = client.send(getRequest(port, "/online/api/lobby/search?q=", ""), HttpResponse.BodyHandlers.ofString());

            assertEquals(200, searchByCode.statusCode());
            assertTrue(searchByCode.body().contains("\"rooms\""));
            assertTrue(searchByCode.body().contains("\"roomCode\":\"" + roomCode + "\""));
            assertTrue(searchByCode.body().contains("\"hostUsername\":\"" + hostName.toLowerCase() + "\""));

            assertEquals(200, searchByUser.statusCode());
            assertTrue(searchByUser.body().contains("\"players\""));
            assertTrue(searchByUser.body().contains("\"username\":\"" + guestName.toLowerCase() + "\""));
            assertTrue(searchByUser.body().contains("\"guestUsername\":\"" + guestName.toLowerCase() + "\""));

            assertEquals(200, empty.statusCode());
            assertTrue(empty.body().contains("\"rooms\":[]"));
            assertTrue(empty.body().contains("\"players\":[]"));
        } finally {
            server.stop();
        }
    }

    @Test
    void quickMatchCreatesWaitingRoomThenMatchesSecondPlayerIntoGame() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> guestQuickMatch = client.send(postRequest(port, "/online/api/rooms/quick-match",
                "{\"gameType\":\"XIANGQI\",\"initialTimeSeconds\":300}", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, guestQuickMatch.statusCode());

            String u1 = "qm_u1_" + Instant.now().toEpochMilli();
            String u2 = "qm_u2_" + Instant.now().toEpochMilli();
            HttpResponse<String> r1 = client.send(postRequest(port, "/online/api/auth/register",
                "{\"username\":\"" + u1 + "\",\"password\":\"Passw0rd123!\"}", ""), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> r2 = client.send(postRequest(port, "/online/api/auth/register",
                "{\"username\":\"" + u2 + "\",\"password\":\"Passw0rd123!\"}", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r1.statusCode());
            assertEquals(200, r2.statusCode());
            String c1 = r1.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];
            String c2 = r2.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];

            HttpResponse<String> first = client.send(postRequest(port, "/online/api/rooms/quick-match",
                "{\"gameType\":\"XIANGQI\",\"initialTimeSeconds\":300}", c1), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, first.statusCode());
            assertTrue(first.body().contains("\"matched\":false"));
            assertTrue(first.body().contains("\"isPublic\":true"));
            assertTrue(first.body().contains("\"hostReady\":true"));
            assertTrue(first.body().contains("\"guest\":null"));
            String roomId = extract(first.body(), "roomId");
            assertTrue(!roomId.isEmpty());

            HttpResponse<String> second = client.send(postRequest(port, "/online/api/rooms/quick-match",
                "{\"gameType\":\"XIANGQI\",\"initialTimeSeconds\":300}", c2), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, second.statusCode());
            assertTrue(second.body().contains("\"matched\":true"));
            assertTrue(second.body().contains("\"roomId\":\"" + roomId + "\""));
            assertTrue(second.body().contains("\"guestReady\":true"));
            assertTrue(second.body().contains("\"status\":\"PLAYING\""));
            assertTrue(second.body().contains("\"game\""));
            String gameId = extract(second.body(), "gameId");
            assertTrue(!gameId.isEmpty());

            HttpResponse<String> room = client.send(getRequest(port, "/online/api/rooms/" + roomId, c1), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, room.statusCode());
            assertTrue(room.body().contains("\"gameId\":\"" + gameId + "\""));
        } finally {
            server.stop();
        }
    }

    @Test
    void bootstrapIncludesPerGameTypeStatsAndRecentGameTimes() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();

            String u1 = "boot_u1_" + Instant.now().toEpochMilli();
            String u2 = "boot_u2_" + Instant.now().toEpochMilli();
            HttpResponse<String> r1 = client.send(postRequest(port, "/online/api/auth/register",
                "{\"username\":\"" + u1 + "\",\"password\":\"Passw0rd123!\"}", ""), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> r2 = client.send(postRequest(port, "/online/api/auth/register",
                "{\"username\":\"" + u2 + "\",\"password\":\"Passw0rd123!\"}", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r1.statusCode());
            assertEquals(200, r2.statusCode());
            String c1 = r1.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];
            String c2 = r2.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];

            HttpResponse<String> created = client.send(postRequest(port, "/online/api/rooms",
                "{\"gameType\":\"GOMOKU\",\"initialTimeSeconds\":600,\"isPublic\":true}", c1), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, created.statusCode());
            String roomCode = extract(created.body(), "roomCode");
            String roomId = extract(created.body(), "roomId");
            assertTrue(!roomCode.isEmpty());
            assertTrue(!roomId.isEmpty());

            HttpResponse<String> joined = client.send(postRequest(port, "/online/api/rooms/" + roomId + "/join", "{}", c2), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, joined.statusCode());
            client.send(postRequest(port, "/online/api/rooms/" + roomId + "/ready", "{\"ready\":true}", c1), HttpResponse.BodyHandlers.ofString());
            client.send(postRequest(port, "/online/api/rooms/" + roomId + "/ready", "{\"ready\":true}", c2), HttpResponse.BodyHandlers.ofString());
            String gameId = extract(client.send(getRequest(port, "/online/api/rooms/" + roomId, c1), HttpResponse.BodyHandlers.ofString()).body(), "gameId");
            assertTrue(!gameId.isEmpty());
            client.send(postRequest(port, "/online/api/games/" + gameId + "/resign", "{}", c2), HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> bootstrap = client.send(getRequest(port, "/online/api/site/bootstrap", ""), HttpResponse.BodyHandlers.ofString());

            assertEquals(200, bootstrap.statusCode());
            assertTrue(bootstrap.body().contains("\"gameStats\""));
            assertTrue(bootstrap.body().contains("\"GOMOKU\""));
            assertTrue(bootstrap.body().contains("\"activePublicRooms\""));
            assertTrue(bootstrap.body().contains("\"startedAt\""));
            assertTrue(bootstrap.body().contains("\"updatedAt\""));
        } finally {
            server.stop();
        }
    }

    @Test
    void profileDashboardAggregatesNotificationsAchievementsAndPreferences() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();
            String username = "dash_" + Instant.now().toEpochMilli();

            HttpResponse<String> guest = client.send(getRequest(port, "/online/api/profile/dashboard", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, guest.statusCode());

            HttpResponse<String> register = client.send(postRequest(port, "/online/api/auth/register",
                "{\"username\":\"" + username + "\",\"password\":\"Passw0rd123!\"}", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, register.statusCode());
            String authCookie = register.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];

            client.send(postRequest(port, "/online/api/profile/preferences",
                "{\"soundEnabled\":false,\"boardTheme\":\"ink\",\"boardFlipped\":true}", authCookie), HttpResponse.BodyHandlers.ofString());
            client.send(postRequest(port, "/online/api/learn/tutorials/tutorial-001/complete", "{}", authCookie), HttpResponse.BodyHandlers.ofString());
            client.send(postRequest(port, "/online/api/learn/puzzles/puzzle-001/complete", "{}", authCookie), HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> created = client.send(postRequest(port, "/online/api/learn/practice-games",
                "{\"gameType\":\"XIANGQI\",\"difficulty\":\"EASY\",\"humanFirst\":true,\"preferredEngine\":\"BUILTIN\"}", authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, created.statusCode());
            String gameId = extract(created.body(), "gameId");
            assertTrue(!gameId.isEmpty());
            client.send(postRequest(port, "/online/api/learn/practice-games/" + gameId + "/resign", "{}", authCookie), HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> dashboard = client.send(getRequest(port, "/online/api/profile/dashboard", authCookie), HttpResponse.BodyHandlers.ofString());

            assertEquals(200, dashboard.statusCode());
            assertTrue(dashboard.body().contains("\"preferences\""));
            assertTrue(dashboard.body().contains("\"learnProgress\""));
            assertTrue(dashboard.body().contains("\"achievements\""));
            assertTrue(dashboard.body().contains("\"notifications\""));
            assertTrue(dashboard.body().contains("\"path\":\"me/settings\""));
            assertTrue(dashboard.body().contains("\"path\":\"me/study\""));
            assertTrue(dashboard.body().contains("\"path\":\"analysis/" + gameId + "\""));
            assertTrue(dashboard.body().contains("\"boardTheme\":\"ink\""));
            assertTrue(dashboard.body().contains("\"earned\":true"));
        } finally {
            server.stop();
        }
    }

    @Test
    void createRoomRequiresGameTypeWithReadableError() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();
            String username = "room_" + Instant.now().toEpochMilli();
            String registerBody = "{\"username\":\"" + username + "\",\"password\":\"Passw0rd123!\"}";
            HttpResponse<String> register = client.send(postRequest(port, "/online/api/auth/register", registerBody, ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, register.statusCode());
            String authCookie = register.headers().firstValue("Set-Cookie").orElse("").split(";", 2)[0];

            HttpResponse<String> missing = client.send(postRequest(port, "/online/api/rooms", "{}", authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, missing.statusCode());
            assertTrue(missing.body().contains("gameType is required"));
            assertTrue(!missing.body().contains("No enum constant"));

            HttpResponse<String> unsupported = client.send(postRequest(port, "/online/api/rooms",
                "{\"gameType\":\"BAD_TYPE\",\"isPublic\":false}", authCookie), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, unsupported.statusCode());
            assertTrue(unsupported.body().contains("unsupported gameType: BAD_TYPE"));
        } finally {
            server.stop();
        }
    }

    @Test
    void servesAudioAssetsWithoutCorruptingBinaryBytes() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<byte[]> audio = client.send(request(port, "/assets/audio/move.wav"), HttpResponse.BodyHandlers.ofByteArray());
            byte[] expected;
            try (InputStream input = PublicSiteServerTest.class.getResourceAsStream("/audio/move.wav")) {
                assertNotNull(input);
                expected = input.readAllBytes();
            }

            assertEquals(200, audio.statusCode());
            assertEquals("audio/wav", audio.headers().firstValue("Content-Type").orElse(""));
            assertArrayEquals(expected, audio.body());
        } finally {
            server.stop();
        }
    }

    private HttpRequest request(int port, String path) {
        return getRequest(port, path, "");
    }

    private HttpRequest headRequest(int port, String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build();
    }

    private HttpRequest getRequest(int port, String path, String cookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (cookie != null && !cookie.isEmpty()) {
            builder.header("Cookie", cookie);
        }
        return builder.build();
    }

    private HttpRequest postRequest(int port, String path, String body, String cookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        if (cookie != null && !cookie.isEmpty()) {
            builder.header("Cookie", cookie);
        }
        return builder.build();
    }

    private String extract(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            return "";
        }
        int from = start + key.length();
        int end = json.indexOf('"', from);
        if (end < 0) {
            return "";
        }
        return json.substring(from, end);
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private OnlineStore newStore() throws Exception {
        OnlineStore store = newUninitializedStore();
        store.initSchema();
        return store;
    }

    private OnlineStore newUninitializedStore() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return new OnlineStore(dataSource);
    }
}
