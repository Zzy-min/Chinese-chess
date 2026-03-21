package com.xiangqi.web;

import com.xiangqi.online.auth.AuthUser;
import com.xiangqi.online.game.GameType;
import com.xiangqi.online.practice.PracticeGameHub;
import com.xiangqi.online.server.OnlineStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyHomeSessionHubTest {

    @Test
    void createsLegacyCompatibleAiGameAndSupportsClickReviewFlow() throws Exception {
        OnlineStore store = newStore();
        PracticeGameHub practiceHub = new PracticeGameHub(store);
        LegacyHomeSessionHub hub = new LegacyHomeSessionHub(practiceHub);
        AuthUser user = new AuthUser("u-home", "home-user");

        Map<String, Object> initial = hub.state("sid-home", user);
        assertFalse(asBoolean(initial.get("started")));
        assertEquals("PVC", initial.get("mode"));

        Map<String, Object> created = hub.newGame("sid-home", user, GameType.XIANGQI, "MEDIUM", true, "BUILTIN");

        assertTrue(asBoolean(created.get("started")));
        assertEquals("PVC", created.get("mode"));
        assertEquals("RED", created.get("pvcHumanColor"));
        assertEquals("XIANGQI", created.get("gameType"));
        assertEquals(-1, created.get("selectedRow"));
        assertEquals(-1, created.get("selectedCol"));
        assertTrue(cell(created, 6, 0).containsKey("color"));

        Map<String, Object> selected = hub.click("sid-home", user, 6, 0);
        assertEquals(6, selected.get("selectedRow"));
        assertEquals(0, selected.get("selectedCol"));

        Map<String, Object> afterMove = hub.click("sid-home", user, 5, 0);
        assertEquals(-1, afterMove.get("selectedRow"));
        assertEquals(-1, afterMove.get("selectedCol"));
        assertEquals("RED", afterMove.get("currentTurn"));
        assertEquals(2, recentMoves(afterMove).size());

        Map<String, Object> review = hub.reviewStart("sid-home", user);
        assertTrue(asBoolean(review.get("reviewMode")));
        assertEquals(0, review.get("reviewMoveIndex"));

        Map<String, Object> reviewNext = hub.reviewNext("sid-home", user);
        assertEquals(1, reviewNext.get("reviewMoveIndex"));

        Map<String, Object> reviewExit = hub.reviewExit("sid-home", user);
        assertFalse(asBoolean(reviewExit.get("reviewMode")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cell(Map<String, Object> state, int row, int col) {
        return (Map<String, Object>) ((List<?>) ((List<?>) state.get("board")).get(row)).get(col);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recentMoves(Map<String, Object> state) {
        return (List<Map<String, Object>>) state.get("recentMoves");
    }

    private boolean asBoolean(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private OnlineStore newStore() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        OnlineStore store = new OnlineStore(dataSource);
        store.initSchema();
        return store;
    }
}
