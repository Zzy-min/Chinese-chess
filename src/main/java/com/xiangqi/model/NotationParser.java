package com.xiangqi.model;

/**
 * 中国象棋标准记谱法解析器
 *
 * 格式：棋子名 + 列号 + 动作 + 目标
 * - 红方列号用中文数字（一至九，从右到左）
 * - 黑方列号用阿拉伯数字（1至9，从右到左）
 * - 动作：进（前进）、退（后退）、平（横移）
 * - 目标：横移时为目标列号，进退时为步数
 *
 * 示例：車一进三（红方車从一列前进三步）、马8进7（黑马从8列进到7列）
 */
public final class NotationParser {

    private static final String[] RED_COLUMNS = {"九", "八", "七", "六", "五", "四", "三", "二", "一"};
    private static final String[] BLACK_COLUMNS = {"9", "8", "7", "6", "5", "4", "3", "2", "1"};

    private static final String RED_PIECES = "帥仕相馬車砲卒";
    private static final String BLACK_PIECES = "将士象马车炮兵";

    private NotationParser() {}

    /**
     * 将走子格式化为标准记谱法
     */
    public static String format(Move move, Board board) {
        Piece piece = board.getPiece(move.getFromRow(), move.getFromCol());
        if (piece == null) return move.toString();

        boolean isRed = piece.getColor() == PieceColor.RED;
        String pieceName = getNotationPieceName(piece);
        String fromColStr = formatColumn(move.getFromCol(), isRed);

        int dRow = move.getToRow() - move.getFromRow();
        int dCol = move.getToCol() - move.getFromCol();

        if (dCol == 0) {
            // 纵向移动（进/退）
            String action = isRed ? (dRow < 0 ? "进" : "退") : (dRow > 0 ? "进" : "退");
            int steps = Math.abs(dRow);
            String destStr = formatNumber(steps, isRed);
            return pieceName + fromColStr + action + destStr;
        } else if (dRow == 0) {
            // 横向移动（平）
            String destStr = formatColumn(move.getToCol(), isRed);
            return pieceName + fromColStr + "平" + destStr;
        } else {
            // 斜向移动（马、象、士）
            String action = isRed ? (dRow < 0 ? "进" : "退") : (dRow > 0 ? "进" : "退");
            String destStr = formatColumn(move.getToCol(), isRed);
            return pieceName + fromColStr + action + destStr;
        }
    }

    /**
     * 解析标准记谱法为 Move 对象
     */
    public static Move parse(String notation, Board board) {
        if (notation == null || notation.length() < 4) return null;

        notation = notation.trim();

        // 解析棋子名
        char pieceChar = notation.charAt(0);
        PieceColor color = isRedPiece(pieceChar) ? PieceColor.RED : PieceColor.BLACK;

        // 解析起始列号
        char fromColChar = notation.charAt(1);
        int fromCol = parseColumn(fromColChar, color);
        if (fromCol < 0) return null;

        // 找到对应的棋子
        Piece piece = findPiece(board, pieceChar, color, fromCol);
        if (piece == null) return null;

        int fromRow = piece.getRow();

        // 解析动作
        char action = notation.charAt(2);
        char destChar = notation.charAt(3);

        int toRow, toCol;

        if (action == '平') {
            // 横移
            toCol = parseColumn(destChar, color);
            toRow = fromRow;
        } else {
            // 进/退
            boolean isForward = action == '进';
            if (color == PieceColor.BLACK) isForward = !isForward;

            // 检查是数字步数还是列号（马/象/士用列号）
            if (Character.isDigit(destChar) || isChineseDigit(destChar)) {
                // 数字步数（車/炮/兵/帅/将）
                int steps = parseNumber(destChar, color);
                toCol = fromCol;
                toRow = isForward ? fromRow - steps : fromRow + steps;
            } else {
                // 列号（马/象/士的斜向移动）
                toCol = parseColumn(destChar, color);
                // 根据棋子类型计算目标行
                PieceType type = piece.getType();
                if (type == PieceType.MA || type == PieceType.MA_RED) {
                    int dCol = Math.abs(toCol - fromCol);
                    toRow = dCol == 1 ? (isForward ? fromRow - 2 : fromRow + 2)
                                      : (isForward ? fromRow - 1 : fromRow + 1);
                } else if (type == PieceType.XIANG || type == PieceType.XIANG_RED) {
                    toRow = isForward ? fromRow - 2 : fromRow + 2;
                } else if (type == PieceType.SHI || type == PieceType.SHI_RED) {
                    toRow = isForward ? fromRow - 1 : fromRow + 1;
                } else {
                    toRow = fromRow;
                }
            }
        }

        if (toRow < 0 || toRow >= Board.ROWS || toCol < 0 || toCol >= Board.COLS) {
            return null;
        }

        return new Move(fromRow, fromCol, toRow, toCol);
    }

    private static String getNotationPieceName(Piece piece) {
        PieceType type = piece.getType();
        switch (type) {
            case JIANG: return "将";
            case SHUAI: return "帅";
            case SHI: return "士";
            case SHI_RED: return "仕";
            case XIANG: return "象";
            case XIANG_RED: return "相";
            case MA: case MA_RED: return "马";
            case CHE: case CHE_RED: return "车";
            case PAO: case PAO_RED: return "炮";
            case ZU: return "兵";
            case ZU_RED: return "卒";
            default: return "?";
        }
    }

    private static String formatColumn(int col, boolean isRed) {
        // 列号：0=从右数第1列
        if (isRed) {
            return RED_COLUMNS[col];
        } else {
            return BLACK_COLUMNS[col];
        }
    }

    private static String formatNumber(int num, boolean isRed) {
        if (isRed) {
            String[] nums = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
            return num >= 0 && num < nums.length ? nums[num] : String.valueOf(num);
        } else {
            return String.valueOf(num);
        }
    }

    private static int parseColumn(char c, PieceColor color) {
        if (color == PieceColor.RED) {
            for (int i = 0; i < RED_COLUMNS.length; i++) {
                if (RED_COLUMNS[i].charAt(0) == c) return i;
            }
        } else {
            if (c >= '1' && c <= '9') {
                return 9 - (c - '0');
            }
        }
        // 也尝试中文数字
        int cn = parseChineseDigit(c);
        if (cn >= 0) return 9 - cn;
        return -1;
    }

    private static int parseNumber(char c, PieceColor color) {
        if (color == PieceColor.RED) {
            int cn = parseChineseDigit(c);
            return cn >= 0 ? cn : -1;
        } else {
            if (c >= '1' && c <= '9') return c - '0';
            int cn = parseChineseDigit(c);
            return cn >= 0 ? cn : -1;
        }
    }

    private static boolean isChineseDigit(char c) {
        return parseChineseDigit(c) >= 0;
    }

    private static int parseChineseDigit(char c) {
        switch (c) {
            case '一': return 1;
            case '二': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return -1;
        }
    }

    private static boolean isRedPiece(char c) {
        return RED_PIECES.indexOf(c) >= 0;
    }

    private static Piece findPiece(Board board, char pieceChar, PieceColor color, int col) {
        for (int row = 0; row < Board.ROWS; row++) {
            Piece piece = board.getPiece(row, col);
            if (piece != null && piece.getColor() == color) {
                String name = getNotationPieceName(piece);
                if (name.length() == 1 && name.charAt(0) == pieceChar) {
                    return piece;
                }
            }
        }
        return null;
    }
}
