package com.xiangqi.web.runtime;

import com.xiangqi.ai.MinimaxAI;
import com.xiangqi.model.go.GoBoard;
import com.xiangqi.model.go.GoEngine;
import com.xiangqi.model.go.GoEngineMove;
import com.xiangqi.model.go.GoMoveResult;
import com.xiangqi.model.go.GoScenario;
import com.xiangqi.model.go.GoScenarioLoader;
import com.xiangqi.model.go.GoScoreSummary;
import com.xiangqi.model.go.GoStone;

import java.util.List;

public final class GoRuntime {
    private static final int BOARD_SIZE = 19;
    private static final double KOMI = 7.5d;

    private final GoEngine engine;
    private GoBoard board = new GoBoard(BOARD_SIZE, KOMI);
    private boolean started;
    private boolean pvcMode;
    private MinimaxAI.Difficulty difficulty = MinimaxAI.Difficulty.MEDIUM;
    private GoStone humanStone = GoStone.BLACK;
    private boolean reviewMode;
    private int reviewMoveIndex;
    private String scenarioName = "标准开局";

    public GoRuntime(GoEngine engine) {
        this.engine = engine;
    }

    public void reset(GameOptions options) {
        board = new GoBoard(BOARD_SIZE, KOMI);
        started = true;
        reviewMode = false;
        reviewMoveIndex = 0;
        difficulty = options.getDifficulty();
        humanStone = options.isHumanFirst() ? GoStone.BLACK : GoStone.WHITE;
        pvcMode = options.isPvcMode() && engine != null && engine.isAvailable();
        scenarioName = "标准开局";
        maybePlayAiMove();
    }

    public void loadScenario(String name, GameOptions options) {
        GoScenario scenario = GoScenarioLoader.findByName(name);
        board = new GoBoard(BOARD_SIZE, KOMI);
        if (scenario != null) {
            board.loadPosition(scenario.getRows(), scenario.getTurn());
            scenarioName = scenario.getName();
        } else {
            scenarioName = "标准开局";
        }
        started = true;
        reviewMode = false;
        reviewMoveIndex = 0;
        difficulty = options.getDifficulty();
        humanStone = options.isHumanFirst() ? GoStone.BLACK : GoStone.WHITE;
        pvcMode = options.isPvcMode() && engine != null && engine.isAvailable();
        maybePlayAiMove();
    }

    public void click(int row, int col) {
        if (!started || reviewMode) {
            return;
        }
        if (pvcMode && board.getCurrentTurn() != humanStone) {
            return;
        }
        GoMoveResult result = board.place(row, col);
        if (result.isSuccess()) {
            maybePlayAiMove();
        }
    }

    public void pass() {
        if (!started || reviewMode) {
            return;
        }
        if (pvcMode && board.getCurrentTurn() != humanStone) {
            return;
        }
        GoMoveResult result = board.pass();
        if (result.isSuccess()) {
            maybePlayAiMove();
        }
    }

    public void undo() {
        if (!started || reviewMode || !board.canUndo()) {
            return;
        }
        board.undoMove();
        if (pvcMode && board.canUndo()) {
            board.undoMove();
        }
    }

    public void startReview() {
        if (!started || !board.canUndo()) {
            return;
        }
        reviewMode = true;
        reviewMoveIndex = 0;
    }

    public void exitReview() {
        reviewMode = false;
        reviewMoveIndex = 0;
    }

    public void reviewPrev() {
        if (reviewMode && reviewMoveIndex > 0) {
            reviewMoveIndex--;
        }
    }

    public void reviewNext() {
        if (reviewMode && reviewMoveIndex < board.getMoveCount()) {
            reviewMoveIndex++;
        }
    }

    public boolean isPvcMode() {
        return pvcMode;
    }

