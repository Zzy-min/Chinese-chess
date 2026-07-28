package com.xiangqi.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PikafishDifficultyProfileTest {
    @TempDir
    Path tempDir;

    @Test
    void usesDistinctIncreasingNativeNodeBudgets() {
        PikafishUciEngine.DifficultyProfile easy =
                PikafishUciEngine.difficultyProfile(MinimaxAI.Difficulty.EASY);
        PikafishUciEngine.DifficultyProfile medium =
                PikafishUciEngine.difficultyProfile(MinimaxAI.Difficulty.MEDIUM);
        PikafishUciEngine.DifficultyProfile hard =
                PikafishUciEngine.difficultyProfile(MinimaxAI.Difficulty.HARD);

        assertEquals(1_500L, easy.nodes);
        assertEquals(40_000L, medium.nodes);
        assertEquals(600_000L, hard.nodes);
        assertTrue(easy.nodes < medium.nodes);
        assertTrue(medium.nodes < hard.nodes);
        assertTrue(easy.timeoutMs < medium.timeoutMs);
        assertTrue(medium.timeoutMs < hard.timeoutMs);
    }

    @Test
    void startsAnAbsoluteEngineCommandFromItsOwnDirectory() throws Exception {
        Path executable = Files.createFile(tempDir.resolve("pikafish.exe"));
        ProcessBuilder builder = new ProcessBuilder(executable.toString());

        PikafishUciEngine.configureWorkingDirectory(builder, List.of(executable.toString()));

        assertEquals(tempDir.toFile(), builder.directory());
    }
}
