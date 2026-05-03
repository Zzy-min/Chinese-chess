package com.xiangqi.online;

import com.xiangqi.online.auth.AuthUser;
import com.xiangqi.online.game.GameType;
import com.xiangqi.online.practice.CreatePracticeGameRequest;
import com.xiangqi.online.practice.PracticeGameHub;
import com.xiangqi.online.server.OnlineStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeGameHubTest {

    @Test
    void createsXiangqiPracticeGameAndFallsBackToBuiltinEngine() throws Exception {
        OnlineStore store = newStore();
        PracticeGameHub hub = new PracticeGameHub(store);
        AuthUser user = new AuthUser("u-practice", "practice-user");

        Map<String, Object> game = hub.createGame(user, new CreatePracticeGameRequest(
            GameType.XIANGQI,
            "MEDIUM",
            true,
            "PIKAFISH"
        ));

        assertEquals("XIANGQI", game.get("gameType"));
        assertEquals("PLAYING", game.get("status"));
        assertEquals("RED", game.get("viewerSide"));
        assertEquals(Boolean.TRUE, game.get("isTraining"));
        assertEquals("AI", game.get("opponentType"));
        assertEquals("builtin", asMap(game.get("ai")).get("engineId"));
        assertTrue(asString(asMap(game.get("ai")).get("engineText")).contains("内置"));
        assertEquals(0, game.get("moveCount"));
    }

    @Test
    void createsGomokuPracticeGameWithAiOpeningWhenHumanChoosesSecond() throws Exception {
        OnlineStore store = newStore();
        PracticeGameHub hub = new PracticeGameHub(store);
        AuthUser user = new AuthUser("u-practice", "practice-user");

        Map<String, Object> game = hub.createGame(user, new CreatePracticeGameRequest(
            GameType.GOMOKU,
            "EASY",
            false,
            "RAPFI"
        ));

        assertEquals("GOMOKU", game.get("gameType"));
        assertEquals("WHITE", game.get("viewerSide"));
        assertEquals("BLACK", game.get("currentTurn"));
        assertEquals(Boolean.TRUE, game.get("aiPending"));
        assertEquals(0, game.get("moveCount"));

        String gameId = asString(game.get("gameId"));
        Map<String, Object> afterAi = waitUntil(() -> hub.gameSnapshotById(gameId, user), snapshot ->
            Boolean.FALSE.equals(snapshot.get("aiPending")) && Integer.valueOf(1).equals(snapshot.get("moveCount"))
        );
        assertEquals("WHITE", afterAi.get("currentTurn"));
        assertEquals(1, afterAi.get("moveCount"));
        assertEquals("BLACK", asMap(firstMove(afterAi).get("payload")).get("side"));
        assertEquals("builtin", asMap(afterAi.get("ai")).get("engineId"));
    }

    @Test
    void humanMoveTriggersAiReplyInPracticeGame() throws Exception {
        OnlineStore store = newStore();
        PracticeGameHub hub = new PracticeGameHub(store);
        AuthUser user = new AuthUser("u-practice", "practice-user");

        Map<String, Object> created = hub.createGame(user, new CreatePracticeGameRequest(
            GameType.XIANGQI,
            "EASY",
            true,
            "BUILTIN"
        ));

        Map<String, Object> updated = hub.applyMove(
            asString(created.get("gameId")),
            user,
            Map.of("fromRow", 6, "fromCol", 0, "toRow", 5, "toCol", 0)
        );

        assertEquals(1, updated.get("moveCount"));
        assertEquals("BLACK", updated.get("currentTurn"));
        assertEquals(Boolean.TRUE, updated.get("aiPending"));
        assertEquals("RED", asMap(lastMove(updated).get("payload")).get("side"));

        String gameId = asString(updated.get("gameId"));
        Map<String, Object> afterAi = waitUntil(() -> hub.gameSnapshotById(gameId, user), snapshot ->
            Boolean.FALSE.equals(snapshot.get("aiPending")) && Integer.valueOf(2).equals(snapshot.get("moveCount"))
        );
        assertEquals(2, afterAi.get("moveCount"));
        assertEquals("RED", afterAi.get("currentTurn"));
        assertEquals("BLACK", asMap(lastMove(afterAi).get("payload")).get("side"));
        assertEquals("builtin", asMap(afterAi.get("ai")).get("engineId"));
    }

    private OnlineStore newStore() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        OnlineStore store = new OnlineStore(dataSource);
        store.initSchema();
        return store;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstMove(Map<String, Object> game) {
        return ((List<Map<String, Object>>) game.get("moves")).get(0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> lastMove(Map<String, Object> game) {
        List<Map<String, Object>> moves = (List<Map<String, Object>>) game.get("moves");
        return moves.get(moves.size() - 1);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
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
