package com.xiangqi.online.game;

import com.xiangqi.model.PieceColor;

import java.time.Clock;
import java.time.Instant;

/**
 * 游戏棋钟 - 服务端强制计时
 *
 * 用法：
 *   GameClock clock = new GameClock(600, Clock.systemUTC()); // 10分钟
 *   clock.start(PieceColor.RED);
 *   // ... 走子后
 *   clock.switchSide();
 *   if (clock.isFlagged()) { /* 超时判负 * / }
 */
public class GameClock {
    private final long initialSeconds;
    private final Clock clock;
    private long redRemaining;
    private long blackRemaining;
    private PieceColor activeSide;
    private Instant lastTickAt;
    private boolean running;

    public GameClock(long initialSeconds, Clock clock) {
        this.initialSeconds = initialSeconds;
        this.clock = clock;
        this.redRemaining = initialSeconds;
        this.blackRemaining = initialSeconds;
        this.running = false;
    }

    public void start(PieceColor side) {
        this.activeSide = side;
        this.lastTickAt = clock.instant();
        this.running = true;
    }

    public void switchSide() {
        if (!running) return;
        tick();
        activeSide = activeSide.opposite();
        lastTickAt = clock.instant();
    }

    public boolean isFlagged() {
        tick();
        return redRemaining <= 0 || blackRemaining <= 0;
    }

    public PieceColor flaggedSide() {
        tick();
        if (redRemaining <= 0) return PieceColor.RED;
        if (blackRemaining <= 0) return PieceColor.BLACK;
        return null;
    }

    public long remaining(PieceColor side) {
        tick();
        return side == PieceColor.RED ? redRemaining : blackRemaining;
    }

    public void stop() {
        tick();
        running = false;
    }

    public long initialSeconds() { return initialSeconds; }
    public PieceColor activeSide() { return activeSide; }
    public boolean isRunning() { return running; }
    public Instant lastTickAt() { return lastTickAt; }

    private void tick() {
        if (!running || lastTickAt == null) return;
        Instant now = clock.instant();
        long elapsed = now.getEpochSecond() - lastTickAt.getEpochSecond();
        if (elapsed <= 0) return;

        if (activeSide == PieceColor.RED) {
            redRemaining = Math.max(0, redRemaining - elapsed);
        } else {
            blackRemaining = Math.max(0, blackRemaining - elapsed);
        }
        lastTickAt = now;
    }
}
