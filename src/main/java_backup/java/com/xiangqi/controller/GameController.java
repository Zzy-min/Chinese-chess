package com.xiangqi.controller;

import com.xiangqi.ai.MinimaxAI;
import com.xiangqi.model.*;
import com.xiangqi.ui.XiangqiPanel;

import javax.swing.*;
import java.util.Random;

/**
 * 游戏控制器 - 管理游戏逻辑和状态
 */
public class GameController {
    private XiangqiPanel panel;
    private MinimaxAI ai;
    private PieceColor aiColor;
    private boolean isRunning;

    // 悔棋相关
    private boolean isReviewMode; // 是否在棋盘回顾模式
    private int reviewMoveIndex; // 当前回顾的步数

    // 计时相关
    private static final int DEFAULT_TIME_LIMIT = 600; // 10分钟 = 600秒
    private int redTimeRemaining;
    private int blackTimeRemaining;
    private Timer timer;
    private long lastUpdateTime;
    private Runnable onTimeUpdate;
    private Runnable onGameOver;

    // 游戏状态
    private boolean hasRedSurrendered = false;
    private boolean hasBlackSurrendered = false;
    private boolean hasRedTimedOut = false;
    private boolean hasBlackTimedOut = false;

    public GameController(XiangqiPanel panel) {
        this.panel = panel;
        this.ai = new MinimaxAI();
        this.isRunning = true;
        this.isReviewMode = false;
        this.reviewMoveIndex = 0;
        initializeTimer();
    }

    private void initializeTimer() {
        timer = new Timer(100, e -> {
            if (!isRunning || panel.getBoard().isGameOver()) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            long deltaTime = (currentTime - lastUpdateTime) / 1000;

            if (deltaTime >= 1) {
                PieceColor currentTurn = panel.getBoard().getCurrentTurn();
                if (currentTurn == PieceColor.RED) {
                    redTimeRemaining -= deltaTime;
                    if (redTimeRemaining <= 0) {
                        redTimeRemaining = 0;
                        hasRedTimedOut = true;
                        endGame();
                    }
                } else {
                    blackTimeRemaining -= deltaTime;
                    if (blackTimeRemaining <= 0) {
                        blackTimeRemaining = 0;
                        hasBlackTimedOut = true;
                        endGame();
                    }
                }

                lastUpdateTime = currentTime;
                if (onTimeUpdate != null) {
                    onTimeUpdate.run();
                }
            }
        });
    }

    public void startTimer() {
        redTimeRemaining = DEFAULT_TIME_LIMIT;
        blackTimeRemaining = DEFAULT_TIME_LIMIT;
        lastUpdateTime = System.currentTimeMillis();
        timer.start();
    }

    public void stopTimer() {
        timer.stop();
    }

    public void resetTimer() {
        redTimeRemaining = DEFAULT_TIME_LIMIT;
        blackTimeRemaining = DEFAULT_TIME_LIMIT;
        lastUpdateTime = System.currentTimeMillis();
        if (onTimeUpdate != null) {
            onTimeUpdate.run();
        }
    }

    public int getRedTimeRemaining() {
        return redTimeRemaining;
    }

    public int getBlackTimeRemaining() {
        return blackTimeRemaining;
    }

