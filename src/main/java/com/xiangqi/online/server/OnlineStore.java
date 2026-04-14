package com.xiangqi.online.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiangqi.online.auth.AuthSessionRepository;
import com.xiangqi.online.auth.AuthUser;
import com.xiangqi.online.auth.UserRecord;
import com.xiangqi.online.auth.UserRepository;
import com.xiangqi.online.auth.UserSession;
import com.xiangqi.online.game.GomokuMatch;
import com.xiangqi.online.game.MatchPlayer;
import com.xiangqi.online.game.PlayerSide;
import com.xiangqi.online.game.XiangqiMatch;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class OnlineStore {
    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final JdbcUserRepository users;
    private final JdbcAuthSessionRepository sessions;

    public OnlineStore(DataSource dataSource) {
        this.dataSource = dataSource;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.users = new JdbcUserRepository(dataSource);
        this.sessions = new JdbcAuthSessionRepository(dataSource, users);
    }

    public static OnlineStore createDefault() throws SQLException, IOException {
        return new OnlineStore(createDefaultDataSource());
    }

    public void initSchema() throws SQLException, IOException {
        String sql = readSchemaSql();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String part : sql.split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        }
    }

    public UserRepository users() {
        return users;
    }

    public AuthSessionRepository sessions() {
        return sessions;
    }

    public Optional<AuthUser> findUserByToken(String token) {
        return sessions.findByToken(token).map(UserSession::user);
    }

    public void createGameRecord(String gameId, String roomId, Map<String, Object> snapshot) {
        String sql = "insert into games(id, room_id, game_type, is_training, opponent_type, ai_engine, difficulty, status, first_user_id, first_username, first_side, second_user_id, second_username, second_side, current_turn, winner_side, result_text, board_json, move_count, initial_time_seconds, first_remaining_seconds, second_remaining_seconds, termination_reason, created_at, started_at, finished_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            Map<String, Object> players = asMap(snapshot.get("players"));
            Map<String, Object> first = asMap(players.get("first"));
            Map<String, Object> second = asMap(players.get("second"));
            ps.setString(1, gameId);
            ps.setString(2, roomId == null ? "" : roomId);
            ps.setString(3, asString(snapshot.get("gameType")));
            ps.setBoolean(4, asBoolean(snapshot.get("isTraining")));
            ps.setString(5, asString(snapshot.get("opponentType")));
            ps.setString(6, asString(snapshot.get("aiEngine")));
            ps.setString(7, asString(snapshot.get("difficulty")));
            ps.setString(8, asString(snapshot.get("status")));
            ps.setString(9, asString(first.get("id")));
            ps.setString(10, asString(first.get("username")));
            ps.setString(11, asString(first.get("side")));
            ps.setString(12, asString(second.get("id")));
            ps.setString(13, asString(second.get("username")));
            ps.setString(14, asString(second.get("side")));
            ps.setString(15, asString(snapshot.get("currentTurn")));
            ps.setString(16, asString(snapshot.get("winnerSide")));
            ps.setString(17, asString(snapshot.get("resultText")));
            ps.setString(18, mapper.writeValueAsString(snapshot.get("board")));
            ps.setInt(19, asInt(snapshot.get("moveCount")));
            ps.setInt(20, asInt(snapshot.get("initialTimeSeconds")));
            ps.setInt(21, asInt(snapshot.get("firstRemainingSeconds")));
            ps.setInt(22, asInt(snapshot.get("secondRemainingSeconds")));
            ps.setString(23, asString(snapshot.get("terminationReason")));
            Instant now = Instant.now();
            ps.setTimestamp(24, Timestamp.from(now));
            ps.setTimestamp(25, Timestamp.from(now));
            ps.setTimestamp(26, null);
            ps.executeUpdate();
        } catch (SQLException | IOException ex) {
            throw new IllegalStateException("failed to create game record", ex);
        }
    }

    public void appendMove(String gameId, int moveIndex, String actorUserId, String side, String notation, Map<String, Object> payload, Map<String, Object> snapshot) {
        String moveSql = "insert into game_moves(id, game_id, move_index, actor_user_id, side, notation, payload_json, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)";
        String updateSql = "update games set status=?, current_turn=?, winner_side=?, result_text=?, board_json=?, move_count=?, first_remaining_seconds=?, second_remaining_seconds=?, termination_reason=?, finished_at=? where id=?";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement movePs = connection.prepareStatement(moveSql)) {
                movePs.setString(1, gameId + "-" + moveIndex);
                movePs.setString(2, gameId);
                movePs.setInt(3, moveIndex);
                movePs.setString(4, actorUserId);
                movePs.setString(5, side);
                movePs.setString(6, notation);
                movePs.setString(7, mapper.writeValueAsString(payload));
                movePs.setTimestamp(8, Timestamp.from(Instant.now()));
                movePs.executeUpdate();
            }
            try (PreparedStatement gamePs = connection.prepareStatement(updateSql)) {
                gamePs.setString(1, asString(snapshot.get("status")));
                gamePs.setString(2, asString(snapshot.get("currentTurn")));
                gamePs.setString(3, asString(snapshot.get("winnerSide")));
                gamePs.setString(4, asString(snapshot.get("resultText")));
                gamePs.setString(5, mapper.writeValueAsString(snapshot.get("board")));
                gamePs.setInt(6, asInt(snapshot.get("moveCount")));
                gamePs.setInt(7, asInt(snapshot.get("firstRemainingSeconds")));
                gamePs.setInt(8, asInt(snapshot.get("secondRemainingSeconds")));
                gamePs.setString(9, asString(snapshot.get("terminationReason")));
                if ("FINISHED".equalsIgnoreCase(asString(snapshot.get("status")))) {
                    gamePs.setTimestamp(10, Timestamp.from(Instant.now()));
                } else {
                    gamePs.setTimestamp(10, null);
                }
                gamePs.setString(11, gameId);
                gamePs.executeUpdate();
            }
        } catch (SQLException | IOException ex) {
            throw new IllegalStateException("failed to persist move", ex);
        }
    }

    public void updateGameRecord(Map<String, Object> snapshot) {
        String sql = "update games set status=?, current_turn=?, winner_side=?, result_text=?, board_json=?, move_count=?, first_remaining_seconds=?, second_remaining_seconds=?, termination_reason=?, finished_at=? where id=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, asString(snapshot.get("status")));
            ps.setString(2, asString(snapshot.get("currentTurn")));
            ps.setString(3, asString(snapshot.get("winnerSide")));
            ps.setString(4, asString(snapshot.get("resultText")));
            ps.setString(5, mapper.writeValueAsString(snapshot.get("board")));
            ps.setInt(6, asInt(snapshot.get("moveCount")));
            ps.setInt(7, asInt(snapshot.get("firstRemainingSeconds")));
            ps.setInt(8, asInt(snapshot.get("secondRemainingSeconds")));
            ps.setString(9, asString(snapshot.get("terminationReason")));
            if ("FINISHED".equalsIgnoreCase(asString(snapshot.get("status")))) {
                ps.setTimestamp(10, Timestamp.from(Instant.now()));
            } else {
                ps.setTimestamp(10, null);
            }
            ps.setString(11, asString(snapshot.get("gameId")));
            ps.executeUpdate();
        } catch (SQLException | IOException ex) {
            throw new IllegalStateException("failed to update game record", ex);
        }
    }

    public List<Map<String, Object>> recentGames(int limit) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        String sql = "select id, room_id, game_type, is_training, opponent_type, ai_engine, difficulty, status, first_username, first_side, second_username, second_side, winner_side, result_text, move_count, finished_at from games order by started_at desc limit ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("gameId", rs.getString("id"));
                    item.put("roomId", rs.getString("room_id"));
                    item.put("gameType", rs.getString("game_type"));
                    item.put("isTraining", rs.getBoolean("is_training"));
                    item.put("opponentType", rs.getString("opponent_type"));
                    item.put("aiEngine", rs.getString("ai_engine"));
                    item.put("difficulty", rs.getString("difficulty"));
                    item.put("status", rs.getString("status"));
                    item.put("firstUsername", rs.getString("first_username"));
                    item.put("firstSide", rs.getString("first_side"));
                    item.put("secondUsername", rs.getString("second_username"));
                    item.put("secondSide", rs.getString("second_side"));
                    item.put("winnerSide", rs.getString("winner_side"));
                    item.put("resultText", rs.getString("result_text"));
                    item.put("moveCount", rs.getInt("move_count"));
                    Timestamp finished = rs.getTimestamp("finished_at");
                    item.put("finishedAt", finished == null ? "" : finished.toInstant().toString());
                    items.add(item);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to list recent games", ex);
        }
        return items;
    }

    public Map<String, Object> profileSummary(String userId) {
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        String sql = "select count(*) as total, "
            + "sum(case when winner_side = first_side and first_user_id = ? then 1 when winner_side = second_side and second_user_id = ? then 1 else 0 end) as wins, "
            + "sum(case when termination_reason = 'DRAW_AGREED' then 1 else 0 end) as draws "
            + "from games where first_user_id = ? or second_user_id = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, userId);
            ps.setString(3, userId);
            ps.setString(4, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    int wins = rs.getInt("wins");
                    int draws = rs.getInt("draws");
                    summary.put("totalGames", total);
                    summary.put("wins", wins);
                    summary.put("draws", draws);
                    summary.put("losses", Math.max(0, total - wins - draws));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to load profile summary", ex);
        }
        return summary;
    }

    public List<Map<String, Object>> recentGamesForUser(String userId, int limit) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        String sql = "select id, room_id, game_type, is_training, opponent_type, ai_engine, difficulty, status, first_user_id, first_username, first_side, second_user_id, second_username, second_side, winner_side, result_text, termination_reason, move_count, finished_at "
            + "from games where first_user_id = ? or second_user_id = ? order by started_at desc limit ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, userId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    String firstUserId = rs.getString("first_user_id");
                    boolean isFirst = userId.equals(firstUserId);
                    item.put("gameId", rs.getString("id"));
                    item.put("roomId", rs.getString("room_id"));
                    item.put("gameType", rs.getString("game_type"));
                    item.put("isTraining", rs.getBoolean("is_training"));
                    item.put("opponentType", rs.getString("opponent_type"));
                    item.put("aiEngine", rs.getString("ai_engine"));
                    item.put("difficulty", rs.getString("difficulty"));
                    item.put("status", rs.getString("status"));
                    item.put("side", isFirst ? rs.getString("first_side") : rs.getString("second_side"));
                    item.put("opponentUsername", isFirst ? rs.getString("second_username") : rs.getString("first_username"));
                    item.put("resultText", rs.getString("result_text"));
                    item.put("terminationReason", rs.getString("termination_reason"));
                    item.put("moveCount", rs.getInt("move_count"));
                    Timestamp finished = rs.getTimestamp("finished_at");
                    item.put("finishedAt", finished == null ? "" : finished.toInstant().toString());
                    items.add(item);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to load recent user games", ex);
        }
        return items;
    }

    public Map<String, Object> loadGameAnalysis(String gameId) {
        String gameSql = "select id, room_id, game_type, is_training, opponent_type, ai_engine, difficulty, status, first_user_id, first_username, first_side, second_user_id, second_username, second_side, current_turn, winner_side, result_text, board_json, move_count, initial_time_seconds, first_remaining_seconds, second_remaining_seconds, termination_reason from games where id = ?";
        String moveSql = "select move_index, actor_user_id, side, notation, payload_json, created_at from game_moves where game_id = ? order by move_index asc";
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement gamePs = connection.prepareStatement(gameSql); PreparedStatement movePs = connection.prepareStatement(moveSql)) {
            gamePs.setString(1, gameId);
            try (ResultSet rs = gamePs.executeQuery()) {
                if (!rs.next()) {
                    return result;
                }
                result.put("gameId", rs.getString("id"));
                result.put("roomId", rs.getString("room_id"));
                result.put("gameType", rs.getString("game_type"));
                result.put("isTraining", rs.getBoolean("is_training"));
                result.put("opponentType", rs.getString("opponent_type"));
                result.put("aiEngine", rs.getString("ai_engine"));
                result.put("difficulty", rs.getString("difficulty"));
                result.put("status", rs.getString("status"));
                result.put("currentTurn", rs.getString("current_turn"));
                result.put("winnerSide", rs.getString("winner_side"));
                result.put("resultText", rs.getString("result_text"));
                result.put("board", mapper.readValue(rs.getString("board_json"), new TypeReference<List<List<String>>>() { }));
                result.put("moveCount", rs.getInt("move_count"));
                result.put("initialTimeSeconds", rs.getInt("initial_time_seconds"));
                result.put("firstRemainingSeconds", rs.getInt("first_remaining_seconds"));
                result.put("secondRemainingSeconds", rs.getInt("second_remaining_seconds"));
                result.put("terminationReason", rs.getString("termination_reason"));
                Map<String, Object> players = new LinkedHashMap<String, Object>();
                Map<String, Object> first = new LinkedHashMap<String, Object>();
                first.put("id", rs.getString("first_user_id"));
                first.put("username", rs.getString("first_username"));
                first.put("side", rs.getString("first_side"));
                Map<String, Object> second = new LinkedHashMap<String, Object>();
                second.put("id", rs.getString("second_user_id"));
                second.put("username", rs.getString("second_username"));
                second.put("side", rs.getString("second_side"));
                players.put("first", first);
                players.put("second", second);
                result.put("players", players);
            }
            movePs.setString(1, gameId);
            List<Map<String, Object>> moves = new ArrayList<Map<String, Object>>();
            try (ResultSet rs = movePs.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> move = new LinkedHashMap<String, Object>();
                    move.put("index", rs.getInt("move_index"));
                    move.put("actorUserId", rs.getString("actor_user_id"));
                    move.put("side", rs.getString("side"));
                    move.put("notation", rs.getString("notation"));
                    move.put("payload", mapper.readValue(rs.getString("payload_json"), new TypeReference<Map<String, Object>>() { }));
                    move.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                    moves.add(move);
                }
            }
            result.put("moves", moves);
            attachReplayData(result);
            return result;
        } catch (SQLException | IOException ex) {
            throw new IllegalStateException("failed to load game analysis", ex);
        }
    }

    public int countUsers() {
        return queryForInt("select count(*) from users");
    }

    public int countGames() {
        return queryForInt("select count(*) from games");
    }

    private int queryForInt(String sql) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to run count query", ex);
        }
    }

    public void recordPuzzleCompletion(String userId, String endgameId, int moveCount, int hintsUsed, String difficulty) {
        String id = userId + ":" + endgameId;
        String sql = "merge into puzzle_completions (id, user_id, endgame_id, difficulty, move_count, hints_used, solved_at) "
            + "key (user_id, endgame_id) values (?, ?, ?, ?, ?, ?, current_timestamp)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, userId);
            ps.setString(3, endgameId);
            ps.setString(4, difficulty);
            ps.setInt(5, moveCount);
            ps.setInt(6, hintsUsed);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Ignore duplicate key — already solved
        }
    }

    public Map<String, Object> getUserPuzzleStats(String userId) {
        Map<String, Object> stats = new LinkedHashMap<String, Object>();
        String totalSql = "select count(*) as total_solved from puzzle_completions where user_id = ?";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(totalSql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.put("totalSolved", rs.getInt("total_solved"));
                    }
                }
            }
            Map<String, Integer> byDifficulty = new LinkedHashMap<String, Integer>();
            String byDiffSql = "select difficulty, count(*) as cnt from puzzle_completions where user_id = ? group by difficulty";
            try (PreparedStatement ps = connection.prepareStatement(byDiffSql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        byDifficulty.put(rs.getString("difficulty"), rs.getInt("cnt"));
                    }
                }
            }
            stats.put("byDifficulty", byDifficulty);
        } catch (SQLException e) {
            stats.put("totalSolved", 0);
            stats.put("byDifficulty", new LinkedHashMap<String, Integer>());
        }
        return stats;
    }

    @SuppressWarnings("unchecked")
    public List<String> getSolvedEndgameIds(String userId) {
        List<String> ids = new ArrayList<String>();
        if (userId == null || userId.isEmpty()) return ids;
        String sql = "select endgame_id from puzzle_completions where user_id = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString("endgame_id"));
                }
            }
        } catch (SQLException e) {
            // Return empty
        }
        return ids;
    }

    private static String readSchemaSql() throws IOException {
        try (InputStream input = OnlineStore.class.getResourceAsStream("/online/schema.sql")) {
            if (input == null) {
                throw new IOException("missing online schema resource");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static DataSource createDefaultDataSource() {
        String rawUrl = getenv("XQ_DATABASE_URL", getenv("DATABASE_URL", ""));
        String username = getenv("XQ_DATABASE_USER", "");
        String password = getenv("XQ_DATABASE_PASSWORD", "");
        HikariConfig config = new HikariConfig();
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            config.setJdbcUrl("jdbc:h2:file:./data/online-site;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE");
            config.setUsername("sa");
            config.setPassword("");
            config.setDriverClassName("org.h2.Driver");
        } else {
            config.setJdbcUrl(toJdbcUrl(rawUrl));
            if (username != null && !username.trim().isEmpty()) {
                config.setUsername(username);
            }
            if (password != null && !password.trim().isEmpty()) {
                config.setPassword(password);
            }
            config.setDriverClassName("org.postgresql.Driver");
        }
        config.setMaximumPoolSize(6);
        config.setMinimumIdle(1);
        config.setPoolName("xq-online-db");
        return new HikariDataSource(config);
    }

    private static String toJdbcUrl(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("jdbc:")) {
            return trimmed;
        }
        if (trimmed.startsWith("postgres://")) {
            return "jdbc:postgresql://" + trimmed.substring("postgres://".length());
        }
        if (trimmed.startsWith("postgresql://")) {
            return "jdbc:" + trimmed;
        }
        return trimmed;
    }

    private static String getenv(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<String, Object>();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private void attachReplayData(Map<String, Object> analysis) {
        String gameType = asString(analysis.get("gameType"));
        List<Map<String, Object>> moves = castMoveList(analysis.get("moves"));
        List<List<List<String>>> historyBoards = "GOMOKU".equals(gameType)
            ? buildGomokuHistory(moves)
            : buildXiangqiHistory(moves);
        if (historyBoards.isEmpty()) {
            analysis.put("initialBoard", new ArrayList<List<String>>());
            analysis.put("historyBoards", new ArrayList<List<List<String>>>());
            return;
        }
        analysis.put("initialBoard", historyBoards.get(0));
        analysis.put("historyBoards", historyBoards);
    }

    private List<List<List<String>>> buildXiangqiHistory(List<Map<String, Object>> moves) {
        XiangqiMatch match = new XiangqiMatch(
            new MatchPlayer("replay-red", "replay-red", PlayerSide.RED),
            new MatchPlayer("replay-black", "replay-black", PlayerSide.BLACK)
        );
        List<List<List<String>>> boards = new ArrayList<List<List<String>>>();
        boards.add(toBoardList(match.board()));
        for (Map<String, Object> move : moves) {
            String actor = "RED".equals(asString(move.get("side"))) ? "replay-red" : "replay-black";
            match.applyMove(actor, asMap(move.get("payload")));
            boards.add(toBoardList(match.board()));
        }
        return boards;
    }

    private List<List<List<String>>> buildGomokuHistory(List<Map<String, Object>> moves) {
        GomokuMatch match = new GomokuMatch(
            new MatchPlayer("replay-black", "replay-black", PlayerSide.BLACK),
            new MatchPlayer("replay-white", "replay-white", PlayerSide.WHITE)
        );
        List<List<List<String>>> boards = new ArrayList<List<List<String>>>();
        boards.add(toBoardList(match.board()));
        for (Map<String, Object> move : moves) {
            String actor = "BLACK".equals(asString(move.get("side"))) ? "replay-black" : "replay-white";
            match.applyMove(actor, asMap(move.get("payload")));
            boards.add(toBoardList(match.board()));
        }
        return boards;
    }

    private List<List<String>> toBoardList(String[][] board) {
        List<List<String>> rows = new ArrayList<List<String>>();
        for (String[] row : board) {
            List<String> cells = new ArrayList<String>();
            for (String cell : row) {
                cells.add(cell == null ? "" : cell);
            }
            rows.add(cells);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castMoveList(Object value) {
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return new ArrayList<Map<String, Object>>();
    }

    private static final class JdbcUserRepository implements UserRepository {
        private final DataSource dataSource;

        private JdbcUserRepository(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public Optional<UserRecord> findByUsername(String username) {
            String sql = "select id, username, password_hash, created_at from users where username = ?";
            try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapUser(rs)) : Optional.<UserRecord>empty();
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("failed to find user by username", ex);
            }
        }

        @Override
        public Optional<UserRecord> findById(String id) {
            String sql = "select id, username, password_hash, created_at from users where id = ?";
            try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapUser(rs)) : Optional.<UserRecord>empty();
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("failed to find user by id", ex);
            }
        }

        @Override
        public UserRecord save(UserRecord user) {
            String sql = "insert into users(id, username, password_hash, created_at) values (?, ?, ?, ?)";
            try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, user.id());
                ps.setString(2, user.username());
                ps.setString(3, user.passwordHash());
                ps.setTimestamp(4, Timestamp.from(user.createdAt()));
                ps.executeUpdate();
                return user;
            } catch (SQLException ex) {
                throw new IllegalStateException("failed to save user", ex);
            }
        }

        private UserRecord mapUser(ResultSet rs) throws SQLException {
            return new UserRecord(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getTimestamp("created_at").toInstant()
            );
        }
    }

    private static final class JdbcAuthSessionRepository implements AuthSessionRepository {
        private final DataSource dataSource;
        private final UserRepository users;

        private JdbcAuthSessionRepository(DataSource dataSource, UserRepository users) {
            this.dataSource = dataSource;
            this.users = users;
        }

        @Override
        public UserSession save(UserSession session) {
            String sql = "insert into auth_sessions(token, user_id, expires_at, created_at) values (?, ?, ?, ?)";
            try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, session.token());
                ps.setString(2, session.user().id());
                ps.setTimestamp(3, Timestamp.from(session.expiresAt()));
                ps.setTimestamp(4, Timestamp.from(Instant.now()));
                ps.executeUpdate();
                return session;
            } catch (SQLException ex) {
                throw new IllegalStateException("failed to save auth session", ex);
            }
        }

        @Override
        public Optional<UserSession> findByToken(String token) {
            String sql = "select token, user_id, expires_at from auth_sessions where token = ? and expires_at > ?";
            try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, token);
                ps.setTimestamp(2, Timestamp.from(Instant.now()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    String userId = rs.getString("user_id");
                    Optional<UserRecord> user = users.findById(userId);
                    if (!user.isPresent()) {
                        return Optional.empty();
                    }
                    return Optional.of(new UserSession(
                        user.get().toAuthUser(),
                        rs.getString("token"),
                        rs.getTimestamp("expires_at").toInstant()
                    ));
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("failed to find auth session", ex);
            }
        }

        @Override
        public void deleteByToken(String token) {
            String sql = "delete from auth_sessions where token = ?";
            try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, token);
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new IllegalStateException("failed to delete auth session", ex);
            }
        }
    }
}
