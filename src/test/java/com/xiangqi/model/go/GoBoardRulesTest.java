package com.xiangqi.model.go;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoBoardRulesTest {

    @Test
    void shouldCaptureSingleStoneGroup() {
        GoBoard board = new GoBoard(9, 7.5);
        board.loadPosition(new String[] {
            ".........",
            "BWB......",
            ".B.......",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            "........."
        }, GoStone.BLACK);

        GoMoveResult result = board.place(0, 1);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getCapturedStones());
        assertEquals(GoStone.EMPTY, board.getStone(1, 1));
        assertEquals(1, board.getBlackCaptures());
    }

    @Test
    void shouldRejectSuicideMove() {
        GoBoard board = new GoBoard(9, 7.5);
        board.loadPosition(new String[] {
            ".........",
            "..B......",
            ".B.B.....",
            "..B......",
            ".........",
            ".........",
            ".........",
            ".........",
            "........."
        }, GoStone.WHITE);

        GoMoveResult result = board.place(2, 2);

        assertFalse(result.isSuccess());
        assertEquals("自杀禁入", result.getReason());
        assertEquals(GoStone.EMPTY, board.getStone(2, 2));
    }

    @Test
    void shouldRejectSimpleKoRepetition() {
        GoBoard board = new GoBoard(5, 7.5);
        board.loadPosition(new String[] {
            ".....",
            "..BW.",
            ".BW.W",
            "..BW.",
            "....."
        }, GoStone.BLACK);

        GoMoveResult capture = board.place(2, 3);
        assertTrue(capture.isSuccess());
        assertEquals(1, capture.getCapturedStones());

        GoMoveResult recapture = board.place(2, 2);
        assertFalse(recapture.isSuccess());
        assertEquals("全局打劫禁入", recapture.getReason());
    }

    @Test
    void shouldScoreAfterDoublePassAndResumeWhenNewStonePlaced() {
        GoBoard board = new GoBoard(9, 7.5);

        GoMoveResult blackPass = board.pass();
        GoMoveResult whitePass = board.pass();

        assertTrue(blackPass.isSuccess());
        assertTrue(whitePass.isSuccess());
        assertEquals(2, board.getConsecutivePasses());
        assertTrue(board.isScoringReady());
        assertNotNull(board.getScoreSummary());

        GoMoveResult resume = board.place(4, 4);
        assertTrue(resume.isSuccess());
        assertEquals(0, board.getConsecutivePasses());
        assertFalse(board.isScoringReady());
        assertEquals(GoStone.BLACK, board.getStone(4, 4));
    }

    @Test
    void shouldUndoMoveAndRestoreCapturesAndTurn() {
        GoBoard board = new GoBoard(9, 7.5);
        board.loadPosition(new String[] {
            ".........",
            "BWB......",
            ".B.......",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            "........."
        }, GoStone.BLACK);

        GoMoveResult result = board.place(0, 1);
        assertTrue(result.isSuccess());

        board.undoMove();

        assertEquals(GoStone.WHITE, board.getStone(1, 1));
        assertEquals(GoStone.EMPTY, board.getStone(0, 1));
        assertEquals(0, board.getBlackCaptures());
        assertEquals(GoStone.BLACK, board.getCurrentTurn());
    }
}
