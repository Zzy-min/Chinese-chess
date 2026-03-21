package com.xiangqi.online.room;

import com.xiangqi.online.auth.AuthUser;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public class RoomService {
    private final RoomRepository rooms;
    @SuppressWarnings("unused")
    private final Clock clock;

    public RoomService(RoomRepository rooms, Clock clock) {
        this.rooms = Objects.requireNonNull(rooms, "rooms");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RoomSnapshot createRoom(AuthUser host, CreateRoomRequest request) {
        InMemoryRoomRepository.RoomState state = new InMemoryRoomRepository.RoomState(
            UUID.randomUUID().toString(),
            generateRoomCode(),
            RoomStatus.WAITING,
            request.gameType(),
            host
        );
        rooms.save(state);
        return state.snapshot();
    }

    public RoomSnapshot joinRoom(String roomId, AuthUser guest) {
        InMemoryRoomRepository.RoomState state = load(roomId);
        if (state.guestPlayer == null) {
            state.guestPlayer = guest;
            state.status = RoomStatus.FULL;
        }
        rooms.save(state);
        return state.snapshot();
    }

    public RoomSnapshot setReady(String roomId, String userId, boolean ready) {
        InMemoryRoomRepository.RoomState state = load(roomId);
        if (state.hostPlayer.id().equals(userId)) {
            state.hostReady = ready;
        } else if (state.guestPlayer != null && state.guestPlayer.id().equals(userId)) {
            state.guestReady = ready;
        } else {
            throw new IllegalArgumentException("user is not in room");
        }
        if (state.hostReady && state.guestReady && state.guestPlayer != null) {
            state.status = RoomStatus.PLAYING;
            if (state.gameId == null) {
                state.gameId = UUID.randomUUID().toString();
            }
        }
        rooms.save(state);
        return state.snapshot();
    }

    private InMemoryRoomRepository.RoomState load(String roomId) {
        return rooms.findById(roomId).orElseThrow(() -> new IllegalArgumentException("room not found"));
    }

    private String generateRoomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
