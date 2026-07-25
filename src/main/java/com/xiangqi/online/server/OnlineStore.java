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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class OnlineStore {
    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final JdbcUserRepository users;
    private final JdbcAuthSessionRepository sessions;
    private volatile Map<String, Object> learnContentSeed;

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

    public void rewriteGameMoves(String gameId, List<Map<String, Object>> moves, Map<String, Object> snapshot) {
        String deleteSql = "delete from game_moves where game_id = ?";
        String insertSql = "insert into game_moves(id, game_id, move_index, actor_user_id, side, notation, payload_json, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)";
        String updateSql = "update games set status=?, current_turn=?, winner_side=?, result_text=?, board_json=?, move_count=?, first_remaining_seconds=?, second_remaining_seconds=?, termination_reason=?, finished_at=? where id=?";
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement deletePs = connection.prepareStatement(deleteSql)) {
                    deletePs.setString(1, gameId);
                    deletePs.executeUpdate();
                }
                try (PreparedStatement insertPs = connection.prepareStatement(insertSql)) {
                    List<Map<String, Object>> safeMoves = moves == null ? new ArrayList<Map<String, Object>>() : moves;
                    for (int idx = 0; idx < safeMoves.size(); idx++) {
                        Map<String, Object> move = safeMoves.get(idx);
                        int moveIndex = idx + 1;
                        insertPs.setString(1, gameId + "-" + moveIndex);
                        insertPs.setString(2, gameId);
                        insertPs.setInt(3, moveIndex);
                        insertPs.setString(4, asString(move.get("actorUserId")));
                        insertPs.setString(5, asString(move.get("side")));
                        insertPs.setString(6, asString(move.get("notation")));
                        insertPs.setString(7, mapper.writeValueAsString(asMap(move.get("payload"))));
                        insertPs.setTimestamp(8, parseMoveCreatedAt(move.get("createdAt")));
                        insertPs.addBatch();
                    }
                    if (!safeMoves.isEmpty()) {
                        insertPs.executeBatch();
                    }
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
                connection.commit();
            } catch (Exception inner) {
                connection.rollback();
                throw inner;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("failed to rewrite game moves", ex);
        }
    }

    public List<Map<String, Object>> recentGames(int limit) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        String sql = "select id, room_id, game_type, is_training, opponent_type, ai_engine, difficulty, status, first_username, first_side, second_username, second_side, winner_side, result_text, move_count, started_at, finished_at from games order by started_at desc limit ?";
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
                    Timestamp started = rs.getTimestamp("started_at");
                    Timestamp finished = rs.getTimestamp("finished_at");
                    item.put("startedAt", started == null ? "" : started.toInstant().toString());
                    item.put("finishedAt", finished == null ? "" : finished.toInstant().toString());
                    Timestamp updated = finished == null ? started : finished;
                    item.put("updatedAt", updated == null ? "" : updated.toInstant().toString());
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
            + "sum(case when termination_reason = 'DRAW_AGREED' then 1 else 0 end) as draws, "
            + "max(coalesce(finished_at, started_at)) as last_game_at "
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
                    int losses = Math.max(0, total - wins - draws);
                    summary.put("losses", losses);
                    summary.put("ratingScore", 1000 + wins * 10 - losses * 5);
                    Timestamp lastGameAt = rs.getTimestamp("last_game_at");
                    summary.put("lastGameAt", lastGameAt == null ? "" : lastGameAt.toInstant().toString());
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to load profile summary", ex);
        }
        return summary;
    }

    public Map<String, Object> gameTypeStats() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("XIANGQI", gameTypeStat("XIANGQI"));
        body.put("GOMOKU", gameTypeStat("GOMOKU"));
        return body;
    }

    public Map<String, Object> profilePreferences(String userId) {
        Map<String, Object> defaults = defaultProfilePreferences();
        String sql = "select sound_enabled, board_theme, board_flipped, updated_at from user_preferences where user_id = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return defaults;
                }
                Map<String, Object> body = new LinkedHashMap<String, Object>();
                body.put("soundEnabled", rs.getBoolean("sound_enabled"));
                body.put("boardTheme", normalizeBoardTheme(rs.getString("board_theme")));
                body.put("boardFlipped", rs.getBoolean("board_flipped"));
                Timestamp updatedAt = rs.getTimestamp("updated_at");
                body.put("updatedAt", updatedAt == null ? "" : updatedAt.toInstant().toString());
                return body;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to load profile preferences", ex);
        }
    }

    public Map<String, Object> saveProfilePreferences(String userId, Map<String, Object> patch) {
        Map<String, Object> current = profilePreferences(userId);
        boolean soundEnabled = patch.containsKey("soundEnabled") ? asBoolean(patch.get("soundEnabled")) : asBoolean(current.get("soundEnabled"));
        boolean boardFlipped = patch.containsKey("boardFlipped") ? asBoolean(patch.get("boardFlipped")) : asBoolean(current.get("boardFlipped"));
        String boardTheme = patch.containsKey("boardTheme") ? normalizeBoardTheme(asString(patch.get("boardTheme"))) : normalizeBoardTheme(asString(current.get("boardTheme")));
        Instant now = Instant.now();
        String updateSql = "update user_preferences set sound_enabled = ?, board_theme = ?, board_flipped = ?, updated_at = ? where user_id = ?";
        String insertSql = "insert into user_preferences(user_id, sound_enabled, board_theme, board_flipped, updated_at) values (?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            int updated;
            try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                ps.setBoolean(1, soundEnabled);
                ps.setString(2, boardTheme);
                ps.setBoolean(3, boardFlipped);
                ps.setTimestamp(4, Timestamp.from(now));
                ps.setString(5, userId);
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    ps.setString(1, userId);
                    ps.setBoolean(2, soundEnabled);
                    ps.setString(3, boardTheme);
                    ps.setBoolean(4, boardFlipped);
                    ps.setTimestamp(5, Timestamp.from(now));
                    ps.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to save profile preferences", ex);
        }
        return profilePreferences(userId);
    }

    public List<Map<String, Object>> recentGamesForUser(String userId, int limit) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        String sql = "select id, room_id, game_type, is_training, opponent_type, ai_engine, difficulty, status, first_user_id, first_username, first_side, second_user_id, second_username, second_side, winner_side, result_text, termination_reason, move_count, started_at, finished_at "
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
                    Timestamp started = rs.getTimestamp("started_at");
                    Timestamp finished = rs.getTimestamp("finished_at");
                    item.put("startedAt", started == null ? "" : started.toInstant().toString());
                    item.put("finishedAt", finished == null ? "" : finished.toInstant().toString());
                    Timestamp updated = finished == null ? started : finished;
                    item.put("updatedAt", updated == null ? "" : updated.toInstant().toString());
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

    public List<Map<String, Object>> watchableGames(int limit) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        String sql = "select id, game_type, status, is_training, first_username, first_side, second_username, second_side, move_count, started_at, finished_at "
            + "from games order by coalesce(finished_at, started_at) desc limit ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("gameId", rs.getString("id"));
                    item.put("gameType", rs.getString("game_type"));
                    item.put("status", rs.getString("status"));
                    item.put("isTraining", rs.getBoolean("is_training"));
                    item.put("moveCount", rs.getInt("move_count"));
                    Timestamp finishedAt = rs.getTimestamp("finished_at");
                    Timestamp startedAt = rs.getTimestamp("started_at");
                    Timestamp updatedAt = finishedAt == null ? startedAt : finishedAt;
                    item.put("updatedAt", updatedAt == null ? "" : updatedAt.toInstant().toString());
                    item.put("finishedAt", finishedAt == null ? "" : finishedAt.toInstant().toString());
                    Map<String, Object> players = new LinkedHashMap<String, Object>();
                    Map<String, Object> first = new LinkedHashMap<String, Object>();
                    first.put("username", rs.getString("first_username"));
                    first.put("side", rs.getString("first_side"));
                    Map<String, Object> second = new LinkedHashMap<String, Object>();
                    second.put("username", rs.getString("second_username"));
                    second.put("side", rs.getString("second_side"));
                    players.put("first", first);
                    players.put("second", second);
                    item.put("players", players);
                    items.add(item);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to load watchable games", ex);
        }
        return items;
    }

    public Map<String, Object> communityLeaderboard(int windowDays, int limit) {
        int safeWindowDays = Math.max(1, windowDays);
        int safeLimit = Math.max(1, limit);
        List<Map<String, Object>> winBoard = queryWinBoard(Integer.valueOf(safeWindowDays), safeLimit, null);
        List<Map<String, Object>> activityBoard = queryActivityBoard(Integer.valueOf(safeWindowDays), safeLimit, null);
        boolean fallback = winBoard.isEmpty() && activityBoard.isEmpty();
        if (fallback) {
            winBoard = queryWinBoard(null, safeLimit, null);
            activityBoard = queryActivityBoard(null, safeLimit, null);
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("requestedWindowDays", safeWindowDays);
        body.put("windowDaysUsed", fallback ? 0 : safeWindowDays);
        body.put("fallbackToAllTime", fallback);
        body.put("sampleSize", Math.max(winBoard.size(), activityBoard.size()));
        body.put("generatedAt", Instant.now().toString());
        body.put("winBoard", winBoard);
        body.put("activityBoard", activityBoard);
        Map<String, Object> byGameType = new LinkedHashMap<String, Object>();
        byGameType.put("XIANGQI", leaderboardBucket(safeWindowDays, safeLimit, fallback, "XIANGQI"));
        byGameType.put("GOMOKU", leaderboardBucket(safeWindowDays, safeLimit, fallback, "GOMOKU"));
        body.put("byGameType", byGameType);
        return body;
    }

    public Map<String, Object> learnContent() {
        Map<String, Object> seed = ensureLearnContentSeed();
        return deepCopy(seed);
    }

    public Map<String, Object> learnCatalog(String filter, String query, int offset, int limit) {
        Map<String, Object> seed = ensureLearnContentSeed();
        String normalizedFilter = asString(filter).trim().toLowerCase(Locale.ROOT);
        String normalizedQuery = asString(query).trim().toLowerCase(Locale.ROOT);
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<Map<String, Object>> matches = new ArrayList<>();

        appendLearnCatalogItems(matches, learnSeedItems(seed, "tutorials"), true,
                normalizedFilter, normalizedQuery);
        appendLearnCatalogItems(matches, learnSeedItems(seed, "puzzles"), false,
                normalizedFilter, normalizedQuery);

        int fromIndex = Math.min(safeOffset, matches.size());
        int toIndex = Math.min(fromIndex + safeLimit, matches.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", new ArrayList<>(matches.subList(fromIndex, toIndex)));
        result.put("total", matches.size());
        result.put("offset", safeOffset);
        result.put("limit", safeLimit);
        result.put("hasMore", toIndex < matches.size());
        Object recommendedPractice = seed.get("recommendedPractice");
        result.put("recommendedPractice", recommendedPractice instanceof List<?>
                ? mapper.convertValue(recommendedPractice, List.class)
                : List.of());
        return result;
    }

    public Optional<Map<String, Object>> learnItem(String id) {
        String expectedId = asString(id).trim();
        if (expectedId.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> seed = ensureLearnContentSeed();
        for (Map<String, Object> tutorial : learnSeedItems(seed, "tutorials")) {
            if (expectedId.equals(asString(tutorial.get("id")))) {
                Map<String, Object> item = deepCopy(tutorial);
                item.put("kind", "tutorial");
                item.put("isTutorial", true);
                return Optional.of(item);
            }
        }
        for (Map<String, Object> puzzle : learnSeedItems(seed, "puzzles")) {
            if (expectedId.equals(asString(puzzle.get("id")))) {
                Map<String, Object> item = deepCopy(puzzle);
                item.put("kind", "puzzle");
                item.put("isTutorial", false);
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> learnSeedItems(Map<String, Object> seed, String key) {
        Object value = seed.get(key);
        if (!(value instanceof List<?>)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map<?, ?>) {
                items.add((Map<String, Object>) item);
            }
        }
        return items;
    }

    private void appendLearnCatalogItems(List<Map<String, Object>> target,
                                         List<Map<String, Object>> source,
                                         boolean tutorial,
                                         String filter,
                                         String query) {
        for (Map<String, Object> item : source) {
            if (!matchesLearnFilter(item, tutorial, filter) || !matchesLearnQuery(item, query)) {
                continue;
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", item.get("id"));
            summary.put("kind", tutorial ? "tutorial" : "puzzle");
            summary.put("isTutorial", tutorial);
            copyLearnSummaryField(item, summary, "gameType");
            copyLearnSummaryField(item, summary, "title");
            copyLearnSummaryField(item, summary, "summary");
            copyLearnSummaryField(item, summary, "difficulty");
            copyLearnSummaryField(item, summary, "theme");
            copyLearnSummaryField(item, summary, "source");
            target.add(summary);
        }
    }

    private boolean matchesLearnFilter(Map<String, Object> item, boolean tutorial, String filter) {
        String gameType = asString(item.get("gameType")).toUpperCase(Locale.ROOT);
        String theme = asString(item.get("theme")).toUpperCase(Locale.ROOT);
        String searchable = (asString(item.get("title")) + " " + asString(item.get("id")))
                .toLowerCase(Locale.ROOT);
        switch (filter) {
            case "":
            case "all":
                return true;
            case "xiangqi":
                return "XIANGQI".equals(gameType);
            case "gomoku":
                return "GOMOKU".equals(gameType);
            case "featured":
                return tutorial;
            case "puzzles":
                return !tutorial;
            case "endgames":
                return !tutorial && "ENDGAME_FEN".equals(theme);
            case "openings":
                return searchable.contains("开局")
                        || searchable.contains("布局")
                        || searchable.contains("opening");
            default:
                return true;
        }
    }

    private boolean matchesLearnQuery(Map<String, Object> item, String query) {
        if (query.isEmpty()) {
            return true;
        }
        String searchable = (asString(item.get("title")) + " "
                + asString(item.get("summary")) + " "
                + asString(item.get("source"))).toLowerCase(Locale.ROOT);
        return searchable.contains(query);
    }

    private void copyLearnSummaryField(Map<String, Object> source,
                                       Map<String, Object> target,
                                       String field) {
        if (source.containsKey(field)) {
            target.put(field, source.get(field));
        }
    }

    public Map<String, Object> learnProgress(String userId) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        List<String> tutorials = new ArrayList<String>();
        List<String> puzzles = new ArrayList<String>();
        String sql = "select content_type, content_id from learn_progress where user_id = ? order by updated_at desc";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String contentType = rs.getString("content_type");
                    String contentId = rs.getString("content_id");
                    if ("TUTORIAL".equalsIgnoreCase(contentType)) {
                        tutorials.add(contentId);
                    } else if ("PUZZLE".equalsIgnoreCase(contentType)) {
                        puzzles.add(contentId);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to load learn progress", ex);
        }
        body.put("tutorialsCompleted", tutorials);
        body.put("puzzlesCompleted", puzzles);
        body.put("updatedAt", Instant.now().toString());
        return body;
    }

    public List<Map<String, Object>> searchUsers(String query, int limit) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, limit);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        String sql = "select id, username from users where lower(username) like ? order by lower(username) asc limit ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "%" + normalized + "%");
            ps.setInt(2, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("id", rs.getString("id"));
                    item.put("username", rs.getString("username"));
                    items.add(item);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to search users", ex);
        }
        return items;
    }

    private Map<String, Object> gameTypeStat(String gameType) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("gameType", gameType);
        item.put("totalGames", 0);
        item.put("completedGames", 0);
        item.put("trainingGames", 0);
        item.put("distinctPlayers", 0);
        String aggregateSql = "select count(*) as total_games, "
            + "sum(case when status = 'FINISHED' then 1 else 0 end) as completed_games, "
            + "sum(case when is_training then 1 else 0 end) as training_games "
            + "from games where game_type = ?";
        String distinctSql = "select count(distinct user_id) as distinct_players from ("
            + "select first_user_id as user_id from games where game_type = ? and first_user_id <> '' "
            + "union all "
            + "select second_user_id as user_id from games where game_type = ? and second_user_id <> ''"
            + ") t";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(aggregateSql)) {
                ps.setString(1, gameType);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        item.put("totalGames", rs.getInt("total_games"));
                        item.put("completedGames", rs.getInt("completed_games"));
                        item.put("trainingGames", rs.getInt("training_games"));
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(distinctSql)) {
                ps.setString(1, gameType);
                ps.setString(2, gameType);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        item.put("distinctPlayers", rs.getInt("distinct_players"));
                    }
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to load game type stats", ex);
        }
        return item;
    }

    public void markLearnProgress(String userId, String contentType, String contentId) {
        String normalizedType = normalizeContentType(contentType);
        String normalizedId = contentId == null ? "" : contentId.trim();
        if (normalizedId.isEmpty()) {
            throw new IllegalArgumentException("content id is required");
        }
        Instant now = Instant.now();
        String updateSql = "update learn_progress set completed_at = ?, updated_at = ? where user_id = ? and content_type = ? and content_id = ?";
        String insertSql = "insert into learn_progress(user_id, content_type, content_id, completed_at, updated_at) values (?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            int updated;
            try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.setTimestamp(2, Timestamp.from(now));
                ps.setString(3, userId);
                ps.setString(4, normalizedType);
                ps.setString(5, normalizedId);
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    ps.setString(1, userId);
                    ps.setString(2, normalizedType);
                    ps.setString(3, normalizedId);
                    ps.setTimestamp(4, Timestamp.from(now));
                    ps.setTimestamp(5, Timestamp.from(now));
                    ps.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to save learn progress", ex);
        }
    }

    private int queryForInt(String sql) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to run count query", ex);
        }
    }

    private String normalizeContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toUpperCase();
        if ("TUTORIAL".equals(normalized) || "PUZZLE".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("unsupported content type");
    }

    private Map<String, Object> leaderboardBucket(int windowDays, int limit, boolean fallbackToAllTime, String gameType) {
        Integer effectiveWindow = fallbackToAllTime ? null : Integer.valueOf(windowDays);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("gameType", gameType);
        body.put("winBoard", queryWinBoard(effectiveWindow, limit, gameType));
        body.put("activityBoard", queryActivityBoard(effectiveWindow, limit, gameType));
        return body;
    }

    private Map<String, Object> defaultProfilePreferences() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("soundEnabled", true);
        body.put("boardTheme", "wood");
        body.put("boardFlipped", false);
        body.put("updatedAt", "");
        return body;
    }

    private String normalizeBoardTheme(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        if ("ink".equals(value)) {
            return "ink";
        }
        return "wood";
    }

    private List<Map<String, Object>> queryWinBoard(Integer windowDays, int limit, String gameType) {
        String sql = "select u.id as user_id, u.username as username, "
            + "count(g.id) as total_games, "
            + "sum(case when (g.first_user_id = u.id and g.winner_side = g.first_side) or (g.second_user_id = u.id and g.winner_side = g.second_side) then 1 else 0 end) as wins, "
            + "sum(case when g.termination_reason = 'DRAW_AGREED' then 1 else 0 end) as draws "
            + "from users u join games g on (g.first_user_id = u.id or g.second_user_id = u.id)"
            + leaderboardWhereClause(windowDays, gameType)
            + "group by u.id, u.username order by wins desc, total_games desc, u.username asc limit ?";
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            bindLeaderboardFilters(ps, windowDays, gameType, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    int totalGames = rs.getInt("total_games");
                    int wins = rs.getInt("wins");
                    int draws = rs.getInt("draws");
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("rank", rank++);
                    item.put("userId", rs.getString("user_id"));
                    item.put("username", rs.getString("username"));
                    item.put("totalGames", totalGames);
                    item.put("wins", wins);
                    item.put("draws", draws);
                    item.put("losses", Math.max(0, totalGames - wins - draws));
                    item.put("winRate", totalGames <= 0 ? 0D : (wins * 1.0D / totalGames));
                    items.add(item);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to query win leaderboard", ex);
        }
        return items;
    }

    private List<Map<String, Object>> queryActivityBoard(Integer windowDays, int limit, String gameType) {
        String sql = "select u.id as user_id, u.username as username, "
            + "count(g.id) as activity_games, "
            + "sum(case when (g.first_user_id = u.id and g.winner_side = g.first_side) or (g.second_user_id = u.id and g.winner_side = g.second_side) then 1 else 0 end) as wins "
            + "from users u join games g on (g.first_user_id = u.id or g.second_user_id = u.id)"
            + leaderboardWhereClause(windowDays, gameType)
            + "group by u.id, u.username order by activity_games desc, wins desc, u.username asc limit ?";
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            bindLeaderboardFilters(ps, windowDays, gameType, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("rank", rank++);
                    item.put("userId", rs.getString("user_id"));
                    item.put("username", rs.getString("username"));
                    item.put("activityGames", rs.getInt("activity_games"));
                    item.put("wins", rs.getInt("wins"));
                    items.add(item);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to query activity leaderboard", ex);
        }
        return items;
    }

    private String leaderboardWhereClause(Integer windowDays, String gameType) {
        List<String> clauses = new ArrayList<String>();
        if (windowDays != null) {
            clauses.add("g.started_at >= ?");
        }
        if (gameType != null && !gameType.trim().isEmpty()) {
            clauses.add("g.game_type = ?");
        }
        if (clauses.isEmpty()) {
            return " ";
        }
        return " where " + String.join(" and ", clauses) + " ";
    }

    private void bindLeaderboardFilters(PreparedStatement ps, Integer windowDays, String gameType, int limit) throws SQLException {
        int index = 1;
        if (windowDays != null) {
            ps.setTimestamp(index++, Timestamp.from(Instant.now().minusSeconds(windowDays.intValue() * 24L * 3600L)));
        }
        if (gameType != null && !gameType.trim().isEmpty()) {
            ps.setString(index++, gameType.trim().toUpperCase());
        }
        ps.setInt(index, limit);
    }

    private Map<String, Object> ensureLearnContentSeed() {
        Map<String, Object> cached = learnContentSeed;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (learnContentSeed != null) {
                return learnContentSeed;
            }
            learnContentSeed = loadLearnContentSeed();
            return learnContentSeed;
        }
    }

    private Map<String, Object> loadLearnContentSeed() {
        try (InputStream input = OnlineStore.class.getResourceAsStream("/online/learn-content.seed.json")) {
            if (input == null) {
                return defaultLearnContentSeed();
            }
            Map<String, Object> parsed = mapper.readValue(input, new TypeReference<Map<String, Object>>() { });
            Map<String, Object> normalized = new LinkedHashMap<String, Object>();
            normalized.put("tutorials", asList(parsed.get("tutorials")));
            normalized.put("puzzles", asList(parsed.get("puzzles")));
            normalized.put("recommendedPractice", asList(parsed.get("recommendedPractice")));
            return normalized;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load learn seed data", ex);
        }
    }

    private Map<String, Object> defaultLearnContentSeed() {
        Map<String, Object> seed = new LinkedHashMap<String, Object>();
        seed.put("tutorials", new ArrayList<Object>());
        seed.put("puzzles", new ArrayList<Object>());
        seed.put("recommendedPractice", new ArrayList<Object>());
        return seed;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return new ArrayList<Object>();
    }

    private Map<String, Object> deepCopy(Map<String, Object> source) {
        if (source == null) {
            return Collections.emptyMap();
        }
        return mapper.convertValue(source, new TypeReference<Map<String, Object>>() { });
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

    private Timestamp parseMoveCreatedAt(Object raw) {
        String text = asString(raw);
        if (text.isEmpty()) {
            return Timestamp.from(Instant.now());
        }
        try {
            return Timestamp.from(Instant.parse(text));
        } catch (Exception ignored) {
            return Timestamp.from(Instant.now());
        }
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
