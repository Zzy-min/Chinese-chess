package com.xiangqi.online.server;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 内存型持久化：不落盘。用于默认构造与单测，保证内存 hub 语义不变。
 * 没有持久化要求的测试环境默认使用它，避免污染磁盘。
 */
public final class NoopRoomPersistence implements RoomPersistence {

    private static final NoopRoomPersistence INSTANCE = new NoopRoomPersistence();

    private NoopRoomPersistence() {
    }

    public static NoopRoomPersistence instance() {
        return INSTANCE;
    }

    @Override
    public void save(List<Map<String, Object>> records) {
        // no-op
    }

    @Override
    public List<Map<String, Object>> load() {
        return Collections.emptyList();
    }
}