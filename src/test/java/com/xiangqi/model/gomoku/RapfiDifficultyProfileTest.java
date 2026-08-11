package com.xiangqi.model.gomoku;

import com.xiangqi.ai.MinimaxAI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RapfiDifficultyProfileTest {
    @TempDir
    Path tempDir;

    @Test
    void usesDistinctIncreasingPiskvorkLimits() {
        PiskvorkGomokuEngine.DifficultyProfile novice =
                PiskvorkGomokuEngine.difficultyProfile(GomokuDifficultyProfile.NOVICE);
        PiskvorkGomokuEngine.DifficultyProfile easy =
                PiskvorkGomokuEngine.difficultyProfile(GomokuDifficultyProfile.EASY);
        PiskvorkGomokuEngine.DifficultyProfile medium =
                PiskvorkGomokuEngine.difficultyProfile(GomokuDifficultyProfile.MEDIUM);
        PiskvorkGomokuEngine.DifficultyProfile hard =
                PiskvorkGomokuEngine.difficultyProfile(GomokuDifficultyProfile.HARD);
        PiskvorkGomokuEngine.DifficultyProfile master =
                PiskvorkGomokuEngine.difficultyProfile(GomokuDifficultyProfile.MASTER);

        assertEquals(120, novice.timeoutTurnMs);
        assertEquals(3, novice.maxDepth);
        assertEquals(220, easy.timeoutTurnMs);
        assertEquals(5, easy.maxDepth);
        assertEquals(480, medium.timeoutTurnMs);
        assertEquals(7, medium.maxDepth);
        assertEquals(1_200, hard.timeoutTurnMs);
        assertEquals(11, hard.maxDepth);
        assertEquals(2_500, master.timeoutTurnMs);
        assertEquals(15, master.maxDepth);
        assertTrue(novice.timeoutTurnMs < easy.timeoutTurnMs);
        assertTrue(easy.timeoutTurnMs < medium.timeoutTurnMs);
        assertTrue(medium.timeoutTurnMs < hard.timeoutTurnMs);
        assertTrue(hard.timeoutTurnMs < master.timeoutTurnMs);
        assertTrue(GomokuDifficultyProfile.NOVICE.preferBuiltin());
        assertTrue(GomokuDifficultyProfile.EASY.preferBuiltin());
        assertTrue(GomokuDifficultyProfile.MEDIUM.preferBuiltin());
        assertTrue(!GomokuDifficultyProfile.HARD.preferBuiltin());
        assertTrue(GomokuDifficultyProfile.NOVICE.blunderRate() > GomokuDifficultyProfile.EASY.blunderRate());
        assertTrue(easy.maxDepth < medium.maxDepth);
        assertTrue(medium.maxDepth < hard.maxDepth);
        assertTrue(hard.maxDepth < master.maxDepth);
    }

    @Test
    void startsAnAbsoluteEngineCommandFromItsOwnDirectory() throws Exception {
        Path executable = Files.createFile(tempDir.resolve("rapfi.exe"));
        ProcessBuilder builder = new ProcessBuilder(executable.toString());

        PiskvorkGomokuEngine.configureWorkingDirectory(builder, List.of(executable.toString()));

        assertEquals(tempDir.toFile(), builder.directory());
    }

    @Test
    void marksFallbackWhenAHighLevelExternalEngineCannotStart() {
        String previousEngine = System.getProperty("xq.gomoku.engine");
        String previousCommand = System.getProperty("xq.gomoku.rapfi.cmd");
        System.setProperty("xq.gomoku.engine", "RAPFI");
        System.setProperty("xq.gomoku.rapfi.cmd", tempDir.resolve("missing-rapfi.exe").toString());
        ConfigurableGomokuEngine engine = new ConfigurableGomokuEngine();
        try {
            engine.setDifficultyProfile(GomokuDifficultyProfile.HARD);

            int[] move = engine.findBestMove(new GomokuBoard(), GomokuStone.BLACK, GomokuDifficultyProfile.HARD);

            assertTrue(move != null);
            assertTrue(engine.isEngineFallback());
            assertEquals("builtin", engine.getEngineId());
        } finally {
            engine.close();
            restoreProperty("xq.gomoku.engine", previousEngine);
            restoreProperty("xq.gomoku.rapfi.cmd", previousCommand);
        }
    }

    private void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
