package com.xiangqi.online.room;

import com.xiangqi.online.auth.AuthUser;

public final class RoomSnapshot {
    private final String roomId;
    private final String roomCode;
    private final String gameId;
    private final RoomStatus status;
    private final RoomVisibility visibility;
    private final AuthUser hostPlayer;
    private final AuthUser guestPlayer;
    private final AuthUser redPlayer;
    private final AuthUser blackPlayer;
    private final boolean hostReady;
    private final boolean guestReady;

    public RoomSnapshot(
        String roomId,
        String roomCode,
        String gameId,
        RoomStatus status,
        RoomVisibility visibility,
        AuthUser hostPlayer,
        AuthUser guestPlayer,
        AuthUser redPlayer,
        AuthUser blackPlayer,
        boolean hostReady,
        boolean guestReady
    ) {
        this.roomId = roomId;
        this.roomCode = roomCode;
        this.gameId = gameId;
        this.status = status;
        this.visibility = visibility;
        this.hostPlayer = hostPlayer;
        this.guestPlayer = guestPlayer;
        this.redPlayer = redPlayer;
        this.blackPlayer = blackPlayer;
        this.hostReady = hostReady;
        this.guestReady = guestReady;
    }

    public String roomId() {
        return roomId;
    }

    public String roomCode() {
        return roomCode;
    }

    public String gameId() {
        return gameId;
    }

    public RoomStatus status() {
        return status;
    }

    public RoomVisibility visibility() {
        return visibility;
    }

    public AuthUser hostPlayer() {
        return hostPlayer;
    }

    public AuthUser guestPlayer() {
        return guestPlayer;
    }

    public AuthUser redPlayer() {
        return redPlayer;
    }

    public AuthUser blackPlayer() {
        return blackPlayer;
    }

    public boolean hostReady() {
        return hostReady;
    }

    public boolean guestReady() {
        return guestReady;
    }
}
