package com.xiangqi.model.gomoku;

import com.xiangqi.ai.MinimaxAI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RapfiDifficultyProfileTest {
    @Test
    void usesDistinctIncreasingPiskvorkLimits() {
        PiskvorkGomokuEngine.DifficultyProfile easy =
                PiskvorkGomokuEngine.difficultyProfile(MinimaxAI.Difficulty.EASY);
        PiskvorkGomokuEngine.DifficultyProfile medium =
                PiskvorkGomokuEngine.difficultyProfile(MinimaxAI.Difficulty.MEDIUM);
        PiskvorkGomokuEngine.DifficultyProfile hard =
                PiskvorkGomokuEngine.difficultyProfile(MinimaxAI.Difficulty.HARD);

        assertEquals(200, easy.timeoutTurnMs);
        assertEquals(5, easy.maxDepth);
        assertEquals(800, medium.timeoutTurnMs);
        assertEquals(9, medium.maxDepth);
        assertEquals(2_500, hard.timeoutTurnMs);
        assertEquals(15, hard.maxDepth);
        assertTrue(easy.timeoutTurnMs < medium.timeoutTurnMs);
        assertTrue(medium.timeoutTurnMs < hard.timeoutTurnMs);
        assertTrue(easy.maxDepth < medium.maxDepth);
        assertTrue(medium.maxDepth < hard.maxDepth);
    }
}
