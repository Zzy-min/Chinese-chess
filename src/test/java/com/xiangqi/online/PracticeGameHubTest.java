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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            "PIKAFISH",
            ""
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
            "RAPFI",
            ""
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
    void gomokuUsesFiveLevelKeysAndDefaultsUnknownValuesToEasy() throws Exception {
        PracticeGameHub hub = new PracticeGameHub(newStore());
        AuthUser user = new AuthUser("u-levels", "level-user");

        Map<String, Object> novice = hub.createGame(user, new CreatePracticeGameRequest(
            GameType.GOMOKU, "NOVICE", true, "RAPFI", ""));
        Map<String, Object> master = hub.createGame(user, new CreatePracticeGameRequest(
            GameType.GOMOKU, "MASTER", true, "RAPFI", ""));
        Map<String, Object> fallback = hub.createGame(user, new CreatePracticeGameRequest(
            GameType.GOMOKU, "unknown", true, "RAPFI", ""));

        assertEquals("NOVICE", novice.get("difficulty"));
        assertEquals("builtin", asMap(novice.get("ai")).get("engineId"));
        assertEquals(Boolean.FALSE, asMap(novice.get("ai")).get("engineFallback"));
        assertEquals("MASTER", master.get("difficulty"));
        assertEquals("EASY", fallback.get("difficulty"));
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
            "BUILTIN",
            ""
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

    @Test
    void undoRewindsLatestHumanRoundInPracticeGame() throws Exception {
        OnlineStore store = newStore();
        PracticeGameHub hub = new PracticeGameHub(store);
        AuthUser user = new AuthUser("u-practice", "practice-user");

        Map<String, Object> created = hub.createGame(user, new CreatePracticeGameRequest(
            GameType.XIANGQI,
            "EASY",
            true,
            "BUILTIN",
            ""
        ));

        Map<String, Object> moved = hub.applyMove(
            asString(created.get("gameId")),
            user,
            Map.of("fromRow", 6, "fromCol", 0, "toRow", 5, "toCol", 0)
        );
        assertEquals(Boolean.TRUE, moved.get("aiPending"));

        String gameId = asString(moved.get("gameId"));
        Map<String, Object> afterAi = waitUntil(() -> hub.gameSnapshotById(gameId, user), snapshot ->
            Boolean.FALSE.equals(snapshot.get("aiPending")) && Integer.valueOf(2).equals(snapshot.get("moveCount"))
        );
        assertEquals(2, afterAi.get("moveCount"));

        Map<String, Object> undone = hub.undo(gameId, user);
        assertEquals("PLAYING", undone.get("status"));
        assertEquals(Boolean.FALSE, undone.get("aiPending"));
        assertEquals(0, undone.get("moveCount"));
        assertEquals("RED", undone.get("currentTurn"));
        assertTrue(((List<?>) undone.get("moves")).isEmpty());
    }

    @Test
    void undoRejectsWhenAiIsPending() throws Exception {
        OnlineStore store = newStore();
        PracticeGameHub hub = new PracticeGameHub(store);
        AuthUser user = new AuthUser("u-practice", "practice-user");

        Map<String, Object> created = hub.createGame(user, new CreatePracticeGameRequest(
            GameType.XIANGQI,
            "EASY",
            true,
            "BUILTIN",
            ""
        ));

        Map<String, Object> moved = hub.applyMove(
            asString(created.get("gameId")),
            user,
            Map.of("fromRow", 6, "fromCol", 0, "toRow", 5, "toCol", 0)
        );
        assertEquals(Boolean.TRUE, moved.get("aiPending"));

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> hub.undo(asString(created.get("gameId")), user)
        );
        assertTrue(error.getMessage().contains("AI is thinking"));
    }

    @Test
    void createsXiangqiPracticeGameFromInitialFenAndKeepsReplayStartBoard() throws Exception {
        OnlineStore store = newStore();
        PracticeGameHub hub = new PracticeGameHub(store);
        AuthUser user = new AuthUser("u-practice", "practice-user");
        String fen = "4k4/9/9/9/9/4R4/9/9/9/4K4 b - - 0 1";

        Map<String, Object> game = hub.createGame(user, new CreatePracticeGameRequest(
            GameType.XIANGQI,
            "MEDIUM",
            false,
            "BUILTIN",
            fen
        ));

        assertEquals("XIANGQI", game.get("gameType"));
        assertEquals("BLACK", game.get("currentTurn"));
        assertEquals("BLACK", game.get("viewerSide"));
        assertEquals(fen, game.get("initialFen"));

        List<List<String>> board = asBoard(game.get("board"));
        assertEquals("将", board.get(0).get(4));
        assertEquals("車", board.get(5).get(4));
        assertEquals("帅", board.get(9).get(4));

        List<List<List<String>>> historyBoards = asHistoryBoards(game.get("historyBoards"));
        assertEquals(1, historyBoards.size());
        assertEquals("車", historyBoards.get(0).get(5).get(4));
    }

    @Test
    void rejectsInvalidInitialFenForXiangqiPracticeGame() throws Exception {
        OnlineStore store = newStore();
        PracticeGameHub hub = new PracticeGameHub(store);
        AuthUser user = new AuthUser("u-practice", "practice-user");

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> hub.createGame(user, new CreatePracticeGameRequest(
                GameType.XIANGQI,
                "MEDIUM",
                true,
                "BUILTIN",
                "invalid-fen"
            ))
        );

        assertTrue(error.getMessage().contains("invalid initial FEN"));
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

    @SuppressWarnings("unchecked")
    private List<List<String>> asBoard(Object value) {
        if (value instanceof String[][]) {
            String[][] board = (String[][]) value;
            List<List<String>> rows = new java.util.ArrayList<List<String>>();
            for (String[] row : board) {
                rows.add(new java.util.ArrayList<String>(java.util.Arrays.asList(row)));
            }
            return rows;
        }
        return (List<List<String>>) value;
    }

    @SuppressWarnings("unchecked")
    private List<List<List<String>>> asHistoryBoards(Object value) {
        return (List<List<List<String>>>) value;
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
