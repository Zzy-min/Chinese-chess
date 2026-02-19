package com.xiangqi.ui;

import com.xiangqi.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;

/**
 * 棋盘面板 - 负责绘制棋盘和处理用户交互
 */
public class XiangqiPanel extends JPanel {
    private static final int CELL_SIZE = 65;
    private static final int MARGIN = 55;
    private static final int PIECE_RADIUS = 28;

    // 颜色定义 - 国风配色
    private static final Color BOARD_BG_COLOR = new Color(218, 175, 120); // 米黄色木纹
    private static final Color BOARD_LINE_COLOR = new Color(70, 45, 25); // 深褐色线条
    private static final Color RED_PIECE_COLOR = new Color(220, 50, 50); // 朱红色
    private static final Color RED_PIECE_BG = new Color(255, 240, 230); // 米红背景
    private static final Color BLACK_PIECE_COLOR = new Color(35, 35, 35); // 墨黑色
    private static final Color BLACK_PIECE_BG = new Color(245, 245, 240); // 宣纸白
    private static final Color SELECTED_COLOR = new Color(0, 160, 70, 180); // 翡翠绿选中
    private static final Color VALID_MOVE_COLOR = new Color(0, 140, 60, 120); // 深绿移动点
    private static final Color LAST_MOVE_COLOR = new Color(0, 180, 80); // 青绿色落棋标记
    private static final Color CHECK_COLOR = new Color(230, 30, 30, 160); // 丹砂红将军警告

    // 国风装饰颜色
    private static final Color DECORATION_GOLD = new Color(200, 160, 80); // 金色装饰
    private static final Color DECORATION_RED = new Color(180, 50, 50); // 绛红色装饰
    private static final Color DECORATION_BORDER = new Color(90, 60, 30); // 边框色

    private Board board;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private boolean isHumanTurn = true;
    private PieceColor humanColor;
    private GameMode gameMode;
    private Runnable onMoveComplete;

    // 棋盘回顾相关
    private Board reviewBoard; // 回顾时显示的棋盘
    private int reviewMoveIndex; // 当前回顾的步数

    public enum GameMode {
        PVP,    // 双人对战
        PVC     // 人机对战
    }

    public XiangqiPanel(GameMode gameMode, PieceColor humanColor) {
        this.gameMode = gameMode;
        this.humanColor = humanColor;
        this.board = new Board();
        this.isHumanTurn = (gameMode == GameMode.PVP) || (board.getCurrentTurn() == humanColor);
        initializeBoard();
        setupMouseListener();
    }

    private void initializeBoard() {
        // 随机决定先手
        Random random = new Random();
        boolean redFirst = random.nextBoolean();
        board.setCurrentTurn(redFirst ? PieceColor.RED : PieceColor.BLACK);

        if (gameMode == GameMode.PVC && humanColor == PieceColor.RED) {
            isHumanTurn = board.getCurrentTurn() == humanColor;
        }
    }

