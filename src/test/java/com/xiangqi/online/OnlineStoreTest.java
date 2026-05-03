package com.xiangqi.online;

import com.xiangqi.online.auth.AuthUser;
import com.xiangqi.online.auth.AuthService;
import com.xiangqi.online.auth.PasswordHasher;
import com.xiangqi.online.game.GameType;
import com.xiangqi.online.practice.CreatePracticeGameRequest;
import com.xiangqi.online.practice.PracticeGameHub;
import com.xiangqi.online.room.CreateRoomRequest;
import com.xiangqi.online.server.OnlineRoomHub;
import com.xiangqi.online.server.OnlineStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineStoreTest {

    @Test
    void analysisContainsReplayBoardsAndProfileSummaryIsUserScoped() throws Exception {
        OnlineStore store = newStore();
        OnlineRoomHub hub = new OnlineRoomHub(store);
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");
        AuthUser otherA = new AuthUser("u-other-a", "other-a");
        AuthUser otherB = new AuthUser("u-other-b", "other-b");

        String firstGameId = startXiangqiGame(hub, host, guest);
        hub.applyMove(firstGameId, host, Map.of("fromRow", 6, "fromCol", 0, "toRow", 5, "toCol", 0));
        hub.offerDraw(firstGameId, host);
        hub.respondDraw(firstGameId, guest, true);

        String secondGameId = startGomokuGame(hub, otherA, otherB);
        hub.resign(secondGameId, otherA);

        Map<String, Object> analysis = store.loadGameAnalysis(firstGameId);
        Map<String, Object> summary = store.profileSummary(host.id());
        List<Map<String, Object>> recent = store.recentGamesForUser(host.id(), 10);

        assertTrue(analysis.containsKey("initialBoard"));
        assertEquals(2, ((List<?>) analysis.get("historyBoards")).size());
        assertEquals(1, summary.get("totalGames"));
        assertEquals(1, summary.get("draws"));
        assertEquals(1, recent.size());
        assertEquals(firstGameId, recent.get(0).get("gameId"));
    }

    @Test
    void practiceGamesAreTaggedInRecentGamesAndRemainReplayable() throws Exception {
        OnlineStore store = newStore();
        PracticeGameHub hub = new PracticeGameHub(store);
        AuthUser user = new AuthUser("u-practice", "practice-user");

        Map<String, Object> created = hub.createGame(user, new CreatePracticeGameRequest(
            GameType.GOMOKU,
            "MEDIUM",
            false,
            "RAPFI",
            ""
        ));

        String gameId = asString(created.get("gameId"));
        waitUntil(() -> hub.gameSnapshotById(gameId, user), snapshot ->
            Boolean.FALSE.equals(snapshot.get("aiPending")) && ((Number) snapshot.get("moveCount")).intValue() >= 1
        );
        Map<String, Object> resigned = hub.resign(gameId, user);
        List<Map<String, Object>> recent = store.recentGamesForUser(user.id(), 10);
        Map<String, Object> analysis = store.loadGameAnalysis(gameId);

        assertEquals("FINISHED", resigned.get("status"));
        assertEquals(1, recent.size());
        assertEquals(Boolean.TRUE, recent.get(0).get("isTraining"));
        assertEquals("AI", recent.get(0).get("opponentType"));
        assertEquals("builtin", recent.get(0).get("aiEngine"));
        assertEquals("MEDIUM", recent.get(0).get("difficulty"));
        assertEquals(Boolean.TRUE, analysis.get("isTraining"));
        assertEquals("AI", analysis.get("opponentType"));
        assertEquals("builtin", analysis.get("aiEngine"));
        assertTrue(((List<?>) analysis.get("historyBoards")).size() >= 2);
    }

    @Test
    void communityLeaderboardUsesStableSortingForWinAndActivityBoards() throws Exception {
        OnlineStore store = newStore();
        AuthService auth = new AuthService(store.users(), store.sessions(), PasswordHasher.bcrypt(), Clock.systemUTC());
        OnlineRoomHub hub = new OnlineRoomHub(store);
        AuthUser alice = auth.register("alice", "Passw0rd123!").user();
        AuthUser bob = auth.register("bob", "Passw0rd123!").user();
        AuthUser carol = auth.register("carol", "Passw0rd123!").user();

        String g1 = startXiangqiGame(hub, alice, bob);
        hub.resign(g1, bob);
        String g2 = startGomokuGame(hub, alice, carol);
        hub.resign(g2, carol);
        String g3 = startXiangqiGame(hub, bob, carol);
        hub.resign(g3, carol);

        Map<String, Object> leaderboard = store.communityLeaderboard(30, 10);
        List<Map<String, Object>> winBoard = asMapList(leaderboard.get("winBoard"));
        List<Map<String, Object>> activityBoard = asMapList(leaderboard.get("activityBoard"));

        assertEquals(Boolean.FALSE, leaderboard.get("fallbackToAllTime"));
        assertEquals("alice", asString(winBoard.get(0).get("username")));
        assertEquals("bob", asString(winBoard.get(1).get("username")));
        assertEquals(2, ((Number) winBoard.get(0).get("wins")).intValue());
        assertEquals(1, ((Number) winBoard.get(1).get("wins")).intValue());

        assertEquals("alice", asString(activityBoard.get(0).get("username")));
        assertEquals("bob", asString(activityBoard.get(1).get("username")));
        assertEquals("carol", asString(activityBoard.get(2).get("username")));
        assertEquals(2, ((Number) activityBoard.get(0).get("activityGames")).intValue());
    }

    @Test
    void learnContentLoadsSeedAndReturnsSafeListDefaults() throws Exception {
        OnlineStore store = newStore();

        Map<String, Object> content = store.learnContent();

        assertNotNull(content);
        assertTrue(content.containsKey("tutorials"));
        assertTrue(content.containsKey("puzzles"));
        assertTrue(content.containsKey("recommendedPractice"));
        assertTrue(content.get("tutorials") instanceof List);
        assertTrue(content.get("puzzles") instanceof List);
        assertTrue(content.get("recommendedPractice") instanceof List);
        assertTrue(((List<?>) content.get("tutorials")).size() >= 1);
        assertTrue(((List<?>) content.get("puzzles")).size() >= 1);
        assertTrue(((List<?>) content.get("recommendedPractice")).size() >= 1);
    }

    private String startXiangqiGame(OnlineRoomHub hub, AuthUser host, AuthUser guest) {
        Map<String, Object> room = hub.createRoom(host, new CreateRoomRequest(GameType.XIANGQI, 600, false));
        hub.joinRoom(asString(room.get("roomId")), guest);
        hub.setReady(asString(room.get("roomId")), host.id(), true);
        room = hub.setReady(asString(room.get("roomId")), guest.id(), true);
        return asString(room.get("gameId"));
    }

    private String startGomokuGame(OnlineRoomHub hub, AuthUser host, AuthUser guest) {
        Map<String, Object> room = hub.createRoom(host, new CreateRoomRequest(GameType.GOMOKU, 600, false));
        hub.joinRoom(asString(room.get("roomId")), guest);
        hub.setReady(asString(room.get("roomId")), host.id(), true);
        room = hub.setReady(asString(room.get("roomId")), guest.id(), true);
        return asString(room.get("gameId"));
    }

    private OnlineStore newStore() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        OnlineStore store = new OnlineStore(dataSource);
        store.initSchema();
        return store;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        return value instanceof List ? (List<Map<String, Object>>) value : List.of();
    }

    private Map<String, Object> waitUntil(Supplier<Map<String, Object>> supplier, java.util.function.Predicate<Map<String, Object>> predicate) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        Map<String, Object> latest = supplier.get();
        while (System.currentTimeMillis() < deadline) {
            if (predicate.test(latest)) {
                return latest;
            }
            Thread.sleep(50L);
            latest = supplier.get();
        }
        return latest;
    }
}
