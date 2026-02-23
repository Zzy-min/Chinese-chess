package com.xiangqi.model.gomoku;

import com.xiangqi.ai.MinimaxAI;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runtime-selectable Gomoku engine with safe fallback to built-in AI.
 *
 * Config:
 * - `xq.gomoku.engine` / `XQ_GOMOKU_ENGINE`: BUILTIN | RAPFI | ALPHAGOMOKU | AUTO
 * - `xq.gomoku.rapfi.cmd` / `XQ_GOMOKU_RAPFI_CMD`
 * - `xq.gomoku.alphagomoku.cmd` / `XQ_GOMOKU_ALPHAGOMOKU_CMD`
 * Backward compatible:
 * - `xq.gomoku.piskvork.cmd` / `XQ_GOMOKU_PISKVORK_CMD` (treated as RAPFI cmd)
 */
public final class ConfigurableGomokuEngine implements GomokuEngine {
    private static final String PREF_BUILTIN = "BUILTIN";
    private static final String PREF_RAPFI = "RAPFI";
    private static final String PREF_ALPHA = "ALPHAGOMOKU";
    private static final String PREF_AUTO = "AUTO";

    private final BuiltinGomokuEngine builtin = new BuiltinGomokuEngine();
    private final String rapfiCmdText;
    private final String alphaCmdText;
    private final List<String> rapfiCmd;
    private final List<String> alphaCmd;
    private String preferredEngine;
    private GomokuEngine selected;
    private String selectedId;
    private String selectedText;

    public ConfigurableGomokuEngine() {
        this.rapfiCmdText = readSetting("xq.gomoku.rapfi.cmd", "XQ_GOMOKU_RAPFI_CMD",
            readSetting("xq.gomoku.piskvork.cmd", "XQ_GOMOKU_PISKVORK_CMD", "")).trim();
        this.alphaCmdText = readSetting("xq.gomoku.alphagomoku.cmd", "XQ_GOMOKU_ALPHAGOMOKU_CMD", "").trim();
        this.rapfiCmd = splitCommand(rapfiCmdText);
        this.alphaCmd = splitCommand(alphaCmdText);
        this.preferredEngine = normalizePreference(readSetting("xq.gomoku.engine", "XQ_GOMOKU_ENGINE", PREF_BUILTIN));
        selectEngineForPreference(preferredEngine);
    }

    @Override
    public synchronized int[] findBestMove(GomokuBoard board, GomokuStone aiStone, MinimaxAI.Difficulty difficulty) {
        if (selected == null) {
            selectEngineForPreference(preferredEngine);
        }
        if (selected != builtin) {
            int[] move;
            try {
                move = selected.findBestMove(board, aiStone, difficulty);
            } catch (Exception ignored) {
                move = null;
            }
            if (move != null) {
                return move;
            }
            selected.close();
            selected = builtin;
            selectedId = builtin.getEngineId();
            selectedText = builtin.getEngineText() + "（外部引擎异常已回退）";
        }
        return builtin.findBestMove(board, aiStone, difficulty);
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

    public boolean isRapfiConfigured() {
        return !rapfiCmd.isEmpty();
    }

    public boolean isAlphaGomokuConfigured() {
        return !alphaCmd.isEmpty();
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
        GomokuEngine next = chooseEngine(preference);
        if (next == null) {
            next = builtin;
        }
        selected = next;
        selectedId = next.getEngineId();
        selectedText = next.getEngineText();
    }

    private GomokuEngine chooseEngine(String preference) {
        if (PREF_BUILTIN.equals(preference)) {
            return builtin;
        }
        if (PREF_RAPFI.equals(preference)) {
            return createExternal("rapfi", "Rapfi", rapfiCmd);
        }
        if (PREF_ALPHA.equals(preference)) {
            return createExternal("alphagomoku", "AlphaGomoku", alphaCmd);
        }
        if (PREF_AUTO.equals(preference)) {
            GomokuEngine rapfi = createExternal("rapfi", "Rapfi", rapfiCmd);
            if (rapfi != null) {
                return rapfi;
            }
            GomokuEngine alpha = createExternal("alphagomoku", "AlphaGomoku", alphaCmd);
            if (alpha != null) {
                return alpha;
            }
            return builtin;
        }
        return builtin;
    }

    private GomokuEngine createExternal(String id, String name, List<String> cmd) {
        if (cmd == null || cmd.isEmpty()) {
            return null;
        }
        try {
            return new PiskvorkGomokuEngine(id, name, cmd);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizePreference(String prefRaw) {
        String p = prefRaw == null ? "" : prefRaw.trim().toUpperCase(Locale.ROOT);
        if (PREF_RAPFI.equals(p) || "PISKVORK".equals(p)) {
            return PREF_RAPFI;
        }
        if (PREF_ALPHA.equals(p) || "ALPHA".equals(p)) {
            return PREF_ALPHA;
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