    private void setupMouseListener() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isHumanTurn || board.isGameOver()) {
                    return;
                }

                int col = (e.getX() - MARGIN + CELL_SIZE / 2) / CELL_SIZE;
                int row = (e.getY() - MARGIN + CELL_SIZE / 2) / CELL_SIZE;

                if (row < 0 || row >= Board.ROWS || col < 0 || col >= Board.COLS) {
                    selectedRow = -1;
                    selectedCol = -1;
                    repaint();
                    return;
                }

                handleSquareClick(row, col);
            }
        });
    }

    private void handleSquareClick(int row, int col) {
        Piece clickedPiece = board.getPiece(row, col);

        if (selectedRow == -1 && selectedCol == -1) {
            // 选择棋子
            if (clickedPiece != null && clickedPiece.getColor() == board.getCurrentTurn()) {
                if (gameMode == GameMode.PVC && clickedPiece.getColor() != humanColor) {
                    return;
                }
                selectedRow = row;
                selectedCol = col;
            }
        } else {
            // 尝试移动
            Move move = new Move(selectedRow, selectedCol, row, col);
            if (board.isValidMove(move)) {
                board.movePiece(move);
                selectedRow = -1;
                selectedCol = -1;

                if (onMoveComplete != null) {
                    onMoveComplete.run();
                }

                if (gameMode == GameMode.PVC) {
                    isHumanTurn = false;
                }
            } else if (clickedPiece != null && clickedPiece.getColor() == board.getCurrentTurn()) {
                // 切换选择
                if (gameMode == GameMode.PVC && clickedPiece.getColor() != humanColor) {
                    return;
                }
                selectedRow = row;
                selectedCol = col;
            } else {
                selectedRow = -1;
                selectedCol = -1;
            }
        }
        repaint();
    }

    public void makeAIMove(Move move) {
        if (move != null && board.isValidMove(move)) {
            board.movePiece(move);
            isHumanTurn = true;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 绘制背景
        g2d.setColor(BOARD_BG_COLOR);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // 绘制棋盘边框
        drawBoardBorder(g2d);

        // 绘制棋盘线
        drawBoardLines(g2d);

        // 绘制楚河汉界
        drawRiver(g2d);

        // 绘制九宫格
        drawPalace(g2d);

        // 使用回顾棋盘或当前棋盘
        Board boardToDraw = (reviewBoard != null) ? reviewBoard : board;

        // 绘制最后一着落棋位置标记
        drawLastMove(g2d, boardToDraw);

        // 绘制将军标记
        drawCheckIndicator(g2d, boardToDraw);

        // 绘制棋子
        drawPieces(g2d, boardToDraw);

        // 绘制选择标记和可移动位置（仅在回顾模式外显示）
        if (reviewBoard == null) {
            drawSelectionAndValidMoves(g2d);
        }

        // 绘制游戏信息
        drawGameInfo(g2d);

        // 绘制回顾模式标记
        if (reviewBoard != null) {
            drawReviewModeIndicator(g2d);
        }
    }

    private void drawBoardBorder(Graphics2D g2d) {
        int borderMargin = MARGIN - 25;
        int width = (Board.COLS - 1) * CELL_SIZE + 50;
        int height = (Board.ROWS - 1) * CELL_SIZE + 50;

        // 绘制外层金色边框
        g2d.setColor(DECORATION_GOLD);
        g2d.setStroke(new BasicStroke(4));
        g2d.drawRoundRect(borderMargin, borderMargin, width, height, 15, 15);

        // 绘制内层深色边框
        g2d.setColor(DECORATION_BORDER);
        g2d.setStroke(new BasicStroke(2));
        int innerMargin = borderMargin + 6;
        int innerWidth = width - 12;
        int innerHeight = height - 12;
        g2d.drawRoundRect(innerMargin, innerMargin, innerWidth, innerHeight, 12, 12);

        // 绘制四角装饰（如意纹样）
        drawCornerDecorations(g2d, borderMargin, width, height);
    }

    private void drawCornerDecorations(Graphics2D g2d, int margin, int width, int height) {
        int cornerSize = 20;
        g2d.setColor(DECORATION_RED);
        g2d.setStroke(new BasicStroke(2));

        // 左上角
        int x1 = margin + 10;
        int y1 = margin + 10;
        drawCloudPattern(g2d, x1, y1, cornerSize);

        // 右上角
        int x2 = margin + width - 10 - cornerSize;
        drawCloudPattern(g2d, x2, y1, cornerSize);

        // 左下角
        int y2 = margin + height - 10 - cornerSize;
        drawCloudPattern(g2d, x1, y2, cornerSize);

        // 右下角
        drawCloudPattern(g2d, x2, y2, cornerSize);
    }

    private void drawCloudPattern(Graphics2D g2d, int x, int y, int size) {
        // 绘制简化的云纹图案
        g2d.drawArc(x, y + size/2, size/2, size/2, 0, 180);
        g2d.drawArc(x + size/2, y, size/2, size/2, 180, 180);
    }

    private void drawBoardLines(Graphics2D g2d) {
        g2d.setColor(BOARD_LINE_COLOR);
        g2d.setStroke(new BasicStroke(2));

        // 绘制横线
        for (int row = 0; row < Board.ROWS; row++) {
            int y = MARGIN + row * CELL_SIZE;
            g2d.drawLine(MARGIN, y, MARGIN + (Board.COLS - 1) * CELL_SIZE, y);
        }

        // 绘制竖线（中间断开）
        for (int col = 0; col < Board.COLS; col++) {
            int x = MARGIN + col * CELL_SIZE;
            if (col == 0 || col == Board.COLS - 1) {
                g2d.drawLine(x, MARGIN, x, MARGIN + (Board.ROWS - 1) * CELL_SIZE);
            } else {
                g2d.drawLine(x, MARGIN, x, MARGIN + 4 * CELL_SIZE);
                g2d.drawLine(x, MARGIN + 5 * CELL_SIZE, x, MARGIN + (Board.ROWS - 1) * CELL_SIZE);
            }
        }

        // 绘制交叉点标记
        drawCrossPoints(g2d);
    }

    private void drawCrossPoints(Graphics2D g2d) {
        g2d.setStroke(new BasicStroke(2));

        // 炮位和兵卒位的交叉点标记
        int[][] crossPoints = {
            // 炮位
            {2, 1}, {2, 7}, {7, 1}, {7, 7},
            // 兵卒位
            {3, 0}, {3, 2}, {3, 4}, {3, 6}, {3, 8},
            {6, 0}, {6, 2}, {6, 4}, {6, 6}, {6, 8}
        };

        for (int[] point : crossPoints) {
            int row = point[0];
            int col = point[1];
            int x = MARGIN + col * CELL_SIZE;
            int y = MARGIN + row * CELL_SIZE;
            int len = 6;

            g2d.setColor(BOARD_LINE_COLOR);

            if (col > 0) {
                // 左上（国风折角样式）
                drawGuofengCorner(g2d, x - len, y - len, x, y, true, true);
                // 左下
                drawGuofengCorner(g2d, x - len, y + len, x, y, true, false);
            }
            if (col < Board.COLS - 1) {
                // 右上
                drawGuofengCorner(g2d, x + len, y - len, x, y, false, true);
                // 右下
                drawGuofengCorner(g2d, x + len, y + len, x, y, false, false);
            }
        }
    }

    private void drawGuofengCorner(Graphics2D g2d, int endX, int endY, int centerX, int centerY, boolean isLeft, boolean isTop) {
        int len = 6;
        int offset = 3;

        // 绘制主折角
        g2d.drawLine(centerX, centerY, endX, centerY);
        g2d.drawLine(centerX, centerY, centerX, endY);

        // 绘制装饰性小折角（增强国风感）
        int decorX = isLeft ? endX + offset : endX - offset;
        int decorY = isTop ? endY + offset : endY - offset;
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawLine(centerX, decorY, decorX, decorY);
        g2d.drawLine(decorX, centerY, decorX, decorY);
        g2d.setStroke(new BasicStroke(2));
    }

    private void drawRiver(Graphics2D g2d) {
        // 绘制波浪装饰
        drawWavePatterns(g2d);

        // 使用书法风格字体
        g2d.setFont(new Font("隶书", Font.BOLD, 38));
        FontMetrics fm = g2d.getFontMetrics();

        String chuHe = "楚 河";
        String hanJie = "汉 界";

        int riverY = MARGIN + 4 * CELL_SIZE + CELL_SIZE / 2 + 8;
        int chuHeX = MARGIN + CELL_SIZE - 10;
        int hanJieX = MARGIN + 5 * CELL_SIZE + CELL_SIZE / 2 - 10;

        // 绘制文字阴影（增加书法晕染效果）
        g2d.setColor(new Color(140, 100, 60, 150));
        g2d.drawString(chuHe, chuHeX + 3, riverY + 3);
        g2d.drawString(hanJie, hanJieX + 3, riverY + 3);

        // 绘制主文字
        g2d.setColor(new Color(90, 60, 35));
        g2d.drawString(chuHe, chuHeX, riverY);
        g2d.drawString(hanJie, hanJieX, riverY);
    }

    private void drawWavePatterns(Graphics2D g2d) {
        g2d.setColor(new Color(180, 140, 80, 80));
        g2d.setStroke(new BasicStroke(1.5f));

        int riverY = MARGIN + 4 * CELL_SIZE;
        int waveHeight = CELL_SIZE;

        // 绘制左侧波浪
        int startX = MARGIN;
        int endX = MARGIN + 4 * CELL_SIZE;
        drawSingleWave(g2d, startX, endX, riverY, waveHeight);

        // 绘制右侧波浪
        startX = MARGIN + 5 * CELL_SIZE;
        endX = MARGIN + 8 * CELL_SIZE;
        drawSingleWave(g2d, startX, endX, riverY, waveHeight);
    }

    private void drawSingleWave(Graphics2D g2d, int startX, int endX, int y, int height) {
        int midY = y + height / 2;
        int amplitude = 8;

        for (int x = startX; x < endX; x += 15) {
            g2d.drawArc(x, midY - amplitude, 15, amplitude * 2, 0, 180);
        }
    }

    private void drawPalace(Graphics2D g2d) {
        g2d.setColor(BOARD_LINE_COLOR);
        g2d.setStroke(new BasicStroke(1.5f));

        // 上方九宫格
        int x1 = MARGIN + 3 * CELL_SIZE;
        int y1 = MARGIN;
        int x2 = MARGIN + 5 * CELL_SIZE;
        int y2 = MARGIN + 2 * CELL_SIZE;
        g2d.drawLine(x1, y1, x2, y2);
        g2d.drawLine(x2, y1, x1, y2);

        // 下方九宫格
        y1 = MARGIN + 7 * CELL_SIZE;
        y2 = MARGIN + 9 * CELL_SIZE;
        g2d.drawLine(x1, y1, x2, y2);
        g2d.drawLine(x2, y1, x1, y2);
    }

    private void drawLastMove(Graphics2D g2d, Board boardToDraw) {
        Move lastMove = boardToDraw.getLastMove();
        if (lastMove == null) {
            return;
        }

        g2d.setColor(LAST_MOVE_COLOR);
        g2d.setStroke(new BasicStroke(3));

        // 起点位置标记（空心框）
        int fromX = MARGIN + lastMove.getFromCol() * CELL_SIZE;
        int fromY = MARGIN + lastMove.getFromRow() * CELL_SIZE;
        int size = PIECE_RADIUS + 5;
        g2d.drawRoundRect(fromX - size, fromY - size, size * 2, size * 2, 8, 8);

        // 终点位置标记（实心框）
        int toX = MARGIN + lastMove.getToCol() * CELL_SIZE;
        int toY = MARGIN + lastMove.getToRow() * CELL_SIZE;
        g2d.setStroke(new BasicStroke(4));
        g2d.drawRoundRect(toX - size - 2, toY - size - 2, size * 2 + 4, size * 2 + 4, 10, 10);
    }

    private void drawCheckIndicator(Graphics2D g2d, Board boardToDraw) {
        PieceColor turn = boardToDraw.getCurrentTurn();
        if (boardToDraw.isInCheck(turn)) {
            Piece general = findGeneral(turn, boardToDraw);
            if (general != null) {
                int x = MARGIN + general.getCol() * CELL_SIZE;
                int y = MARGIN + general.getRow() * CELL_SIZE;
                int size = PIECE_RADIUS + 8;

                // 绘制将军警告框
                g2d.setStroke(new BasicStroke(3));
                g2d.setColor(CHECK_COLOR);
                g2d.drawRoundRect(x - size, y - size, size * 2, size * 2, 10, 10);
                g2d.drawRoundRect(x - size + 3, y - size + 3, size * 2 - 6, size * 2 - 6, 8, 8);
            }
        }
    }

    private Piece findGeneral(PieceColor color, Board boardToDraw) {
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = boardToDraw.getPiece(row, col);
                if (piece != null && piece.getColor() == color && piece.getType().isGeneral()) {
                    return piece;
                }
            }
        }
        return null;
    }

    private void drawPieces(Graphics2D g2d, Board boardToDraw) {
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = boardToDraw.getPiece(row, col);
                if (piece != null) {
                    drawPiece(g2d, piece, row, col);
                }
            }
        }
    }

    private void drawReviewModeIndicator(Graphics2D g2d) {
        int panelWidth = getWidth();
        String reviewText = "棋盘回顾 - 第 " + reviewMoveIndex + " 步 (共 " + board.getMoveCount() + " 步)";

        g2d.setFont(new Font("微软雅黑", Font.BOLD, 16));
        g2d.setColor(new Color(200, 100, 50));

        FontMetrics fm = g2d.getFontMetrics();
        int x = (panelWidth - fm.stringWidth(reviewText)) / 2;
        int y = getHeight() - 55;

        // 绘制半透明背景框
        int boxWidth = fm.stringWidth(reviewText) + 20;
        int boxHeight = 30;
        g2d.setColor(new Color(255, 240, 220, 200));
        g2d.fillRoundRect(x - 10, y - 20, boxWidth, boxHeight, 10, 10);

        // 绘制边框
        g2d.setStroke(new BasicStroke(2));
        g2d.setColor(new Color(200, 120, 60));
        g2d.drawRoundRect(x - 10, y - 20, boxWidth, boxHeight, 10, 10);

        // 绘制文字
        g2d.drawString(reviewText, x, y);
    }

    private void drawPiece(Graphics2D g2d, Piece piece, int row, int col) {
        int x = MARGIN + col * CELL_SIZE;
        int y = MARGIN + row * CELL_SIZE;
        int radius = PIECE_RADIUS;

        // 绘制棋子阴影（国风晕染效果）
        g2d.setColor(new Color(30, 20, 10, 60));
        g2d.fillOval(x - radius + 3, y - radius + 3, radius * 2, radius * 2);

        // 绘制棋子背景（木纹渐变效果）
        GradientPaint bgGradient;
        if (piece.getColor() == PieceColor.RED) {
            bgGradient = new GradientPaint(x - radius, y - radius, new Color(255, 245, 235),
                x + radius, y + radius, new Color(255, 220, 210));
        } else {
            bgGradient = new GradientPaint(x - radius, y - radius, new Color(250, 250, 245),
                x + radius, y + radius, new Color(230, 230, 225));
        }
        g2d.setPaint(bgGradient);
        g2d.fillOval(x - radius, y - radius, radius * 2, radius * 2);

        // 绘制棋子边框（外圈 - 双层边框增强国风感）
        g2d.setStroke(new BasicStroke(3));
        if (piece.getColor() == PieceColor.RED) {
            g2d.setColor(RED_PIECE_COLOR);
        } else {
            g2d.setColor(BLACK_PIECE_COLOR);
        }
        g2d.drawOval(x - radius, y - radius, radius * 2, radius * 2);

        // 绘制装饰性内圈（金色细线）
        g2d.setColor(DECORATION_GOLD);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawOval(x - radius + 3, y - radius + 3, radius * 2 - 6, radius * 2 - 6);

        // 绘制棋子内圈（细线）
        g2d.setStroke(new BasicStroke(1.5f));
        if (piece.getColor() == PieceColor.RED) {
            g2d.setColor(new Color(200, 45, 45));
        } else {
            g2d.setColor(new Color(50, 50, 50));
        }
        g2d.drawOval(x - radius + 5, y - radius + 5, radius * 2 - 10, radius * 2 - 10);

        // 绘制棋子文字（使用楷体增强书法感）
        String text = piece.getType().getDisplayName();

        if (piece.getColor() == PieceColor.RED) {
            g2d.setFont(new Font("楷体", Font.BOLD, 33));
            g2d.setColor(RED_PIECE_COLOR);
        } else {
            g2d.setFont(new Font("楷体", Font.BOLD, 33));
            g2d.setColor(BLACK_PIECE_COLOR);
        }

        FontMetrics fm = g2d.getFontMetrics();
        int textX = x - fm.stringWidth(text) / 2;
        int textY = y + fm.getAscent() / 2 - fm.getDescent() / 2 - 1;
        g2d.drawString(text, textX, textY);
    }

    private void drawSelectionAndValidMoves(Graphics2D g2d) {
        if (selectedRow != -1 && selectedCol != -1) {
            int x = MARGIN + selectedCol * CELL_SIZE;
            int y = MARGIN + selectedRow * CELL_SIZE;
            int size = CELL_SIZE - 4;

            // 绘制选中标记（半透明绿色）
            g2d.setColor(SELECTED_COLOR);
            g2d.fillRoundRect(x - size / 2, y - size / 2, size, size, 10, 10);

            // 绘制边框
            g2d.setStroke(new BasicStroke(2));
            g2d.setColor(new Color(0, 150, 0));
            g2d.drawRoundRect(x - size / 2, y - size / 2, size, size, 10, 10);

            // 绘制可移动位置
            drawValidMoves(g2d);
        }
    }

    private void drawValidMoves(Graphics2D g2d) {
        Piece piece = board.getPiece(selectedRow, selectedCol);
        if (piece == null) return;

        g2d.setColor(VALID_MOVE_COLOR);
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Move move = new Move(selectedRow, selectedCol, row, col);
                if (board.isValidMoveForPiece(piece, row, col)) {
                    Piece target = board.getPiece(row, col);
                    int x = MARGIN + col * CELL_SIZE;
                    int y = MARGIN + row * CELL_SIZE;

                    if (target == null) {
                        // 空位：绘制小圆点
                        g2d.fillOval(x - 10, y - 10, 20, 20);
                        g2d.setColor(new Color(0, 120, 0));
                        g2d.fillOval(x - 6, y - 6, 12, 12);
                        g2d.setColor(VALID_MOVE_COLOR);
                    } else if (target.getColor() != piece.getColor()) {
                        // 吃子：绘制红色圆圈
                        g2d.setColor(new Color(255, 0, 0, 150));
                        g2d.setStroke(new BasicStroke(3));
                        g2d.drawOval(x - PIECE_RADIUS - 2, y - PIECE_RADIUS - 2,
                            (PIECE_RADIUS + 2) * 2, (PIECE_RADIUS + 2) * 2);
                        g2d.setColor(VALID_MOVE_COLOR);
                    }
                }
            }
        }
    }

    private void drawGameInfo(Graphics2D g2d) {
        g2d.setFont(new Font("微软雅黑", Font.PLAIN, 14));

        String modeText = gameMode == GameMode.PVP ? "模式: 双人对战" : "模式: 人机对战";
        String turnText = "回合: " + (board.getCurrentTurn() == PieceColor.RED ? "红方" : "黑方");

        // 检查是否被将军
        if (board.isInCheck(board.getCurrentTurn())) {
            turnText += " (将军!)";
            g2d.setColor(new Color(200, 50, 50));
        } else {
            g2d.setColor(new Color(80, 60, 40));
        }

        g2d.drawString(modeText, 10, 22);
        g2d.drawString(turnText, 10, 42);

        // 显示步数
        g2d.setColor(new Color(80, 60, 40));
        g2d.drawString("步数: " + board.getMoveCount(), 10, 62);

        if (board.isGameOver()) {
            String winnerText = board.getGameResult();
            g2d.setFont(new Font("微软雅黑", Font.BOLD, 28));

            if (winnerText.contains("红方")) {
                g2d.setColor(RED_PIECE_COLOR);
            } else if (winnerText.contains("黑方")) {
                g2d.setColor(BLACK_PIECE_COLOR);
            } else {
                g2d.setColor(new Color(80, 60, 40));
            }

            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString(winnerText, (getWidth() - fm.stringWidth(winnerText)) / 2, getHeight() - 25);
        }

        // 绘制特别提示信息
        drawSpecialAlerts(g2d);
    }

    private void drawSpecialAlerts(Graphics2D g2d) {
        if (board.isGameOver()) {
            return;
        }

        PieceColor currentTurn = board.getCurrentTurn();
        int alertY = getHeight() - 80;

        // 检查将军情况
        if (board.isInCheck(currentTurn)) {
            String checkAlert = (currentTurn == PieceColor.RED ? "红方" : "黑方") + "被将军！";
            drawAlertBox(g2d, checkAlert, new Color(220, 40, 40), new Color(255, 200, 200), alertY);
        }
    }

    private void drawAlertBox(Graphics2D g2d, String text, Color borderColor, Color bgColor, int y) {
        g2d.setFont(new Font("微软雅黑", Font.BOLD, 20));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();
        int padding = 15;
        int boxWidth = textWidth + padding * 2;
        int boxHeight = textHeight + padding;
        int x = (getWidth() - boxWidth) / 2;

        // 绘制半透明背景
        g2d.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 180));
        g2d.fillRoundRect(x, y, boxWidth, boxHeight, 15, 15);

        // 绘制边框
        g2d.setColor(borderColor);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRoundRect(x, y, boxWidth, boxHeight, 15, 15);

        // 绘制文字
        g2d.setColor(borderColor);
        g2d.drawString(text, x + padding, y + textHeight - 5);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(MARGIN * 2 + (Board.COLS - 1) * CELL_SIZE,
            MARGIN * 2 + (Board.ROWS - 1) * CELL_SIZE);
    }

    public Board getBoard() {
        return board;
    }

    public void setOnMoveComplete(Runnable onMoveComplete) {
        this.onMoveComplete = onMoveComplete;
    }

    public void setGameMode(GameMode mode) {
        this.gameMode = mode;
    }

    public void setHumanColor(PieceColor color) {
        this.humanColor = color;
    }

    public void resetGame() {
        this.board = new Board();
        initializeBoard();
        selectedRow = -1;
        selectedCol = -1;
        isHumanTurn = true;
        repaint();
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void loadPosition(String fen) {
        // 简单的FEN加载功能，用于残局
        board = new Board();
        // TODO: 实现FEN解析
        initializeBoard();
        repaint();
    }

    // 棋盘回顾相关方法
    public void setReviewBoard(Board board) {
        this.reviewBoard = board;
    }

    public void setReviewMoveIndex(int index) {
        this.reviewMoveIndex = index;
    }

    public int getReviewMoveIndex() {
        return reviewMoveIndex;
    }

    public int getCurrentMoveCount() {
        return board.getMoveCount();
    }

    /**
     * 同步回合状态（悔棋后调用）
     */
    public void syncTurnState() {
        if (gameMode == GameMode.PVP) {
            // 双人对战模式：总是允许操作
            isHumanTurn = true;
        } else {
            // 人机对战模式：检查是否是人类玩家的回合
            isHumanTurn = board.getCurrentTurn() == humanColor;
        }
    }

    /**
     * 设置是否允许人类操作
     */
    public void setHumanTurn(boolean turn) {
        this.isHumanTurn = turn;
    }

    /**
     * 检查是否允许人类操作
     */
    public boolean isHumanTurn() {
        return isHumanTurn;
    }
}
