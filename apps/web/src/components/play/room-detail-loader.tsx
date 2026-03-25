'use client';

import type { RoomSummary } from '@qiju/core';
import { useEffect, useRef, useState } from 'react';

import { ApiError, fetchApiJson, getWsBase } from '../../lib/api-base';
import { RoomDetailPage } from './room-detail-page';

type RoomSyncEvent = {
  type: 'room.sync';
  room: RoomSummary;
};

export function RoomDetailLoader({ apiBase, roomId }: { apiBase: string; roomId: string }) {
  const [room, setRoom] = useState<RoomSummary | null>(null);
  const [status, setStatus] = useState('房间加载中...');
  const [syncHint, setSyncHint] = useState('正在建立同步...');
  const reconnectTimer = useRef<number | null>(null);

  useEffect(() => {
    let active = true;
    let socket: WebSocket | null = null;

    const load = () => {
      fetchApiJson<RoomSummary>(apiBase, `/api/rooms/${roomId}`, { method: 'GET', headers: {} })
        .then((payload) => {
          if (!active) {
            return;
          }
          setRoom(payload);
          setSyncHint('已通过 HTTP 取得初始状态，等待 WebSocket 同步。');
        })
        .catch((error) => {
          if (!active) {
            return;
          }
          if (error instanceof ApiError && error.status === 404) {
            setStatus('房间不存在。');
          } else {
            setStatus('房间不存在或暂时不可用。');
          }
          setSyncHint('初始加载失败，正在等待重试。');
        });
    };

    const connect = () => {
      if (!active) {
        return;
      }
      setSyncHint('正在连接 WebSocket...');
      socket = new WebSocket(`${getWsBase(apiBase)}/ws`);

      socket.addEventListener('open', () => {
        setSyncHint('WebSocket 已连接，等待房间状态推送。');
        socket?.send(JSON.stringify({ type: 'subscribe', roomId }));
      });

      socket.addEventListener('message', (event) => {
        try {
          const payload = JSON.parse(String(event.data)) as RoomSyncEvent;
          if (payload.type === 'room.sync' && payload.room?.roomId === roomId) {
            setRoom(payload.room);
            setSyncHint(`最近同步：${new Date().toLocaleTimeString('zh-CN', { hour12: false })}`);
          }
        } catch {
          setSyncHint('收到无法识别的同步消息。');
        }
      });

      socket.addEventListener('close', () => {
        if (!active) {
          return;
        }
        setSyncHint('WebSocket 已断开，正在重连...');
        reconnectTimer.current = window.setTimeout(connect, 1000);
      });

      socket.addEventListener('error', () => {
        setSyncHint('WebSocket 连接异常，等待重连。');
      });
    };

    load();
    connect();

    return () => {
      active = false;
      socket?.close();
      if (reconnectTimer.current !== null) {
        window.clearTimeout(reconnectTimer.current);
      }
    };
  }, [apiBase, roomId]);

  if (!room) {
    return (
      <div className="pageShell pageShell--subpage">
        <section className="subpageHero">
          <div>
            <div className="subpageHero__eyebrow">Room</div>
            <h1>{status}</h1>
          </div>
          <div className="syncHint">{syncHint}</div>
        </section>
      </div>
    );
  }

  return <RoomDetailPage apiBase={apiBase} room={room} syncHint={syncHint} />;
}
