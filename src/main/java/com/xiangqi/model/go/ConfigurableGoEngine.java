package com.xiangqi.model.go;

import com.xiangqi.ai.MinimaxAI;
import com.xiangqi.model.go.GoBoard.GoHistoryEntry;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight HTTP client for the external Go engine service.
 *
 * Config:
 * - `xq.go.engine.url` / `XQ_GO_ENGINE_URL`
 * - optional `xq.go.engine` / `XQ_GO_ENGINE`: AUTO | REMOTE | DISABLED
 */
public final class ConfigurableGoEngine implements GoEngine {
    private static final String PREF_AUTO = "AUTO";
    private static final String PREF_REMOTE = "REMOTE";
    private static final String PREF_DISABLED = "DISABLED";
    private static final long HEALTH_CACHE_MS = 10_000L;
    private static final Pattern BOOL_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(true|false)");
    private static final Pattern INT_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(-?\\d+)");
    private static final Pattern DOUBLE_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);

    private final HttpClient client;
    private final String baseUrl;
    private final String preferredEngine;
    private volatile long lastHealthCheckAt = 0L;
    private volatile boolean engineAvailable = false;
    private volatile String engineName = "KataGo";

    public ConfigurableGoEngine() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
        this.baseUrl = trimTrailingSlash(readSetting("xq.go.engine.url", "XQ_GO_ENGINE_URL", "").trim());
        this.preferredEngine = normalizePreference(readSetting("xq.go.engine", "XQ_GO_ENGINE", PREF_AUTO));
    }

    @Override
    public boolean isAvailable() {
        if (PREF_DISABLED.equals(preferredEngine) || baseUrl.isEmpty()) {
            engineAvailable = false;
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastHealthCheckAt < HEALTH_CACHE_MS) {
            return engineAvailable;
        }
        synchronized (this) {
            if (now - lastHealthCheckAt < HEALTH_CACHE_MS) {
                return engineAvailable;
            }
            lastHealthCheckAt = now;
            engineAvailable = pingHealth();
            return engineAvailable;
        }
    }

    @Override
    public GoEngineMove genMove(GoBoard board, GoStone aiStone, MinimaxAI.Difficulty difficulty) {
        if (!isAvailable()) {
            return null;
        }
        String body = "{"
            + "\"size\":" + board.getSize() + ","
            + "\"komi\":" + board.getKomi() + ","
            + "\"currentTurn\":\"" + board.getCurrentTurn().name() + "\","
            + "\"toPlay\":\"" + board.getCurrentTurn().name() + "\","
            + "\"aiStone\":\"" + (aiStone == null ? board.getCurrentTurn().name() : aiStone.name()) + "\","
            + "\"difficulty\":\"" + (difficulty == null ? MinimaxAI.Difficulty.MEDIUM.name() : difficulty.name()) + "\","
            + "\"rows\":" + rowsJson(board) + ","
            + "\"moves\":" + movesJson(board)
            + "}";
        try {
            String response = postJson("/genmove", body);
            if (readBoolean(response, "pass", false)) {
                updateEngineName(response);
                return GoEngineMove.pass();
            }
            Integer row = readInt(response, "row");
            Integer col = readInt(response, "col");
            if (row == null || col == null) {
                return null;
            }
            updateEngineName(response);
            return new GoEngineMove(row.intValue(), col.intValue(), false);
        } catch (Exception e) {
            markUnavailable();
            return null;
        }
    }

    @Override
    public GoScoreSummary score(GoBoard board) {
        if (!isAvailable()) {
            return board == null ? null : board.scoreGame();
        }
        String body = "{"
            + "\"size\":" + board.getSize() + ","
            + "\"komi\":" + board.getKomi() + ","
            + "\"currentTurn\":\"" + board.getCurrentTurn().name() + "\","
            + "\"rows\":" + rowsJson(board) + ","
            + "\"moves\":" + movesJson(board)
            + "}";
        try {
            String response = postJson("/score", body);
            Integer blackArea = readInt(response, "blackArea");
            Integer whiteArea = readInt(response, "whiteArea");
            Double komi = readDouble(response, "komi");
            Double finalScore = readDouble(response, "finalScore");
            String winner = readString(response, "winner", "");
            if (blackArea == null || whiteArea == null || komi == null || finalScore == null) {
                return board.scoreGame();
            }
            updateEngineName(response);
            return new GoScoreSummary(blackArea.intValue(), whiteArea.intValue(), komi.doubleValue(), finalScore.doubleValue(), winner);
        } catch (Exception e) {
            markUnavailable();
            return board == null ? null : board.scoreGame();
        }
    }

    @Override
    public String getEngineName() {
        if (baseUrl.isEmpty() || PREF_DISABLED.equals(preferredEngine)) {
            return "disabled";
        }
        return engineName;
    }

    @Override
    public void close() {
        // no-op
    }

    private boolean pingHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 == 2) {
                String body = response.body();
                updateEngineName(body);
                return readBoolean(body, "ready", true).booleanValue();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return false;
    }

    private String postJson(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(12))
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("go engine http " + response.statusCode());
        }
        return response.body() == null ? "" : response.body();
    }

    private void updateEngineName(String response) {
        String raw = readString(response, "engine", null);
        if (raw == null || raw.trim().isEmpty()) {
            raw = readString(response, "engineName", null);
        }
        if (raw != null && !raw.trim().isEmpty()) {
            engineName = raw.trim();
        }
    }

    private void markUnavailable() {
        engineAvailable = false;
        lastHealthCheckAt = System.currentTimeMillis();
    }

    private String movesJson(GoBoard board) {
        List<GoHistoryEntry> history = board.getMoveHistory();
        StringBuilder sb = new StringBuilder(Math.max(32, history.size() * 48));
        sb.append('[');
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            GoHistoryEntry move = history.get(i);
            sb.append('{');
            sb.append("\"color\":\"").append(move.getStone().name()).append("\",");
            sb.append("\"pass\":").append(move.isPass());
            if (!move.isPass()) {
                sb.append(',');
                sb.append("\"row\":").append(move.getRow()).append(',');
                sb.append("\"col\":").append(move.getCol());
            }
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private String rowsJson(GoBoard board) {
        StringBuilder sb = new StringBuilder(board.getSize() * (board.getSize() + 4));
        sb.append('[');
        for (int row = 0; row < board.getSize(); row++) {
            if (row > 0) {
                sb.append(',');
            }
            sb.append('"');
            for (int col = 0; col < board.getSize(); col++) {
                GoStone stone = board.getStone(row, col);
                if (stone == GoStone.BLACK) {
                    sb.append('B');
                } else if (stone == GoStone.WHITE) {
                    sb.append('W');
                } else {
                    sb.append('.');
                }
            }
            sb.append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private String normalizePreference(String prefRaw) {
        String normalized = prefRaw == null ? "" : prefRaw.trim().toUpperCase(Locale.ROOT);
        if (PREF_REMOTE.equals(normalized) || "KATAGO".equals(normalized)) {
            return PREF_REMOTE;
        }
        if (PREF_DISABLED.equals(normalized) || "OFF".equals(normalized)) {
            return PREF_DISABLED;
        }
        return PREF_AUTO;
    }

    private String readSetting(String prop, String env, String defaultValue) {
        String value = System.getProperty(prop);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(env);
        }
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
    }

    private String trimTrailingSlash(String value) {
        String out = value == null ? "" : value.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private Boolean readBoolean(String json, String field, boolean defaultValue) {
        Matcher matcher = Pattern.compile(String.format(BOOL_FIELD.pattern(), Pattern.quote(field))).matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return Boolean.valueOf(defaultValue);
        }
        return Boolean.valueOf(Boolean.parseBoolean(matcher.group(1)));
    }

    private Integer readInt(String json, String field) {
        Matcher matcher = Pattern.compile(String.format(INT_FIELD.pattern(), Pattern.quote(field))).matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double readDouble(String json, String field) {
        Matcher matcher = Pattern.compile(String.format(DOUBLE_FIELD.pattern(), Pattern.quote(field))).matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.valueOf(Double.parseDouble(matcher.group(1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String readString(String json, String field, String defaultValue) {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD.pattern(), Pattern.quote(field)), Pattern.DOTALL)
            .matcher(json == null ? "" : json);
        if (!matcher.find()) {
            return defaultValue;
        }
        String value = matcher.group(1);
        return value == null ? defaultValue : value.replace("\\\"", "\"");
    }
}
