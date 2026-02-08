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

public class XiangqiPanel extends JPanel {
    private static final int ROWS = 10;
    private static final int COLS = 9;
    private static final int CELL = 60;
    private static final int MARGIN = 60;
    private static final Color BOARD_COLOR = new Color(221, 183, 114);
    private static final Color LINE_COLOR = new Color(82, 54, 28);

    private final Piece[][] board = new Piece[ROWS][COLS];
    private boolean redTurn = true;
    private Point selected;
    private final Consumer<String> statusConsumer;

    public XiangqiPanel(Consumer<String> statusConsumer) {
        this.statusConsumer = statusConsumer;
        setPreferredSize(new Dimension(900, 720));
        setBackground(new Color(150, 100, 55));
        initBoard();
        addMouseListener(new BoardMouseListener());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(BOARD_COLOR);
        g2.fillRoundRect(MARGIN - 30, MARGIN - 30, CELL * (COLS - 1) + 60, CELL * (ROWS - 1) + 60, 30, 30);

        g2.setColor(LINE_COLOR);
        g2.setStroke(new BasicStroke(2f));
        for (int r = 0; r < ROWS; r++) {
            int y = MARGIN + r * CELL;
            g2.drawLine(MARGIN, y, MARGIN + CELL * (COLS - 1), y);
        }
        for (int c = 0; c < COLS; c++) {
            int x = MARGIN + c * CELL;
            if (c == 0 || c == COLS - 1) {
                g2.drawLine(x, MARGIN, x, MARGIN + CELL * (ROWS - 1));
            } else {
                g2.drawLine(x, MARGIN, x, MARGIN + CELL * 4);
                g2.drawLine(x, MARGIN + CELL * 5, x, MARGIN + CELL * (ROWS - 1));
            }
        }

        drawPalace(g2, 0);
        drawPalace(g2, 7);
        drawRiverText(g2);

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Piece piece = board[r][c];
                if (piece != null) {
                    drawPiece(g2, r, c, piece, selected != null && selected.x == c && selected.y == r);
                }
            }
        }
    }

    private void drawPalace(Graphics2D g2, int startRow) {
        int topY = MARGIN + startRow * CELL;
        int bottomY = MARGIN + (startRow + 2) * CELL;
        int leftX = MARGIN + 3 * CELL;
        int rightX = MARGIN + 5 * CELL;
        g2.drawLine(leftX, topY, rightX, bottomY);
        g2.drawLine(rightX, topY, leftX, bottomY);
    }

    private void drawRiverText(Graphics2D g2) {
        g2.setFont(new Font("Serif", Font.BOLD, 26));
        g2.drawString("楚河", MARGIN + CELL, MARGIN + CELL * 4 + 35);
        g2.drawString("汉界", MARGIN + CELL * 5, MARGIN + CELL * 4 + 35);
    }

    private void drawPiece(Graphics2D g2, int row, int col, Piece piece, boolean highlight) {
        int x = MARGIN + col * CELL - 22;
        int y = MARGIN + row * CELL - 22;
        g2.setColor(highlight ? new Color(255, 221, 140) : new Color(244, 230, 200));
        g2.fillOval(x, y, 44, 44);
        g2.setColor(piece.isRed() ? new Color(166, 26, 24) : new Color(30, 30, 30));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(x, y, 44, 44);
        g2.setFont(new Font("Serif", Font.BOLD, 22));
        String label = piece.getType().getLabel(piece.isRed());
        g2.drawString(label, x + 12, y + 28);
    }

    private class BoardMouseListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            Point cell = toCell(e.getPoint());
            if (cell == null) {
                return;
            }

            Piece clicked = board[cell.y][cell.x];
            if (selected == null) {
                if (clicked != null && clicked.isRed() == redTurn) {
                    selected = cell;
                    statusConsumer.accept("已选择" + (redTurn ? "红" : "黑") + "方棋子");
                }
            } else {
                if (selected.equals(cell)) {
                    selected = null;
                } else {
                    attemptMove(selected, cell);
                }
            }
            repaint();
        }
    }

    private Point toCell(Point p) {
        int col = Math.round((p.x - MARGIN) / (float) CELL);
        int row = Math.round((p.y - MARGIN) / (float) CELL);
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return null;
        }
        return new Point(col, row);
    }

    private void attemptMove(Point from, Point to) {
        Piece piece = board[from.y][from.x];
        Piece target = board[to.y][to.x];
        if (piece == null) {
            selected = null;
            return;
        }
        if (target != null && target.isRed() == piece.isRed()) {
            statusConsumer.accept("不能吃掉己方棋子");
            return;
        }
        if (!isValidMove(piece, from, to, target)) {
            statusConsumer.accept("不符合棋子走法");
            return;
        }
        board[to.y][to.x] = piece;
        board[from.y][from.x] = null;
        selected = null;
        redTurn = !redTurn;
        statusConsumer.accept((redTurn ? "红" : "黑") + "方行动");
    }

    private boolean isValidMove(Piece piece, Point from, Point to, Piece target) {
        int dr = to.y - from.y;
        int dc = to.x - from.x;
        switch (piece.getType()) {
            case GENERAL:
                return validateGeneral(piece, from, to, dr, dc);
            case ADVISOR:
                return validateAdvisor(piece, from, to, dr, dc);
            case ELEPHANT:
                return validateElephant(piece, from, to, dr, dc);
            case HORSE:
                return validateHorse(from, to, dr, dc);
            case ROOK:
                return validateRook(from, to);
            case CANNON:
                return validateCannon(from, to, target);
            case SOLDIER:
                return validateSoldier(piece, from, to, dr, dc);
            default:
                return false;
        }
    }

    private boolean validateGeneral(Piece piece, Point from, Point to, int dr, int dc) {
        if (!isInPalace(piece.isRed(), to)) {
            return false;
        }
        if (Math.abs(dr) + Math.abs(dc) != 1) {
            return false;
        }
        Piece[][] snapshot = cloneBoard();
        snapshot[to.y][to.x] = piece;
        snapshot[from.y][from.x] = null;
        return !generalsFacing(snapshot);
    }

    private boolean validateAdvisor(Piece piece, Point from, Point to, int dr, int dc) {
        return isInPalace(piece.isRed(), to) && Math.abs(dr) == 1 && Math.abs(dc) == 1;
    }

    private boolean validateElephant(Piece piece, Point from, Point to, int dr, int dc) {
        if (Math.abs(dr) != 2 || Math.abs(dc) != 2) {
            return false;
        }
        if (piece.isRed() && to.y < 5) {
            return false;
        }
        if (!piece.isRed() && to.y > 4) {
            return false;
        }
        int eyeRow = from.y + dr / 2;
        int eyeCol = from.x + dc / 2;
        return board[eyeRow][eyeCol] == null;
    }

    private boolean validateHorse(Point from, Point to, int dr, int dc) {
        if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2))) {
            return false;
        }
        int legRow = from.y + (Math.abs(dr) == 2 ? dr / 2 : 0);
        int legCol = from.x + (Math.abs(dc) == 2 ? dc / 2 : 0);
        return board[legRow][legCol] == null;
    }

    private boolean validateRook(Point from, Point to) {
        if (from.x != to.x && from.y != to.y) {
            return false;
        }
        return countPiecesBetween(from, to) == 0;
    }

    private boolean validateCannon(Point from, Point to, Piece target) {
        if (from.x != to.x && from.y != to.y) {
            return false;
        }
        int between = countPiecesBetween(from, to);
        if (target == null) {
            return between == 0;
        }
        return between == 1;
    }

    private boolean validateSoldier(Piece piece, Point from, Point to, int dr, int dc) {
        int forward = piece.isRed() ? -1 : 1;
        if (dr == forward && dc == 0) {
            return true;
        }
        boolean crossed = piece.isRed() ? from.y <= 4 : from.y >= 5;
        return crossed && dr == 0 && Math.abs(dc) == 1;
    }

    private int countPiecesBetween(Point from, Point to) {
        int count = 0;
        int dr = Integer.compare(to.y, from.y);
        int dc = Integer.compare(to.x, from.x);
        int r = from.y + dr;
        int c = from.x + dc;
        while (r != to.y || c != to.x) {
            if (board[r][c] != null) {
                count++;
            }
            r += dr;
            c += dc;
        }
        return count;
    }

    private boolean isInPalace(boolean red, Point p) {
        int rowMin = red ? 7 : 0;
        int rowMax = red ? 9 : 2;
        return p.x >= 3 && p.x <= 5 && p.y >= rowMin && p.y <= rowMax;
    }

    private boolean generalsFacing(Piece[][] snapshot) {
        Point redGeneral = findGeneral(snapshot, true);
        Point blackGeneral = findGeneral(snapshot, false);
        if (redGeneral == null || blackGeneral == null) {
            return false;
        }
        if (redGeneral.x != blackGeneral.x) {
            return false;
        }
        int col = redGeneral.x;
        int start = Math.min(redGeneral.y, blackGeneral.y) + 1;
        int end = Math.max(redGeneral.y, blackGeneral.y);
        for (int r = start; r < end; r++) {
            if (snapshot[r][col] != null) {
                return false;
            }
        }
        return true;
    }

    private Point findGeneral(Piece[][] snapshot, boolean red) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Piece piece = snapshot[r][c];
                if (piece != null && piece.isRed() == red && piece.getType() == PieceType.GENERAL) {
                    return new Point(c, r);
                }
            }
        }
        return null;
    }

    private Piece[][] cloneBoard() {
        Piece[][] copy = new Piece[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(board[r], 0, copy[r], 0, COLS);
        }
        return copy;
    }

    private void initBoard() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                board[r][c] = null;
            }
        }
        placePieces(false, 0, 3, 2, 0, 0, 0);
        placePieces(true, 9, 6, 7, 9, 9, 9);
        statusConsumer.accept("红方先行");
    }

    private void placePieces(boolean red, int mainRow, int soldierRow, int cannonRow, int advisorRow, int elephantRow, int horseRow) {
        board[mainRow][0] = new Piece(PieceType.ROOK, red);
        board[mainRow][8] = new Piece(PieceType.ROOK, red);
        board[horseRow][1] = new Piece(PieceType.HORSE, red);
        board[horseRow][7] = new Piece(PieceType.HORSE, red);
        board[elephantRow][2] = new Piece(PieceType.ELEPHANT, red);
        board[elephantRow][6] = new Piece(PieceType.ELEPHANT, red);
        board[advisorRow][3] = new Piece(PieceType.ADVISOR, red);
        board[advisorRow][5] = new Piece(PieceType.ADVISOR, red);
        board[mainRow][4] = new Piece(PieceType.GENERAL, red);
        board[cannonRow][1] = new Piece(PieceType.CANNON, red);
        board[cannonRow][7] = new Piece(PieceType.CANNON, red);
        for (int c = 0; c < COLS; c += 2) {
            board[soldierRow][c] = new Piece(PieceType.SOLDIER, red);
        }
    }
}
