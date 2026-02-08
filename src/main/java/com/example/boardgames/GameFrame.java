package com.example.boardgames;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolBar;

public class GameFrame extends JFrame {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final JLabel statusLabel = new JLabel("欢迎来到棋盘游戏");

    public GameFrame() {
        setTitle("中国象棋背景 - 五子棋 & 中国象棋");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(900, 780));
        setLayout(new BorderLayout());

        GomokuPanel gomokuPanel = new GomokuPanel(this::setStatusText);
        XiangqiPanel xiangqiPanel = new XiangqiPanel(this::setStatusText);

        cardPanel.add(gomokuPanel, "gomoku");
        cardPanel.add(xiangqiPanel, "xiangqi");

        add(buildToolbar(), BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        cardLayout.show(cardPanel, "gomoku");
    }

    private JToolBar buildToolbar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBackground(new Color(138, 94, 55));

        JButton gomokuButton = new JButton("五子棋");
        gomokuButton.addActionListener(this::showGomoku);
        JButton xiangqiButton = new JButton("中国象棋");
        xiangqiButton.addActionListener(this::showXiangqi);

        styleButton(gomokuButton);
        styleButton(xiangqiButton);

        toolBar.add(gomokuButton);
        toolBar.add(xiangqiButton);
        return toolBar;
    }

    private JPanel buildStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        panel.add(statusLabel, BorderLayout.WEST);
        return panel;
    }

    private void showGomoku(ActionEvent event) {
        cardLayout.show(cardPanel, "gomoku");
        setStatusText("切换到五子棋");
    }

    private void showXiangqi(ActionEvent event) {
        cardLayout.show(cardPanel, "xiangqi");
        setStatusText("切换到中国象棋");
    }

    public void setStatusText(String text) {
        statusLabel.setText(text);
    }

    private void styleButton(JButton button) {
        button.setFont(new Font("SansSerif", Font.BOLD, 15));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(104, 65, 31));
        button.setFocusPainted(false);
    }
}
