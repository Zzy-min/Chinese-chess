package com.xiangqi.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyHomepageResourceContractTest {

    @Test
    void legacyHomepageDeclaresBoardFacesAndSyncsCurrentGame() throws Exception {
        String html = readResource("/web/index.html");
        String js = readResource("/web/app.js");
        String css = readResource("/web/app.css");

        assertTrue(html.contains("data-board-face=\"XIANGQI\""));
        assertTrue(html.contains("data-board-face=\"GOMOKU\""));
        assertTrue(html.contains("id=\"flipStage\""));
        assertTrue(js.contains("flipStage.dataset.game"));
        assertTrue(css.contains(".flipStage.gomokuFace [data-board-face=\"GOMOKU\"]"));
        assertTrue(css.contains(".flipStage:not(.gomokuFace) [data-board-face=\"XIANGQI\"]"));
    }

    private String readResource(String path) throws IOException {
        try (InputStream input = LegacyHomepageResourceContractTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("missing resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
