package com.xiangqi.online;

import com.xiangqi.online.game.MatchEvent;
import com.xiangqi.online.game.MatchPlayer;
import com.xiangqi.online.game.PlayerSide;
import com.xiangqi.online.game.XiangqiMatch;
import com.xiangqi.online.game.XiangqiMoveInput;
import com.xiangqi.model.Board;
import com.xiangqi.model.Piece;
import com.xiangqi.model.PieceColor;
import com.xiangqi.model.PieceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XiangqiMatchTest {

    @Test
    void legalMoveAdvancesTurnAndIllegalMoveIsRejected() {
        XiangqiMatch match = new XiangqiMatch(
            new MatchPlayer("u-red", "red", PlayerSide.RED),
            new MatchPlayer("u-black", "black", PlayerSide.BLACK)
        );

        MatchEvent accepted = match.applyMove("u-red", new XiangqiMoveInput(6, 0, 5, 0));
        MatchEvent rejected = match.applyMove("u-red", new XiangqiMoveInput(6, 2, 5, 2));

        assertTrue(accepted.accepted());
        assertEquals(PlayerSide.BLACK, match.currentTurn());
        assertEquals("卒", match.board()[5][0]);
        assertFalse(rejected.accepted());
        assertEquals("not your turn", rejected.message());
    }

    @Test
    void exposesTheSideThatIsCurrentlyInCheck() {
        Board board = new Board();
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                board.setPiece(row, col, null);
            }
        }
        board.setPiece(0, 4, new Piece(PieceType.JIANG, PieceColor.BLACK, 0, 4));
        board.setPiece(9, 4, new Piece(PieceType.SHUAI, PieceColor.RED, 9, 4));
        board.setPiece(1, 3, new Piece(PieceType.CHE_RED, PieceColor.RED, 1, 3));
        XiangqiMatch match = new XiangqiMatch(
            new MatchPlayer("u-red", "red", PlayerSide.RED),
            new MatchPlayer("u-black", "black", PlayerSide.BLACK),
            board
        );

        MatchEvent accepted = match.applyMove("u-red", new XiangqiMoveInput(1, 3, 1, 4));

        assertTrue(accepted.accepted());
        assertEquals("BLACK", match.inCheckSide());
    }
}
