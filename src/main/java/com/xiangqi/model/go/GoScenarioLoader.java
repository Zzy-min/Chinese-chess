package com.xiangqi.model.go;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoScenarioLoader {
    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
    private static final Pattern SIZE_PATTERN = Pattern.compile("\"size\"\\s*:\\s*(\\d+)");
    private static final Pattern ROWS_PATTERN = Pattern.compile("\"rows\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static volatile List<GoScenario> cache;

    private GoScenarioLoader() {
    }

    public static List<GoScenario> loadAll() {
        List<GoScenario> local = cache;
        if (local != null) {
            return local;
        }
        synchronized (GoScenarioLoader.class) {
            if (cache == null) {
                cache = Collections.unmodifiableList(readScenarios());
            }
            return cache;
        }
    }

    public static GoScenario findByName(String name) {
        if (name == null) {
            return null;
        }
        for (GoScenario scenario : loadAll()) {
            if (name.equals(scenario.getName())) {
                return scenario;
            }
        }
        return null;
    }

    private static List<GoScenario> readScenarios() {
        InputStream input = GoScenarioLoader.class.getResourceAsStream("/go-scenarios.json");
        if (input == null) {
            return new ArrayList<GoScenario>();
        }

        String text;
        try {
            text = readAll(input);
        } catch (IOException e) {
            return new ArrayList<GoScenario>();
        }

        List<GoScenario> scenarios = new ArrayList<GoScenario>();
        Matcher matcher = OBJECT_PATTERN.matcher(text);
        while (matcher.find()) {
            String block = matcher.group(1);
            String name = extractString(block, "name");
            String description = extractString(block, "description");
            int size = extractSize(block);
            GoStone turn = parseTurn(extractString(block, "turn"));
            String[] rows = extractRows(block);
            if (!name.isEmpty() && size > 0 && rows.length == size) {
                scenarios.add(new GoScenario(name, description, size, turn, rows));
            }
        }
        return scenarios;
    }

    private static String readAll(InputStream input) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static String extractString(String block, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(block);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).replace("\\\"", "\"");
    }

    private static int extractSize(String block) {
        Matcher matcher = SIZE_PATTERN.matcher(block);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String[] extractRows(String block) {
        Matcher matcher = ROWS_PATTERN.matcher(block);
        if (!matcher.find()) {
            return new String[0];
        }
        String body = matcher.group(1);
        List<String> rows = new ArrayList<String>();
        Matcher itemMatcher = Pattern.compile("\"(.*?)\"", Pattern.DOTALL).matcher(body);
        while (itemMatcher.find()) {
            rows.add(itemMatcher.group(1));
        }
        return rows.toArray(new String[0]);
    }

    private static GoStone parseTurn(String raw) {
        if ("WHITE".equalsIgnoreCase(raw)) {
            return GoStone.WHITE;
        }
        return GoStone.BLACK;
    }
}
