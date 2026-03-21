package com.xiangqi.online.room;

import com.xiangqi.online.game.GameType;

public final class CreateRoomRequest {
    private final GameType gameType;
    private final int initialTimeSeconds;
    private final boolean isPublic;

    public CreateRoomRequest(GameType gameType, int initialTimeSeconds, boolean isPublic) {
        this.gameType = gameType;
        this.initialTimeSeconds = initialTimeSeconds;
        this.isPublic = isPublic;
    }

    public GameType gameType() {
        return gameType;
    }

    public int initialTimeSeconds() {
        return initialTimeSeconds;
    }

    public boolean isPublic() {
        return isPublic;
    }
}
