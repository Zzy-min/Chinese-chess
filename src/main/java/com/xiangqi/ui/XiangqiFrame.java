package com.xiangqi.ui;

import com.xiangqi.ai.MinimaxAI;
import com.xiangqi.controller.EndgameLoader;
import com.xiangqi.controller.GameController;
import com.xiangqi.model.PieceColor;
import com.xiangqi.web.BrowserModeMain;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * 主窗口 - 包含棋盘和控制菜单
 */
public class XiangqiFrame extends JFrame {
    private final XiangqiPanel boardPanel;
    private final GameController controller;
    private JLabel redTimeLabel;
    private JLabel blackTimeLabel;
    private JLabel statusLabel;
    private JLabel drawReasonLabel;

    // 功能面板组件
    private JPanel functionPanel;
    private JButton undoButton;
    private JButton surrenderButton;
    private JButton drawButton;
    private JButton reviewButton;
    private JButton reviewPrevButton;
    private JButton reviewNextButton;
    private JButton reviewExitButton;
    private JLabel reviewStepLabel;
    private JCheckBox soundToggleCheckBox;

    // 棋盘回顾控制面板
    private JPanel reviewControlPanel;
    private JPanel topBarPanel;
    private JCheckBoxMenuItem quickModeItem;

    public XiangqiFrame() {
        setTitle("中国象棋 - 经典对弈");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        boardPanel = new XiangqiPanel(XiangqiPanel.GameMode.PVP, PieceColor.RED);
        boardPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(165, 125, 75), 2),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));

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

        JMenu gameMenu = new JMenu("游戏");
        gameMenu.setMnemonic(KeyEvent.VK_G);

        JMenuItem pvpItem = new JMenuItem("双人对战");
        pvpItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, KeyEvent.CTRL_DOWN_MASK));
        pvpItem.addActionListener(e -> startPvPWithTimeControlDialog());
        gameMenu.add(pvpItem);

        JMenuItem pvcItem = new JMenuItem("人机对战");
        pvcItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_2, KeyEvent.CTRL_DOWN_MASK));
        pvcItem.addActionListener(e -> startPvCWithDifficultyDialog());
        gameMenu.add(pvcItem);

        gameMenu.addSeparator();

        quickModeItem = new JCheckBoxMenuItem("桌面快捷模式");
        quickModeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0));
        quickModeItem.addActionListener(e -> setQuickMode(quickModeItem.isSelected()));
        gameMenu.add(quickModeItem);

        JMenuItem browserItem = new JMenuItem("在浏览器打开");
        browserItem.addActionListener(e -> openInBrowser());
        gameMenu.add(browserItem);

        gameMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK));
        exitItem.addActionListener(e -> {
            controller.stop();
            System.exit(0);
        });
        gameMenu.add(exitItem);

        JMenu endgameMenu = new JMenu("残局");
        for (String endgameName : EndgameLoader.getEndgameNames()) {
            JMenuItem item = new JMenuItem(endgameName);
            item.addActionListener(e -> showEndgameModeDialog(endgameName));
            endgameMenu.add(item);
        }

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

        topBarPanel = createTopBar();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        add(topBarPanel, gbc);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(new Color(240, 235, 225));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(centerPanel, gbc);

        GridBagConstraints boardGbc = new GridBagConstraints();
        boardGbc.gridx = 0;
        boardGbc.gridy = 0;
        boardGbc.insets = new Insets(15, 15, 15, 15);
        centerPanel.add(boardPanel, boardGbc);

        functionPanel = createFunctionPanel();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(functionPanel, gbc);
    }

    private JPanel createTopBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(205, 170, 120)),
            BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));
        panel.setBackground(new Color(250, 245, 235));

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBackground(new Color(250, 245, 235));

        statusLabel = new JLabel("正常对弈中");
        statusLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        statusLabel.setForeground(new Color(80, 50, 30));
        leftPanel.add(statusLabel);

        drawReasonLabel = new JLabel("和棋原因: -");
        drawReasonLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        drawReasonLabel.setForeground(new Color(130, 90, 45));
        drawReasonLabel.setVisible(false);
        leftPanel.add(Box.createVerticalStrut(2));
        leftPanel.add(drawReasonLabel);

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

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(245, 240, 230));

        JPanel mainButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        mainButtonPanel.setBackground(new Color(245, 240, 230));

        JButton pvpButton = createMediumButton("双人对战", "快捷键 Ctrl+1", e -> startPvPWithTimeControlDialog());
        JButton pvcButton = createMediumButton("人机对战", "快捷键 Ctrl+2，可选难度", e -> startPvCWithDifficultyDialog());
        JButton resetButton = createMediumButton("重新开始", "按当前模式重开本局", e -> {
            if (boardPanel.getGameMode() == XiangqiPanel.GameMode.PVC) {
                controller.startPvCGame(controller.getAiDifficulty(), controller.isPvcHumanFirst());
            } else {
                controller.startPvPGame(controller.getTimeControl());
            }
        });

        undoButton = createMediumButton("悔棋", "撤销上一步", e -> {
            if (controller.canUndo()) {
                controller.undo();
                updateFunctionPanel();
            }
        });

        surrenderButton = createMediumButton("认输", "当前回合方认输", e -> {
            int option = JOptionPane.showConfirmDialog(this,
                "确定要认输吗？", "确认", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                controller.surrender();
            }
        });

        drawButton = createMediumButton("和棋", "双人对战下直接判和并封盘", e -> {
            int option = JOptionPane.showConfirmDialog(this,
                "确认本局和棋？", "确认", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                controller.agreeDraw();
            }
        });

        reviewButton = createMediumButton("棋盘回顾", "查看整局变化", e -> {
            if (controller.getCanUndo()) {
                controller.startReview();
                updateFunctionPanel();
            }
        });

        reviewPrevButton = createSmallActionButton("◀ 上一步", e -> {
            controller.reviewPrevious();
            updateReviewDisplay();
        });

        reviewNextButton = createSmallActionButton("下一步 ▶", e -> {
            controller.reviewNext();
            updateReviewDisplay();
        });

        reviewExitButton = createSmallActionButton("退出回顾", e -> {
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

        reviewControlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        reviewControlPanel.setBackground(new Color(245, 240, 230));
        reviewControlPanel.add(reviewStepLabel);
        reviewControlPanel.add(reviewPrevButton);
        reviewControlPanel.add(reviewNextButton);
        reviewControlPanel.add(reviewExitButton);

        soundToggleCheckBox = new JCheckBox("音效");
        soundToggleCheckBox.setSelected(SoundManager.getInstance().isEnabled());
        soundToggleCheckBox.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        soundToggleCheckBox.setFocusPainted(false);
        soundToggleCheckBox.setBackground(new Color(245, 240, 230));
        soundToggleCheckBox.setForeground(new Color(80, 50, 30));
        soundToggleCheckBox.addActionListener(e ->
            SoundManager.getInstance().setEnabled(soundToggleCheckBox.isSelected()));

        mainButtonPanel.add(pvpButton);
        mainButtonPanel.add(pvcButton);
        mainButtonPanel.add(resetButton);
        mainButtonPanel.add(soundToggleCheckBox);
        mainButtonPanel.add(Box.createHorizontalStrut(20));
        mainButtonPanel.add(undoButton);
        mainButtonPanel.add(surrenderButton);
        mainButtonPanel.add(drawButton);
        mainButtonPanel.add(reviewButton);
        mainButtonPanel.add(reviewControlPanel);

        panel.add(mainButtonPanel, BorderLayout.CENTER);

        return panel;
    }

    private JButton createMediumButton(String text, String tooltip, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        button.setPreferredSize(new Dimension(96, 36));
        button.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setToolTipText(tooltip);

        Color bg = new Color(248, 240, 228);
        Color hover = new Color(255, 247, 236);
        button.setBackground(bg);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(165, 125, 75), 1),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        button.setForeground(new Color(80, 50, 30));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(hover);
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(bg);
            }
        });

        return button;
    }

    private JButton createSmallActionButton(String text, java.awt.event.ActionListener listener) {
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

    private void initializeDisplay() {
        SwingUtilities.invokeLater(this::updateFunctionPanel);
    }

    private void updateFunctionPanel() {
        boolean isReviewMode = controller.isReviewMode();
        boolean ended = controller.isGameEnded();

        undoButton.setVisible(false);
        surrenderButton.setVisible(false);
        drawButton.setVisible(false);
        reviewButton.setVisible(false);
        reviewControlPanel.setVisible(false);

        if (isReviewMode) {
            reviewControlPanel.setVisible(true);
            reviewStepLabel.setText("第 " + boardPanel.getReviewMoveIndex() + " 步");
            updateReviewDisplay();
            return;
        }

        undoButton.setVisible(controller.canUndo());
        surrenderButton.setVisible(!ended);
        drawButton.setVisible(!ended && boardPanel.getGameMode() == XiangqiPanel.GameMode.PVP);
        reviewButton.setVisible(boardPanel.getCurrentMoveCount() > 0);

        undoButton.setEnabled(!ended && controller.canUndo());
        drawButton.setEnabled(!ended && boardPanel.getGameMode() == XiangqiPanel.GameMode.PVP);
        reviewButton.setEnabled(boardPanel.getCurrentMoveCount() > 0);

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

        updateStatusLabel();

        PieceColor turn = boardPanel.getBoard().getCurrentTurn();
        if (controller.getRedTimeRemaining() >= 0 && controller.getRedTimeRemaining() <= 60 && turn == PieceColor.RED) {
            redTimeLabel.setForeground(new Color(200, 50, 50));
        } else {
            redTimeLabel.setForeground(new Color(80, 50, 30));
        }

        if (controller.getBlackTimeRemaining() >= 0 && controller.getBlackTimeRemaining() <= 60 && turn == PieceColor.BLACK) {
            blackTimeLabel.setForeground(new Color(200, 50, 50));
        } else {
            blackTimeLabel.setForeground(new Color(80, 50, 30));
        }

        updateFunctionPanel();
    }

    private void updateStatusLabel() {
        if (controller.isGameEnded() || boardPanel.getBoard().isGameOver()) {
            String result = controller.isGameEnded() ? controller.getGameResultText() : boardPanel.getBoard().getGameResult();
            statusLabel.setText(result);
            statusLabel.setForeground(new Color(200, 50, 50));
            statusLabel.setFont(new Font("微软雅黑", Font.BOLD, 13));
            if (controller.isDrawResult()) {
                String reason = controller.getDrawReason();
                drawReasonLabel.setText("和棋原因: " + (reason == null || reason.isEmpty() ? "和棋" : reason));
                drawReasonLabel.setVisible(true);
            } else {
                drawReasonLabel.setVisible(false);
            }
            return;
        }

        drawReasonLabel.setVisible(false);

        PieceColor turn = boardPanel.getBoard().getCurrentTurn();
        StringBuilder statusText = new StringBuilder("正常对弈中");

        if (boardPanel.getGameMode() == XiangqiPanel.GameMode.PVC) {
            statusText.append(" | AI难度: ").append(controller.getAiDifficulty().getDisplayName());
        } else {
            statusText.append(" | 计时: ").append(controller.getTimeControl().getDisplayName());
            int stepRemain = controller.getCurrentMoveRemainingSeconds(turn);
            if (stepRemain >= 0) {
                statusText.append(" | 步时剩余: ").append(stepRemain).append("s");
            }
        }

        if (boardPanel.getBoard().isInCheck(turn)) {
            statusText = new StringBuilder((turn == PieceColor.RED ? "红方" : "黑方") + "被将军，请解将！");
            statusLabel.setForeground(new Color(220, 40, 40));
        } else {
            statusLabel.setForeground(new Color(80, 50, 30));
        }

        statusLabel.setFont(new Font("微软雅黑", Font.BOLD, 13));
        statusLabel.setText(statusText.toString());
    }


    private void startPvPWithTimeControlDialog() {
        GameController.TimeControl selected = showTimeControlDialog(controller.getTimeControl());
        if (selected != null) {
            controller.startPvPGame(selected);
            updateFunctionPanel();
        }
    }

    private GameController.TimeControl showTimeControlDialog(GameController.TimeControl defaultValue) {
        GameController.TimeControl[] options = {
            GameController.TimeControl.TEN_MIN,
            GameController.TimeControl.TWENTY_MIN,
            GameController.TimeControl.UNLIMITED
        };

        Object selected = JOptionPane.showInputDialog(
            this,
            "请选择双人对战计时：",
            "双人计时",
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            defaultValue
        );

        if (selected instanceof GameController.TimeControl) {
            return (GameController.TimeControl) selected;
        }
        return null;
    }
    private void startPvCWithDifficultyDialog() {
        JPanel dialogPanel = new JPanel(new GridLayout(3, 1, 6, 6));

        JComboBox<MinimaxAI.Difficulty> difficultyBox = new JComboBox<>(new MinimaxAI.Difficulty[]{
            MinimaxAI.Difficulty.EASY,
            MinimaxAI.Difficulty.MEDIUM,
            MinimaxAI.Difficulty.HARD
        });
        difficultyBox.setSelectedItem(controller.getAiDifficulty());

        JRadioButton firstRadio = new JRadioButton("我先手（执红）", controller.isPvcHumanFirst());
        JRadioButton secondRadio = new JRadioButton("我后手（执黑）", !controller.isPvcHumanFirst());
        ButtonGroup group = new ButtonGroup();
        group.add(firstRadio);
        group.add(secondRadio);

        dialogPanel.add(new JLabel("请选择 AI 难度："));
        dialogPanel.add(difficultyBox);
        JPanel sidePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sidePanel.add(firstRadio);
        sidePanel.add(secondRadio);
        dialogPanel.add(sidePanel);

        int result = JOptionPane.showConfirmDialog(
            this,
            dialogPanel,
            "人机对战设置",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            MinimaxAI.Difficulty difficulty = (MinimaxAI.Difficulty) difficultyBox.getSelectedItem();
            boolean humanFirst = firstRadio.isSelected();
            controller.startPvCGame(difficulty, humanFirst);
            updateFunctionPanel();
        }
    }

    private MinimaxAI.Difficulty showDifficultyDialog(MinimaxAI.Difficulty defaultValue) {
        MinimaxAI.Difficulty[] options = {
            MinimaxAI.Difficulty.EASY,
            MinimaxAI.Difficulty.MEDIUM,
            MinimaxAI.Difficulty.HARD
        };

        Object selected = JOptionPane.showInputDialog(
            this,
            "请选择 AI 难度：",
            "人机难度",
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            defaultValue
        );

        if (selected instanceof MinimaxAI.Difficulty) {
            return (MinimaxAI.Difficulty) selected;
        }
        return null;
    }

    private void setQuickMode(boolean enabled) {
        boardPanel.setQuickMode(enabled);
        if (topBarPanel != null) {
            topBarPanel.setVisible(!enabled);
        }
        if (functionPanel != null) {
            functionPanel.setVisible(!enabled);
        }
        if (enabled) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            setExtendedState(JFrame.NORMAL);
            pack();
            setLocationRelativeTo(null);
        }
        revalidate();
        repaint();
    }

    private void openInBrowser() {
        try {
            URI uri = URI.create("http://" + BrowserModeMain.TRUSTED_HOST + ":" + BrowserModeMain.PORT + "/");
            // 每次打开浏览器模式都重启服务，避免旧进程导致资源（如音效）版本不一致
            restartStandaloneBrowserServer();
            waitBrowserServerReady(uri, 6000);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            } else {
                JOptionPane.showMessageDialog(this,
                    "当前环境不支持自动打开浏览器，请手动访问：\n" + uri,
                    "浏览器模式",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "浏览器模式启动失败：" + ex.getMessage(),
                "错误",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restartStandaloneBrowserServer() throws IOException {
        stopServerOnPort(BrowserModeMain.PORT);
        launchStandaloneBrowserServer();
    }

    private void stopServerOnPort(int port) {
        Integer pid = findListeningPid(port);
        if (pid == null) {
            return;
        }
        try {
            Process kill;
            if (isWindows()) {
                kill = new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/F").start();
            } else {
                kill = new ProcessBuilder("kill", "-TERM", String.valueOf(pid)).start();
            }
            kill.waitFor();
        } catch (Exception ignored) {
            // 失败时保持静默，后续由启动逻辑与健康检查兜底
        }
    }

    private Integer findListeningPid(int port) {
        if (isWindows()) {
            return findListeningPidWindows(port);
        }
        return findListeningPidUnix(port);
    }

    private Integer findListeningPidWindows(int port) {
        try {
            Process query = new ProcessBuilder("netstat", "-ano", "-p", "tcp").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(query.getInputStream()))) {
                String line;
                String key = ":" + port;
                while ((line = reader.readLine()) != null) {
                    String normalized = line.trim().replaceAll("\\s+", " ");
                    if (!normalized.contains(key) || !normalized.contains("LISTENING")) {
                        continue;
                    }
                    String[] parts = normalized.split(" ");
                    if (parts.length >= 5) {
                        return Integer.parseInt(parts[parts.length - 1]);
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Integer findListeningPidUnix(int port) {
        try {
            Process query = new ProcessBuilder("lsof", "-t", "-i", "tcp:" + port).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(query.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    return Integer.parseInt(line.trim());
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    private void launchStandaloneBrowserServer() throws IOException {
        String javaHome = System.getProperty("java.home");
        String javaBin;
        if (isWindows()) {
            String javaw = javaHome + java.io.File.separator + "bin" + java.io.File.separator + "javaw.exe";
            String javaExe = javaHome + java.io.File.separator + "bin" + java.io.File.separator + "java.exe";
            javaBin = new java.io.File(javaw).exists() ? javaw : javaExe;
        } else {
            javaBin = javaHome + java.io.File.separator + "bin" + java.io.File.separator + "java";
        }

        ProcessBuilder pb = new ProcessBuilder(
            javaBin,
            "-cp",
            System.getProperty("java.class.path"),
            BrowserModeMain.class.getName()
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.start();
    }

    private boolean isBrowserServerAlive(URI uri) {
        try {
            URL url = uri.resolve("api/state").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(350);
            conn.setReadTimeout(350);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void waitBrowserServerReady(URI uri, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isBrowserServerAlive(uri)) {
                return;
            }
            try {
                Thread.sleep(120);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }


    private void showAboutDialog() {
        String message = "中国象棋 v2.2\n\n" +
                "功能特性:\n" +
                "• 双人对战（棋子朝内显示）\n" +
                "• 人机对战（简单/中等/困难）\n" +
                "• 经典残局练习\n" +
                "• 悔棋与棋盘回顾\n" +
                "• 对局结束自动封盘\n" +
                "• 浏览器模式（本地网页）\n" +
                "• 10分钟计时模式";
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
                "• 被将军时必须解将";
        JOptionPane.showMessageDialog(this, message, "游戏规则", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showEndgameModeDialog(String endgameName) {
        JPanel dialogPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        JRadioButton pvpRadio = new JRadioButton("双人对战", true);
        JRadioButton pvcRadio = new JRadioButton("人机对战 (执红方)");
        ButtonGroup group = new ButtonGroup();
        group.add(pvpRadio);
        group.add(pvcRadio);

        JComboBox<MinimaxAI.Difficulty> difficultyBox = new JComboBox<>(new MinimaxAI.Difficulty[]{
            MinimaxAI.Difficulty.EASY,
            MinimaxAI.Difficulty.MEDIUM,
            MinimaxAI.Difficulty.HARD
        });
        difficultyBox.setSelectedItem(controller.getAiDifficulty());
        difficultyBox.setEnabled(false);

        pvcRadio.addActionListener(e -> difficultyBox.setEnabled(true));
        pvpRadio.addActionListener(e -> difficultyBox.setEnabled(false));

        dialogPanel.add(pvpRadio);
        dialogPanel.add(pvcRadio);
        dialogPanel.add(difficultyBox);

        int result = JOptionPane.showConfirmDialog(
            this,
            dialogPanel,
            endgameName + " - 选择游戏模式",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            XiangqiPanel.GameMode mode = pvcRadio.isSelected() ? XiangqiPanel.GameMode.PVC : XiangqiPanel.GameMode.PVP;
            MinimaxAI.Difficulty difficulty = pvcRadio.isSelected()
                ? (MinimaxAI.Difficulty) difficultyBox.getSelectedItem()
                : controller.getAiDifficulty();
            controller.loadEndgame(endgameName, mode, difficulty);
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











