package com.xiangqi.ui;

import com.xiangqi.controller.GameController;
import com.xiangqi.model.PieceColor;

import javax.swing.*;
import java.awt.*;

/**
 * 主窗口 - 包含棋盘和控制菜单
 */
public class XiangqiFrame extends JFrame {
    private XiangqiPanel boardPanel;
    private GameController controller;
    private JLabel redTimeLabel;
    private JLabel blackTimeLabel;
    private JLabel statusLabel;

    // 功能面板组件
    private JPanel functionPanel;
    private JButton undoButton;
    private JButton surrenderButton;
    private JButton reviewButton;
    private JButton reviewPrevButton;
    private JButton reviewNextButton;
    private JButton reviewExitButton;
    private JLabel reviewStepLabel;

    // 棋盘回顾控制面板
    private JPanel reviewControlPanel;

    public XiangqiFrame() {
        setTitle("中国象棋 - 经典对弈");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        boardPanel = new XiangqiPanel(XiangqiPanel.GameMode.PVP, PieceColor.RED);
        controller = new GameController(boardPanel);

        setupTimerCallbacks();
        setupMenuBar();
        setupLayout();

        pack();
        setLocationRelativeTo(null);
        initializeDisplay();
    }

    private void setupTimerCallbacks() {
        controller.setOnTimeUpdate(this::updateTimerDisplay);
        controller.setOnGameOver(this::updateFunctionPanel);
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // 游戏菜单
        JMenu gameMenu = new JMenu("游戏");

        JMenuItem pvpItem = new JMenuItem("双人对战");
        pvpItem.addActionListener(e -> controller.startPvPGame());
        gameMenu.add(pvpItem);

        JMenuItem pvcItem = new JMenuItem("人机对战");
        pvcItem.addActionListener(e -> controller.startPvCGame());
        gameMenu.add(pvcItem);

        gameMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> {
            controller.stop();
            System.exit(0);
        });
        gameMenu.add(exitItem);

        // 残局菜单
        JMenu endgameMenu = new JMenu("残局");

        JMenuItem sevenStarsItem = new JMenuItem("七星聚会");
        sevenStarsItem.addActionListener(e -> showEndgameModeDialog("七星聚会"));
        endgameMenu.add(sevenStarsItem);

        JMenuItem wormItem = new JMenuItem("蚯蚓降龙");
        wormItem.addActionListener(e -> showEndgameModeDialog("蚯蚓降龙"));
        endgameMenu.add(wormItem);

        JMenuItem thousandLiItem = new JMenuItem("千里独行");
        thousandLiItem.addActionListener(e -> showEndgameModeDialog("千里独行"));
        endgameMenu.add(thousandLiItem);

        JMenuItem wildHorseItem = new JMenuItem("野马操田");
        wildHorseItem.addActionListener(e -> showEndgameModeDialog("野马操田"));
        endgameMenu.add(wildHorseItem);

        // 帮助菜单
        JMenu helpMenu = new JMenu("帮助");

