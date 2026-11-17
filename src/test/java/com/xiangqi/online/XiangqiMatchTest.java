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
        assertFalse(match.checkTactic().isEmpty());
    }

    @Test
    void reportsSpecificRuleReasonsForBlockedHorseElephantRiverAndGeneralsFacing() {
        Board board = new Board();
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                board.setPiece(row, col, null);
            }
        }
        board.setPiece(0, 4, new Piece(PieceType.JIANG, PieceColor.BLACK, 0, 4));
        board.setPiece(9, 4, new Piece(PieceType.SHUAI, PieceColor.RED, 9, 4));
        // Red horse at (9,1) blocked by pawn at (8,1) -> moving to (7,2) triggers 蹩马腿
        board.setPiece(9, 1, new Piece(PieceType.MA_RED, PieceColor.RED, 9, 1));
        board.setPiece(8, 1, new Piece(PieceType.ZU_RED, PieceColor.RED, 8, 1));
        // Red elephant at (9,2) blocked at (8,3) -> moving to (7,4) triggers 塞象眼
        board.setPiece(9, 2, new Piece(PieceType.XIANG_RED, PieceColor.RED, 9, 2));
        board.setPiece(8, 3, new Piece(PieceType.ZU_RED, PieceColor.RED, 8, 3));
        // Red elephant at (5,2) -> moving to (3,4) triggers 象不过河
        board.setPiece(5, 2, new Piece(PieceType.XIANG_RED, PieceColor.RED, 5, 2));
        // Blocking piece between generals at (5,4); moving it off file 4 triggers 将帅照面
        board.setPiece(5, 4, new Piece(PieceType.CHE_RED, PieceColor.RED, 5, 4));

        XiangqiMatch match = new XiangqiMatch(
            new MatchPlayer("u-red", "red", PlayerSide.RED),
            new MatchPlayer("u-black", "black", PlayerSide.BLACK),
            board
        );

        MatchEvent horseBlocked = match.previewMove("u-red", new XiangqiMoveInput(9, 1, 7, 2));
        assertFalse(horseBlocked.accepted());
        assertTrue(horseBlocked.message().contains("蹩马腿"), horseBlocked.message());

        MatchEvent elephantBlocked = match.previewMove("u-red", new XiangqiMoveInput(9, 2, 7, 4));
        assertFalse(elephantBlocked.accepted());
        assertTrue(elephantBlocked.message().contains("塞象眼"), elephantBlocked.message());

        MatchEvent elephantRiver = match.previewMove("u-red", new XiangqiMoveInput(5, 2, 3, 4));
        assertFalse(elephantRiver.accepted());
        assertTrue(elephantRiver.message().contains("象不过河"), elephantRiver.message());

        MatchEvent generalsFacing = match.previewMove("u-red", new XiangqiMoveInput(5, 4, 5, 5));
        assertFalse(generalsFacing.accepted());
        assertTrue(generalsFacing.message().contains("将帅照面"), generalsFacing.message());
    }
}
