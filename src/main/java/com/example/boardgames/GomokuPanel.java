package com.example.boardgames;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JPanel;

public class GomokuPanel extends JPanel {
    private static final int GRID_SIZE = 15;
    private static final int CELL = 40;
    private static final int MARGIN = 60;
    private static final Color BOARD_COLOR = new Color(214, 172, 102);
    private static final Color LINE_COLOR = new Color(76, 50, 22);

    private final int[][] board = new int[GRID_SIZE][GRID_SIZE];
    private boolean blackTurn = true;
    private boolean gameOver = false;
    private final Consumer<String> statusConsumer;

    public GomokuPanel(Consumer<String> statusConsumer) {
        this.statusConsumer = statusConsumer;
        setPreferredSize(new Dimension(900, 720));
        setBackground(new Color(150, 100, 55));
        addMouseListener(new BoardMouseListener());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(BOARD_COLOR);
        g2.fillRoundRect(MARGIN - 20, MARGIN - 20, CELL * (GRID_SIZE - 1) + 40, CELL * (GRID_SIZE - 1) + 40, 25, 25);

        g2.setColor(LINE_COLOR);
        g2.setStroke(new BasicStroke(2f));
        for (int i = 0; i < GRID_SIZE; i++) {
            int pos = MARGIN + i * CELL;
            g2.drawLine(MARGIN, pos, MARGIN + CELL * (GRID_SIZE - 1), pos);
            g2.drawLine(pos, MARGIN, pos, MARGIN + CELL * (GRID_SIZE - 1));
        }

        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2.drawString("五子棋", MARGIN, MARGIN - 25);

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                if (board[row][col] != 0) {
                    drawStone(g2, row, col, board[row][col] == 1);
                }
            }
        }
    }

    private void drawStone(Graphics2D g2, int row, int col, boolean black) {
        int x = MARGIN + col * CELL - 16;
        int y = MARGIN + row * CELL - 16;
        g2.setColor(black ? Color.BLACK : Color.WHITE);
        g2.fillOval(x, y, 32, 32);
        g2.setColor(Color.DARK_GRAY);
        g2.drawOval(x, y, 32, 32);
    }

    private class BoardMouseListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (gameOver) {
                resetGame();
                return;
            }

            Point cell = toCell(e.getPoint());
            if (cell == null) {
                return;
            }

            if (board[cell.y][cell.x] != 0) {
                statusConsumer.accept("该位置已有棋子");
                return;
            }

            board[cell.y][cell.x] = blackTurn ? 1 : 2;
            if (checkWin(cell.y, cell.x)) {
                gameOver = true;
                statusConsumer.accept((blackTurn ? "黑棋" : "白棋") + "获胜！点击棋盘重新开始。");
            } else {
                blackTurn = !blackTurn;
                statusConsumer.accept((blackTurn ? "黑棋" : "白棋") + "落子");
            }
            repaint();
        }
    }

    private Point toCell(Point p) {
        int col = Math.round((p.x - MARGIN) / (float) CELL);
        int row = Math.round((p.y - MARGIN) / (float) CELL);
        if (row < 0 || row >= GRID_SIZE || col < 0 || col >= GRID_SIZE) {
            return null;
        }
        int centerX = MARGIN + col * CELL;
        int centerY = MARGIN + row * CELL;
        if (Math.abs(p.x - centerX) > CELL / 2 || Math.abs(p.y - centerY) > CELL / 2) {
            return null;
        }
        return new Point(col, row);
    }

    private boolean checkWin(int row, int col) {
        int player = board[row][col];
        return countInDirection(row, col, 1, 0, player)
                || countInDirection(row, col, 0, 1, player)
                || countInDirection(row, col, 1, 1, player)
                || countInDirection(row, col, 1, -1, player);
    }

    private boolean countInDirection(int row, int col, int dr, int dc, int player) {
        int count = 1;
        count += countLine(row, col, dr, dc, player);
        count += countLine(row, col, -dr, -dc, player);
        return count >= 5;
    }

    private int countLine(int row, int col, int dr, int dc, int player) {
        int count = 0;
        int r = row + dr;
        int c = col + dc;
        while (r >= 0 && r < GRID_SIZE && c >= 0 && c < GRID_SIZE && board[r][c] == player) {
            count++;
            r += dr;
            c += dc;
        }
        return count;
    }

    private void resetGame() {
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                board[r][c] = 0;
            }
        }
        blackTurn = true;
        gameOver = false;
        statusConsumer.accept("新一局开始，黑棋先行");
        repaint();
    }
}
