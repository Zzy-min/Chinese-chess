package com.xiangqi.online.learn;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EndgameCatalog {
    private final List<Map<String, String>> endgames;

    public EndgameCatalog() {
        this.endgames = loadFromResource();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> loadFromResource() {
        try (InputStream in = EndgameCatalog.class.getResourceAsStream("/online/endgames.json")) {
            if (in == null) {
                return Collections.emptyList();
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // Simple JSON parsing without external dependencies
            return parseEndgameArray(json);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static List<Map<String, String>> parseEndgameArray(String json) {
        List<Map<String, String>> result = new ArrayList<>();
        // Strip outer [ and ]
        String inner = json.trim();
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);
        inner = inner.trim();
        if (inner.isEmpty()) return result;

        // Split by },{ to get individual objects
        int depth = 0;
        int start = 0;
        List<String> objects = new ArrayList<>();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            else if (c == ',' && depth == 0) {
                objects.add(inner.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < inner.length()) {
            objects.add(inner.substring(start).trim());
        }

        for (String obj : objects) {
            if (obj.startsWith("{")) obj = obj.substring(1);
            if (obj.endsWith("}")) obj = obj.substring(0, obj.length() - 1);
            obj = obj.trim();
            if (obj.isEmpty()) continue;

            Map<String, String> map = new LinkedHashMap<>();
            // Parse key-value pairs
            String[] pairs = obj.split(",(?=\\s*\")");
            for (String pair : pairs) {
                int colonIdx = pair.indexOf(':');
                if (colonIdx < 0) continue;
                String key = pair.substring(0, colonIdx).trim().replaceAll("^\"|\"$", "");
                String value = pair.substring(colonIdx + 1).trim().replaceAll("^\"|\"$", "");
                map.put(key, value);
            }
            if (!map.isEmpty() && map.containsKey("id")) {
                result.add(map);
            }
        }
        return result;
    }

    public List<Map<String, String>> all() {
        return Collections.unmodifiableList(endgames);
    }

    public List<Map<String, String>> byDifficulty(String difficulty) {
        List<Map<String, String>> filtered = new ArrayList<>();
        for (Map<String, String> eg : endgames) {
            if (difficulty == null || difficulty.isEmpty() || difficulty.equalsIgnoreCase(eg.getOrDefault("difficulty", ""))) {
                filtered.add(eg);
            }
        }
        return filtered;
    }

    public Map<String, String> findById(String id) {
        for (Map<String, String> eg : endgames) {
            if (id != null && id.equals(eg.get("id"))) {
                return eg;
            }
        }
        return null;
    }
}
