package com.xiangqi.model.go;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GoBoard {
    private final int size;
    private final double komi;
    private GoStone[][] board;
    private GoStone currentTurn;
    private int blackCaptures;
    private int whiteCaptures;
    private int consecutivePasses;
    private boolean scoringReady;
    private GoScoreSummary scoreSummary;
    private final List<GoHistoryEntry> history;
    private final List<Snapshot> snapshots;
    private final Set<String> seenBoardHashes;

    public GoBoard(int size, double komi) {
        this.size = Math.max(5, size);
        this.komi = komi;
        this.history = new ArrayList<GoHistoryEntry>();
        this.snapshots = new ArrayList<Snapshot>();
        this.seenBoardHashes = new HashSet<String>();
        reset();
    }

    public GoBoard(GoBoard other) {
        this.size = other.size;
        this.komi = other.komi;
        this.history = new ArrayList<GoHistoryEntry>(other.history.size());
        this.snapshots = new ArrayList<Snapshot>(other.snapshots.size());
        this.seenBoardHashes = new HashSet<String>(other.seenBoardHashes);
        this.board = cloneBoard(other.board);
        this.currentTurn = other.currentTurn;
        this.blackCaptures = other.blackCaptures;
        this.whiteCaptures = other.whiteCaptures;
        this.consecutivePasses = other.consecutivePasses;
        this.scoringReady = other.scoringReady;
        this.scoreSummary = other.scoreSummary == null
            ? null
            : new GoScoreSummary(other.scoreSummary.getBlackArea(), other.scoreSummary.getWhiteArea(),
                other.scoreSummary.getKomi(), other.scoreSummary.getFinalScore(), other.scoreSummary.getWinner());
        for (GoHistoryEntry entry : other.history) {
            this.history.add(new GoHistoryEntry(entry.getRow(), entry.getCol(), entry.getStone(), entry.isPass()));
        }
        for (Snapshot snapshot : other.snapshots) {
            GoScoreSummary summary = snapshot.scoreSummary == null
                ? null
                : new GoScoreSummary(snapshot.scoreSummary.getBlackArea(), snapshot.scoreSummary.getWhiteArea(),
                    snapshot.scoreSummary.getKomi(), snapshot.scoreSummary.getFinalScore(), snapshot.scoreSummary.getWinner());
            this.snapshots.add(new Snapshot(cloneBoard(snapshot.board), snapshot.currentTurn, snapshot.blackCaptures,
                snapshot.whiteCaptures, snapshot.consecutivePasses, snapshot.scoringReady, summary));
        }
    }

    public void reset() {
        this.board = new GoStone[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                board[row][col] = GoStone.EMPTY;
            }
        }
        this.currentTurn = GoStone.BLACK;
        this.blackCaptures = 0;
        this.whiteCaptures = 0;
        this.consecutivePasses = 0;
        this.scoringReady = false;
        this.scoreSummary = null;
        this.history.clear();
        this.snapshots.clear();
        this.seenBoardHashes.clear();
        pushSnapshot();
    }

    public void loadPosition(String[] rows, GoStone turn) {
        reset();
        for (int row = 0; row < Math.min(rows.length, size); row++) {
            String line = rows[row] == null ? "" : rows[row];
            for (int col = 0; col < Math.min(line.length(), size); col++) {
                char ch = line.charAt(col);
                if (ch == 'B' || ch == 'b' || ch == 'X' || ch == 'x') {
                    board[row][col] = GoStone.BLACK;
                } else if (ch == 'W' || ch == 'w' || ch == 'O' || ch == 'o') {
                    board[row][col] = GoStone.WHITE;
                } else {
                    board[row][col] = GoStone.EMPTY;
                }
            }
        }
        this.currentTurn = turn == GoStone.WHITE ? GoStone.WHITE : GoStone.BLACK;
        this.history.clear();
        this.snapshots.clear();
        this.seenBoardHashes.clear();
        pushSnapshot();
    }

    public GoMoveResult place(int row, int col) {
        if (!isInside(row, col) || board[row][col] != GoStone.EMPTY) {
            return GoMoveResult.illegal("落点无效");
        }
        clearScoringIfNeeded();

        GoStone[][] next = cloneBoard(board);
        next[row][col] = currentTurn;
        GoStone opponent = currentTurn.opposite();
        int captured = 0;
        boolean[][] visited = new boolean[size][size];
        for (int[] dir : dirs()) {
            int nr = row + dir[0];
            int nc = col + dir[1];
            if (!isInside(nr, nc) || next[nr][nc] != opponent || visited[nr][nc]) {
                continue;
            }
            Set<Integer> group = collectGroup(next, nr, nc, opponent, visited);
            if (countLiberties(next, group) == 0) {
                captured += removeGroup(next, group);
            }
        }

        Set<Integer> ownGroup = collectGroup(next, row, col, currentTurn, new boolean[size][size]);
        if (countLiberties(next, ownGroup) == 0) {
            return GoMoveResult.illegal("自杀禁入");
        }

        String hash = hash(next);
        if (seenBoardHashes.contains(hash)) {
            return GoMoveResult.illegal("全局打劫禁入");
        }

        this.board = next;
        if (currentTurn == GoStone.BLACK) {
            blackCaptures += captured;
        } else {
            whiteCaptures += captured;
        }
        history.add(new GoHistoryEntry(row, col, currentTurn, false));
        currentTurn = opponent;
        consecutivePasses = 0;
        pushSnapshot();
        return GoMoveResult.success(captured, false);
    }

    public GoMoveResult pass() {
        clearScoringIfNeeded();
        history.add(new GoHistoryEntry(-1, -1, currentTurn, true));
        currentTurn = currentTurn.opposite();
        consecutivePasses++;
        pushSnapshot();
        if (consecutivePasses >= 2) {
            scoringReady = true;
            scoreSummary = scoreGame();
        }
        return GoMoveResult.success(0, true);
    }

    public void undoMove() {
        if (history.isEmpty()) {
            return;
        }
        history.remove(history.size() - 1);
        snapshots.remove(snapshots.size() - 1);
        restoreSnapshot(snapshots.get(snapshots.size() - 1));
        rebuildSeenHashes();
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public GoStone[][] getBoardAtMove(int moveIndex) {
        if (moveIndex < 0 || moveIndex >= snapshots.size()) {
            return null;
        }
        return cloneBoard(snapshots.get(moveIndex).board);
    }

    public List<GoHistoryEntry> getMoveHistory() {
        return new ArrayList<GoHistoryEntry>(history);
    }

    public int getMoveCount() {
        return history.size();
    }

    public GoStone getStone(int row, int col) {
        if (!isInside(row, col)) {
            return GoStone.EMPTY;
        }
        return board[row][col];
    }

    public boolean isInside(int row, int col) {
        return row >= 0 && row < size && col >= 0 && col < size;
    }

    public int getSize() {
        return size;
    }

    public double getKomi() {
        return komi;
    }

    public GoStone getCurrentTurn() {
        return currentTurn;
    }

    public int getBlackCaptures() {
        return blackCaptures;
    }

    public int getWhiteCaptures() {
        return whiteCaptures;
    }

    public int getConsecutivePasses() {
        return consecutivePasses;
    }

    public boolean isScoringReady() {
        return scoringReady;
    }

    public GoScoreSummary getScoreSummary() {
        return scoreSummary;
    }

    public GoScoreSummary scoreGame() {
        int blackArea = 0;
        int whiteArea = 0;
        boolean[][] visited = new boolean[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (board[row][col] == GoStone.BLACK) {
                    blackArea++;
                } else if (board[row][col] == GoStone.WHITE) {
                    whiteArea++;
                }
            }
        }

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (board[row][col] != GoStone.EMPTY || visited[row][col]) {
                    continue;
                }
                Territory territory = collectTerritory(row, col, visited);
                if (territory.owner == GoStone.BLACK) {
                    blackArea += territory.points;
                } else if (territory.owner == GoStone.WHITE) {
                    whiteArea += territory.points;
                }
            }
        }

        double finalScore = blackArea - (whiteArea + komi);
        String winner = finalScore > 0 ? "BLACK" : (finalScore < 0 ? "WHITE" : "DRAW");
        return new GoScoreSummary(blackArea, whiteArea, komi, finalScore, winner);
    }

    private void clearScoringIfNeeded() {
        if (!scoringReady) {
            return;
        }
        scoringReady = false;
        scoreSummary = null;
        consecutivePasses = 0;
    }

    private void pushSnapshot() {
        snapshots.add(new Snapshot(cloneBoard(board), currentTurn, blackCaptures, whiteCaptures,
            consecutivePasses, scoringReady, scoreSummary));
        seenBoardHashes.add(hash(board));
    }

    private void restoreSnapshot(Snapshot snapshot) {
        this.board = cloneBoard(snapshot.board);
        this.currentTurn = snapshot.currentTurn;
        this.blackCaptures = snapshot.blackCaptures;
        this.whiteCaptures = snapshot.whiteCaptures;
        this.consecutivePasses = snapshot.consecutivePasses;
        this.scoringReady = snapshot.scoringReady;
        this.scoreSummary = snapshot.scoreSummary;
    }

    private void rebuildSeenHashes() {
        seenBoardHashes.clear();
        for (Snapshot snapshot : snapshots) {
            seenBoardHashes.add(hash(snapshot.board));
        }
    }

    private Territory collectTerritory(int row, int col, boolean[][] visited) {
        ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        queue.add(new int[] {row, col});
        visited[row][col] = true;
        int points = 0;
        Set<GoStone> borders = new HashSet<GoStone>();
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            points++;
            for (int[] dir : dirs()) {
                int nr = cell[0] + dir[0];
                int nc = cell[1] + dir[1];
                if (!isInside(nr, nc)) {
                    continue;
                }
                GoStone stone = board[nr][nc];
                if (stone == GoStone.EMPTY) {
                    if (!visited[nr][nc]) {
                        visited[nr][nc] = true;
                        queue.addLast(new int[] {nr, nc});
                    }
                } else {
                    borders.add(stone);
                }
            }
        }
        GoStone owner = borders.size() == 1 ? borders.iterator().next() : GoStone.EMPTY;
        return new Territory(points, owner);
    }

    private GoStone[][] cloneBoard(GoStone[][] source) {
        GoStone[][] copy = new GoStone[size][size];
        for (int row = 0; row < size; row++) {
            System.arraycopy(source[row], 0, copy[row], 0, size);
        }
        return copy;
    }

    private Set<Integer> collectGroup(GoStone[][] source, int row, int col, GoStone stone, boolean[][] visited) {
        Set<Integer> group = new HashSet<Integer>();
        ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        queue.add(new int[] {row, col});
        visited[row][col] = true;
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            int r = cell[0];
            int c = cell[1];
            group.add(Integer.valueOf(r * size + c));
            for (int[] dir : dirs()) {
                int nr = r + dir[0];
                int nc = c + dir[1];
                if (!isInside(nr, nc) || visited[nr][nc] || source[nr][nc] != stone) {
                    continue;
                }
                visited[nr][nc] = true;
                queue.addLast(new int[] {nr, nc});
            }
        }
        return group;
    }

    private int countLiberties(GoStone[][] source, Set<Integer> group) {
        Set<Integer> liberties = new HashSet<Integer>();
        for (Integer value : group) {
            int row = value.intValue() / size;
            int col = value.intValue() % size;
            for (int[] dir : dirs()) {
                int nr = row + dir[0];
                int nc = col + dir[1];
                if (isInside(nr, nc) && source[nr][nc] == GoStone.EMPTY) {
                    liberties.add(Integer.valueOf(nr * size + nc));
                }
            }
        }
        return liberties.size();
    }

    private int removeGroup(GoStone[][] source, Set<Integer> group) {
        int removed = 0;
        for (Integer value : group) {
            int row = value.intValue() / size;
            int col = value.intValue() % size;
            if (source[row][col] != GoStone.EMPTY) {
                source[row][col] = GoStone.EMPTY;
                removed++;
            }
        }
        return removed;
    }

    private String hash(GoStone[][] source) {
        StringBuilder sb = new StringBuilder(size * size + size);
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                GoStone stone = source[row][col];
                sb.append(stone == GoStone.BLACK ? 'B' : (stone == GoStone.WHITE ? 'W' : '.'));
            }
            sb.append('/');
        }
        return sb.toString();
    }

    private int[][] dirs() {
        return new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    }

    public static final class GoHistoryEntry {
        private final int row;
        private final int col;
        private final GoStone stone;
        private final boolean pass;

        public GoHistoryEntry(int row, int col, GoStone stone, boolean pass) {
            this.row = row;
            this.col = col;
            this.stone = stone;
            this.pass = pass;
        }

        public int getRow() {
            return row;
        }

        public int getCol() {
            return col;
        }

        public GoStone getStone() {
            return stone;
        }

        public boolean isPass() {
            return pass;
        }
    }

    private static final class Snapshot {
        private final GoStone[][] board;
        private final GoStone currentTurn;
        private final int blackCaptures;
        private final int whiteCaptures;
        private final int consecutivePasses;
        private final boolean scoringReady;
        private final GoScoreSummary scoreSummary;

        private Snapshot(GoStone[][] board, GoStone currentTurn, int blackCaptures, int whiteCaptures,
                         int consecutivePasses, boolean scoringReady, GoScoreSummary scoreSummary) {
            this.board = board;
            this.currentTurn = currentTurn;
            this.blackCaptures = blackCaptures;
            this.whiteCaptures = whiteCaptures;
            this.consecutivePasses = consecutivePasses;
            this.scoringReady = scoringReady;
            this.scoreSummary = scoreSummary;
        }
    }

    private static final class Territory {
        private final int points;
        private final GoStone owner;

        private Territory(int points, GoStone owner) {
            this.points = points;
            this.owner = owner;
        }
    }
}
