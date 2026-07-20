package com.xiangqi.online;

import com.xiangqi.online.auth.AuthUser;
import com.xiangqi.online.game.GameType;
import com.xiangqi.online.room.CreateRoomRequest;
import com.xiangqi.online.server.OnlineRoomHub;
import com.xiangqi.online.server.OnlineStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OnlineRoomHubTest {

    @Test
    void drawOfferCanBeRejectedOrAccepted() throws Exception {
        OnlineStore store = newStore();
        OnlineRoomHub hub = new OnlineRoomHub(store);
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");

        Map<String, Object> room = hub.createRoom(host, new CreateRoomRequest(GameType.XIANGQI, 600, false));
        hub.joinRoom(asString(room.get("roomId")), guest);
        hub.setReady(asString(room.get("roomId")), host.id(), true);
        room = hub.setReady(asString(room.get("roomId")), guest.id(), true);
        String gameId = asString(room.get("gameId"));

        Map<String, Object> offered = hub.offerDraw(gameId, host);
        Map<String, Object> rejected = hub.respondDraw(gameId, guest, false);
        Map<String, Object> reoffered = hub.offerDraw(gameId, host);
        Map<String, Object> accepted = hub.respondDraw(gameId, guest, true);

        assertEquals("u-host", asMap(offered.get("drawOffer")).get("userId"));
        assertNull(rejected.get("drawOffer"));
        assertEquals("u-host", asMap(reoffered.get("drawOffer")).get("userId"));
        assertEquals("FINISHED", accepted.get("status"));
        assertEquals("draw agreed", accepted.get("resultText"));
        assertEquals("FINISHED", hub.roomSnapshotById(asString(room.get("roomId"))).get("status"));
    }

    @Test
    void resignFinishesGameAndExposesActivityEntry() throws Exception {
        OnlineStore store = newStore();
        OnlineRoomHub hub = new OnlineRoomHub(store);
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");

        Map<String, Object> room = hub.createRoom(host, new CreateRoomRequest(GameType.GOMOKU, 600, false));
        hub.joinRoom(asString(room.get("roomId")), guest);
        hub.setReady(asString(room.get("roomId")), host.id(), true);
        room = hub.setReady(asString(room.get("roomId")), guest.id(), true);
        String gameId = asString(room.get("gameId"));

        Map<String, Object> activity = hub.activityForUser(host.id());
        Map<String, Object> game = hub.resign(gameId, host);

        assertEquals(gameId, asMap(activity.get("game")).get("gameId"));
        assertEquals(asString(room.get("roomId")), asMap(activity.get("room")).get("roomId"));
        assertEquals("FINISHED", game.get("status"));
        assertEquals("WHITE", game.get("winnerSide"));
        assertEquals("host resigned", game.get("resultText"));
        assertNotNull(store.loadGameAnalysis(gameId).get("terminationReason"));
    }

    @Test
    void illegalMoveDoesNotConsumeClockButLegalMoveDoes() throws Exception {
        OnlineStore store = newStore();
        MutableClock clock = new MutableClock(Instant.parse("2026-03-21T00:00:00Z"));
        OnlineRoomHub hub = new OnlineRoomHub(store, clock);
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");

        Map<String, Object> room = hub.createRoom(host, new CreateRoomRequest(GameType.XIANGQI, 10, false));
        hub.joinRoom(asString(room.get("roomId")), guest);
        hub.setReady(asString(room.get("roomId")), host.id(), true);
        room = hub.setReady(asString(room.get("roomId")), guest.id(), true);
        String gameId = asString(room.get("gameId"));

        clock.plusSeconds(4);
        IllegalArgumentException illegal = assertThrows(IllegalArgumentException.class,
            () -> hub.applyMove(gameId, host, Map.of("fromRow", 0, "fromCol", 0, "toRow", 0, "toCol", 0)));
        Map<String, Object> afterIllegal = hub.gameSnapshotById(gameId, host);

        // Illegal move must not refresh lastTickAt. Remaining stays 10 until a legal move
        // applies elapsed time from the original lastTickAt (4s + 3s = 7s -> remaining 3).
        clock.plusSeconds(3);
        Map<String, Object> afterLegal = hub.applyMove(gameId, host, Map.of("fromRow", 6, "fromCol", 0, "toRow", 5, "toCol", 0));

        assertEquals("illegal move", illegal.getMessage());
        assertEquals(10, afterIllegal.get("firstRemainingSeconds"));
        assertEquals(3, afterLegal.get("firstRemainingSeconds"));
        assertEquals("RUNNING", afterLegal.get("clockState"));
    }

    @Test
    void outsiderCannotApplyMoveOrRefreshClock() throws Exception {
        OnlineStore store = newStore();
        MutableClock clock = new MutableClock(Instant.parse("2026-03-21T00:00:00Z"));
        OnlineRoomHub hub = new OnlineRoomHub(store, clock);
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");
        AuthUser outsider = new AuthUser("u-outsider", "outsider");

        Map<String, Object> room = hub.createRoom(host, new CreateRoomRequest(GameType.XIANGQI, 10, false));
        hub.joinRoom(asString(room.get("roomId")), guest);
        hub.setReady(asString(room.get("roomId")), host.id(), true);
        room = hub.setReady(asString(room.get("roomId")), guest.id(), true);
        String gameId = asString(room.get("gameId"));

        clock.plusSeconds(2);
        IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
            () -> hub.applyMove(gameId, outsider, Map.of("fromRow", 0, "fromCol", 0, "toRow", 0, "toCol", 0)));
        Map<String, Object> afterDenied = hub.gameSnapshotById(gameId, host);

        clock.plusSeconds(3);
        Map<String, Object> afterLegal = hub.applyMove(gameId, host, Map.of("fromRow", 6, "fromCol", 0, "toRow", 5, "toCol", 0));

        assertEquals("user is not in game", denied.getMessage());
        assertEquals(10, afterDenied.get("firstRemainingSeconds"));
        // Outsider must not refresh lastTickAt either: 2s + 3s = 5s elapsed.
        assertEquals(5, afterLegal.get("firstRemainingSeconds"));
    }

    @Test
    void moveAfterFlagFallsEndsGameByTimeout() throws Exception {
        OnlineStore store = newStore();
        MutableClock clock = new MutableClock(Instant.parse("2026-03-21T00:00:00Z"));
        OnlineRoomHub hub = new OnlineRoomHub(store, clock);
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");

        Map<String, Object> room = hub.createRoom(host, new CreateRoomRequest(GameType.GOMOKU, 3, false));
        hub.joinRoom(asString(room.get("roomId")), guest);
        hub.setReady(asString(room.get("roomId")), host.id(), true);
        room = hub.setReady(asString(room.get("roomId")), guest.id(), true);
        String gameId = asString(room.get("gameId"));

        clock.plusSeconds(5);
        Map<String, Object> timedOut = hub.applyMove(gameId, host, Map.of("row", 7, "col", 7));

        assertEquals("FINISHED", timedOut.get("status"));
        assertEquals("WHITE", timedOut.get("winnerSide"));
        assertEquals("TIMEOUT", timedOut.get("terminationReason"));
        assertEquals(0, timedOut.get("firstRemainingSeconds"));
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

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void plusSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }
    }
}
