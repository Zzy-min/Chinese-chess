package com.xiangqi.online.game;

import com.xiangqi.model.gomoku.GomokuBoard;
import com.xiangqi.model.gomoku.GomokuMove;
import com.xiangqi.model.gomoku.GomokuPlaceResult;
import com.xiangqi.model.gomoku.GomokuStone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GomokuMatch implements OnlineMatchEngine {
    private final MatchPlayer black;
    private final MatchPlayer white;
    private final GomokuBoard board;

    public GomokuMatch(MatchPlayer black, MatchPlayer white) {
        this.black = black;
        this.white = white;
        this.board = new GomokuBoard();
    }

    @Override
    public GameType gameType() {
        return GameType.GOMOKU;
    }

    @Override
    public String currentTurnKey() {
        return board.getCurrentTurn().name();
    }

    public GomokuBoard boardState() {
        return board;
    }

    @Override
    public String[][] board() {
        String[][] cells = new String[GomokuBoard.SIZE][GomokuBoard.SIZE];
        for (int row = 0; row < GomokuBoard.SIZE; row++) {
            for (int col = 0; col < GomokuBoard.SIZE; col++) {
                GomokuStone stone = board.getStone(row, col);
                cells[row][col] = stone == GomokuStone.EMPTY ? "" : stone.name();
            }
        }
        return cells;
    }

    @Override
    public MatchEvent previewMove(String actorUserId, Map<String, Object> movePayload) {
        GomokuStone turn = board.getCurrentTurn();
        if (turn == GomokuStone.BLACK && !black.userId().equals(actorUserId)) {
            return MatchEvent.rejected("not your turn");
        }
        if (turn == GomokuStone.WHITE && !white.userId().equals(actorUserId)) {
            return MatchEvent.rejected("not your turn");
        }
        int row = asInt(movePayload.get("row"));
        int col = asInt(movePayload.get("col"));
        GomokuPlaceResult result = board.place(row, col, true);
        if (!result.isSuccess()) {
            return MatchEvent.rejected(result.getReason());
        }
        board.undoMove();
        return MatchEvent.accepted("move accepted");
    }

    @Override
    public MatchEvent applyMove(String actorUserId, Map<String, Object> movePayload) {
        MatchEvent preview = previewMove(actorUserId, movePayload);
        if (!preview.accepted()) {
            return preview;
        }
        int row = asInt(movePayload.get("row"));
        int col = asInt(movePayload.get("col"));
        GomokuPlaceResult result = board.place(row, col, true);
        if (!result.isSuccess()) {
            return MatchEvent.rejected(result.getReason());
        }
        return MatchEvent.accepted("move accepted");
    }

    @Override
    public boolean finished() {
        return board.isGameOver();
    }

    @Override
    public String winnerSide() {
        GomokuStone winner = board.getWinner();
        return winner == GomokuStone.EMPTY ? "" : winner.name();
    }

    @Override
    public String resultText() {
        return board.getGameResult();
    }

    @Override
    public List<Map<String, Object>> moves() {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        int index = 1;
        for (GomokuMove move : board.getMoveHistory()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("index", index++);
            item.put("side", move.getStone().name());
            item.put("notation", move.getStone().name() + " " + move.getRow() + "," + move.getCol());
            item.put("row", move.getRow());
            item.put("col", move.getCol());
            items.add(item);
        }
        return items;
    }

    private int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
