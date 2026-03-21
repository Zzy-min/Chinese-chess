package com.xiangqi.online.room;

import java.util.Optional;

public interface RoomRepository {
    InMemoryRoomRepository.RoomState save(InMemoryRoomRepository.RoomState state);

    Optional<InMemoryRoomRepository.RoomState> findById(String roomId);

    default Optional<InMemoryRoomRepository.RoomState> findByCode(String roomCode) {
        return Optional.empty();
    }
}
