package com.xiangqi.ai;

import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.PieceColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConfigurableXiangqiEngine implements XiangqiEngine {
    private static final String PREF_BUILTIN = "BUILTIN";
    private static final String PREF_PIKAFISH = "PIKAFISH";
    private static final String PREF_AUTO = "AUTO";

    private final BuiltinXiangqiEngine builtin = new BuiltinXiangqiEngine();
    private final String pikafishCmdText;
    private final List<String> pikafishCmd;
    private String preferredEngine;
    private XiangqiEngine selected;
    private String selectedId;
    private String selectedText;

    public ConfigurableXiangqiEngine() {
        this.pikafishCmdText = readSetting("xq.xiangqi.pikafish.cmd", "XQ_XIANGQI_PIKAFISH_CMD",
            readSetting("xq.xiangqi.uci.cmd", "XQ_XIANGQI_UCI_CMD", "")).trim();
        this.pikafishCmd = splitCommand(pikafishCmdText);
        this.preferredEngine = normalizePreference(readSetting("xq.xiangqi.engine", "XQ_XIANGQI_ENGINE", PREF_BUILTIN));
        selectEngineForPreference(preferredEngine);
    }

    @Override
    public synchronized Move findBestMove(Board board, PieceColor aiColor, MinimaxAI.Difficulty difficulty) {
        if (selected == null) {
            selectEngineForPreference(preferredEngine);
        }
        if (selected != builtin) {
            Move m;
            try {
                m = selected.findBestMove(board, aiColor, difficulty);
            } catch (Exception ignored) {
                m = null;
            }
            if (m != null) {
                return m;
            }
            selected.close();
            selected = builtin;
            selectedId = builtin.getEngineId();
            selectedText = builtin.getEngineText() + "（外部引擎异常已回退）";
        }
        return builtin.findBestMove(board, aiColor, difficulty);
    }

    @Override
    public synchronized String getEngineId() {
        return selectedId == null ? builtin.getEngineId() : selectedId;
    }

    @Override
    public synchronized String getEngineText() {
        return selectedText == null ? builtin.getEngineText() : selectedText;
    }

    public synchronized String getPreferredEngine() {
        return preferredEngine;
    }

    public synchronized void setPreferredEngine(String preference) {
        String normalized = normalizePreference(preference);
        if (normalized.equals(preferredEngine) && selected != null) {
            return;
        }
        preferredEngine = normalized;
        selectEngineForPreference(preferredEngine);
    }

    public boolean isPikafishConfigured() {
        return !pikafishCmd.isEmpty();
    }

    @Override
    public synchronized void close() {
        if (selected != null && selected != builtin) {
            selected.close();
        }
    }

    private void selectEngineForPreference(String preference) {
        if (selected != null && selected != builtin) {
            selected.close();
        }
        XiangqiEngine next = chooseEngine(preference);
        if (next == null) {
            next = builtin;
        }
        selected = next;
        selectedId = next.getEngineId();
        selectedText = next.getEngineText();
    }

    private XiangqiEngine chooseEngine(String preference) {
        if (PREF_BUILTIN.equals(preference)) {
            return builtin;
        }
        if (PREF_PIKAFISH.equals(preference)) {
            return createPikafish();
        }
        if (PREF_AUTO.equals(preference)) {
            XiangqiEngine p = createPikafish();
            return p != null ? p : builtin;
        }
        return builtin;
    }

    private XiangqiEngine createPikafish() {
        if (pikafishCmd.isEmpty()) {
            return null;
        }
        try {
            return new PikafishUciEngine(pikafishCmd);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizePreference(String prefRaw) {
        String p = prefRaw == null ? "" : prefRaw.trim().toUpperCase(Locale.ROOT);
        if (PREF_PIKAFISH.equals(p)) {
            return PREF_PIKAFISH;
        }
        if ("UCI".equals(p)) {
            return PREF_PIKAFISH;
        }
        if (PREF_AUTO.equals(p)) {
            return PREF_AUTO;
        }
        return PREF_BUILTIN;
    }

    private String readSetting(String prop, String env, String defaultValue) {
        String v = System.getProperty(prop);
        if (v == null || v.trim().isEmpty()) {
            v = System.getenv(env);
        }
        if (v == null || v.trim().isEmpty()) {
            return defaultValue;
        }
        return v;
    }

    private List<String> splitCommand(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (ch == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (Character.isWhitespace(ch) && !inSingle && !inDouble) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }
            cur.append(ch);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }
}

