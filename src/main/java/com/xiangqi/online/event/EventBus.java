package com.xiangqi.online.event;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 轻量级事件总线 - 模块间解耦通信
 *
 * 用法：
 *   EventBus bus = new EventBus();
 *   bus.subscribe("room:updated", event -> handleRoomUpdate(event));
 *   bus.publish("room:updated", roomSnapshot);
 */
public final class EventBus {
    private final ConcurrentHashMap<String, List<Consumer<Object>>> subscriptions = new ConcurrentHashMap<>();

    public void publish(String channel, Object event) {
        List<Consumer<Object>> handlers = subscriptions.get(channel);
        if (handlers != null) {
            for (Consumer<Object> handler : handlers) {
                try {
                    handler.accept(event);
                } catch (Exception e) {
                    // 记录日志但不中断其他处理器
                    System.err.println("[EventBus] handler error on " + channel + ": " + e.getMessage());
                }
            }
        }
    }

    public void subscribe(String channel, Consumer<Object> handler) {
        subscriptions.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void unsubscribe(String channel, Consumer<Object> handler) {
        List<Consumer<Object>> handlers = subscriptions.get(channel);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }
}
