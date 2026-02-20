package com.xiangqi.controller;

import com.xiangqi.ai.MinimaxAI;
import com.xiangqi.model.*;
import com.xiangqi.ui.XiangqiPanel;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏控制器 - 管理游戏逻辑和状态
 */
public class GameController {
    private static final long MIN_MOVE_INTERVAL_MS = 500L;

    public enum TimeControl {
        TEN_MIN("10分钟", 10 * 60),
        TWENTY_MIN("20分钟", 20 * 60),
        UNLIMITED("无限", -1);

        private final String displayName;
        private final int totalSeconds;

        TimeControl(String displayName, int totalSeconds) {
            this.displayName = displayName;
            this.totalSeconds = totalSeconds;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getTotalSeconds() {
            return totalSeconds;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private final XiangqiPanel panel;
    private final MinimaxAI ai;
    private PieceColor aiColor;
    private MinimaxAI.Difficulty aiDifficulty = MinimaxAI.Difficulty.MEDIUM;
    private boolean pvcHumanFirst = true;
    private boolean isRunning;
    private boolean gameEnded;

    // 悔棋与回顾
    private boolean isReviewMode;
    private int reviewMoveIndex;

    // 计时相关
    private TimeControl timeControl = TimeControl.TEN_MIN;
    private long redTimeRemainingMs;
    private long blackTimeRemainingMs;
    private long redStepElapsedMs;
    private long blackStepElapsedMs;
    private int redCompletedMoves;
    private int blackCompletedMoves;
    private PieceColor initialTurn;
    private PieceColor trackedTurn;
    private final Timer timer;
    private long lastUpdateTime;
    private Runnable onTimeUpdate;
    private Runnable onGameOver;

    // 游戏状态
    private boolean hasRedSurrendered;
    private boolean hasBlackSurrendered;
    private boolean hasRedTimedOut;
    private boolean hasBlackTimedOut;
    private boolean hasRedStepTimedOut;
    private boolean hasBlackStepTimedOut;
    private boolean agreedDraw;
    private boolean autoDraw;
    private String drawReason = "";
    private int noCaptureHalfMoves;
    private final Map<String, Integer> positionCount = new HashMap<>();

    public GameController(XiangqiPanel panel) {
        this.panel = panel;
        this.ai = new MinimaxAI();
        this.ai.setDifficulty(aiDifficulty);
        this.isRunning = true;
        this.gameEnded = false;
        this.isReviewMode = false;
        this.reviewMoveIndex = 0;
        this.timer = createTimer();
        resetTimer();
    }

    private Timer createTimer() {
        return new Timer(100, e -> {
            if (!isRunning || gameEnded || panel.getBoard().isGameOver()) {
                return;
            }

            long now = System.currentTimeMillis();
            if (lastUpdateTime <= 0L) {
                lastUpdateTime = now;
                return;
            }

            PieceColor currentTurn = panel.getBoard().getCurrentTurn();
            if (trackedTurn != currentTurn) {
                onTurnSwitched(trackedTurn, currentTurn);
                trackedTurn = currentTurn;
            }

            long deltaMs = now - lastUpdateTime;
            if (deltaMs <= 0L) {
                return;
            }
            lastUpdateTime = now;

            if (panel.getGameMode() != XiangqiPanel.GameMode.PVC && !isUnlimitedTime()) {
                if (currentTurn == PieceColor.RED) {
                    redTimeRemainingMs -= deltaMs;
                    if (redTimeRemainingMs <= 0) {
                        redTimeRemainingMs = 0;
                        hasRedTimedOut = true;
                        endGame();
                        return;
                    }
                } else {
                    blackTimeRemainingMs -= deltaMs;
                    if (blackTimeRemainingMs <= 0) {
                        blackTimeRemainingMs = 0;
                        hasBlackTimedOut = true;
                        endGame();
                        return;
                    }
                }
            }

            if (panel.getGameMode() != XiangqiPanel.GameMode.PVC && isStepLimitEnabled()) {
                if (currentTurn == PieceColor.RED) {
                    redStepElapsedMs += deltaMs;
                    if (redStepElapsedMs > getStepLimitMs(PieceColor.RED)) {
                        hasRedStepTimedOut = true;
                        endGame();
                        return;
                    }
                } else {
                    blackStepElapsedMs += deltaMs;
                    if (blackStepElapsedMs > getStepLimitMs(PieceColor.BLACK)) {
                        hasBlackStepTimedOut = true;
                        endGame();
                        return;
                    }
                }
            }

            if (onTimeUpdate != null) {
                onTimeUpdate.run();
            }
        });
    }

    private void onTurnSwitched(PieceColor previousTurn, PieceColor currentTurn) {
        if (previousTurn == null || currentTurn == null || previousTurn == currentTurn) {
            return;
        }

        if (previousTurn == PieceColor.RED) {
            redCompletedMoves++;
            blackStepElapsedMs = 0L;
        } else {
            blackCompletedMoves++;
            redStepElapsedMs = 0L;
        }
    }

    public void startTimer() {
        lastUpdateTime = System.currentTimeMillis();
        timer.start();
    }

    public void stopTimer() {
        timer.stop();
    }

    public void resetTimer() {
        long totalMs = isUnlimitedTime() ? Long.MAX_VALUE : (long) timeControl.getTotalSeconds() * 1000L;
        redTimeRemainingMs = totalMs;
        blackTimeRemainingMs = totalMs;
        redStepElapsedMs = 0L;
        blackStepElapsedMs = 0L;
        redCompletedMoves = 0;
        blackCompletedMoves = 0;
        initialTurn = panel.getBoard().getCurrentTurn();
        trackedTurn = initialTurn;
        lastUpdateTime = System.currentTimeMillis();
        if (onTimeUpdate != null) {
            onTimeUpdate.run();
        }
    }

    private boolean isStepLimitEnabled() {
        return panel.getGameMode() == XiangqiPanel.GameMode.PVP && timeControl != TimeControl.UNLIMITED;
    }

    private long getStepLimitMs(PieceColor color) {
        int completed = color == PieceColor.RED ? redCompletedMoves : blackCompletedMoves;
        return completed < 3 ? 30_000L : 60_000L;
    }

    public int getRedTimeRemaining() {
        if (panel.getGameMode() == XiangqiPanel.GameMode.PVC) {
            return -1;
        }
        return isUnlimitedTime() ? -1 : (int) Math.max(0, (redTimeRemainingMs + 999L) / 1000L);
    }

    public int getBlackTimeRemaining() {
        if (panel.getGameMode() == XiangqiPanel.GameMode.PVC) {
            return -1;
        }
        return isUnlimitedTime() ? -1 : (int) Math.max(0, (blackTimeRemainingMs + 999L) / 1000L);
    }

    public int getCurrentMoveRemainingSeconds(PieceColor color) {
        if (panel.getGameMode() == XiangqiPanel.GameMode.PVC || !isStepLimitEnabled()) {
            return -1;
        }
        long elapsed = color == PieceColor.RED ? redStepElapsedMs : blackStepElapsedMs;
        long remainMs = Math.max(0L, getStepLimitMs(color) - elapsed);
        return (int) ((remainMs + 999L) / 1000L);
    }

    public String getFormattedTime(int seconds) {
        if (seconds < 0) {
            return "∞";
        }
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    public void setOnTimeUpdate(Runnable callback) {
        this.onTimeUpdate = callback;
    }

    public void setOnGameOver(Runnable callback) {
        this.onGameOver = callback;
    }

    public MinimaxAI.Difficulty getAiDifficulty() {
        return aiDifficulty;
    }

    public boolean isPvcHumanFirst() {
        return pvcHumanFirst;
    }

    public void setAiDifficulty(MinimaxAI.Difficulty aiDifficulty) {
        if (aiDifficulty != null) {
            this.aiDifficulty = aiDifficulty;
            this.ai.setDifficulty(aiDifficulty);
        }
    }

    public TimeControl getTimeControl() {
        return timeControl;
    }

    public void setTimeControl(TimeControl control) {
        if (control != null) {
            this.timeControl = control;
            resetTimer();
        }
    }

    public boolean isUnlimitedTime() {
        return timeControl == TimeControl.UNLIMITED;
    }

    public void startPvPGame() {
        startPvPGame(timeControl);
    }

    public void startPvPGame(TimeControl control) {
        if (control != null) {
            this.timeControl = control;
        }

        panel.setGameMode(XiangqiPanel.GameMode.PVP);
        panel.resetGame();
        panel.setInteractionEnabled(true);

        resetGameFlags();

        resetTimer();
        startTimer();
        panel.setOnMoveComplete(null);
        initDrawTracking();
    }

    public void startPvCGame() {
        startPvCGame(aiDifficulty, pvcHumanFirst);
    }

    public void startPvCGame(MinimaxAI.Difficulty difficulty) {
        startPvCGame(difficulty, pvcHumanFirst);
    }

    public void startPvCGame(MinimaxAI.Difficulty difficulty, boolean humanFirst) {
        setAiDifficulty(difficulty);
        pvcHumanFirst = humanFirst;

        panel.setGameMode(XiangqiPanel.GameMode.PVC);
        panel.setHumanColor(humanFirst ? PieceColor.RED : PieceColor.BLACK);
        aiColor = humanFirst ? PieceColor.BLACK : PieceColor.RED;

        panel.resetGame();
        panel.setInteractionEnabled(true);

        resetGameFlags();

        resetTimer();
        startTimer();

        // 红方先行。若人类后手，则AI执红先走。
        panel.getBoard().setCurrentTurn(PieceColor.RED);
        trackedTurn = panel.getBoard().getCurrentTurn();
        initialTurn = trackedTurn;
        initDrawTracking();

        panel.setOnMoveComplete(this::afterPlayerMove);

        if (panel.getBoard().getCurrentTurn() == aiColor) {
            makeAIMove();
        }
    }

    private void resetGameFlags() {
        hasRedSurrendered = false;
        hasBlackSurrendered = false;
        hasRedTimedOut = false;
        hasBlackTimedOut = false;
        hasRedStepTimedOut = false;
        hasBlackStepTimedOut = false;
        agreedDraw = false;
        autoDraw = false;
        drawReason = "";
        noCaptureHalfMoves = 0;
        positionCount.clear();
        gameEnded = false;
        isReviewMode = false;
        reviewMoveIndex = 0;
        panel.setReviewBoard(null);
    }

    private void afterPlayerMove() {
        if (!isRunning || gameEnded) {
            return;
        }

        updateAutoDrawStateAfterMove();
        if (autoDraw) {
            endGame();
            return;
        }

        if (panel.getBoard().isGameOver()) {
            endGame();
            return;
        }

        if (panel.getBoard().getCurrentTurn() == aiColor) {
            makeAIMove();
        }
    }

    private void makeAIMove() {
        if (!isRunning || gameEnded) {
            return;
        }

        SwingWorker<Move, Void> worker = new SwingWorker<Move, Void>() {
            @Override
            protected Move doInBackground() {
                return ai.findBestMove(panel.getBoard(), aiColor);
            }

            @Override
            protected void done() {
                try {
                    Move move = get();
                    SwingUtilities.invokeLater(() -> {
                        if (gameEnded || !isRunning) {
                            return;
                        }
                        long wait = MIN_MOVE_INTERVAL_MS - (System.currentTimeMillis() - panel.getLastMoveTimestamp());
                        int delay = (int) Math.max(0L, wait);
                        Timer delayTimer = new Timer(delay, evt -> {
                            if (gameEnded || !isRunning) {
                                return;
                            }
                            panel.makeAIMove(move);
                            updateAutoDrawStateAfterMove();
                            if (autoDraw) {
                                endGame();
                                return;
                            }
                            if (panel.getBoard().isGameOver()) {
                                endGame();
                            }
                        });
                        delayTimer.setRepeats(false);
                        delayTimer.start();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    public void surrender() {
        if (gameEnded) {
            return;
        }
        PieceColor currentTurn = panel.getBoard().getCurrentTurn();
        if (currentTurn == PieceColor.RED) {
            hasRedSurrendered = true;
        } else {
            hasBlackSurrendered = true;
        }
        endGame();
    }

    public void agreeDraw() {
        if (gameEnded || panel.getGameMode() != XiangqiPanel.GameMode.PVP) {
            return;
        }
        agreedDraw = true;
        drawReason = "双方议和，和棋";
        endGame();
    }

    private void endGame() {
        if (gameEnded) {
            return;
        }

        gameEnded = true;
        panel.setInteractionEnabled(false);
        panel.setHumanTurn(false);
        panel.setOnMoveComplete(null);
        stopTimer();

        String result = getGameResult();

        if (onGameOver != null) {
            onGameOver.run();
        }

        SwingUtilities.invokeLater(() ->
            JOptionPane.showMessageDialog(panel, result, "游戏结束", JOptionPane.INFORMATION_MESSAGE)
        );
    }

    private String getGameResult() {
        if (hasRedSurrendered) {
            return "红方认输！黑方获胜";
        }
        if (hasBlackSurrendered) {
            return "黑方认输！红方获胜";
        }

        if (hasRedTimedOut) {
            return "红方总时超时！黑方获胜";
        }
        if (hasBlackTimedOut) {
            return "黑方总时超时！红方获胜";
        }
        if (hasRedStepTimedOut) {
            return "红方步时超限！黑方获胜";
        }
        if (hasBlackStepTimedOut) {
            return "黑方步时超限！红方获胜";
        }

        if (agreedDraw || autoDraw) {
            return drawReason == null || drawReason.isEmpty() ? "和棋" : drawReason;
        }

        return panel.getBoard().getGameResult();
    }

    public String getGameResultText() {
        return getGameResult();
    }

    public boolean isDrawResult() {
        return agreedDraw || autoDraw;
    }

    public String getDrawReason() {
        if (!isDrawResult()) {
            return "";
        }
        return drawReason == null ? "" : drawReason;
    }

    public void loadEndgame(String endgameName) {
        loadEndgame(endgameName, XiangqiPanel.GameMode.PVP);
    }

    public void loadEndgame(String endgameName, XiangqiPanel.GameMode mode) {
        loadEndgame(endgameName, mode, aiDifficulty);
    }

    public void loadEndgame(String endgameName, XiangqiPanel.GameMode mode, MinimaxAI.Difficulty difficulty) {
        setAiDifficulty(difficulty);

        panel.setGameMode(mode);
        panel.setInteractionEnabled(true);
        EndgameLoader.loadEndgame(panel.getBoard(), endgameName);
        panel.repaint();

        resetGameFlags();

        resetTimer();
        startTimer();

        if (mode == XiangqiPanel.GameMode.PVC) {
            panel.setHumanColor(PieceColor.RED);
            aiColor = PieceColor.BLACK;
            panel.setOnMoveComplete(this::afterPlayerMove);

            if (panel.getBoard().getCurrentTurn() == aiColor) {
                makeAIMove();
            }
        } else {
            panel.setOnMoveComplete(null);
        }
        initDrawTracking();
    }

    public void undo() {
        if (!isRunning || isReviewMode || gameEnded) {
            return;
        }

        Board board = panel.getBoard();
        if (!board.canUndo()) {
            return;
        }

        if (panel.getGameMode() == XiangqiPanel.GameMode.PVC) {
            if (board.getCurrentTurn() == aiColor) {
                board.undoMove();
            } else {
                board.undoMove();
                if (board.canUndo()) {
                    board.undoMove();
                }
            }
        } else {
            board.undoMove();
        }

        recalcMoveCountersFromBoard();
        trackedTurn = board.getCurrentTurn();
        if (trackedTurn == PieceColor.RED) {
            redStepElapsedMs = 0L;
        } else {
            blackStepElapsedMs = 0L;
        }
        lastUpdateTime = System.currentTimeMillis();

        panel.syncTurnState();
        agreedDraw = false;
        autoDraw = false;
        drawReason = "";
        initDrawTracking();
        panel.repaint();
    }

    private void recalcMoveCountersFromBoard() {
        List<Move> history = panel.getBoard().getMoveHistory();
        int redMoves = 0;
        int blackMoves = 0;
        PieceColor mover = initialTurn == null ? PieceColor.RED : initialTurn;
        for (int i = 0; i < history.size(); i++) {
            if (mover == PieceColor.RED) {
                redMoves++;
            } else {
                blackMoves++;
            }
            mover = mover.opposite();
        }
        redCompletedMoves = redMoves;
        blackCompletedMoves = blackMoves;
    }

    private void initDrawTracking() {
        noCaptureHalfMoves = 0;
        positionCount.clear();
        positionCount.put(buildPositionKey(panel.getBoard()), 1);
    }

    private void updateAutoDrawStateAfterMove() {
        Board board = panel.getBoard();
        Move lastMove = board.getLastMove();
        if (lastMove == null) {
            return;
        }
        if (lastMove.getCapturedPiece() == null) {
            noCaptureHalfMoves++;
        } else {
            noCaptureHalfMoves = 0;
        }

        String key = buildPositionKey(board);
        int seen = positionCount.getOrDefault(key, 0) + 1;
        positionCount.put(key, seen);

        if (panel.getGameMode() != XiangqiPanel.GameMode.PVC || gameEnded) {
            return;
        }

        if (noCaptureHalfMoves >= 120) {
            autoDraw = true;
            drawReason = "自动判和：连续60回合无吃子";
            return;
        }
        if (seen >= 3) {
            autoDraw = true;
            drawReason = "自动判和：三次重复局面";
        }
    }

    private String buildPositionKey(Board board) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(board.getCurrentTurn().name()).append('|');
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.getPiece(r, c);
                if (p == null) {
                    sb.append('.');
                } else {
                    sb.append(p.getColor() == PieceColor.RED ? 'R' : 'B');
                    sb.append(p.getType().ordinal());
                }
            }
            sb.append('/');
        }
        return sb.toString();
    }

    public void startReview() {
        if (!isRunning) {
            return;
        }

        isReviewMode = true;
        reviewMoveIndex = 0;
        showReviewMove();
    }

    public void endReview() {
        if (!isReviewMode) {
            return;
        }

        isReviewMode = false;
        reviewMoveIndex = 0;
        panel.setReviewBoard(null);
        panel.repaint();
    }

    public void showReviewMove() {
        if (!isReviewMode) {
            return;
        }

        Board reviewBoard = panel.getBoard().getBoardAtMove(reviewMoveIndex);
        panel.setReviewBoard(reviewBoard);
        panel.setReviewMoveIndex(reviewMoveIndex);
        panel.repaint();
    }

    public void reviewNext() {
        if (!isReviewMode) {
            return;
        }

        int maxMove = panel.getBoard().getMoveCount();
        if (reviewMoveIndex < maxMove) {
            reviewMoveIndex++;
            showReviewMove();
        }
    }

    public void reviewPrevious() {
        if (!isReviewMode) {
            return;
        }

        if (reviewMoveIndex > 0) {
            reviewMoveIndex--;
            showReviewMove();
        }
    }

    public void reviewJumpTo(int moveIndex) {
        if (!isReviewMode) {
            return;
        }

        int maxMove = panel.getBoard().getMoveCount();
        if (moveIndex >= 0 && moveIndex <= maxMove) {
            reviewMoveIndex = moveIndex;
            showReviewMove();
        }
    }

    public boolean canUndo() {
        return !gameEnded && panel.getBoard().canUndo();
    }

    public boolean isReviewMode() {
        return isReviewMode;
    }

    public boolean getCanUndo() {
        return panel.getBoard().canUndo();
    }

    public boolean isGameEnded() {
        return gameEnded;
    }

    public void stop() {
        isRunning = false;
        gameEnded = true;
        panel.setInteractionEnabled(false);
        stopTimer();
    }
}
