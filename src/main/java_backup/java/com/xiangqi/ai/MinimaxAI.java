package com.xiangqi.ai;

import com.xiangqi.model.*;

import java.util.List;
import java.util.Random;

/**
 * 中国象棋AI - 使用Minimax算法和Alpha-Beta剪枝
 */
public class MinimaxAI {
    private static final int MAX_DEPTH = 5; // 最大搜索深度
    private static final int TIME_LIMIT_MS = 15000; // 15秒时间限制
    private static final Random random = new Random();

    // 时间控制相关
    private long searchStartTime;
    private boolean timeUp;

    public Move findBestMove(Board board, PieceColor aiColor) {
        List<Move> validMoves = board.getAllValidMoves(aiColor);
        if (validMoves.isEmpty()) {
            return null;
        }

        // 排序根节点的移动
        sortMovesByCaptureValue(validMoves, board);

        // 迭代加深搜索，受时间限制
        searchStartTime = System.currentTimeMillis();
        timeUp = false;

        Move bestMove = validMoves.get(0); // 默认第一个
        int bestScore = Integer.MIN_VALUE;
        int depth = 1;

        while (depth <= MAX_DEPTH && !timeUp) {
            int currentDepthBestScore = Integer.MIN_VALUE;
            Move currentDepthBestMove = null;

            for (Move move : validMoves) {
                if (timeUp) {
                    break;
                }

                Board testBoard = new Board(board);
                testBoard.movePiece(move);

                int score = minimax(testBoard, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false, aiColor);

                if (score > currentDepthBestScore || (score == currentDepthBestScore && random.nextBoolean())) {
                    currentDepthBestScore = score;
                    currentDepthBestMove = move;
                }
            }

            // 如果成功完成当前深度的搜索，更新最佳移动
            if (!timeUp && currentDepthBestMove != null) {
                bestMove = currentDepthBestMove;
                bestScore = currentDepthBestScore;
            }

            depth++;
        }

        return bestMove;
    }

    private int minimax(Board board, int depth, int alpha, int beta, boolean isMaximizing, PieceColor aiColor) {
        // 检查时间限制
        if (System.currentTimeMillis() - searchStartTime >= TIME_LIMIT_MS) {
            timeUp = true;
            return 0; // 返回中性值，因为时间到了
        }

        if (depth == 0 || board.isGameOver()) {
            return evaluate(board, aiColor);
        }

        PieceColor currentColor = isMaximizing ? aiColor : aiColor.opposite();
        List<Move> validMoves = board.getAllValidMoves(currentColor);

        // 排序移动以提升剪枝效率
        sortMovesByCaptureValue(validMoves, board);

        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : validMoves) {
                if (timeUp) {
                    break;
                }
                Board testBoard = new Board(board);
                testBoard.movePiece(move);
                int eval = minimax(testBoard, depth - 1, alpha, beta, false, aiColor);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : validMoves) {
                if (timeUp) {
                    break;
                }
                Board testBoard = new Board(board);
                testBoard.movePiece(move);
                int eval = minimax(testBoard, depth - 1, alpha, beta, true, aiColor);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return minEval;
        }
    }

    private int evaluate(Board board, PieceColor aiColor) {
        int score = 0;

        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece != null) {
                    int pieceValue = getPieceValue(piece);
                    int positionValue = getPositionValue(piece, row, col);

                    if (piece.getColor() == aiColor) {
                        score += pieceValue + positionValue;
                    } else {
                        score -= (pieceValue + positionValue);
                    }
                }
            }
        }

        // 检查将/帅是否被吃
        if (board.isGameOver()) {
            PieceColor winner = board.getWinner();
            if (winner == aiColor) {
                return 100000;
            } else {
                return -100000;
            }
        }

        return score;
    }

    private int getPieceValue(Piece piece) {
        switch (piece.getType()) {
            case JIANG:
            case SHUAI:
                return 10000;
            case CHE:
            case CHE_RED:
                return 900;
            case MA:
            case MA_RED:
                return 400;
            case PAO:
            case PAO_RED:
                return 450;
            case XIANG:
            case XIANG_RED:
                return 200;
            case SHI:
            case SHI_RED:
                return 200;
            case ZU:
            case ZU_RED:
                return 100;
            default:
                return 0;
        }
    }

    private void sortMovesByCaptureValue(List<Move> moves, Board board) {
        moves.sort((move1, move2) -> {
            boolean isCapture1 = board.getPiece(move1.getToRow(), move1.getToCol()) != null;
            boolean isCapture2 = board.getPiece(move2.getToRow(), move2.getToCol()) != null;

            // 吃子移动优先
            if (isCapture1 && !isCapture2) {
                return -1;
            }
            if (!isCapture1 && isCapture2) {
                return 1;
            }

            // 都是吃子时，按被吃棋子价值排序（降序）
            if (isCapture1 && isCapture2) {
                Piece captured1 = board.getPiece(move1.getToRow(), move1.getToCol());
                Piece captured2 = board.getPiece(move2.getToRow(), move2.getToCol());
                int value1 = getPieceValue(captured1);
                int value2 = getPieceValue(captured2);
                return Integer.compare(value2, value1);
            }

            return 0;
        });
    }

    private int getPositionValue(Piece piece, int row, int col) {
        PieceType type = piece.getType();
        PieceColor color = piece.getColor();

        // 兵/卒过河价值增加
        if (type == PieceType.ZU || type == PieceType.ZU_RED) {
            int riverCrossedBonus = 0;
            if (color == PieceColor.RED && row <= 4) {
                riverCrossedBonus = (4 - row) * 20;
            } else if (color == PieceColor.BLACK && row >= 5) {
                riverCrossedBonus = (row - 4) * 20;
            }
            return riverCrossedBonus;
        }

        // 马、车中心位置价值
        if (type == PieceType.MA || type == PieceType.MA_RED || type == PieceType.CHE || type == PieceType.CHE_RED) {
            int centerBonus = 0;
            int centerRow = 4;
            int centerCol = 4;
            int distance = Math.abs(row - centerRow) + Math.abs(col - centerCol);
            centerBonus = (8 - distance) * 10;
            return centerBonus;
        }

        // 炮位置价值
        if (type == PieceType.PAO || type == PieceType.PAO_RED) {
            int positionBonus = 0;
            if (row >= 2 && row <= 7 && col >= 2 && col <= 6) {
                positionBonus = 30;
            }
            return positionBonus;
        }

        return 0;
    }
}
