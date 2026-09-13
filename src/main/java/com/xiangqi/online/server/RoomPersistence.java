package com.xiangqi.online.server;

import java.util.List;
import java.util.Map;

/**
 * 房间 + 对局运行时状态的最小持久化契约。
 *
 * <p>OnlineRoomHub 会把活动房间（及其最新对局）导出为一组可序列化的记录，
 * 通过本接口落盘；进程重启后由 hub.restoreRoomState() 读回并重建内存状态，
 * 从而保证「重启不丢盘面、GET /rooms|/games 与 WS 订阅可恢复进行中对局」。
 *
 * <p>默认实现是 Noop（不落盘），生产路径使用 FileRoomPersistence 写入 JSON。
 */
public interface RoomPersistence {

    /** 覆盖式保存当前全部活动房间/对局。records 一定可通过 JSON 序列化。 */
    void save(List<Map<String, Object>> records);

    /** 读取上次保存的整套房间记录；无记录时返回空列表。 */
    List<Map<String, Object>> load();
}