        JMenuItem aboutItem = new JMenuItem("关于");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);

        JMenuItem rulesItem = new JMenuItem("游戏规则");
        rulesItem.addActionListener(e -> showRulesDialog());
        helpMenu.add(rulesItem);

        menuBar.add(gameMenu);
        menuBar.add(endgameMenu);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private void setupLayout() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // 创建顶部状态栏 - 计时器和状态信息
        JPanel topBar = createTopBar();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        add(topBar, gbc);

        // 创建中间主区域 - 棋盘居中
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(new Color(240, 235, 225));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(centerPanel, gbc);

        // 棋盘添加到中间面板
        GridBagConstraints boardGbc = new GridBagConstraints();
        boardGbc.gridx = 0;
        boardGbc.gridy = 0;
        boardGbc.insets = new Insets(15, 15, 15, 15);
        centerPanel.add(boardPanel, boardGbc);

        // 创建底部功能面板 - 与棋盘完全分离
        functionPanel = createFunctionPanel();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(functionPanel, gbc);
    }

    private JPanel createTopBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        panel.setBackground(new Color(250, 245, 235));

        // 左侧：状态显示
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        leftPanel.setBackground(new Color(250, 245, 235));

        statusLabel = new JLabel("正常对弈中");
        statusLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        statusLabel.setForeground(new Color(80, 50, 30));
        leftPanel.add(statusLabel);

        // 右侧：计时器
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightPanel.setBackground(new Color(250, 245, 235));

        JPanel redTimerPanel = createTimerPanel("红方", new Color(180, 50, 50));
        redTimeLabel = (JLabel) redTimerPanel.getComponent(1);

        JPanel blackTimerPanel = createTimerPanel("黑方", new Color(50, 50, 50));
        blackTimeLabel = (JLabel) blackTimerPanel.getComponent(1);

        rightPanel.add(redTimerPanel);
        rightPanel.add(Box.createHorizontalStrut(20));
        rightPanel.add(blackTimerPanel);

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    private JPanel createTimerPanel(String labelText, Color color) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        panel.setBackground(new Color(250, 245, 235));

        JLabel label = new JLabel(labelText + ":");
        label.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        label.setForeground(color);

        JLabel timeLabel = new JLabel("10:00");
        timeLabel.setFont(new Font("Consolas", Font.BOLD, 15));
        timeLabel.setForeground(new Color(80, 50, 30));
        timeLabel.setPreferredSize(new Dimension(60, 22));

        panel.add(label);
        panel.add(timeLabel);

        return panel;
    }

    private JPanel createFunctionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(180, 140, 90)));
        panel.setBackground(new Color(245, 240, 230));

        // 主按钮容器 - 居中排列
        JPanel mainButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
        mainButtonPanel.setBackground(new Color(245, 240, 230));

        // 游戏模式按钮组
        JButton pvpButton = createMediumButton("双人对战", e -> controller.startPvPGame());
        JButton pvcButton = createMediumButton("人机对战", e -> controller.startPvCGame());
        JButton resetButton = createMediumButton("重新开始", e -> {
            if (boardPanel.getGameMode() == XiangqiPanel.GameMode.PVC) {
                controller.startPvCGame();
            } else {
                controller.startPvPGame();
            }
        });

        // 功能按钮组
        undoButton = createMediumButton("悔棋", e -> {
            if (controller.canUndo()) {
                controller.undo();
                updateFunctionPanel();
            }
        });

        surrenderButton = createMediumButton("认输", e -> {
            int option = JOptionPane.showConfirmDialog(this,
                "确定要认输吗？", "确认", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                controller.surrender();
            }
        });

        reviewButton = createMediumButton("棋盘回顾", e -> {
            if (!controller.getCanUndo()) {
                controller.startReview();
                updateFunctionPanel();
            }
        });

        // 回顾控制按钮
        reviewPrevButton = createSmallActionButton2("◀ 上一步", e -> {
            controller.reviewPrevious();
            updateReviewDisplay();
        });

        reviewNextButton = createSmallActionButton2("下一步 ▶", e -> {
            controller.reviewNext();
            updateReviewDisplay();
        });

        reviewExitButton = createSmallActionButton2("退出回顾", e -> {
            controller.endReview();
            updateFunctionPanel();
        });

        reviewStepLabel = new JLabel("第 0 步");
        reviewStepLabel.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        reviewStepLabel.setForeground(new Color(80, 50, 30));
        reviewStepLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(160, 120, 70), 1),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));
        reviewStepLabel.setBackground(new Color(255, 250, 240));
        reviewStepLabel.setOpaque(true);

        // 棋盘回顾控制面板
        reviewControlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        reviewControlPanel.setBackground(new Color(245, 240, 230));
        reviewControlPanel.add(reviewStepLabel);
        reviewControlPanel.add(reviewPrevButton);
        reviewControlPanel.add(reviewNextButton);
        reviewControlPanel.add(reviewExitButton);

        // 添加按钮到主面板
        mainButtonPanel.add(pvpButton);
        mainButtonPanel.add(pvcButton);
        mainButtonPanel.add(resetButton);
        mainButtonPanel.add(Box.createHorizontalStrut(20));
        mainButtonPanel.add(undoButton);
        mainButtonPanel.add(surrenderButton);
        mainButtonPanel.add(reviewButton);
        mainButtonPanel.add(reviewControlPanel);

        panel.add(mainButtonPanel, BorderLayout.CENTER);

        return panel;
    }

    private JButton createMediumButton(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        button.setPreferredSize(new Dimension(90, 36));
        button.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.setBackground(new Color(248, 240, 228));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(165, 125, 75), 1),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        button.setForeground(new Color(80, 50, 30));

        return button;
    }

    private JButton createSmallActionButton2(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        button.setPreferredSize(new Dimension(100, 32));
        button.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.setBackground(new Color(250, 242, 232));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(170, 130, 80), 1),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        button.setForeground(new Color(80, 50, 30));

        return button;
    }

    // 初始化显示（在设置完成后调用）
    private void initializeDisplay() {
        SwingUtilities.invokeLater(() -> updateFunctionPanel());
    }

    private void updateFunctionPanel() {
        XiangqiPanel.GameMode mode = boardPanel.getGameMode();
        boolean isReviewMode = controller.isReviewMode();

        // 隐藏所有按钮
        undoButton.setVisible(false);
        surrenderButton.setVisible(false);
        reviewButton.setVisible(false);
        reviewControlPanel.setVisible(false);

        if (isReviewMode) {
            // 回顾模式：只显示回顾控制
            reviewControlPanel.setVisible(true);
            reviewStepLabel.setText("第 " + boardPanel.getReviewMoveIndex() + " 步");
            updateReviewDisplay();
        } else {
            // 正常对弈模式 - 显示所有按钮
            undoButton.setVisible(controller.canUndo());
            surrenderButton.setVisible(true);
            reviewButton.setVisible(true);
        }

        functionPanel.revalidate();
        functionPanel.repaint();
    }

    private void updateReviewDisplay() {
        reviewStepLabel.setText("第 " + boardPanel.getReviewMoveIndex() + " 步");
        reviewPrevButton.setEnabled(boardPanel.getReviewMoveIndex() > 0);
        reviewNextButton.setEnabled(boardPanel.getReviewMoveIndex() < boardPanel.getCurrentMoveCount());
    }

    private void updateTimerDisplay() {
        redTimeLabel.setText(controller.getFormattedTime(controller.getRedTimeRemaining()));
        blackTimeLabel.setText(controller.getFormattedTime(controller.getBlackTimeRemaining()));

        // 更新状态
        updateStatusLabel();

        // 低时间警告
        PieceColor turn = boardPanel.getBoard().getCurrentTurn();
        if (controller.getRedTimeRemaining() <= 60 && turn == PieceColor.RED) {
            redTimeLabel.setForeground(new Color(200, 50, 50));
        } else {
            redTimeLabel.setForeground(new Color(80, 50, 30));
        }

        if (controller.getBlackTimeRemaining() <= 60 && turn == PieceColor.BLACK) {
            blackTimeLabel.setForeground(new Color(200, 50, 50));
        } else {
            blackTimeLabel.setForeground(new Color(80, 50, 30));
        }

        // 更新功能面板显示
        updateFunctionPanel();
    }

    private void updateStatusLabel() {
        if (boardPanel.getBoard().isGameOver()) {
            statusLabel.setText(boardPanel.getBoard().getGameResult());
            statusLabel.setForeground(new Color(200, 50, 50));
            statusLabel.setFont(new Font("微软雅黑", Font.BOLD, 13));
            return;
        }

        PieceColor turn = boardPanel.getBoard().getCurrentTurn();
        String statusText = "";

        if (boardPanel.getBoard().isInCheck(turn)) {
            statusText = (turn == PieceColor.RED ? "红方" : "黑方") + "被将军，请解将！";
            statusLabel.setForeground(new Color(220, 40, 40));
            statusLabel.setFont(new Font("微软雅黑", Font.BOLD, 13));
        } else {
            statusText = "正常对弈中";
            statusLabel.setForeground(new Color(80, 50, 30));
            statusLabel.setFont(new Font("微软雅黑", Font.BOLD, 13));
        }

        statusLabel.setText(statusText);
    }

    private void showAboutDialog() {
        String message = "中国象棋 v2.0\n\n" +
                "功能特性:\n" +
                "• 双人对战\n" +
                "• 人机对战 (Minimax算法)\n" +
                "• 经典残局练习\n" +
                "• 悔棋功能\n" +
                "• 棋盘回顾\n" +
                "• 完整规则实现\n" +
                "• 开局随机先手\n" +
                "• 10分钟计时模式\n\n" +
                "操作方式:\n" +
                "• 点击棋子选中，再点击目标位置移动\n" +
                "• 悔棋可撤销上一步操作\n" +
                "• 棋盘回顾可查看整局对弈过程";
        JOptionPane.showMessageDialog(this, message, "关于", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showRulesDialog() {
        String message = "=== 中国象棋规则 ===\n\n" +
                "【棋子走法】\n" +
                "将/帅: 九宫内横竖走一步\n" +
                "士/仕: 九宫内斜走一步\n" +
                "象/相: 田字移动，不能过河，塞象眼\n" +
                "馬: 日字移动，蹩马腿\n" +
                "車: 横竖任意走，不能越子\n" +
                "炮/砲: 走法同车，吃子需翻山\n" +
                "兵/卒: 过河前只能前行，过河后可横走，不能后退\n\n" +
                "【特殊规则】\n" +
                "• 将帅不能在同一直线上直接对面\n" +
                "• 被将军时必须解将\n\n" +
                "【胜负判定】\n" +
                "• 将死或困毙对方获胜\n" +
                "• 对方认输获胜\n" +
                "• 对方超时获胜\n\n" +
                "【人机模式】\n" +
                "• 人类执红方(棋盘下侧)\n" +
                "• 开局先手随机\n" +
                "• AI落棋时间限制15秒";
        JOptionPane.showMessageDialog(this, message, "游戏规则", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showEndgameModeDialog(String endgameName) {
        JPanel dialogPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        JRadioButton pvpRadio = new JRadioButton("双人对战", true);
        JRadioButton pvcRadio = new JRadioButton("人机对战 (执红方)");
        ButtonGroup group = new ButtonGroup();
        group.add(pvpRadio);
        group.add(pvcRadio);
        dialogPanel.add(pvpRadio);
        dialogPanel.add(pvcRadio);

        int result = JOptionPane.showConfirmDialog(
                this,
                dialogPanel,
                endgameName + " - 选择游戏模式",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            XiangqiPanel.GameMode mode = pvcRadio.isSelected() ?
                    XiangqiPanel.GameMode.PVC : XiangqiPanel.GameMode.PVP;
            controller.loadEndgame(endgameName, mode);
            updateFunctionPanel();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new XiangqiFrame().setVisible(true);
        });
    }
}
