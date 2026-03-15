package com.xiangqi.model.go;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoScenarioLoaderTest {

    @Test
    void shouldLoadBundledGoScenarios() {
        List<GoScenario> scenarios = GoScenarioLoader.loadAll();

        assertNotNull(scenarios);
        assertFalse(scenarios.isEmpty());
        assertTrue(scenarios.stream().anyMatch(s -> "角部提子".equals(s.getName())));
        assertTrue(scenarios.stream().allMatch(s -> s.getRows().length == s.getSize()));
    }
}
