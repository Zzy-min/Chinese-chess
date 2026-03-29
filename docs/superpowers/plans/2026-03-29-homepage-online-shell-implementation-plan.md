# Homepage Online Shell Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the online shell the public homepage, reorder homepage modes around AI-first, and add complete endgame presentation plus audio feedback across AI and online play.

**Architecture:** Repoint the root route to the online shell, rewrite the online home composition to act as the public homepage, keep the legacy board page as the dedicated AI play surface, and add a shared endgame presentation layer in both shells with route-correct actions. Keep data APIs stable unless a new frontend behavior strictly needs extra state.

**Tech Stack:** Java Undertow server, static HTML/CSS/JS resources, JUnit, browser verification.

---

## Chunk 1: Route And Resource Contracts

- [ ] Update server/resource tests to expect `/` to serve the online shell homepage.
- [ ] Add/adjust resource contract tests for public-facing homepage copy and AI-first mode ordering.
- [ ] Implement the root-route change in `PublicSiteServer`.
- [ ] Run targeted web contract tests.

## Chunk 2: Homepage Shell Rewrite

- [ ] Rewrite `online/app.js` home rendering to become the public homepage.
- [ ] Remove internal-language copy from the homepage and keep the full top navigation.
- [ ] Reorder homepage mode cards to AI first and online second.
- [ ] Reduce empty space by tightening hero/cards/activity/archive composition.
- [ ] Update `online/app.css` as needed to support the new homepage rhythm.
- [ ] Run targeted frontend resource tests.

## Chunk 3: Endgame UX And Button Fixes

- [ ] Add explicit finished-state action groups for online play and AI practice.
- [ ] Replace AI practice “return to learn” end-state actions with replay/analyze/home actions.
- [ ] Ensure online finished games show replay/analyze/return actions with “play again” primary.
- [ ] Add tests for the new rendered action labels and routing intent where practical.

## Chunk 4: Legacy AI Board Endgame Presentation

- [ ] Add an endgame overlay layer to the legacy AI board page.
- [ ] Trigger one-shot result broadcast, board sealing, and action buttons when game over first appears.
- [ ] Implement missing ceremony helpers and connect them to game-over detection.
- [ ] Improve sound toggle clarity and add result audio feedback.

## Chunk 5: Verification

- [ ] Run targeted JUnit tests for web/public resource contracts.
- [ ] Run broader `mvn -q test`.
- [ ] Run a browser check for desktop and mobile-width homepage and endgame flows.
