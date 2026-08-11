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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineRoomHubTest {

    @Test
    void hostCanCloseWaitingRoomButGuestCannot() throws Exception {
        OnlineRoomHub hub = new OnlineRoomHub(newStore());
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");

        Map<String, Object> room = hub.createRoom(host, new CreateRoomRequest(GameType.XIANGQI, 600, true));
        String roomId = asString(room.get("roomId"));
        hub.joinRoom(roomId, guest);

        assertThrows(SecurityException.class, () -> hub.closeRoom(roomId, guest));

        Map<String, Object> closed = hub.closeRoom(roomId, host);

        assertEquals(true, closed.get("closed"));
        assertEquals(roomId, closed.get("roomId"));
        assertEquals(0, hub.activeRoomCount());
        assertThrows(IllegalArgumentException.class, () -> hub.roomSnapshotById(roomId));
    }

    @Test
    void playingRoomMustFinishBeforeHostCanCloseIt() throws Exception {
        OnlineRoomHub hub = new OnlineRoomHub(newStore());
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");

        Map<String, Object> room = hub.createRoom(host, new CreateRoomRequest(GameType.GOMOKU, 600, false));
        String roomId = asString(room.get("roomId"));
        hub.joinRoom(roomId, guest);
        hub.setReady(roomId, host.id(), true);
        hub.setReady(roomId, guest.id(), true);

        assertThrows(IllegalStateException.class, () -> hub.closeRoom(roomId, host));
    }

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
        assertEquals("BETWEEN_GAMES", hub.roomSnapshotById(asString(room.get("roomId"))).get("status"));
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

    @Test
    void eitherMemberCanLeaveAfterAnEpisodeAndDissolveTheSession() throws Exception {
        OnlineRoomHub hub = new OnlineRoomHub(newStore());
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");
        Map<String, Object> room = hub.createRoom(host, new CreateRoomRequest(GameType.GOMOKU, 600, false));
        String roomId = asString(room.get("roomId"));
        hub.joinRoom(roomId, guest);
        hub.setReady(roomId, host.id(), true);
        room = hub.setReady(roomId, guest.id(), true);
        hub.resign(asString(room.get("gameId")), host);

        Map<String, Object> left = hub.leaveRoom(roomId, guest);

        assertEquals(true, left.get("closed"));
        assertEquals(0, hub.activeRoomCount());
        assertThrows(IllegalArgumentException.class, () -> hub.roomSnapshotById(roomId));
    }

    @Test
    void roomCanPlayThreeEpisodesWithStableCodeAndAlternatingSeats() throws Exception {
        OnlineRoomHub hub = new OnlineRoomHub(newStore());
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");

        Map<String, Object> created = hub.createRoom(host, new CreateRoomRequest(GameType.GOMOKU, 600, false));
        String roomId = asString(created.get("roomId"));
        String roomCode = asString(created.get("roomCode"));
        hub.joinRoom(roomId, guest);
        hub.setReady(roomId, host.id(), true);
        Map<String, Object> roundOne = hub.setReady(roomId, guest.id(), true);
        String gameOne = asString(roundOne.get("gameId"));

        assertEquals(1, roundOne.get("roundIndex"));
        assertEquals(host.id(), asMap(roundOne.get("seatAssignment")).get("firstUserId"));
        hub.resign(gameOne, host);
        Map<String, Object> betweenOne = hub.roomSnapshotById(roomId);

        assertEquals("BETWEEN_GAMES", betweenOne.get("status"));
        assertEquals(gameOne, betweenOne.get("lastGameId"));
        assertEquals(1, asMap(betweenOne.get("seriesScore")).get("guest"));
        assertFalse((Boolean) betweenOne.get("hostReady"));
        assertFalse((Boolean) betweenOne.get("guestReady"));

        hub.rematch(roomId, host, "offer");
        Map<String, Object> roundTwo = hub.rematch(roomId, guest, "accept");
        String gameTwo = asString(roundTwo.get("gameId"));

        assertEquals(roomCode, roundTwo.get("roomCode"));
        assertEquals(2, roundTwo.get("roundIndex"));
        assertNotEquals(gameOne, gameTwo);
        assertEquals(guest.id(), asMap(roundTwo.get("seatAssignment")).get("firstUserId"));
        assertEquals(gameOne, hub.analysis(gameOne).get("gameId"));

        hub.resign(gameTwo, guest);
        hub.setReady(roomId, host.id(), true);
        Map<String, Object> roundThree = hub.setReady(roomId, guest.id(), true);

        assertEquals(3, roundThree.get("roundIndex"));
        assertNotEquals(gameTwo, roundThree.get("gameId"));
        assertEquals(host.id(), asMap(roundThree.get("seatAssignment")).get("firstUserId"));
        assertEquals(1, asMap(roundThree.get("seriesScore")).get("host"));
        assertEquals(1, asMap(roundThree.get("seriesScore")).get("guest"));
    }

    @Test
    void rematchOfferCanBeDeclinedCancelledOrExpired() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-21T00:00:00Z"));
        OnlineRoomHub hub = new OnlineRoomHub(newStore(), clock);
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");
        AuthUser outsider = new AuthUser("u-outsider", "outsider");

        Map<String, Object> room = hub.createRoom(host, new CreateRoomRequest(GameType.XIANGQI, 600, false));
        String roomId = asString(room.get("roomId"));
        hub.joinRoom(roomId, guest);
        hub.setReady(roomId, host.id(), true);
        room = hub.setReady(roomId, guest.id(), true);
        hub.resign(asString(room.get("gameId")), host);

        assertThrows(IllegalArgumentException.class, () -> hub.rematch(roomId, outsider, "offer"));
        Map<String, Object> offered = hub.rematch(roomId, host, "offer");
        assertEquals("OFFERED", asMap(offered.get("rematch")).get("state"));
        assertThrows(IllegalArgumentException.class, () -> hub.rematch(roomId, host, "accept"));
        assertNull(hub.rematch(roomId, guest, "decline").get("rematch"));

        hub.rematch(roomId, guest, "offer");
        assertNull(hub.rematch(roomId, guest, "cancel").get("rematch"));

        hub.rematch(roomId, host, "offer");
        clock.plusSeconds(61);
        IllegalArgumentException expired = assertThrows(IllegalArgumentException.class,
            () -> hub.rematch(roomId, guest, "accept"));
        assertEquals("rematch offer expired", expired.getMessage());
        assertNull(hub.roomSnapshotById(roomId).get("rematch"));
        assertTrue((Boolean) hub.roomSnapshotById(roomId).get("canStartNext"));
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
