'use client';

import type { GameType } from '@qiju/core';
import { gameCatalog } from '@qiju/core';
import { useEffect, useState, useTransition } from 'react';

import { ApiError, fetchApiJson } from '../../lib/api-base';

const onlineGameTypes: GameType[] = ['XIANGQI', 'GOMOKU', 'CHESS', 'GO'];

type OnlineRoom = {
  roomId: string;
  roomCode: string;
  gameType: GameType;
  timeControl: string;
  visibility: 'PUBLIC' | 'PRIVATE';
  status: string;
};

type PlayPageProps = {
  apiBase: string;
};

export function PlayPage({ apiBase }: PlayPageProps) {
  const [rooms, setRooms] = useState<OnlineRoom[]>([]);
  const [gameType, setGameType] = useState<GameType>('XIANGQI');
  const [timeControl, setTimeControl] = useState('10+5');
  const [roomCode, setRoomCode] = useState('');
  const [status, setStatus] = useState('正在加载大厅...');
  const [isPending, startTransition] = useTransition();

  useEffect(() => {
    let active = true;
    fetchApiJson<{ rooms: OnlineRoom[] }>(apiBase, '/api/lobby', { method: 'GET', headers: {} })
      .then((payload) => {
        if (!active) {
          return;
        }
        setRooms(payload.rooms);
        setStatus(payload.rooms.length ? '已连接大厅，创建或加入房间前需要登录。' : '还没有公开房间，先登录后创建一间。');
      })
      .catch(() => {
        if (!active) {
          return;
        }
        setStatus('大厅暂时不可用，请稍后重试。');
      });

    return () => {
      active = false;
    };
  }, [apiBase]);

  function handleCreateRoom() {
    startTransition(() => {
      setStatus('正在创建房间...');
      fetchApiJson<OnlineRoom>(apiBase, '/api/rooms', {
        method: 'POST',
        body: JSON.stringify({ gameType, timeControl, visibility: 'PUBLIC' })
      })
        .then((room) => {
          setRooms((current) => [room, ...current]);
          setStatus(`房间已创建：${room.roomCode}`);
        })
        .catch((error) => {
          if (error instanceof ApiError && error.status === 401) {
            setStatus('请先登录后再创建房间。');
            return;
          }
          setStatus('房间创建失败，请稍后再试。');
        });
    });
  }

  function handleJoinByCode() {
    startTransition(() => {
      setStatus('正在查找房间...');
      fetchApiJson<OnlineRoom>(apiBase, '/api/rooms/join-by-code', {
        method: 'POST',
        body: JSON.stringify({ roomCode })
      })
        .then((room) => {
          setStatus(`已找到房间：${room.roomCode}`);
          if (!rooms.find((current) => current.roomId === room.roomId)) {
            setRooms((current) => [room, ...current]);
          }
        })
        .catch(() => {
          setStatus('没有找到对应房间码。');
        });
    });
  }

  return (
    <div className="pageShell pageShell--subpage">
      <section className="subpageHero">
        <div>
          <div className="subpageHero__eyebrow">Play</div>
          <h1>在线对局大厅</h1>
          <p>当前在线对局已对中国象棋、五子棋、国际象棋和围棋开放。游客可浏览大厅，登录后才能建房、加入和落子。</p>
        </div>
        <div className="subpageHero__status">{status}</div>
      </section>

      <section className="actionGrid">
        <article className="surfaceCard">
          <h2>创建房间</h2>
          <label className="fieldLabel">
            棋种
            <select aria-label="棋种" value={gameType} onChange={(event) => setGameType(event.target.value as GameType)}>
              {gameCatalog.filter((entry) => onlineGameTypes.includes(entry.type)).map((entry) => (
                <option key={entry.type} value={entry.type}>{entry.label}</option>
              ))}
            </select>
          </label>
          <label className="fieldLabel">
            时控
            <select aria-label="时控" value={timeControl} onChange={(event) => setTimeControl(event.target.value)}>
              <option value="5+0">5+0</option>
              <option value="10+5">10+5</option>
              <option value="15+10">15+10</option>
            </select>
          </label>
          <button className="primaryAction primaryAction--full" type="button" onClick={handleCreateRoom} disabled={isPending}>创建房间</button>
        </article>

        <article className="surfaceCard">
          <h2>公开房间</h2>
          <label className="fieldLabel">
            房间码
            <input aria-label="房间码" value={roomCode} onChange={(event) => setRoomCode(event.target.value.toUpperCase())} placeholder="输入 8 位房间码" />
          </label>
          <button className="secondaryAction secondaryAction--full" type="button" onClick={handleJoinByCode} disabled={isPending || !roomCode}>通过房间码查找</button>
          <div className="roomList">
            {rooms.map((room) => (
              <div key={room.roomId} className="roomList__item">
                <div>
                  <strong>{room.roomCode}</strong>
                  <div className="roomList__chips"><span className="surfaceChip">{room.gameType}</span></div>
                  <div className="roomList__meta">{room.gameType} · {room.timeControl} · {room.status}</div>
                </div>
                <div className="roomList__actions">
                  <span className="surfaceChip">{room.visibility}</span>
                  <a className="secondaryAction secondaryAction--inline" href={`/play/${room.roomId}`}>进入房间 {room.roomCode}</a>
                </div>
              </div>
            ))}
            {!rooms.length ? <p className="emptyHint">当前没有房间。</p> : null}
          </div>
        </article>
      </section>
    </div>
  );
}
