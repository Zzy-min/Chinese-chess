package com.xiangqi.model.gomoku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GomokuBoard {
    public static final int SIZE = 15;

    private static final int[][] DIRS = {
        {0, 1},
        {1, 0},
        {1, 1},
        {1, -1}
    };

    private static final String[] FOUR_PATTERNS = {
        "XXXX.",
        ".XXXX",
        "XXX.X",
        "XX.XX",
        "X.XXX"
    };

    private static final String[] THREE_PATTERNS = {
        ".XXX.",
        ".XX.X.",
        ".X.XX."
    };

    private GomokuStone[][] board;
    private GomokuStone currentTurn;
    private GomokuStone winner;
    private int[] winnerLine;
    private String lastForbiddenReason;
    private final List<GomokuMove> moveHistory;
    private final List<GomokuStone[][]> snapshots;

    public GomokuBoard() {
        this.moveHistory = new ArrayList<>();
        this.snapshots = new ArrayList<>();
        reset();
    }

    public GomokuBoard(GomokuBoard other) {
        this.moveHistory = new ArrayList<>(other.moveHistory.size());
        this.snapshots = new ArrayList<>(other.snapshots.size());
        this.board = cloneBoard(other.board);
        this.currentTurn = other.currentTurn;
        this.winner = other.winner;
        this.winnerLine = other.winnerLine == null ? null : other.winnerLine.clone();
        this.lastForbiddenReason = other.lastForbiddenReason;
        for (GomokuMove move : other.moveHistory) {
            this.moveHistory.add(new GomokuMove(move.getRow(), move.getCol(), move.getStone()));
        }
        for (GomokuStone[][] snap : other.snapshots) {
            this.snapshots.add(cloneBoard(snap));
        }
    }

    public void reset() {
        this.board = new GomokuStone[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                board[r][c] = GomokuStone.EMPTY;
            }
        }
        this.currentTurn = GomokuStone.BLACK;
        this.winner = GomokuStone.EMPTY;
        this.winnerLine = null;
        this.lastForbiddenReason = "";
        this.moveHistory.clear();
        this.snapshots.clear();
        this.snapshots.add(cloneBoard(board));
    }

    void setBoardForTest(String[] rows, GomokuStone turn) {
        if (rows == null || rows.length != SIZE) {
            throw new IllegalArgumentException("rows must be 15 lines");
        }
        this.board = new GomokuStone[SIZE][SIZE];
        this.moveHistory.clear();
        this.snapshots.clear();
        for (int r = 0; r < SIZE; r++) {
            if (rows[r] == null || rows[r].length() != SIZE) {
                throw new IllegalArgumentException("each row must have length 15");
            }
            for (int c = 0; c < SIZE; c++) {
                char ch = rows[r].charAt(c);
                GomokuStone s;
                if (ch == 'B' || ch == 'b' || ch == 'X' || ch == 'x') {
                    s = GomokuStone.BLACK;
                } else if (ch == 'W' || ch == 'w' || ch == 'O' || ch == 'o') {
                    s = GomokuStone.WHITE;
                } else {
                    s = GomokuStone.EMPTY;
                }
                board[r][c] = s;
            }
        }
        this.currentTurn = (turn == GomokuStone.WHITE) ? GomokuStone.WHITE : GomokuStone.BLACK;
        this.winner = GomokuStone.EMPTY;
        this.winnerLine = null;
        this.lastForbiddenReason = "";
        this.snapshots.add(cloneBoard(board));
    }

    public void setCurrentTurnForSearch(GomokuStone color) {
        if (color == GomokuStone.BLACK || color == GomokuStone.WHITE) {
            this.currentTurn = color;
        }
    }

    public GomokuPlaceResult place(int row, int col, boolean forbiddenEnabled) {
        if (!isInside(row, col) || board[row][col] != GomokuStone.EMPTY) {
            return GomokuPlaceResult.illegal("落点无效");
        }
        if (isGameOver()) {
            return GomokuPlaceResult.illegal("对局已结束");
        }

        if (forbiddenEnabled && currentTurn == GomokuStone.BLACK) {
            String reason = getForbiddenReasonForBlack(row, col);
            if (!reason.isEmpty()) {
                lastForbiddenReason = reason;
                return GomokuPlaceResult.forbidden(reason);
            }
        }

        GomokuStone placed = currentTurn;
        board[row][col] = placed;
        moveHistory.add(new GomokuMove(row, col, placed));
        snapshots.add(cloneBoard(board));

        int maxLine = maxLineLength(row, col, placed);
        if (maxLine >= 5) {
            winner = placed;
            winnerLine = computeWinnerLine(row, col, placed);
        } else {
            winner = GomokuStone.EMPTY;
            winnerLine = null;
        }

        currentTurn = placed.opposite();
        lastForbiddenReason = "";
        return GomokuPlaceResult.success();
    }

    public boolean undoMove() {
        if (moveHistory.isEmpty()) {
            return false;
        }
        moveHistory.remove(moveHistory.size() - 1);
        snapshots.remove(snapshots.size() - 1);
        board = cloneBoard(snapshots.get(snapshots.size() - 1));
        winner = GomokuStone.EMPTY;
        winnerLine = null;
        lastForbiddenReason = "";
        currentTurn = (moveHistory.size() % 2 == 0) ? GomokuStone.BLACK : GomokuStone.WHITE;
        return true;
    }

    public boolean canUndo() {
        return !moveHistory.isEmpty();
    }

    public int getMoveCount() {
        return moveHistory.size();
    }

    public boolean isInside(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    public GomokuStone getStone(int row, int col) {
        if (!isInside(row, col)) {
            return GomokuStone.EMPTY;
        }
        return board[row][col];
    }

    public GomokuStone getCurrentTurn() {
        return currentTurn;
    }

    public GomokuStone getWinner() {
        return winner;
    }

    public boolean isGameOver() {
        return winner != GomokuStone.EMPTY || moveHistory.size() >= SIZE * SIZE;
    }

    public String getGameResult() {
        if (winner == GomokuStone.BLACK) {
            return "黑方五连！黑方获胜";
        }
        if (winner == GomokuStone.WHITE) {
            return "白方五连！白方获胜";
        }
        if (moveHistory.size() >= SIZE * SIZE) {
            return "和棋（棋盘已满）";
        }
        return "";
    }

    public GomokuMove getLastMove() {
        if (moveHistory.isEmpty()) {
            return null;
        }
        return moveHistory.get(moveHistory.size() - 1);
    }

    public List<GomokuMove> getMoveHistory() {
        return Collections.unmodifiableList(moveHistory);
    }

    public GomokuStone[][] getBoardAtMove(int moveIndex) {
        if (moveIndex < 0 || moveIndex >= snapshots.size()) {
            return null;
        }
        return cloneBoard(snapshots.get(moveIndex));
    }

    public int[] getWinnerLine() {
        return winnerLine == null ? null : winnerLine.clone();
    }

    public String getLastForbiddenReason() {
        return lastForbiddenReason;
    }

    public List<int[]> getForbiddenPointsForBlack(int limit) {
        List<int[]> points = new ArrayList<>();
        if (isGameOver() || currentTurn != GomokuStone.BLACK) {
            return points;
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] != GomokuStone.EMPTY) {
                    continue;
                }
                if (!getForbiddenReasonForBlack(r, c).isEmpty()) {
                    points.add(new int[] {r, c});
                    if (limit > 0 && points.size() >= limit) {
                        return points;
                    }
                }
            }
        }
        return points;
    }

    public String getForbiddenReasonForBlack(int row, int col) {
        if (!isInside(row, col) || board[row][col] != GomokuStone.EMPTY) {
            return "";
        }
        board[row][col] = GomokuStone.BLACK;
        int maxLine = maxLineLength(row, col, GomokuStone.BLACK);
        if (maxLine == 5) {
            board[row][col] = GomokuStone.EMPTY;
            return "";
        }
        if (maxLine > 5) {
            board[row][col] = GomokuStone.EMPTY;
            return "长连禁手";
        }
        int four = countPatternDirections(row, col, GomokuStone.BLACK, FOUR_PATTERNS);
        if (four >= 2) {
            board[row][col] = GomokuStone.EMPTY;
            return "四四禁手";
        }
        int three = countPatternDirections(row, col, GomokuStone.BLACK, THREE_PATTERNS);
        if (three >= 2) {
            board[row][col] = GomokuStone.EMPTY;
            return "三三禁手";
        }
        board[row][col] = GomokuStone.EMPTY;
        return "";
    }

    private int countPatternDirections(int row, int col, GomokuStone color, String[] patterns) {
        int count = 0;
        for (int[] d : DIRS) {
            String line = buildLine(row, col, d[0], d[1], color);
            if (hasAnyPatternThroughCenter(line, 5, patterns)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasAnyPatternThroughCenter(String line, int center, String[] patterns) {
        for (String pattern : patterns) {
            int len = pattern.length();
            for (int i = 0; i + len <= line.length(); i++) {
                if (i > center || i + len <= center) {
                    continue;
                }
                if (matchPattern(line, i, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchPattern(String line, int start, String pattern) {
        for (int i = 0; i < pattern.length(); i++) {
            if (line.charAt(start + i) != pattern.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private String buildLine(int row, int col, int dr, int dc, GomokuStone color) {
        StringBuilder sb = new StringBuilder(11);
        for (int k = -5; k <= 5; k++) {
            int r = row + dr * k;
            int c = col + dc * k;
            if (!isInside(r, c)) {
                sb.append('O');
                continue;
            }
            GomokuStone s = board[r][c];
            if (s == GomokuStone.EMPTY) {
                sb.append('.');
            } else if (s == color) {
                sb.append('X');
            } else {
                sb.append('O');
            }
        }
        return sb.toString();
    }

    private int maxLineLength(int row, int col, GomokuStone stone) {
        int best = 1;
        for (int[] d : DIRS) {
            int v = 1 + countDirection(row, col, d[0], d[1], stone) + countDirection(row, col, -d[0], -d[1], stone);
            if (v > best) {
                best = v;
            }
        }
        return best;
    }

    private int[] computeWinnerLine(int row, int col, GomokuStone stone) {
        int bestLen = 1;
        int[] best = new int[] {row, col, row, col};
        for (int[] d : DIRS) {
            int a = countDirection(row, col, d[0], d[1], stone);
            int b = countDirection(row, col, -d[0], -d[1], stone);
            int len = 1 + a + b;
            if (len > bestLen) {
                bestLen = len;
                best = new int[] {
                    row - b * d[0],
                    col - b * d[1],
                    row + a * d[0],
                    col + a * d[1]
                };
            }
        }
        return best;
    }

    private int countDirection(int row, int col, int dr, int dc, GomokuStone stone) {
        int n = 0;
        int r = row + dr;
        int c = col + dc;
        while (isInside(r, c) && board[r][c] == stone) {
            n++;
            r += dr;
            c += dc;
        }
        return n;
    }

    private GomokuStone[][] cloneBoard(GomokuStone[][] src) {
        GomokuStone[][] copy = new GomokuStone[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            System.arraycopy(src[r], 0, copy[r], 0, SIZE);
        }
        return copy;
    }
}
