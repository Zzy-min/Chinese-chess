package com.xiangqi.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyHomepageResourceContractTest {

    @Test
    void legacyHomepageDeclaresBoardFacesAndSyncsCurrentGame() throws Exception {
        String html = readResource("/web/index.html");
        String js = readResource("/web/app.js");
        String css = readResource("/web/app.css");
        String onlineJs = readResource("/online/app.js");

        assertTrue(html.contains("data-board-face=\"XIANGQI\""));
        assertTrue(html.contains("data-board-face=\"GOMOKU\""));
        assertTrue(html.contains("id=\"flipStage\""));
        assertTrue(js.contains("flipStage.dataset.game"));
        assertTrue(css.contains(".flipStage.gomokuFace [data-board-face=\"GOMOKU\"]"));
        assertTrue(css.contains(".flipStage:not(.gomokuFace) [data-board-face=\"XIANGQI\"]"));
        assertFalse(onlineJs.contains("window.location.href = '/home-ai';"));
        assertTrue(onlineJs.contains("在线 AI 练习"));
        assertTrue(onlineJs.contains("data-action=\"quick-start-ai-practice\""));
        assertTrue(onlineJs.contains("async function quickStartAiPractice"));
        assertTrue(onlineJs.contains("xiangqiBoardRiver"));
        assertTrue(onlineJs.contains("function normalizeXiangqiPiece"));
        assertTrue(onlineJs.contains("function isXiangqiMarkerPoint"));
        assertTrue(onlineJs.contains("function renderPlayableBoardByGameType"));
        assertTrue(onlineJs.contains("function renderAnalysisBoardByGameType"));
        assertTrue(onlineJs.contains("function renderUnsupportedBoard"));
        assertTrue(onlineJs.contains("if (!game || !isSupportedGameType(game.gameType))"));
        assertTrue(onlineJs.contains("return renderUnsupportedBoard(gameType, 'analysis');"));
        assertTrue(onlineJs.contains("function renderLearnPage"));
        assertTrue(onlineJs.contains("function renderWatchPage"));
        assertTrue(onlineJs.contains("function renderCommunityPage"));
        assertTrue(onlineJs.contains("data-action=\"start-puzzle-practice\""));
        assertTrue(onlineJs.contains("async function startPracticeFromPuzzle"));
        assertTrue(onlineJs.contains("function isValidXiangqiInitialFen"));
        assertTrue(onlineJs.contains("async function loadLearnContent"));
        assertTrue(onlineJs.contains("async function loadWatchOverview"));
        assertTrue(onlineJs.contains("async function loadCommunityLeaderboard"));
        assertTrue(onlineJs.contains("const LEARN_SUB_ROUTES = ['tutorials', 'puzzles', 'practice'];"));
        assertTrue(onlineJs.contains("const PUZZLE_THEMES = ['ALL', 'TACTIC', 'MATE', 'POSITION', 'ENDGAME_FEN'];"));
        assertTrue(onlineJs.contains("function resolveLearnSubRoute"));
        assertTrue(onlineJs.contains("function resolvePuzzleTheme"));
        assertTrue(onlineJs.contains("function renderPuzzleThemeTiles"));
        assertTrue(onlineJs.contains("learnThemeTiles"));
        assertTrue(onlineJs.contains("href=\"#/learn/puzzles/${theme}\""));
        assertTrue(onlineJs.contains("parts[2] || 'ALL'"));
        assertTrue(onlineJs.contains("route-practice-locked"));
        assertTrue(onlineJs.contains("boardPane--practice"));
        assertTrue(onlineJs.contains("practiceInfoLine"));
        assertTrue(onlineJs.contains("learnSummaryOneLine"));
        assertTrue(onlineJs.contains("moveInFlight"));
        assertTrue(onlineJs.contains("moveRequestToken"));
        assertTrue(onlineJs.contains("pendingMoveMarker"));
        assertTrue(onlineJs.contains("function createPendingMoveMarker"));
        assertTrue(onlineJs.contains("function fitPracticeBoardToViewport"));
        assertTrue(onlineJs.contains("function tickLiveGameClock"));
        assertTrue(onlineJs.contains("function syncPracticePolling"));
        assertTrue(onlineJs.contains("function startPracticePolling"));
        assertTrue(onlineJs.contains("function schedulePracticePoll"));
        assertTrue(onlineJs.contains("async function pollPracticeGame"));
        assertTrue(onlineJs.contains("function renderXiangqiLastMoveMarker"));
        assertTrue(onlineJs.contains("function renderGomokuLastMoveMarker"));
        assertTrue(onlineJs.contains("function activeAiMoveHint"));
        assertTrue(onlineJs.contains("function maybeRememberAiMove"));
        assertTrue(onlineJs.contains("function shouldFlipOnlineBoardForViewer"));
        assertTrue(onlineJs.contains("function mapDisplayToBoardPosition"));
        assertTrue(onlineJs.contains("function mapBoardToDisplayPosition"));
        assertTrue(onlineJs.contains("function resolveBoardRenderOptions"));
        assertTrue(onlineJs.contains("function sideLabel"));
        assertTrue(onlineJs.contains("function turnTextForViewer"));
        assertTrue(onlineJs.contains("function onlineGameStatusText"));
        assertTrue(onlineJs.contains("data-river-layer=\"under-piece\""));
        assertTrue(onlineJs.contains("data-live-board-host"));
        assertTrue(onlineJs.contains("data-live-status"));
        assertTrue(onlineJs.contains("data-live-game-actions"));
        assertTrue(onlineJs.contains("function refreshLiveBoardSurface"));
        assertTrue(onlineJs.contains("function refreshOnlineGameMetaPills"));
        assertTrue(onlineJs.contains("function patchOnlineGameRealtimeView"));
        assertTrue(onlineJs.contains("xiangqiLastMove--pending"));
        assertTrue(onlineJs.contains("gomokuLastMove--pending"));
        assertTrue(onlineJs.contains("function renderGameEndModal"));
        assertTrue(onlineJs.contains("endGameOverlay"));
        assertTrue(onlineJs.contains("data-action=\"undo-practice\""));
        assertTrue(onlineJs.contains("async function undoPracticeMove"));
        assertTrue(onlineJs.contains("function practiceUndoDisabledReason"));
        assertTrue(onlineJs.contains("function initOnlineAudio"));
        assertTrue(onlineJs.contains("function toggleOnlineSound"));
        assertTrue(onlineJs.contains("function playOnlineSound"));
        assertTrue(onlineJs.contains("xq_online_sound_enabled"));
        assertTrue(js.contains("function applyOptimisticPracticeMove"));
        assertTrue(js.contains("location.pathname==='\\/home-ai'") || js.contains("location.pathname === '/home-ai'"));
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
