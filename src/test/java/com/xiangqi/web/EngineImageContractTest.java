package com.xiangqi.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class EngineImageContractTest {
    @Test
    void productionImagePinsAndEnablesExternalBoardEngines() throws Exception {
        String dockerfile = read("Dockerfile");
        String compose = read("compose.yaml");
        String pikafishWrapper = read("deploy/engines/pikafish-engine");
        String rapfiWrapper = read("deploy/engines/rapfi-engine");

        assertTrue(dockerfile.contains("Pikafish-2026-01-02"));
        assertTrue(dockerfile.contains("84257063905615919fb4ee6a70273a94843bb6ec04c45e3ac706098838bc1a49"));
        assertTrue(dockerfile.contains("Rapfi-engine.7z"));
        assertTrue(dockerfile.contains("1a3e24024062a153ac079060ee9589a37c6bdd1ecc54fed3908793c519594e05"));
        assertTrue(dockerfile.contains("pikafish-avx2"));
        assertTrue(dockerfile.contains("pbrain-rapfi-linux-clang-avx2"));
        assertTrue(dockerfile.contains("libatomic1"));

        assertTrue(compose.contains("XQ_XIANGQI_ENGINE: PIKAFISH"));
        assertTrue(compose.contains("XQ_XIANGQI_PIKAFISH_CMD: /usr/local/bin/pikafish-engine"));
        assertTrue(compose.contains("XQ_GOMOKU_ENGINE: RAPFI"));
        assertTrue(compose.contains("XQ_GOMOKU_RAPFI_CMD: /usr/local/bin/rapfi-engine"));

        assertTrue(pikafishWrapper.contains("cd /opt/engines/pikafish"));
        assertTrue(rapfiWrapper.contains("cd /opt/engines/rapfi"));
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }
}
