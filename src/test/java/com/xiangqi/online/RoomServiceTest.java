package com.xiangqi.online;

import com.xiangqi.online.auth.AuthUser;
import com.xiangqi.online.game.GameType;
import com.xiangqi.online.room.CreateRoomRequest;
import com.xiangqi.online.room.InMemoryRoomRepository;
import com.xiangqi.online.room.RoomService;
import com.xiangqi.online.room.RoomSnapshot;
import com.xiangqi.online.room.RoomStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void invitedRoomStartsAfterSecondPlayerJoinsAndBothReady() {
        RoomService roomService = new RoomService(new InMemoryRoomRepository(), clock);
        AuthUser host = new AuthUser("u-host", "host");
        AuthUser guest = new AuthUser("u-guest", "guest");

        RoomSnapshot created = roomService.createRoom(host, new CreateRoomRequest(GameType.XIANGQI, 600, false));
        RoomSnapshot joined = roomService.joinRoom(created.roomId(), guest);
        RoomSnapshot hostReady = roomService.setReady(joined.roomId(), host.id(), true);
        RoomSnapshot started = roomService.setReady(hostReady.roomId(), guest.id(), true);

        assertEquals(RoomStatus.PLAYING, started.status());
        assertNotNull(started.gameId());
        assertEquals(host.id(), started.redPlayer().id());
        assertEquals(guest.id(), started.blackPlayer().id());
        assertTrue(started.roomCode().length() >= 6);
    }
}