    public String toJson(long seq) {
        GoStone[][] boardToDraw = reviewMode ? board.getBoardAtMove(reviewMoveIndex) : board.getBoardAtMove(board.getMoveCount());
        if (boardToDraw == null) {
            boardToDraw = board.getBoardAtMove(0);
        }
        GoScoreSummary summary = board.getScoreSummary();
        StringBuilder sb = new StringBuilder(8192);
        sb.append('{');
        sb.append("\"seq\":").append(seq).append(',');
        sb.append("\"gameType\":\"GO\",");
        sb.append("\"boardSize\":").append(board.getSize()).append(',');
        sb.append("\"boardRows\":").append(board.getSize()).append(',');
        sb.append("\"boardCols\":").append(board.getSize()).append(',');
        sb.append("\"ruleset\":\"go_cn_area_komi_7_5\",");
        sb.append("\"started\":").append(started).append(',');
        sb.append("\"mode\":\"").append(pvcMode ? "PVC" : "PVP").append("\",");
        sb.append("\"difficulty\":\"").append(difficulty.name()).append("\",");
        sb.append("\"difficultyText\":\"").append(difficulty.getDisplayName()).append("\",");
        sb.append("\"pvcHumanColor\":\"").append(humanStone.name()).append("\",");
        sb.append("\"endgame\":\"").append(escape(scenarioName)).append("\",");
        sb.append("\"currentTurn\":\"").append(board.getCurrentTurn().name()).append("\",");
        sb.append("\"gameOver\":false,");
        sb.append("\"canDraw\":false,");
        sb.append("\"result\":\"").append(summary == null ? "" : escape(summary.getResultText())).append("\",");
        sb.append("\"drawReason\":\"\",");
        sb.append("\"selectedRow\":-1,");
        sb.append("\"selectedCol\":-1,");
        sb.append("\"canReview\":").append(board.canUndo()).append(',');
        sb.append("\"reviewMode\":").append(reviewMode).append(',');
        sb.append("\"reviewMoveIndex\":").append(reviewMoveIndex).append(',');
        sb.append("\"reviewMaxMove\":").append(board.getMoveCount()).append(',');
        sb.append("\"stepRemainSec\":-1,");
        sb.append("\"redTotalSec\":-1,");
        sb.append("\"blackTotalSec\":-1,");
        sb.append("\"tacticText\":\"\",");
        sb.append("\"tacticSeq\":0,");
        appendRecentMoves(sb);
        sb.append(',');
        sb.append("\"go\":{");
        sb.append("\"komi\":").append(board.getKomi()).append(',');
        sb.append("\"engineAvailable\":").append(engine != null && engine.isAvailable()).append(',');
        sb.append("\"scenarioName\":\"").append(escape(scenarioName)).append("\",");
        sb.append("\"consecutivePasses\":").append(board.getConsecutivePasses()).append(',');
        sb.append("\"captures\":{\"black\":").append(board.getBlackCaptures()).append(",\"white\":").append(board.getWhiteCaptures()).append("},");
        sb.append("\"score\":");
        if (summary == null) {
            sb.append("null");
        } else {
            sb.append('{');
            sb.append("\"blackArea\":").append(summary.getBlackArea()).append(',');
            sb.append("\"whiteArea\":").append(summary.getWhiteArea()).append(',');
            sb.append("\"komi\":").append(summary.getKomi()).append(',');
            sb.append("\"finalScore\":").append(summary.getFinalScore()).append(',');
            sb.append("\"winner\":\"").append(summary.getWinner()).append("\",");
            sb.append("\"resultText\":\"").append(escape(summary.getResultText())).append("\"");
            sb.append('}');
        }
        sb.append("},");
        sb.append("\"board\":[");
        for (int row = 0; row < board.getSize(); row++) {
            if (row > 0) {
                sb.append(',');
            }
            sb.append('[');
            for (int col = 0; col < board.getSize(); col++) {
                if (col > 0) {
                    sb.append(',');
                }
                GoStone stone = boardToDraw[row][col];
                if (stone == GoStone.EMPTY) {
                    sb.append("null");
                } else {
                    sb.append("{\"name\":\"").append(stone.getDisplayText()).append("\",\"color\":\"").append(stone.name()).append("\"}");
                }
            }
            sb.append(']');
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    private void maybePlayAiMove() {
        if (!pvcMode || engine == null || !engine.isAvailable() || board.getCurrentTurn() == humanStone) {
            return;
        }
        GoEngineMove move = engine.genMove(board, board.getCurrentTurn(), difficulty);
        if (move == null) {
            pvcMode = false;
            return;
        }
        if (move.isPass()) {
            board.pass();
            return;
        }
        GoMoveResult result = board.place(move.getRow(), move.getCol());
        if (!result.isSuccess()) {
            pvcMode = false;
        }
    }

    private void appendRecentMoves(StringBuilder sb) {
        List<GoBoard.GoHistoryEntry> history = board.getMoveHistory();
        sb.append("\"recentMoves\":[");
        int total = reviewMode ? Math.min(reviewMoveIndex, history.size()) : history.size();
        int show = Math.min(2, total);
        for (int i = 0; i < show; i++) {
            if (i > 0) {
                sb.append(',');
            }
            GoBoard.GoHistoryEntry move = history.get(total - 1 - i);
            sb.append('{');
            sb.append("\"order\":").append(i + 1).append(',');
            sb.append("\"color\":\"").append(move.getStone().name()).append("\",");
            sb.append("\"fromRow\":").append(move.isPass() ? -1 : move.getRow()).append(',');
            sb.append("\"fromCol\":").append(move.isPass() ? -1 : move.getCol()).append(',');
            sb.append("\"toRow\":").append(move.isPass() ? -1 : move.getRow()).append(',');
            sb.append("\"toCol\":").append(move.isPass() ? -1 : move.getCol()).append(',');
            sb.append("\"pass\":").append(move.isPass());
            sb.append('}');
        }
        sb.append(']');
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