    public String getFormattedTime(int seconds) {
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

    public void startPvPGame() {
        panel.setGameMode(XiangqiPanel.GameMode.PVP);
        panel.resetGame();

        hasRedSurrendered = false;
        hasBlackSurrendered = false;
        hasRedTimedOut = false;
        hasBlackTimedOut = false;

        resetTimer();
        startTimer();
    }

    public void startPvCGame() {
        // 人机模式，人类总是执红（棋盘下侧）
        panel.setGameMode(XiangqiPanel.GameMode.PVC);
        panel.setHumanColor(PieceColor.RED);
        aiColor = PieceColor.BLACK;

        panel.resetGame();

        hasRedSurrendered = false;
        hasBlackSurrendered = false;
        hasRedTimedOut = false;
        hasBlackTimedOut = false;

        resetTimer();
        startTimer();

        // 随机先手
        Random random = new Random();
        boolean redFirst = random.nextBoolean();
        panel.getBoard().setCurrentTurn(redFirst ? PieceColor.RED : PieceColor.BLACK);

        // 如果AI先手，让AI走第一步
        if (!redFirst) {
            makeAIMove();
        }

        panel.setOnMoveComplete(this::afterPlayerMove);
    }

    private void afterPlayerMove() {
        if (!isRunning) {
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
        if (!isRunning) {
            return;
        }

        SwingWorker<Move, Void> worker = new SwingWorker<Move, Void>() {
            @Override
            protected Move doInBackground() throws Exception {
                // 在后台线程执行AI计算
                return ai.findBestMove(panel.getBoard(), aiColor);
            }

            @Override
            protected void done() {
                try {
                    Move move = get();
                    SwingUtilities.invokeLater(() -> {
                        panel.makeAIMove(move);
                        if (panel.getBoard().isGameOver()) {
                            endGame();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    public void surrender() {
        PieceColor currentTurn = panel.getBoard().getCurrentTurn();
        if (currentTurn == PieceColor.RED) {
            hasRedSurrendered = true;
        } else {
            hasBlackSurrendered = true;
        }
        endGame();
    }

    private void endGame() {
        stopTimer();

        String result = getGameResult();

        if (onGameOver != null) {
            onGameOver.run();
        }

        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(panel, result, "游戏结束", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private String getGameResult() {
        // 检查认输
        if (hasRedSurrendered) {
            return "红方认输！黑方获胜";
        }
        if (hasBlackSurrendered) {
            return "黑方认输！红方获胜";
        }

        // 检查超时
        if (hasRedTimedOut) {
            return "红方超时！黑方获胜";
        }
        if (hasBlackTimedOut) {
            return "黑方超时！红方获胜";
        }

        // 检查棋盘游戏状态
        return panel.getBoard().getGameResult();
    }

    public void loadEndgame(String endgameName) {
        loadEndgame(endgameName, XiangqiPanel.GameMode.PVP);
    }

    public void loadEndgame(String endgameName, XiangqiPanel.GameMode mode) {
        panel.setGameMode(mode);
        EndgameLoader.loadEndgame(panel.getBoard(), endgameName);
        panel.repaint();

        hasRedSurrendered = false;
        hasBlackSurrendered = false;
        hasRedTimedOut = false;
        hasBlackTimedOut = false;

        resetTimer();
        startTimer();

        // 如果是PVC模式，设置AI相关属性
        if (mode == XiangqiPanel.GameMode.PVC) {
            panel.setHumanColor(PieceColor.RED);
            aiColor = PieceColor.BLACK;
            panel.setOnMoveComplete(this::afterPlayerMove);

            // 如果AI先手，让AI走第一步
            if (panel.getBoard().getCurrentTurn() == aiColor) {
                makeAIMove();
            }
        }
    }

    /**
     * 悔棋功能
     */
    public void undo() {
        if (!isRunning || isReviewMode) {
            return;
        }

        Board board = panel.getBoard();
        if (!board.canUndo()) {
            return;
        }

        // 人机模式下需要悔两步（玩家一步 + AI一步）
        if (panel.getGameMode() == XiangqiPanel.GameMode.PVC) {
            // 如果是AI的回合，玩家还没有走，只悔一步
            if (board.getCurrentTurn() == aiColor) {
                board.undoMove();
            } else {
                // 如果是玩家的回合，需要悔两步
                board.undoMove(); // 悔玩家的一步
                if (board.canUndo()) {
                    board.undoMove(); // 悔AI的一步
                }
            }
        } else {
            // 双人对战模式，悔一步
            board.undoMove();
        }

        // 悔棋后同步回合状态，确保可以继续操作
        panel.syncTurnState();
        panel.repaint();
    }

    /**
     * 开始棋盘回顾模式
     */
    public void startReview() {
        if (!isRunning) {
            return;
        }

        isReviewMode = true;
        reviewMoveIndex = 0;
        showReviewMove();
    }

    /**
     * 结束棋盘回顾模式，回到当前状态
     */
    public void endReview() {
        if (!isReviewMode) {
            return;
        }

        isReviewMode = false;
        reviewMoveIndex = 0;
        panel.setReviewBoard(null); // 清除回顾棋盘
        panel.repaint();
    }

    /**
     * 在回顾模式下显示指定步数的棋盘
     */
    public void showReviewMove() {
        if (!isReviewMode) {
            return;
        }

        Board reviewBoard = panel.getBoard().getBoardAtMove(reviewMoveIndex);
        panel.setReviewBoard(reviewBoard);
        panel.setReviewMoveIndex(reviewMoveIndex);
        panel.repaint();
    }

    /**
     * 回顾模式下前进到下一步
     */
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

    /**
     * 回顾模式下后退到上一步
     */
    public void reviewPrevious() {
        if (!isReviewMode) {
            return;
        }

        if (reviewMoveIndex > 0) {
            reviewMoveIndex--;
            showReviewMove();
        }
    }

    /**
     * 跳转到指定步数
     */
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

    /**
     * 检查是否可以悔棋
     */
    public boolean canUndo() {
        return panel.getBoard().canUndo();
    }

    /**
     * 检查是否在回顾模式
     */
    public boolean isReviewMode() {
        return isReviewMode;
    }

    /**
     * 获取当前棋盘移动历史是否为空
     */
    public boolean getCanUndo() {
        return panel.getBoard().canUndo();
    }

    public void stop() {
        isRunning = false;
        stopTimer();
    }
}
