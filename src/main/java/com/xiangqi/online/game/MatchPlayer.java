package com.xiangqi.online.game;

public final class MatchPlayer {
    private final String userId;
    private final String username;
    private final PlayerSide side;

    public MatchPlayer(String userId, String username, PlayerSide side) {
        this.userId = userId;
        this.username = username;
        this.side = side;
    }

    public String userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public PlayerSide side() {
        return side;
    }
}
