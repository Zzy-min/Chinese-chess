package com.xiangqi.online.game;

import java.util.List;
import java.util.Map;

public interface OnlineMatchEngine {
    GameType gameType();

    String currentTurnKey();

    String[][] board();

    MatchEvent previewMove(String actorUserId, Map<String, Object> movePayload);

    MatchEvent applyMove(String actorUserId, Map<String, Object> movePayload);

    boolean finished();

    String winnerSide();

    String resultText();

    List<Map<String, Object>> moves();

    default String inCheckSide() {
        return "";
    }

    default long stateId() {
        return 0;
    }
}
