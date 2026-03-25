'use client';

import type { GameType, LeaderboardEntry } from '@qiju/core';
import { useEffect, useState } from 'react';

import { fetchApiJson } from '../../lib/api-base';

export function LeaderboardPage({ apiBase }: { apiBase: string }) {
  const [gameType, setGameType] = useState<GameType>('XIANGQI');
  const [entries, setEntries] = useState<LeaderboardEntry[]>([]);
  const [status, setStatus] = useState('正在加载排行榜...');

  useEffect(() => {
    let active = true;
    fetchApiJson<{ entries: LeaderboardEntry[] }>(apiBase, `/api/leaderboard?gameType=${gameType}`, { method: 'GET', headers: {} })
      .then((payload) => {
        if (!active) return;
        setEntries(payload.entries);
        setStatus(payload.entries.length ? '在线对局主榜按积分、胜场和对局数排序。' : '当前棋种还没有排行榜数据。');
      })
      .catch(() => {
        if (!active) return;
        setStatus('排行榜暂时不可用。');
      });
    return () => {
      active = false;
    };
  }, [apiBase, gameType]);

  return (
    <div className="pageShell pageShell--subpage">
      <section className="subpageHero">
        <div>
          <div className="subpageHero__eyebrow">Leaderboard</div>
          <h1>四棋排行榜</h1>
          <p>在线对局主榜与基础战绩聚合都来自归档后的持久化数据。</p>
        </div>
        <div className="subpageHero__status">{status}</div>
      </section>

      <section className="surfaceCard">
        <label className="fieldLabel leaderboardFilter">
          棋种
          <select value={gameType} onChange={(event) => setGameType(event.target.value as GameType)}>
            <option value="XIANGQI">中国象棋</option>
            <option value="GOMOKU">五子棋</option>
            <option value="GO">围棋</option>
            <option value="CHESS">国际象棋</option>
          </select>
        </label>
        <div className="leaderboardList">
          {entries.map((entry) => (
            <div key={`${entry.gameType}-${entry.userId}`} className="leaderboardItem">
              <strong>#{entry.rank} {entry.displayName}</strong>
              <span>{entry.points} 分</span>
              <span>{entry.wins} 胜 / {entry.draws} 和 / {entry.losses} 负</span>
            </div>
          ))}
          {!entries.length ? <p className="emptyHint">暂无榜单数据。</p> : null}
        </div>
      </section>
    </div>
  );
}
