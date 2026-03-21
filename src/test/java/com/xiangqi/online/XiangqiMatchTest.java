package com.xiangqi.online;

import com.xiangqi.online.game.MatchEvent;
import com.xiangqi.online.game.MatchPlayer;
import com.xiangqi.online.game.PlayerSide;
import com.xiangqi.online.game.XiangqiMatch;
import com.xiangqi.online.game.XiangqiMoveInput;
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
}
