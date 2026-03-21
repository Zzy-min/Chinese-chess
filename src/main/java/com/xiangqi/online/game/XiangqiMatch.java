package com.xiangqi.online.game;

import com.xiangqi.model.Board;
import com.xiangqi.model.Move;
import com.xiangqi.model.Piece;
import com.xiangqi.model.PieceColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class XiangqiMatch implements OnlineMatchEngine {
    private final MatchPlayer red;
    private final MatchPlayer black;
    private final Board board;

    public XiangqiMatch(MatchPlayer red, MatchPlayer black) {
        this.red = red;
        this.black = black;
        this.board = new Board();
    }

    @Override
    public GameType gameType() {
        return GameType.XIANGQI;
    }

    public MatchEvent applyMove(String actorUserId, XiangqiMoveInput input) {
        MatchEvent preview = previewMove(actorUserId, input);
        if (!preview.accepted()) {
            return preview;
        }
        Move move = new Move(input.fromRow(), input.fromCol(), input.toRow(), input.toCol());
        board.movePiece(move);
        return MatchEvent.accepted("move accepted");
    }

    @Override
    public MatchEvent previewMove(String actorUserId, Map<String, Object> movePayload) {
        return previewMove(actorUserId, new XiangqiMoveInput(
            asInt(movePayload.get("fromRow")),
            asInt(movePayload.get("fromCol")),
            asInt(movePayload.get("toRow")),
            asInt(movePayload.get("toCol"))
        ));
    }

    public MatchEvent previewMove(String actorUserId, XiangqiMoveInput input) {
        PlayerSide expected = currentTurn();
        if (expected == PlayerSide.RED && !red.userId().equals(actorUserId)) {
            return MatchEvent.rejected("not your turn");
        }
        if (expected == PlayerSide.BLACK && !black.userId().equals(actorUserId)) {
            return MatchEvent.rejected("not your turn");
        }
        Move move = new Move(input.fromRow(), input.fromCol(), input.toRow(), input.toCol());
        if (!board.isValidMove(move)) {
            return MatchEvent.rejected("illegal move");
        }
        return MatchEvent.accepted("move accepted");
    }

    @Override
    public MatchEvent applyMove(String actorUserId, Map<String, Object> movePayload) {
        return applyMove(actorUserId, new XiangqiMoveInput(
            asInt(movePayload.get("fromRow")),
            asInt(movePayload.get("fromCol")),
            asInt(movePayload.get("toRow")),
            asInt(movePayload.get("toCol"))
        ));
    }

    public PlayerSide currentTurn() {
        return board.getCurrentTurn() == PieceColor.RED ? PlayerSide.RED : PlayerSide.BLACK;
    }

    public Board boardState() {
        return board;
    }

    @Override
    public String currentTurnKey() {
        return currentTurn().name();
    }

    @Override
    public String[][] board() {
        String[][] cells = new String[Board.ROWS][Board.COLS];
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.getPiece(row, col);
                cells[row][col] = piece == null ? "" : piece.getType().getDisplayName();
            }
        }
        return cells;
    }

    @Override
    public boolean finished() {
        return board.isGameOver();
    }

    @Override
    public String winnerSide() {
        PieceColor winner = board.getWinner();
        return winner == null ? "" : winner.name();
    }

    @Override
    public String resultText() {
        return board.getGameResult();
    }

    @Override
    public List<Map<String, Object>> moves() {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        List<Move> history = board.getMoveHistory();
        for (int i = 0; i < history.size(); i++) {
            Move move = history.get(i);
            Piece piece = board.getBoardAtMove(i + 1).getPiece(move.getToRow(), move.getToCol());
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("index", i + 1);
            item.put("side", i % 2 == 0 ? "RED" : "BLACK");
            item.put("notation", displayNotation(piece, move));
            item.put("fromRow", move.getFromRow());
            item.put("fromCol", move.getFromCol());
            item.put("toRow", move.getToRow());
            item.put("toCol", move.getToCol());
            items.add(item);
        }
        return items;
    }

    private String displayNotation(Piece piece, Move move) {
        String name = piece == null ? "子" : piece.getType().getDisplayName();
        return name + " " + move.getFromRow() + "," + move.getFromCol() + " -> " + move.getToRow() + "," + move.getToCol();
    }

    private int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
