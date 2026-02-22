package com.xiangqi.model.gomoku;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GomokuBoardForbiddenTest {

    @Test
    void shouldDetectOverlineForbiddenForBlack() {
        GomokuBoard board = new GomokuBoard();
        String[] rows = emptyRows();
        rows[7] = "...BBBB.BB.....";
        board.setBoardForTest(rows, GomokuStone.BLACK);

        String reason = board.getForbiddenReasonForBlack(7, 7);
        assertEquals("长连禁手", reason);
    }

    @Test
    void shouldAllowExactFiveForBlack() {
        GomokuBoard board = new GomokuBoard();
        String[] rows = emptyRows();
        rows[7] = "...BBBB........";
        board.setBoardForTest(rows, GomokuStone.BLACK);

        GomokuPlaceResult place = board.place(7, 7, true);
        assertTrue(place.isSuccess());
        assertEquals(GomokuStone.BLACK, board.getWinner());
    }

    @Test
    void shouldDetectDoubleFourForbiddenForBlack() {
        GomokuBoard board = new GomokuBoard();
        String[] rows = emptyRows();
        rows[7] = "....BBB........";
        rows[4] = ".......B.......";
        rows[5] = ".......B.......";
        rows[6] = ".......B.......";
        board.setBoardForTest(rows, GomokuStone.BLACK);

        String reason = board.getForbiddenReasonForBlack(7, 7);
        assertEquals("四四禁手", reason);
    }

    @Test
    void shouldDetectDoubleThreeForbiddenForBlack() {
        GomokuBoard board = new GomokuBoard();
        String[] rows = emptyRows();
        rows[6] = ".......B.......";
        rows[7] = "......B.B......";
        rows[8] = ".......B.......";
        board.setBoardForTest(rows, GomokuStone.BLACK);

        String reason = board.getForbiddenReasonForBlack(7, 7);
        assertEquals("三三禁手", reason);
    }

    @Test
    void shouldNotApplyForbiddenRulesToWhite() {
        GomokuBoard board = new GomokuBoard();
        String[] rows = emptyRows();
        rows[6] = ".......W.......";
        rows[7] = "......W.W......";
        rows[8] = ".......W.......";
        board.setBoardForTest(rows, GomokuStone.WHITE);

        GomokuPlaceResult place = board.place(7, 7, true);
        assertTrue(place.isSuccess());
        assertFalse(place.isForbidden());
    }

    private static String[] emptyRows() {
        String[] rows = new String[GomokuBoard.SIZE];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = "...............";
        }
        return rows;
    }
}

