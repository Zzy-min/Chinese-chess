package com.xiangqi.online;

import com.xiangqi.online.auth.AuthUser;
import com.xiangqi.online.game.GameType;
import com.xiangqi.online.room.CreateRoomRequest;
import com.xiangqi.online.server.FileRoomPersistence;
import com.xiangqi.online.server.OnlineRoomHub;
import com.xiangqi.online.server.OnlineStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运行态持久化：序列化 -> 清空内存(重构 hub) -> 加载恢复，验证房间/对局快照一致。
 * 使用真实文件落盘，确保「重启不丢盘面」这一最小闭环可用。
 */
class RoomPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void serializeThenReloadRecoversOngoingRoomAndGame() throws Exception {
        String stateFile = tempDir.resolve("room-state.json").toString();
        OnlineStore store = newStore();
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");

        // 第一段：真实文件持久化，创建一对局并走一手，触发落盘。
        FileRoomPersistence persistence = new FileRoomPersistence(stateFile);
        OnlineRoomHub hub = new OnlineRoomHub(store, java.time.Clock.systemUTC(), persistence);

        Map<String, Object> created = hub.createRoom(host, new CreateRoomRequest(GameType.XIANGQI, 600, false));
        String roomId = asString(created.get("roomId"));
        String roomCode = asString(created.get("roomCode"));
        assertFalse(roomId.isEmpty());
        hub.joinRoom(roomId, guest);
        hub.setReady(roomId, host.id(), true);
        Map<String, Object> afterReady = hub.setReady(roomId, guest.id(), true);
        String gameId = asString(afterReady.get("gameId"));
        assertFalse(gameId.isEmpty());

        // 走一手（host/红先行），落盘 game_moves 供恢复回放。
        Map<String, Object> afterMove = hub.applyMove(gameId, host,
            Map.of("fromRow", 6, "fromCol", 0, "toRow", 5, "toCol", 0));
        assertEquals(1, afterMove.get("moveCount"));
        assertEquals("RUNNING", afterMove.get("clockState"));

        // 记录重启前的权威状态。
        Map<String, Object> beforeRoom = hub.roomSnapshotById(roomId);
        Map<String, Object> beforeGame = hub.gameSnapshotById(gameId, null);
        assertNotNull(beforeGame.get("moves"));

        // 第二段：模拟进程重启——新建同 store(DB 不变) + 同持久化文件的 hub 自动恢复。
        OnlineRoomHub restarted = new OnlineRoomHub(store, java.time.Clock.systemUTC(), persistence);

        assertTrue(restarted.activeRoomCount() >= 1, "restart should restore participating room");
        Map<String, Object> afterRoom = restarted.roomSnapshotById(roomId);
        Map<String, Object> afterGame = restarted.gameSnapshotById(gameId, host);

        // 房间关键字段一致
        assertEquals(beforeRoom.get("status"), afterRoom.get("status"));
        assertEquals(roomId, afterRoom.get("roomId"));
        assertEquals(roomCode, afterRoom.get("roomCode"));
        assertEquals(gameId, afterRoom.get("gameId"));
        assertEquals(beforeRoom.get("hostReady"), afterRoom.get("hostReady"));
        assertNotNull(afterRoom.get("guest"));
        assertEquals(host.id(), asMap(afterRoom.get("host")).get("id"));
        assertEquals(guest.id(), asMap(afterRoom.get("guest")).get("id"));

        // 对局状态、盘面与着法一致
        assertEquals(gameId, afterGame.get("gameId"));
        assertEquals("PLAYING", afterGame.get("status"));
        assertEquals("RUNNING", afterGame.get("clockState"));
        assertEquals(beforeGame.get("currentTurn"), afterGame.get("currentTurn"));
        assertEquals(beforeGame.get("winnerSide"), afterGame.get("winnerSide"));
        assertEquals(beforeGame.get("resultText"), afterGame.get("resultText"));
        assertEquals(beforeGame.get("stateId"), afterGame.get("stateId"));
        assertEquals(beforeGame.get("moveCount"), afterGame.get("moveCount"));
        assertBoardEquals(beforeGame.get("board"), afterGame.get("board"));
        assertEquals(beforeGame.get("firstRemainingSeconds"), afterGame.get("firstRemainingSeconds"));
        assertEquals(beforeGame.get("secondRemainingSeconds"), afterGame.get("secondRemainingSeconds"));
    }

    @Test
    void persistAndReloadWaitingRoomSurvivesRestart() throws Exception {
        String stateFile = tempDir.resolve("waiting-room.json").toString();
        OnlineStore store = newStore();
        FileRoomPersistence persistence = new FileRoomPersistence(stateFile);
        OnlineRoomHub hub = new OnlineRoomHub(store, java.time.Clock.systemUTC(), persistence);

        AuthUser host = new AuthUser("u-host2", "host2");
        Map<String, Object> created = hub.createRoom(host, new CreateRoomRequest(GameType.XIANGQI, 600, true));
        String roomId = asString(created.get("roomId"));

        OnlineRoomHub restarted = new OnlineRoomHub(store, java.time.Clock.systemUTC(), persistence);
        Map<String, Object> restored = restarted.roomSnapshotById(roomId);

        assertEquals("WAITING", restored.get("status"));
        assertEquals(true, restored.get("isPublic"));
        assertEquals(roomId, restored.get("roomId"));
    }

    @Test
    void finishedEpisodeBetweenGamesSurvivesReload() throws Exception {
        String stateFile = tempDir.resolve("between.json").toString();
        OnlineStore store = newStore();
        FileRoomPersistence persistence = new FileRoomPersistence(stateFile);
        OnlineRoomHub hub = new OnlineRoomHub(store, java.time.Clock.systemUTC(), persistence);

        AuthUser host = new AuthUser("u-host3", "host3");
        AuthUser guest = new AuthUser("u-guest3", "guest3");
        Map<String, Object> room = hub.createRoom(host, new CreateRoomRequest(GameType.GOMOKU, 600, false));
        String roomId = asString(room.get("roomId"));
        hub.joinRoom(roomId, guest);
        hub.setReady(roomId, host.id(), true);
        room = hub.setReady(roomId, guest.id(), true);
        hub.resign(asString(room.get("gameId")), host);

        OnlineRoomHub restarted = new OnlineRoomHub(store, java.time.Clock.systemUTC(), persistence);
        Map<String, Object> between = restarted.roomSnapshotById(roomId);

        assertEquals("BETWEEN_GAMES", between.get("status"));
        assertEquals(1, asMap(between.get("seriesScore")).get("guest"));
        // 复盘出口：对局快照仍可读。
        Map<String, Object> finished = restarted.gameSnapshotById(asString(room.get("gameId")), host);
        assertEquals("FINISHED", finished.get("status"));
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

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void assertBoardEquals(Object a, Object b) {
        assertNotNull(a);
        assertNotNull(b);
        String jsonA = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(a).toString();
        String jsonB = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(b).toString();
        assertEquals(jsonA, jsonB);
    }
}