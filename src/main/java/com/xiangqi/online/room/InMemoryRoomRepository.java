package com.xiangqi.online.room;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRoomRepository implements RoomRepository {
    private final Map<String, RoomState> rooms = new ConcurrentHashMap<>();

    @Override
    public RoomState save(RoomState state) {
        rooms.put(state.roomId, state);
        return state;
    }

    @Override
    public Optional<RoomState> findById(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    @Override
    public Optional<RoomState> findByCode(String roomCode) {
        for (RoomState state : rooms.values()) {
            if (state.roomCode.equalsIgnoreCase(roomCode)) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }

    static final class RoomState {
        final String roomId;
        final String roomCode;
        RoomStatus status;
        String gameId;
        final com.xiangqi.online.game.GameType gameType;
        final com.xiangqi.online.auth.AuthUser hostPlayer;
        com.xiangqi.online.auth.AuthUser guestPlayer;
        boolean hostReady;
        boolean guestReady;

        RoomState(
            String roomId,
            String roomCode,
            RoomStatus status,
            com.xiangqi.online.game.GameType gameType,
            com.xiangqi.online.auth.AuthUser hostPlayer
        ) {
            this.roomId = roomId;
            this.roomCode = roomCode;
            this.status = status;
            this.gameType = gameType;
            this.hostPlayer = hostPlayer;
        }

        RoomSnapshot snapshot() {
            return new RoomSnapshot(
                roomId,
                roomCode,
                gameId,
                status,
                hostPlayer,
                guestPlayer,
                hostPlayer,
                guestPlayer,
                hostReady,
                guestReady
            );
        }
    }
}
