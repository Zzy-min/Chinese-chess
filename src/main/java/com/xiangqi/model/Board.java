package com.xiangqi.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 棋盘类 - 管理棋盘状态和棋子移动规则
 */
public class Board {
    public static final int ROWS = 10;
    public static final int COLS = 9;

    private Piece[][] board;
    private PieceColor currentTurn;
    private int moveCount;
    private List<Move> moveHistory;

    public Board() {
        board = new Piece[ROWS][COLS];
        currentTurn = PieceColor.RED;
        moveCount = 0;
        moveHistory = new ArrayList<>();
        initializeBoard();
    }

    public Board(Board other) {
        this.board = new Piece[ROWS][COLS];
        this.currentTurn = other.currentTurn;
        this.moveCount = other.moveCount;
        this.moveHistory = new ArrayList<>(other.moveHistory);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Piece piece = other.board[row][col];
                if (piece != null) {
                    this.board[row][col] = piece.copy();
                }
            }
        }
    }

    public void initializeBoard() {
        // 初始化黑方棋子 (上方)
        board[0][0] = new Piece(PieceType.CHE, PieceColor.BLACK, 0, 0);
        board[0][1] = new Piece(PieceType.MA, PieceColor.BLACK, 0, 1);
        board[0][2] = new Piece(PieceType.XIANG, PieceColor.BLACK, 0, 2);
        board[0][3] = new Piece(PieceType.SHI, PieceColor.BLACK, 0, 3);
        board[0][4] = new Piece(PieceType.JIANG, PieceColor.BLACK, 0, 4);
        board[0][5] = new Piece(PieceType.SHI, PieceColor.BLACK, 0, 5);
        board[0][6] = new Piece(PieceType.XIANG, PieceColor.BLACK, 0, 6);
        board[0][7] = new Piece(PieceType.MA, PieceColor.BLACK, 0, 7);
        board[0][8] = new Piece(PieceType.CHE, PieceColor.BLACK, 0, 8);
        board[2][1] = new Piece(PieceType.PAO, PieceColor.BLACK, 2, 1);
        board[2][7] = new Piece(PieceType.PAO, PieceColor.BLACK, 2, 7);
        for (int i = 0; i < 9; i += 2) {
            board[3][i] = new Piece(PieceType.ZU, PieceColor.BLACK, 3, i);
        }

        // 初始化红方棋子 (下方)
        board[9][0] = new Piece(PieceType.CHE_RED, PieceColor.RED, 9, 0);
        board[9][1] = new Piece(PieceType.MA_RED, PieceColor.RED, 9, 1);
        board[9][2] = new Piece(PieceType.XIANG_RED, PieceColor.RED, 9, 2);
        board[9][3] = new Piece(PieceType.SHI_RED, PieceColor.RED, 9, 3);
        board[9][4] = new Piece(PieceType.SHUAI, PieceColor.RED, 9, 4);
        board[9][5] = new Piece(PieceType.SHI_RED, PieceColor.RED, 9, 5);
        board[9][6] = new Piece(PieceType.XIANG_RED, PieceColor.RED, 9, 6);
        board[9][7] = new Piece(PieceType.MA_RED, PieceColor.RED, 9, 7);
        board[9][8] = new Piece(PieceType.CHE_RED, PieceColor.RED, 9, 8);
        board[7][1] = new Piece(PieceType.PAO_RED, PieceColor.RED, 7, 1);
        board[7][7] = new Piece(PieceType.PAO_RED, PieceColor.RED, 7, 7);
        for (int i = 0; i < 9; i += 2) {
            board[6][i] = new Piece(PieceType.ZU_RED, PieceColor.RED, 6, i);
        }

        moveCount = 0;
        moveHistory.clear();
    }

    public Piece getPiece(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return null;
        }
        return board[row][col];
    }

    public void setPiece(int row, int col, Piece piece) {
        if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
            if (piece != null) {
                piece.setPosition(row, col);
            }
            board[row][col] = piece;
        }
    }

    public void movePiece(Move move) {
        Piece piece = board[move.getFromRow()][move.getFromCol()];
        Piece captured = board[move.getToRow()][move.getToCol()]; // 保存被吃的棋子

        // 设置被吃掉的棋子
        move.setCapturedPiece(captured);

        setPiece(move.getToRow(), move.getToCol(), piece);
        setPiece(move.getFromRow(), move.getFromCol(), null);

        moveHistory.add(move);
        moveCount++;
        currentTurn = currentTurn.opposite();
    }

    /**
     * 悔棋 - 撤销最后一步
     */
    public void undoMove() {
        if (moveHistory.isEmpty()) {
            return;
        }

        Move lastMove = moveHistory.remove(moveHistory.size() - 1);
        moveCount--;

        // 将棋子移回原位
        Piece piece = board[lastMove.getToRow()][lastMove.getToCol()];
        setPiece(lastMove.getFromRow(), lastMove.getFromCol(), piece);

        // 恢复被吃的棋子
        if (lastMove.getCapturedPiece() != null) {
            setPiece(lastMove.getToRow(), lastMove.getToCol(), lastMove.getCapturedPiece());
        } else {
            setPiece(lastMove.getToRow(), lastMove.getToCol(), null);
        }

        // 切换回上一步的回合
        currentTurn = currentTurn.opposite();
    }

    /**
     * 恢复到指定步数的棋盘状态
     */
    public void restoreToMove(int targetMoveCount) {
        if (targetMoveCount < 0 || targetMoveCount > moveCount) {
            return;
        }

        int movesToUndo = moveCount - targetMoveCount;
        for (int i = 0; i < movesToUndo; i++) {
            undoMove();
        }
    }

    /**
     * 获取指定步数的棋盘状态副本（用于棋盘回顾）
     */
    public Board getBoardAtMove(int targetMoveCount) {
        if (targetMoveCount < 0 || targetMoveCount > moveCount) {
            return null;
        }

        // 创建当前棋盘的深拷贝（含棋子）并复制移动历史
        Board result = new Board(this);
        result.moveHistory = copyMoveHistory(this.moveHistory);
        result.moveCount = this.moveCount;
        result.currentTurn = this.currentTurn;

        // 撤销到目标步数
        while (result.moveCount > targetMoveCount) {
            result.undoMove();
        }

        return result;
    }

    private List<Move> copyMoveHistory(List<Move> source) {
        List<Move> copy = new ArrayList<>(source.size());
        for (Move move : source) {
            Move cloned = new Move(move.getFromRow(), move.getFromCol(), move.getToRow(), move.getToCol());
            Piece captured = move.getCapturedPiece();
            if (captured != null) {
                cloned.setCapturedPiece(captured.copy());
            }
            copy.add(cloned);
        }
        return copy;
    }

    /**
     * 检查是否可以悔棋
     */
    public boolean canUndo() {
        return !moveHistory.isEmpty();
    }

    public boolean isValidMove(Move move) {
        Piece piece = getPiece(move.getFromRow(),move.getFromCol());
        if (piece == null || piece.getColor() != currentTurn) {
            return false;
        }

        Piece captured = getPiece(move.getToRow(), move.getToCol());
        if (captured != null && captured.getColor() == piece.getColor()) {
            return false;
        }

        // 检查移动后是否会被将军
        Board testBoard = new Board(this);
        testBoard.moveWithoutValidation(move);

        // 检查将帅是否在同一直线上直接对面
        if (testBoard.areGeneralsFacing()) {
            return false;
        }

        // 检查自己是否被将军
        if (testBoard.isInCheck(piece.getColor())) {
            return false;
        }

        return isValidMoveForPiece(piece, move.getToRow(), move.getToCol());
    }

    private void moveWithoutValidation(Move move) {
        Piece piece = board[move.getFromRow()][move.getFromCol()];
        setPiece(move.getToRow(), move.getToCol(), piece);
        setPiece(move.getFromRow(), move.getFromCol(), null);
        currentTurn = currentTurn.opposite();
    }

    public boolean isValidMoveForPiece(Piece piece, int toRow, int toCol) {
        int fromRow = piece.getRow();
        int fromCol = piece.getCol();

        switch (piece.getType()) {
            case CHE:
            case CHE_RED:
                return isValidCheMove(fromRow, fromCol, toRow, toCol);
            case MA:
            case MA_RED:
                return isValidMaMove(fromRow, fromCol, toRow, toCol);
            case XIANG:
            case XIANG_RED:
                return isValidXiangMove(piece.getColor(), fromRow, fromCol, toRow, toCol);
            case SHI:
            case SHI_RED:
                return isValidShiMove(piece.getColor(), fromRow, fromCol, toRow, toCol);
            case JIANG:
            case SHUAI:
                return isValidJiangMove(piece.getColor(), fromRow, fromCol, toRow, toCol);
            case PAO:
            case PAO_RED:
                return isValidPaoMove(fromRow, fromCol, toRow, toCol);
            case ZU:
            case ZU_RED:
                return isValidZuMove(piece.getColor(), fromRow, fromCol, toRow, toCol);
        }
        return false;
    }

    // 帅/将：九宫内横竖走一步
    private boolean isValidJiangMove(PieceColor color, int fromRow, int fromCol, int toRow, int toCol) {
        int dRow = Math.abs(toRow - fromRow);
        int dCol = Math.abs(toCol - fromCol);

        // 每一步只可以水平或垂直移动一点
        if (dRow + dCol != 1) {
            return false;
        }

        // 只能在九宫内移动
        if (!isInPalace(toRow, toCol, color)) {
            return false;
        }

        return true;
    }

    // 仕/士：九宫内斜走一步
    private boolean isValidShiMove(PieceColor color, int fromRow, int fromCol, int toRow, int toCol) {
        int dRow = Math.abs(toRow - fromRow);
        int dCol = Math.abs(toCol - fromCol);

        // 每一步只可以沿对角线方向移动一点
        if (dRow != 1 || dCol != 1) {
            return false;
        }

        // 只能在九宫内移动
        if (!isInPalace(toRow, toCol, color)) {
            return false;
        }

        return true;
    }

    // 相/象：田字移动，不能过河，塞象眼
    private boolean isValidXiangMove(PieceColor color, int fromRow, int fromCol, int toRow, int toCol) {
        int dRow = Math.abs(toRow - fromRow);
        int dCol = Math.abs(toCol - fromCol);

        // 每一步只可以沿对角线方向移动两点（田字）
        if (dRow != 2 || dCol != 2) {
            return false;
        }

        // 只能在河界的一侧移动
        if (color == PieceColor.RED && toRow < 5) {
            return false;
        }
        if (color == PieceColor.BLACK && toRow > 4) {
            return false;
        }

        // 塞象眼：田字中心有棋子则不能通过
        int eyeRow = (fromRow + toRow) / 2;
        int eyeCol = (fromCol + toCol) / 2;
        if (board[eyeRow][eyeCol] != null) {
            return false;
        }

        return true;
    }

    // 馬：日字移动，蹩马腿
    private boolean isValidMaMove(int fromRow, int fromCol, int toRow, int toCol) {
        int dRow = Math.abs(toRow - fromRow);
        int dCol = Math.abs(toCol - fromCol);

        // 马走日字：横向1步纵向2步，或横向2步纵向1步
        if (!((dRow == 2 && dCol == 1) || (dRow == 1 && dCol == 2))) {
            return false;
        }

        // 蹩马腿：第一步直行或横行处有棋子挡住则不能通过
        int legRow, legCol;
        if (dRow == 2) {
            legRow = fromRow + (toRow > fromRow ? 1 : -1);
            legCol = fromCol;
        } else {
            legRow = fromRow;
            legCol = fromCol + (toCol > fromCol ? 1 : -1);
        }

        if (board[legRow][legCol] != null) {
            return false;
        }

        return true;
    }

    // 車：横竖任意走，不能越子
    private boolean isValidCheMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow != toRow && fromCol != toCol) {
            return false;
        }

        return countPiecesBetween(fromRow, fromCol, toRow, toCol) == 0;
    }

    // 炮/砲：走法同车，吃子需翻山
    private boolean isValidPaoMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow != toRow && fromCol != toCol) {
            return false;
        }

        Piece target = board[toRow][toCol];
        int piecesBetween = countPiecesBetween(fromRow, fromCol, toRow, toCol);

        if (target == null) {
            // 移动时不能越子
            return piecesBetween == 0;
        } else {
            // 吃子时必须跳过一个棋子（翻山）
            return piecesBetween == 1;
        }
    }

    // 兵/卒：过河前只能前行，过河后可横走，不能后退
    private boolean isValidZuMove(PieceColor color, int fromRow, int fromCol, int toRow, int toCol) {
        int dRow = toRow - fromRow;
        int dCol = Math.abs(toCol - fromCol);

        // 是否已过河
        boolean crossedRiver;
        if (color == PieceColor.RED) {
            crossedRiver = fromRow <= 4;
        } else {
            crossedRiver = fromRow >= 5;
        }

        if (color == PieceColor.RED) {
            // 红方只能向上（行数减小）
            if (dRow > 0) {
                return false; // 不能后退
            }
            if (crossedRiver) {
                // 过河后：只能前行或横走一步
                return ((dRow == -1 && dCol == 0) || (dRow == 0 && dCol == 1));
            } else {
                // 过河前：只能前行一步
                return (dRow == -1 && dCol == 0);
            }
        } else {
            // 黑方只能向下（行数增加）
            if (dRow < 0) {
                return false; // 不能后退
            }
            if (crossedRiver) {
                // 过河后：只能前行或横走一步
                return ((dRow == 1 && dCol == 0) || (dRow == 0 && dCol == 1));
            } else {
                // 过河前：只能前行一步
                return (dRow == 1 && dCol == 0);
            }
        }
    }

    private boolean isInPalace(int row, int col, PieceColor color) {
        // 九宫范围：列3-5，红方行7-9，黑方行0-2
        if (col < 3 || col > 5) {
            return false;
        }
        if (color == PieceColor.RED) {
            return row >= 7 && row <= 9;
        } else {
            return row >= 0 && row <= 2;
        }
    }

    private int countPiecesBetween(int fromRow, int fromCol, int toRow, int toCol) {
        int count = 0;
        int dRow = Integer.signum(toRow - fromRow);
        int dCol = Integer.signum(toCol - fromCol);

        int row = fromRow + dRow;
        int col = fromCol + dCol;

        while (row != toRow || col != toCol) {
            if (board[row][col] != null) {
                count++;
            }
            row += dRow;
            col += dCol;
        }

        return count;
    }

    // 检查将帅是否在同一直线上直接对面（中间无棋子）
    public boolean areGeneralsFacing() {
        Piece redGeneral = findGeneral(PieceColor.RED);
        Piece blackGeneral = findGeneral(PieceColor.BLACK);

        if (redGeneral == null || blackGeneral == null) {
            return false;
        }

        // 必须在同一列
        if (redGeneral.getCol() != blackGeneral.getCol()) {
            return false;
        }

        // 检查中间是否有棋子
        int piecesBetween = countPiecesBetween(
            redGeneral.getRow(), redGeneral.getCol(),
            blackGeneral.getRow(), blackGeneral.getCol()
        );

        return piecesBetween == 0;
    }

    private Piece findGeneral(PieceColor color) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Piece piece = board[row][col];
                if (piece != null && piece.getColor() == color && piece.getType().isGeneral()) {
                    return piece;
                }
            }
        }
        return null;
    }

    // 检查某方是否被将军
    public boolean isInCheck(PieceColor color) {
        Piece general = findGeneral(color);
        if (general == null) {
            return false;
        }

        PieceColor opponent = color.opposite();
        List<Move> opponentMoves = getAllValidMovesForPieceCheck(opponent);

        for (Move move : opponentMoves) {
            if (move.getToRow() == general.getRow() && move.getToCol() == general.getCol()) {
                return true;
            }
        }

        return false;
    }

    // 获取某方所有可能的移动（用于检查将军）
    private List<Move> getAllValidMovesForPieceCheck(PieceColor color) {
        List<Move> moves = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Piece piece = board[row][col];
                if (piece != null && piece.getColor() == color) {
                    moves.addAll(getValidMovesForPieceWithoutCheck(piece));
                }
            }
        }
        return moves;
    }

    private List<Move> getValidMovesForPieceWithoutCheck(Piece piece) {
        List<Move> moves = new ArrayList<>();
        int fromRow = piece.getRow();
        int fromCol = piece.getCol();

        for (int toRow = 0; toRow < ROWS; toRow++) {
            for (int toCol = 0; toCol < COLS; toCol++) {
                if (isValidMoveForPiece(piece, toRow, toCol)) {
                    Piece target = board[toRow][toCol];
                    if (target == null || target.getColor() != piece.getColor()) {
                        moves.add(new Move(fromRow, fromCol, toRow, toCol));
                    }
                }
            }
        }
        return moves;
    }

    public List<Move> getAllValidMoves(PieceColor color) {
        List<Move> moves = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                Piece piece = board[row][col];
                if (piece != null && piece.getColor() == color) {
                    int fromRow = piece.getRow();
                    int fromCol = piece.getCol();

                    for (int toRow = 0; toRow < ROWS; toRow++) {
                        for (int toCol = 0; toCol < COLS; toCol++) {
                            Move move = new Move(fromRow, fromCol, toRow, toCol);
                            if (isValidMove(move)) {
                                moves.add(move);
                            }
                        }
                    }
                }
            }
        }
        return moves;
    }

    // 检查是否被将死
    public boolean isCheckmate(PieceColor color) {
        if (!isInCheck(color)) {
            return false;
        }

        List<Move> allMoves = getAllValidMoves(color);
        return allMoves.isEmpty();
    }

    // 检查是否被困毙（无子可动但未被将军）
    public boolean isStalemate(PieceColor color) {
        if (isInCheck(color)) {
            return false;
        }

        List<Move> allMoves = getAllValidMoves(color);
        return allMoves.isEmpty();
    }

    public PieceColor getCurrentTurn() {
        return currentTurn;
    }

    public void setCurrentTurn(PieceColor color) {
        this.currentTurn = color;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public List<Move> getMoveHistory() {
        return new ArrayList<>(moveHistory);
    }

    public Move getLastMove() {
        return moveHistory.isEmpty() ? null : moveHistory.get(moveHistory.size() - 1);
    }

    public boolean isGameOver() {
        PieceColor currentColor = getCurrentTurn();

        // 检查是否被将死
        if (isCheckmate(currentColor)) {
            return true;
        }

        // 检查是否被困毙（无子可动）
        if (isStalemate(currentColor)) {
            return true;
        }

        // 检查将/帅是否被吃
        if (findGeneral(PieceColor.RED) == null || findGeneral(PieceColor.BLACK) == null) {
            return true;
        }

        return false;
    }

    public PieceColor getWinner() {
        PieceColor currentColor = getCurrentTurn();

        // 将死：当前走棋方输了
        if (isCheckmate(currentColor)) {
            return currentColor.opposite();
        }

        // 困毙：当前走棋方输了（无子可动）
        if (isStalemate(currentColor)) {
            return currentColor.opposite();
        }

        // 将/帅被吃
        if (findGeneral(PieceColor.RED) == null) {
            return PieceColor.BLACK;
        }
        if (findGeneral(PieceColor.BLACK) == null) {
            return PieceColor.RED;
        }

        return null;
    }

    public String getGameResult() {
        PieceColor currentColor = getCurrentTurn();

        if (isCheckmate(currentColor)) {
            return "将死！" + (currentColor == PieceColor.RED ? "黑方" : "红方") + "获胜";
        }

        if (isStalemate(currentColor)) {
            return "困毙！" + (currentColor == PieceColor.RED ? "黑方" : "红方") + "获胜";
        }

        if (findGeneral(PieceColor.RED) == null) {
            return "红帅被吃！黑方获胜";
        }
        if (findGeneral(PieceColor.BLACK) == null) {
            return "黑将被吃！红方获胜";
        }

        return "";
    }
}

